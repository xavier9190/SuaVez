package xavier.pedro.suavez.ui.state

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import xavier.pedro.suavez.data.model.Assignment
import xavier.pedro.suavez.data.model.House
import xavier.pedro.suavez.data.model.HouseRole
import xavier.pedro.suavez.data.model.Member
import xavier.pedro.suavez.data.model.PreferenceLevel
import xavier.pedro.suavez.data.model.User
import xavier.pedro.suavez.data.repository.DateCycleUtils
import xavier.pedro.suavez.data.repository.SuaVezDataSource
import xavier.pedro.suavez.data.repository.toFirestoreUser
import xavier.pedro.suavez.data.repository.toUiAssignment
import xavier.pedro.suavez.data.repository.toUiHouse
import xavier.pedro.suavez.data.repository.toUiMember
import xavier.pedro.suavez.data.repository.toUiRecurringTask
import xavier.pedro.suavez.data.repository.toUiUser
import kotlin.math.roundToInt

/**
 * Perfis padrão fixos de teste usados na tela inicial para validar a persistência
 * no Cloud Firestore antes da integração com Firebase Auth (Etapa 3).
 */
enum class TestProfile(
    val uid: String,
    val nome: String,
    val email: String,
    val role: HouseRole,
) {
    ADMIN(
        uid = "u_admin",
        nome = "Admin (Teste)",
        email = "admin@suavez.app",
        role = HouseRole.ADMIN,
    ),
    MEMBER(
        uid = "u_membro",
        nome = "Membro (Teste)",
        email = "membro@suavez.app",
        role = HouseRole.MEMBER,
    ),
}

data class TestProfileSession(
    val user: User,
    val house: House?,
    val members: List<Member>,
)

data class SnackbarFeedback(
    val id: Long = System.nanoTime(),
    val message: String,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
)

@Stable
class SuaVezStateHolder(
    private val dataSource: SuaVezDataSource,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) {

    var destination by mutableStateOf<AppDestination>(AppDestination.Login)
        private set

    var isAuthenticating by mutableStateOf(false)
        private set

    var activeTab by mutableStateOf(MainTab.TASKS)
        private set

    var currentUser by mutableStateOf<UiUser?>(null)
        private set

    var currentHouse by mutableStateOf<UiHouse?>(null)
        private set

    var members by mutableStateOf<List<UiMember>>(emptyList())
        private set

    var recurringTasks by mutableStateOf<List<UiRecurringTask>>(emptyList())
        private set

    var assignments by mutableStateOf<List<UiAssignment>>(emptyList())
        private set

    var snackbar by mutableStateOf<SnackbarFeedback?>(null)
        private set

    private var rawAssignments: List<Assignment> = emptyList()
    private var currentWeekKey: String = DateCycleUtils.currentIsoWeekKey()
    private var lastToggledAssignmentId: String? = null
    private val claimedInSessionIds = mutableSetOf<String>()

    private var houseObservationJob: Job? = null
    private var weekAssignmentsJob: Job? = null

    val currentMember: UiMember?
        get() = currentUser?.let { user -> members.firstOrNull { it.uid == user.uid } }

    val isAdmin: Boolean
        get() = currentMember?.role == HouseRole.ADMIN

    val myAssignments: List<UiAssignment>
        get() {
            val uid = currentUser?.uid ?: return emptyList()
            val mine = assignments.filter { it.assigneeUid == uid }
            val pending = mine.filter { !it.isDone }
                .sortedBy { it.deadline?.urgencyOrder ?: 99 }
            val done = mine.filter { it.isDone }
            return pending + done
        }

    val openAssignments: List<UiAssignment>
        get() = assignments.filter { it.assigneeUid == null && !it.isDone }

    val openScreenAssignments: List<UiAssignment>
        get() = assignments.filter { (it.assigneeUid == null || it.justClaimedInSession) && !it.isDone }

    val weeklyQuotaPerMember: Int
        get() = calculateWeeklyQuota(recurringTasks, members.size.coerceAtLeast(1))

    val myDonePoints: Int
        get() = myAssignments.filter { it.isDone }.sumOf { it.pontos }

    val myTotalPoints: Int
        get() {
            val assignedSum = myAssignments.sumOf { it.pontos }
            return maxOf(assignedSum, weeklyQuotaPerMember)
        }

    val myPendingPoints: Int
        get() = (myTotalPoints - myDonePoints).coerceAtLeast(0)

    val myPendingCount: Int
        get() = myAssignments.count { !it.isDone }

    val houseDoneCount: Int
        get() = assignments.count { it.isDone }

    val houseTotalCount: Int
        get() = assignments.size

    val houseCompletionPercent: Int
        get() {
            if (houseTotalCount == 0) return 0
            return ((houseDoneCount.toFloat() / houseTotalCount.toFloat()) * 100f).roundToInt()
        }

    fun memberWeeklyPoints(uid: String): Int =
        members.firstOrNull { it.uid == uid }?.monthlyPoints ?: 0

    fun memberPendingCount(uid: String): Int =
        assignments.count { it.assigneeUid == uid && !it.isDone }

    fun memberPendingPoints(uid: String): Int =
        assignments.filter { it.assigneeUid == uid && !it.isDone }.sumOf { it.pontos }

    fun nextInTurnUid(): String? {
        if (members.isEmpty()) return null
        return members.minByOrNull { memberWeeklyPoints(it.uid) + memberPendingPoints(it.uid) }?.uid
    }

    fun rankedMembers(): List<UiMember> =
        members.sortedByDescending { memberWeeklyPoints(it.uid) }

    fun isBalanced(): Boolean =
        isHouseBalanced(members.map { memberWeeklyPoints(it.uid) })

    fun balanceGapPercent(): Int {
        val scores = members.map { memberWeeklyPoints(it.uid) }
        val max = scores.maxOrNull() ?: return 0
        val min = scores.minOrNull() ?: return 0
        if (max <= 0) return 0
        return (((max - min).toDouble() / max.toDouble()) * 100.0).roundToInt()
    }

    // --- Navegação ---

    fun selectTab(tab: MainTab) {
        if (currentHouse == null && (tab == MainTab.TASKS || tab == MainTab.PREFERENCES)) {
            return
        }
        activeTab = tab
        destination = if (currentHouse == null) AppDestination.NoHouse else AppDestination.Main
    }

    fun openOpenTasks() {
        if (currentHouse == null) return
        claimedInSessionIds.clear()
        assignments = assignments.map { it.copy(justClaimedInSession = false) }
        activeTab = MainTab.TASKS
        destination = AppDestination.OpenTasks
    }

    fun navigateBack(): Boolean {
        return when (destination) {
            AppDestination.OpenTasks -> {
                destination = AppDestination.Main
                activeTab = MainTab.TASKS
                true
            }
            AppDestination.Main -> {
                if (activeTab != MainTab.TASKS) {
                    activeTab = MainTab.TASKS
                    true
                } else {
                    false
                }
            }
            AppDestination.NoHouse -> {
                if (activeTab != MainTab.HOUSE) {
                    activeTab = MainTab.HOUSE
                    true
                } else {
                    false
                }
            }
            AppDestination.Login -> false
        }
    }

    // --- Autenticação (perfis padrão de teste) ---

    suspend fun signInAs(profile: TestProfile) {
        isAuthenticating = true
        try {
            dataSource.loadOrBootstrapTestProfile(profile)
                .onSuccess { session ->
                    currentUser = session.user.toUiUser()
                    recurringTasks = emptyList()
                    assignments = emptyList()
                    rawAssignments = emptyList()
                    claimedInSessionIds.clear()
                    val house = session.house
                    if (house == null) {
                        stopObservingHouse()
                        currentHouse = null
                        members = emptyList()
                        activeTab = MainTab.HOUSE
                        destination = AppDestination.NoHouse
                    } else {
                        enterHouseSession(house, session.members)
                    }
                }
                .onFailure { error ->
                    showError("Erro ao entrar", error)
                }
        } finally {
            isAuthenticating = false
        }
    }

    fun signOut() {
        stopObservingHouse()
        currentUser = null
        currentHouse = null
        members = emptyList()
        recurringTasks = emptyList()
        assignments = emptyList()
        rawAssignments = emptyList()
        claimedInSessionIds.clear()
        activeTab = MainTab.TASKS
        destination = AppDestination.Login
    }

    // --- Casa (sem casa -> com casa) ---

    fun createHouse(houseName: String) {
        val user = currentUser ?: return
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
        val user = currentUser ?: return
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
        currentHouse = house.toUiHouse()
        if (initialMembers.isNotEmpty()) {
            members = initialMembers.map { it.toUiMember() }
        }
        val initialWeek = house.semanaAtual.ifBlank { DateCycleUtils.currentIsoWeekKey() }
        currentWeekKey = initialWeek
        activeTab = MainTab.TASKS
        destination = AppDestination.Main

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
                            if (currentHouse != null) {
                                clearHouseForCurrentUser("A casa foi encerrada")
                            }
                            return@collect
                        }
                        currentHouse = house.toUiHouse()
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
                        members = remoteMembers.map { it.toUiMember() }
                    }
            }
            launch {
                dataSource.observeTasks(houseId)
                    .catch { showError("Erro ao sincronizar tarefas recorrentes", it) }
                    .collect { tasks ->
                        recurringTasks = tasks.map { it.toUiRecurringTask() }
                            .sortedBy { it.titulo.lowercase() }
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
                    assignments = list.map { item ->
                        item.toUiAssignment(justClaimedInSession = item.id in claimedInSessionIds)
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
        val houseId = currentHouse?.id ?: return
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
        val houseId = currentHouse?.id ?: return
        val id = lastToggledAssignmentId ?: return
        val target = rawAssignments.firstOrNull { it.id == id } ?: return
        lastToggledAssignmentId = null
        scope.launch {
            dataSource.setTaskCompleted(houseId, target, !target.isConcluida())
                .onFailure { showError("Erro ao desfazer", it) }
        }
    }

    fun claimOpenTask(assignmentId: String) {
        val houseId = currentHouse?.id ?: return
        val user = currentUser ?: return
        val task = assignments.firstOrNull { it.id == assignmentId } ?: return
        claimedInSessionIds.add(assignmentId)
        scope.launch {
            dataSource.claimOpenTask(houseId, assignmentId, user.uid, user.nome)
                .onSuccess {
                    val projectedSum = myAssignments.sumOf { it.pontos } +
                        if (myAssignments.any { it.id == assignmentId }) 0 else task.pontos
                    val msg = if (projectedSum >= weeklyQuotaPerMember) {
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
        val houseId = currentHouse?.id ?: return
        val uid = currentUser?.uid ?: return
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
        if (!isAdmin) return
        val houseId = currentHouse?.id ?: return
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
        if (!isAdmin) return
        val houseId = currentHouse?.id ?: return
        scope.launch {
            dataSource.deleteAssignment(houseId, assignmentId)
                .onSuccess { showMessage("Atividade excluída") }
                .onFailure { showError("Erro ao excluir atividade", it) }
        }
    }

    fun distributePendingTasks() {
        if (!isAdmin) return
        val houseId = currentHouse?.id ?: return
        if (openAssignments.isEmpty() || members.isEmpty()) {
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
        val houseId = currentHouse?.id ?: return
        val uid = currentUser?.uid ?: return
        scope.launch {
            dataSource.updatePreference(houseId, uid, recurringTaskId, level)
                .onSuccess { showMessage("Preferência salva") }
                .onFailure { showError("Erro ao salvar preferência", it) }
        }
    }

    fun getMyPreference(recurringTaskId: String): PreferenceLevel {
        return currentMember?.preferences?.get(recurringTaskId) ?: PreferenceLevel.TANTO_FAZ
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
        if (!isAdmin) return
        val houseId = currentHouse?.id ?: return
        val uid = currentUser?.uid ?: return
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
        if (!isAdmin) return
        val houseId = currentHouse?.id ?: return
        scope.launch {
            dataSource.deleteRecurringTask(houseId, recurringTaskId, currentWeekKey)
                .onSuccess { showMessage("Tarefa recorrente excluída") }
                .onFailure { showError("Erro ao excluir tarefa recorrente", it) }
        }
    }

    fun promoteToAdmin(memberUid: String) {
        if (!isAdmin) return
        val houseId = currentHouse?.id ?: return
        val currentAdminUid = currentUser?.uid ?: return
        val target = members.firstOrNull { it.uid == memberUid } ?: return
        scope.launch {
            dataSource.promoteToAdmin(houseId, currentAdminUid, memberUid)
                .onSuccess { showMessage("${target.nome} agora é Admin da casa") }
                .onFailure { showError("Erro ao transferir administração", it) }
        }
    }

    fun removeMember(memberUid: String) {
        if (!isAdmin || memberUid == currentUser?.uid) return
        val houseId = currentHouse?.id ?: return
        val target = members.firstOrNull { it.uid == memberUid } ?: return
        scope.launch {
            dataSource.removeMember(houseId, memberUid)
                .onSuccess { showMessage("${target.nome} foi removido(a) da casa") }
                .onFailure { showError("Erro ao remover morador", it) }
        }
    }

    // --- Perfil / Configurações da Casa ---

    fun renameHouse(newName: String) {
        if (!isAdmin) return
        val houseId = currentHouse?.id ?: return
        val clean = newName.trim()
        if (clean.isEmpty()) return
        scope.launch {
            dataSource.renameHouse(houseId, clean)
                .onSuccess { showMessage("Casa renomeada para \"$clean\"") }
                .onFailure { showError("Erro ao renomear casa", it) }
        }
    }

    fun transferAdminAndLeave(newAdminUid: String) {
        val houseId = currentHouse?.id ?: return
        val uid = currentUser?.uid ?: return
        scope.launch {
            dataSource.transferAdminAndLeave(houseId, uid, newAdminUid)
                .onSuccess { clearHouseForCurrentUser("Você saiu da casa") }
                .onFailure { showError("Erro ao sair da casa", it) }
        }
    }

    fun leaveHouseAsMember() {
        val houseId = currentHouse?.id ?: return
        val uid = currentUser?.uid ?: return
        scope.launch {
            dataSource.leaveHouseAsMember(houseId, uid)
                .onSuccess { clearHouseForCurrentUser("Você saiu da casa") }
                .onFailure { showError("Erro ao sair da casa", it) }
        }
    }

    fun destroyHouse() {
        if (!isAdmin) return
        val houseId = currentHouse?.id ?: return
        scope.launch {
            dataSource.destroyHouse(houseId)
                .onSuccess { clearHouseForCurrentUser("Casa excluída") }
                .onFailure { showError("Erro ao excluir casa", it) }
        }
    }

    private fun clearHouseForCurrentUser(feedback: String) {
        stopObservingHouse()
        currentHouse = null
        members = emptyList()
        recurringTasks = emptyList()
        assignments = emptyList()
        rawAssignments = emptyList()
        claimedInSessionIds.clear()
        activeTab = MainTab.HOUSE
        destination = AppDestination.NoHouse
        showMessage(feedback)
    }

    fun notifyMessage(message: String) {
        showMessage(message)
    }

    fun clearSnackbar() {
        snackbar = null
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
        snackbar = SnackbarFeedback(
            message = message,
            actionLabel = actionLabel,
            onAction = onAction,
        )
    }
}
