package xavier.pedro.suavez.ui.state

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import xavier.pedro.suavez.data.model.Assignment
import xavier.pedro.suavez.data.model.House
import xavier.pedro.suavez.data.model.Member
import xavier.pedro.suavez.data.model.PreferenceLevel
import xavier.pedro.suavez.data.repository.AuthSession
import xavier.pedro.suavez.data.repository.DateCycleUtils
import xavier.pedro.suavez.data.repository.SuaVezDataSource
import xavier.pedro.suavez.data.repository.toFirestoreUser
import xavier.pedro.suavez.data.repository.toUiAssignment
import xavier.pedro.suavez.data.repository.toUiHouse
import xavier.pedro.suavez.data.repository.toUiMember
import xavier.pedro.suavez.data.repository.toUiRecurringTask
import xavier.pedro.suavez.data.repository.toUiUser

data class SnackbarFeedback(
    val id: Long = System.nanoTime(),
    val message: String,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
)

class SuaVezStateHolder(
    private val dataSource: SuaVezDataSource,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) {

    private val _uiState = MutableStateFlow(SuaVezUiState())
    val uiState: StateFlow<SuaVezUiState> = _uiState.asStateFlow()

    private val state: SuaVezUiState
        get() = _uiState.value

    private var rawAssignments: List<Assignment> = emptyList()
    private var currentWeekKey: String = DateCycleUtils.currentIsoWeekKey()
    private var lastToggledAssignmentId: String? = null
    private val claimedInSessionIds = mutableSetOf<String>()

    private var houseObservationJob: Job? = null
    private var weekAssignmentsJob: Job? = null
    private var sessionRestoreAttempted: Boolean = false

    // --- Navegação ---

    fun selectTab(tab: MainTab) {
        _uiState.update { current ->
            if (current.currentHouse == null && (tab == MainTab.TASKS || tab == MainTab.PREFERENCES)) {
                return@update current
            }
            current.copy(
                activeTab = tab,
                destination = if (current.currentHouse == null) AppDestination.NoHouse else AppDestination.Main,
            )
        }
    }

    fun openOpenTasks() {
        if (state.currentHouse == null) return
        claimedInSessionIds.clear()
        _uiState.update { current ->
            current.copy(
                assignments = current.assignments.map { it.copy(justClaimedInSession = false) },
                activeTab = MainTab.TASKS,
                destination = AppDestination.OpenTasks,
            )
        }
    }

    fun navigateBack(): Boolean {
        var handled = false
        _uiState.update { current ->
            when (current.destination) {
                AppDestination.OpenTasks -> {
                    handled = true
                    current.copy(destination = AppDestination.Main, activeTab = MainTab.TASKS)
                }
                AppDestination.Main -> {
                    if (current.activeTab != MainTab.TASKS) {
                        handled = true
                        current.copy(activeTab = MainTab.TASKS)
                    } else {
                        handled = false
                        current
                    }
                }
                AppDestination.NoHouse -> {
                    if (current.activeTab != MainTab.HOUSE) {
                        handled = true
                        current.copy(activeTab = MainTab.HOUSE)
                    } else {
                        handled = false
                        current
                    }
                }
                AppDestination.Login -> {
                    handled = false
                    current
                }
            }
        }
        return handled
    }

    // --- Autenticação (Google + Firebase Auth) ---

    fun restoreSessionIfNeeded() {
        if (sessionRestoreAttempted || _uiState.value.currentUser != null || _uiState.value.isAuthenticating) return
        sessionRestoreAttempted = true
        scope.launch {
            restoreSession()
        }
    }

    suspend fun restoreSession() {
        _uiState.update { it.copy(isAuthenticating = true, snackbar = null) }
        try {
            dataSource.restoreSession()
                .onSuccess { session ->
                    if (session != null) {
                        applySession(session)
                    }
                }
                .onFailure { error ->
                    showError("Erro ao restaurar sessão", error)
                }
        } finally {
            _uiState.update { it.copy(isAuthenticating = false) }
        }
    }

    fun startGoogleSignIn(idToken: String) {
        if (_uiState.value.isAuthenticating) return
        scope.launch {
            signInWithGoogleIdToken(idToken)
        }
    }

    suspend fun signInWithGoogleIdToken(idToken: String) {
        _uiState.update { it.copy(isAuthenticating = true, snackbar = null) }
        try {
            dataSource.signInWithGoogleIdToken(idToken)
                .onSuccess { session ->
                    applySession(session)
                }
                .onFailure { error ->
                    showError("Erro ao entrar com Google", error)
                }
        } finally {
            _uiState.update { it.copy(isAuthenticating = false) }
        }
    }

    fun reportAuthError(message: String) {
        showMessage(message)
    }

    private suspend fun applySession(session: AuthSession) {
        rawAssignments = emptyList()
        claimedInSessionIds.clear()
        val house = session.house
        if (house == null) {
            stopObservingHouse()
            _uiState.update { current ->
                current.copy(
                    currentUser = session.user.toUiUser(),
                    currentHouse = null,
                    members = emptyList(),
                    recurringTasks = emptyList(),
                    assignments = emptyList(),
                    activeTab = MainTab.HOUSE,
                    destination = AppDestination.NoHouse,
                    snackbar = null,
                )
            }
        } else {
            _uiState.update { current ->
                current.copy(
                    currentUser = session.user.toUiUser(),
                    recurringTasks = emptyList(),
                    assignments = emptyList(),
                    snackbar = null,
                )
            }
            enterHouseSession(house, session.members)
        }
    }

    fun signOut() {
        stopObservingHouse()
        rawAssignments = emptyList()
        claimedInSessionIds.clear()
        sessionRestoreAttempted = false
        _uiState.update { current ->
            current.copy(
                currentUser = null,
                currentHouse = null,
                members = emptyList(),
                recurringTasks = emptyList(),
                assignments = emptyList(),
                activeTab = MainTab.TASKS,
                destination = AppDestination.Login,
                snackbar = null,
            )
        }
        scope.launch {
            dataSource.signOut()
        }
    }

    // --- Casa (sem casa -> com casa) ---

    fun createHouse(houseName: String) {
        val user = state.currentUser ?: return
        scope.launch {
            dataSource.createHouse(houseName, user.toFirestoreUser())
                .onSuccess { house ->
                    enterHouseSession(house, emptyList())
                    showMessage("Casa \"${house.nome}\" criada!")
                }
                .onFailure { showError("Erro ao criar casa", it) }
        }
    }

    fun joinHouseByCode(code: String) {
        val user = state.currentUser ?: return
        scope.launch {
            dataSource.joinHouseByCode(code, user.toFirestoreUser())
                .onSuccess { house ->
                    enterHouseSession(house, emptyList())
                    showMessage("Você entrou na ${house.nome}!")
                }
                .onFailure { showError("Erro ao entrar na casa", it) }
        }
    }

    private suspend fun enterHouseSession(house: House, initialMembers: List<Member>) {
        val initialWeek = house.semanaAtual.ifBlank { DateCycleUtils.currentIsoWeekKey() }
        currentWeekKey = initialWeek
        _uiState.update { current ->
            current.copy(
                currentHouse = house.toUiHouse(),
                members = if (initialMembers.isNotEmpty()) {
                    initialMembers.map { it.toUiMember() }
                } else {
                    current.members
                },
                activeTab = MainTab.TASKS,
                destination = AppDestination.Main,
            )
        }

        // Sincroniza virada de semana (rollover + catálogo) antes de observar a semana atual
        dataSource.ensureCurrentWeekAndRollover(house.id)
            .onSuccess { plan ->
                if (plan != null) {
                    currentWeekKey = plan.newWeekKey
                }
            }
            .onFailure { showError("Erro ao atualizar ciclo semanal", it) }

        startObservingHouse(house.id, currentWeekKey)
    }

    private fun startObservingHouse(houseId: String, initialWeekKey: String) {
        stopObservingHouse()
        currentWeekKey = initialWeekKey

        houseObservationJob = scope.launch {
            launch {
                dataSource.observeHouse(houseId)
                    .catch { showError("Erro ao sincronizar casa", it) }
                    .collect { house ->
                        if (house == null) {
                            if (state.currentHouse != null) {
                                clearHouseForCurrentUser("A casa foi encerrada")
                            }
                            return@collect
                        }
                        _uiState.update { it.copy(currentHouse = house.toUiHouse()) }
                        val weekKey = house.semanaAtual.ifBlank { DateCycleUtils.currentIsoWeekKey() }
                        if (weekKey != currentWeekKey || weekAssignmentsJob == null) {
                            currentWeekKey = weekKey
                            restartWeekAssignmentsObserver(houseId, weekKey)
                        }
                    }
            }
            launch {
                dataSource.observeMembers(houseId)
                    .catch { showError("Erro ao sincronizar moradores", it) }
                    .collect { remoteMembers ->
                        _uiState.update { it.copy(members = remoteMembers.map { m -> m.toUiMember() }) }
                    }
            }
            launch {
                dataSource.observeTasks(houseId)
                    .catch { showError("Erro ao sincronizar tarefas recorrentes", it) }
                    .collect { tasks ->
                        _uiState.update { current ->
                            current.copy(
                                recurringTasks = tasks.map { it.toUiRecurringTask() }
                                    .sortedBy { it.titulo.lowercase() },
                            )
                        }
                    }
            }
            restartWeekAssignmentsObserver(houseId, initialWeekKey)
        }
    }

    private fun restartWeekAssignmentsObserver(houseId: String, weekKey: String) {
        weekAssignmentsJob?.cancel()
        weekAssignmentsJob = scope.launch {
            dataSource.observeWeekAssignments(houseId, weekKey)
                .catch { showError("Erro ao sincronizar tarefas da semana", it) }
                .collect { list ->
                    rawAssignments = list
                    _uiState.update { current ->
                        current.copy(
                            assignments = list.map { item ->
                                item.toUiAssignment(justClaimedInSession = item.id in claimedInSessionIds)
                            },
                        )
                    }
                }
        }
    }

    private fun stopObservingHouse() {
        weekAssignmentsJob?.cancel()
        weekAssignmentsJob = null
        houseObservationJob?.cancel()
        houseObservationJob = null
    }

    // --- Tarefas ---

    fun toggleTaskDone(assignmentId: String) {
        val houseId = state.currentHouse?.id ?: return
        val target = rawAssignments.firstOrNull { it.id == assignmentId } ?: return
        val newDone = !target.isConcluida()
        scope.launch {
            dataSource.setTaskCompleted(houseId, target, newDone)
                .onSuccess {
                    lastToggledAssignmentId = assignmentId
                    if (newDone) {
                        showMessage(
                            message = "Concluída",
                            actionLabel = "Desfazer",
                            onAction = { undoLastToggle() },
                        )
                    } else {
                        showMessage("Tarefa reaberta")
                    }
                }
                .onFailure { showError("Erro ao atualizar tarefa", it) }
        }
    }

    private fun undoLastToggle() {
        val houseId = state.currentHouse?.id ?: return
        val id = lastToggledAssignmentId ?: return
        val target = rawAssignments.firstOrNull { it.id == id } ?: return
        lastToggledAssignmentId = null
        scope.launch {
            dataSource.setTaskCompleted(houseId, target, !target.isConcluida())
                .onFailure { showError("Erro ao desfazer", it) }
        }
    }

    fun claimOpenTask(assignmentId: String) {
        val snapshot = state
        val houseId = snapshot.currentHouse?.id ?: return
        val user = snapshot.currentUser ?: return
        val task = snapshot.assignments.firstOrNull { it.id == assignmentId } ?: return
        claimedInSessionIds.add(assignmentId)
        scope.launch {
            dataSource.claimOpenTask(houseId, assignmentId, user.uid, user.nome)
                .onSuccess {
                    val current = state
                    val projectedSum = current.myAssignments.sumOf { it.pontos } +
                        if (current.myAssignments.any { it.id == assignmentId }) 0 else task.pontos
                    val msg = if (projectedSum >= current.weeklyQuotaPerMember) {
                        "\"${task.titulo}\" adicionada · Você atingiu sua cota!"
                    } else {
                        "\"${task.titulo}\" adicionada às suas tarefas"
                    }
                    showMessage(msg)
                }
                .onFailure {
                    claimedInSessionIds.remove(assignmentId)
                    showError("Erro ao pegar tarefa", it)
                }
        }
    }

    fun addWeeklyTask(
        title: String,
        category: TaskCategory,
        points: Int,
        deadline: TaskDeadline,
    ) {
        val snapshot = state
        val houseId = snapshot.currentHouse?.id ?: return
        val uid = snapshot.currentUser?.uid ?: return
        scope.launch {
            dataSource.addWeeklyTask(
                houseId = houseId,
                title = title,
                category = category,
                points = points,
                deadline = deadline,
                createdByUid = uid,
                semana = currentWeekKey,
            ).onSuccess { created ->
                showMessage("Tarefa \"${created.titulo}\" adicionada")
            }.onFailure { showError("Erro ao adicionar tarefa", it) }
        }
    }

    fun updateWeeklyAssignment(
        assignmentId: String,
        title: String,
        category: TaskCategory,
        points: Int,
        deadline: TaskDeadline,
    ) {
        if (!state.isAdmin) return
        val houseId = state.currentHouse?.id ?: return
        scope.launch {
            dataSource.updateWeeklyAssignment(
                houseId = houseId,
                assignmentId = assignmentId,
                title = title,
                category = category,
                points = points,
                deadline = deadline,
            ).onSuccess {
                showMessage("Alterações salvas")
            }.onFailure { showError("Erro ao atualizar atividade", it) }
        }
    }

    fun deleteWeeklyAssignment(assignmentId: String) {
        if (!state.isAdmin) return
        val houseId = state.currentHouse?.id ?: return
        scope.launch {
            dataSource.deleteAssignment(houseId, assignmentId)
                .onSuccess { showMessage("Atividade excluída") }
                .onFailure { showError("Erro ao excluir atividade", it) }
        }
    }

    fun distributePendingTasks() {
        val snapshot = state
        if (!snapshot.isAdmin) return
        val houseId = snapshot.currentHouse?.id ?: return
        if (snapshot.openAssignments.isEmpty() || snapshot.members.isEmpty()) {
            showMessage("Não há tarefas abertas para distribuir")
            return
        }
        scope.launch {
            dataSource.distributePendingTasks(houseId, currentWeekKey)
                .onSuccess { decisions ->
                    if (decisions.isEmpty()) {
                        showMessage("Não há tarefas abertas para distribuir")
                    } else {
                        showMessage("${decisions.size} tarefa(s) distribuída(s) de forma justa!")
                    }
                }
                .onFailure { showError("Erro ao distribuir tarefas", it) }
        }
    }

    // --- Preferências ---

    fun setPreference(recurringTaskId: String, level: PreferenceLevel) {
        val snapshot = state
        val houseId = snapshot.currentHouse?.id ?: return
        val uid = snapshot.currentUser?.uid ?: return
        if (snapshot.getMyPreference(recurringTaskId) == level) return
        scope.launch {
            dataSource.updatePreference(houseId, uid, recurringTaskId, level)
                .onSuccess { showMessage("Preferência salva") }
                .onFailure { showError("Erro ao salvar preferência", it) }
        }
    }

    // --- Casa: tarefas recorrentes e moradores ---

    fun saveRecurringTask(
        editingId: String?,
        title: String,
        category: TaskCategory,
        points: Int,
        frequency: TaskFrequency,
        openInCurrentWeek: Boolean = false,
        deadline: TaskDeadline? = null,
    ) {
        val snapshot = state
        if (!snapshot.isAdmin) return
        val houseId = snapshot.currentHouse?.id ?: return
        val uid = snapshot.currentUser?.uid ?: return
        val cleanTitle = title.trim()
        scope.launch {
            dataSource.saveRecurringTask(
                houseId = houseId,
                editingId = editingId,
                title = cleanTitle,
                category = category,
                points = points,
                frequency = frequency,
                createdByUid = uid,
                semanaAtual = currentWeekKey,
                createInitialAssignmentInCurrentWeek = editingId == null && openInCurrentWeek,
                initialDeadline = deadline,
            ).onSuccess {
                when {
                    editingId != null -> showMessage("Alterações salvas")
                    openInCurrentWeek -> showMessage("Atividade recorrente \"$cleanTitle\" criada e aberta na semana")
                    else -> showMessage("Atividade recorrente \"$cleanTitle\" criada")
                }
            }.onFailure { showError("Erro ao salvar atividade recorrente", it) }
        }
    }

    fun deleteRecurringTask(recurringTaskId: String) {
        if (!state.isAdmin) return
        val houseId = state.currentHouse?.id ?: return
        scope.launch {
            dataSource.deleteRecurringTask(houseId, recurringTaskId, currentWeekKey)
                .onSuccess { showMessage("Tarefa recorrente excluída") }
                .onFailure { showError("Erro ao excluir tarefa recorrente", it) }
        }
    }

    fun promoteToAdmin(memberUid: String) {
        val snapshot = state
        if (!snapshot.isAdmin) return
        val houseId = snapshot.currentHouse?.id ?: return
        val currentAdminUid = snapshot.currentUser?.uid ?: return
        val target = snapshot.members.firstOrNull { it.uid == memberUid } ?: return
        scope.launch {
            dataSource.promoteToAdmin(houseId, currentAdminUid, memberUid)
                .onSuccess { showMessage("${target.nome} agora é Admin da casa") }
                .onFailure { showError("Erro ao transferir administração", it) }
        }
    }

    fun removeMember(memberUid: String) {
        val snapshot = state
        if (!snapshot.isAdmin || memberUid == snapshot.currentUser?.uid) return
        val houseId = snapshot.currentHouse?.id ?: return
        val target = snapshot.members.firstOrNull { it.uid == memberUid } ?: return
        scope.launch {
            dataSource.removeMember(houseId, memberUid)
                .onSuccess { showMessage("${target.nome} foi removido(a) da casa") }
                .onFailure { showError("Erro ao remover morador", it) }
        }
    }

    // --- Perfil / Configurações da Casa ---

    fun renameHouse(newName: String) {
        if (!state.isAdmin) return
        val houseId = state.currentHouse?.id ?: return
        val clean = newName.trim()
        if (clean.length < 2) return
        scope.launch {
            dataSource.renameHouse(houseId, clean)
                .onSuccess { showMessage("Casa renomeada para \"$clean\"") }
                .onFailure { showError("Erro ao renomear casa", it) }
        }
    }

    fun transferAdminAndLeave(newAdminUid: String) {
        val snapshot = state
        if (!snapshot.isAdmin) return
        val houseId = snapshot.currentHouse?.id ?: return
        val uid = snapshot.currentUser?.uid ?: return
        if (newAdminUid.isBlank() || newAdminUid == uid) return
        if (snapshot.members.none { it.uid == newAdminUid }) return
        scope.launch {
            dataSource.transferAdminAndLeave(houseId, uid, newAdminUid)
                .onSuccess { clearHouseForCurrentUser("Você saiu da casa") }
                .onFailure { showError("Erro ao sair da casa", it) }
        }
    }

    fun leaveHouseAsMember() {
        val snapshot = state
        if (snapshot.isAdmin) return
        val houseId = snapshot.currentHouse?.id ?: return
        val uid = snapshot.currentUser?.uid ?: return
        scope.launch {
            dataSource.leaveHouseAsMember(houseId, uid)
                .onSuccess { clearHouseForCurrentUser("Você saiu da casa") }
                .onFailure { showError("Erro ao sair da casa", it) }
        }
    }

    fun destroyHouse() {
        if (!state.isAdmin) return
        val houseId = state.currentHouse?.id ?: return
        scope.launch {
            dataSource.destroyHouse(houseId)
                .onSuccess { clearHouseForCurrentUser("Casa excluída") }
                .onFailure { showError("Erro ao excluir casa", it) }
        }
    }

    private fun clearHouseForCurrentUser(feedback: String) {
        stopObservingHouse()
        rawAssignments = emptyList()
        claimedInSessionIds.clear()
        _uiState.update { current ->
            current.copy(
                currentHouse = null,
                members = emptyList(),
                recurringTasks = emptyList(),
                assignments = emptyList(),
                activeTab = MainTab.HOUSE,
                destination = AppDestination.NoHouse,
            )
        }
        showMessage(feedback)
    }

    fun notifyMessage(message: String) {
        showMessage(message)
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbar = null) }
    }

    private fun showError(prefix: String, error: Throwable) {
        val detail = error.localizedMessage?.takeIf { it.isNotBlank() } ?: "tente novamente"
        showMessage("$prefix: $detail")
    }

    private fun showMessage(
        message: String,
        actionLabel: String? = null,
        onAction: (() -> Unit)? = null,
    ) {
        _uiState.update { current ->
            current.copy(
                snackbar = SnackbarFeedback(
                    message = message,
                    actionLabel = actionLabel,
                    onAction = onAction,
                ),
            )
        }
    }
}
