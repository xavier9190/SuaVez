package xavier.pedro.suavez.ui.state

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import xavier.pedro.suavez.data.model.House
import xavier.pedro.suavez.data.model.HouseRole
import xavier.pedro.suavez.data.model.Member
import xavier.pedro.suavez.data.model.PreferenceLevel
import xavier.pedro.suavez.data.model.User
import xavier.pedro.suavez.data.repository.toUiHouse
import xavier.pedro.suavez.data.repository.toUiMember
import xavier.pedro.suavez.data.repository.toUiUser
import kotlin.math.abs
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

fun interface TestProfileGateway {
    suspend fun loadOrBootstrap(profile: TestProfile): Result<TestProfileSession>
}

data class SnackbarFeedback(
    val id: Long = System.nanoTime(),
    val message: String,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
)

@Stable
class SuaVezStateHolder(
    private val testProfileGateway: TestProfileGateway? = null,
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

    var fixedTasks by mutableStateOf<List<UiFixedTask>>(emptyList())
        private set

    var assignments by mutableStateOf<List<UiAssignment>>(emptyList())
        private set

    var snackbar by mutableStateOf<SnackbarFeedback?>(null)
        private set

    private var lastToggledAssignmentId: String? = null
    private var nextIdCounter = 100

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
        get() = calculateWeeklyQuota(fixedTasks, members.size.coerceAtLeast(1))

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

    fun memberWeeklyPoints(uid: String): Int {
        val baseScore = members.firstOrNull { it.uid == uid }?.monthlyPoints ?: 0
        val completedThisWeek = assignments.filter { it.assigneeUid == uid && it.isDone }.sumOf { it.pontos }
        return baseScore + completedThisWeek
    }

    fun memberPendingCount(uid: String): Int =
        assignments.count { it.assigneeUid == uid && !it.isDone }

    fun memberPendingPoints(uid: String): Int =
        assignments.filter { it.assigneeUid == uid && !it.isDone }.sumOf { it.pontos }

    fun nextInTurnUid(): String? {
        val nonAdminsOrAll = members
        if (nonAdminsOrAll.isEmpty()) return null
        return nonAdminsOrAll.minByOrNull { memberWeeklyPoints(it.uid) + memberPendingPoints(it.uid) }?.uid
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
        val gateway = testProfileGateway
        if (gateway == null) {
            loadDemoHouseForUser(
                UiUser(
                    uid = profile.uid,
                    nome = profile.nome,
                    email = profile.email,
                ),
            )
            activeTab = MainTab.TASKS
            destination = AppDestination.Main
            return
        }

        isAuthenticating = true
        try {
            val result = gateway.loadOrBootstrap(profile)
            result.onSuccess { session ->
                currentUser = session.user.toUiUser()
                fixedTasks = emptyList()
                assignments = emptyList()
                val house = session.house
                if (house == null) {
                    currentHouse = null
                    members = emptyList()
                    activeTab = MainTab.HOUSE
                    destination = AppDestination.NoHouse
                } else {
                    currentHouse = house.toUiHouse()
                    members = session.members.map { it.toUiMember() }
                    activeTab = MainTab.TASKS
                    destination = AppDestination.Main
                }
            }.onFailure { error ->
                showMessage("Erro ao entrar: ${error.localizedMessage ?: "tente novamente"}")
            }
        } finally {
            isAuthenticating = false
        }
    }

    fun createAccount(name: String, email: String) {
        val newUid = "u_${nextId()}"
        currentUser = UiUser(uid = newUid, nome = name.trim(), email = email.trim())
        currentHouse = null
        members = emptyList()
        fixedTasks = emptyList()
        assignments = emptyList()
        activeTab = MainTab.HOUSE
        destination = AppDestination.NoHouse
        showMessage("Conta criada! Crie ou entre em uma casa.")
    }

    fun signOut() {
        currentUser = null
        currentHouse = null
        members = emptyList()
        fixedTasks = emptyList()
        assignments = emptyList()
        activeTab = MainTab.TASKS
        destination = AppDestination.Login
    }

    // --- Casa (sem casa -> com casa) ---

    fun createHouse(houseName: String) {
        val user = currentUser ?: return
        val codeSuffix = (1000..9999).random()
        val house = UiHouse(
            id = "house_${nextId()}",
            nome = houseName.trim(),
            codigoConvite = "SV-$codeSuffix",
            semanaNumero = 43,
        )
        val initialFixed = defaultFixedTasksSeed()
        currentHouse = house
        members = listOf(
            UiMember(
                uid = user.uid,
                nome = user.nome,
                role = HouseRole.ADMIN,
                monthlyPoints = 0,
            ),
        )
        fixedTasks = initialFixed
        assignments = initialFixed.take(3).mapIndexed { idx, task ->
            UiAssignment(
                id = "a_${nextId()}",
                fixedTaskId = task.id,
                titulo = task.titulo,
                categoria = task.categoria,
                pontos = task.pontos,
                deadline = if (idx == 0) TaskDeadline.SABADO else TaskDeadline.DOMINGO,
                assigneeUid = null,
            )
        }
        activeTab = MainTab.TASKS
        destination = AppDestination.Main
        showMessage("Casa \"${house.nome}\" criada!")
    }

    fun joinHouseByCode(code: String) {
        val user = currentUser ?: return
        val normalized = code.trim().uppercase()
        loadDemoHouseForUser(user, customCode = normalized.ifBlank { "SV-7824" })
        if (members.none { it.uid == user.uid }) {
            members = members + UiMember(
                uid = user.uid,
                nome = user.nome,
                role = HouseRole.MEMBER,
                monthlyPoints = 20,
            )
        }
        activeTab = MainTab.TASKS
        destination = AppDestination.Main
        showMessage("Você entrou na ${currentHouse?.nome}!")
    }

    // --- Tarefas ---

    fun toggleTaskDone(assignmentId: String) {
        val target = assignments.firstOrNull { it.id == assignmentId } ?: return
        val newDone = !target.isDone
        assignments = assignments.map { item ->
            if (item.id == assignmentId) {
                item.copy(
                    isDone = newDone,
                    subtitleOverride = if (newDone) "feita agora" else null,
                )
            } else {
                item
            }
        }
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

    private fun undoLastToggle() {
        val id = lastToggledAssignmentId ?: return
        val target = assignments.firstOrNull { it.id == id } ?: return
        assignments = assignments.map { item ->
            if (item.id == id) {
                item.copy(isDone = !target.isDone, subtitleOverride = null)
            } else {
                item
            }
        }
        lastToggledAssignmentId = null
    }

    fun claimOpenTask(assignmentId: String) {
        val uid = currentUser?.uid ?: return
        val task = assignments.firstOrNull { it.id == assignmentId } ?: return
        assignments = assignments.map { item ->
            if (item.id == assignmentId) {
                item.copy(assigneeUid = uid, justClaimedInSession = true)
            } else {
                item
            }
        }
        val reachedQuota = myAssignments.sumOf { it.pontos } >= weeklyQuotaPerMember
        val msg = if (reachedQuota) {
            "\"${task.titulo}\" adicionada · Você atingiu sua cota!"
        } else {
            "\"${task.titulo}\" adicionada às suas tarefas"
        }
        showMessage(msg)
    }

    fun addWeeklyTask(
        title: String,
        category: TaskCategory,
        points: Int,
        deadline: TaskDeadline,
    ) {
        val newTask = UiAssignment(
            id = "a_${nextId()}",
            fixedTaskId = null,
            titulo = title.trim(),
            categoria = category,
            pontos = points,
            deadline = deadline,
            assigneeUid = null,
        )
        assignments = listOf(newTask) + assignments
        showMessage("Tarefa \"${newTask.titulo}\" adicionada")
    }

    fun distributePendingTasks() {
        if (!isAdmin) return
        val open = openAssignments.sortedByDescending { it.pontos }
        if (open.isEmpty() || members.isEmpty()) {
            showMessage("Não há tarefas abertas para distribuir")
            return
        }

        val quota = weeklyQuotaPerMember.toDouble()
        val toleranceLimit = quota * 1.10
        val currentLoad = members.associate { m ->
            m.uid to assignments.filter { it.assigneeUid == m.uid }.sumOf { it.pontos }
        }.toMutableMap()

        val updatedMap = mutableMapOf<String, String>()

        for (task in open) {
            val refTaskId = task.fixedTaskId
            fun prefOf(m: UiMember): PreferenceLevel =
                if (refTaskId != null) m.preferences[refTaskId] ?: PreferenceLevel.TANTO_FAZ
                else PreferenceLevel.TANTO_FAZ

            var candidates = members.filter { m ->
                prefOf(m) != PreferenceLevel.ODEIO &&
                    ((currentLoad[m.uid] ?: 0) + task.pontos) <= toleranceLimit
            }
            if (candidates.isEmpty()) {
                candidates = members.filter { m -> prefOf(m) != PreferenceLevel.ODEIO }
            }
            if (candidates.isEmpty()) {
                candidates = members
            }

            val chosen = candidates.sortedWith(
                compareByDescending<UiMember> { m -> quota - (currentLoad[m.uid] ?: 0) }
                    .thenByDescending { m -> if (prefOf(m) == PreferenceLevel.GOSTO) 1 else 0 }
                    .thenBy { m -> memberWeeklyPoints(m.uid) }
            ).first()

            updatedMap[task.id] = chosen.uid
            currentLoad[chosen.uid] = (currentLoad[chosen.uid] ?: 0) + task.pontos
        }

        assignments = assignments.map { item ->
            val assignedTo = updatedMap[item.id]
            if (assignedTo != null) {
                item.copy(assigneeUid = assignedTo, justClaimedInSession = false)
            } else {
                item
            }
        }
        showMessage("${updatedMap.size} tarefa(s) distribuída(s) de forma justa!")
    }

    // --- Preferências ---

    fun setPreference(fixedTaskId: String, level: PreferenceLevel) {
        val uid = currentUser?.uid ?: return
        members = members.map { member ->
            if (member.uid == uid) {
                member.copy(preferences = member.preferences + (fixedTaskId to level))
            } else {
                member
            }
        }
        showMessage("Preferência salva")
    }

    fun getMyPreference(fixedTaskId: String): PreferenceLevel {
        return currentMember?.preferences?.get(fixedTaskId) ?: PreferenceLevel.TANTO_FAZ
    }

    // --- Casa: tarefas fixas e moradores ---

    fun saveFixedTask(
        editingId: String?,
        title: String,
        category: TaskCategory,
        points: Int,
        frequency: TaskFrequency,
        openInCurrentWeek: Boolean = false,
        deadline: TaskDeadline? = null,
    ) {
        if (!isAdmin) return
        val cleanTitle = title.trim()
        if (editingId == null) {
            val newTask = UiFixedTask(
                id = "f_${nextId()}",
                titulo = cleanTitle,
                categoria = category,
                pontos = points,
                frequencia = frequency,
            )
            fixedTasks = fixedTasks + newTask
            if (openInCurrentWeek) {
                val newAssignment = UiAssignment(
                    id = "a_${nextId()}",
                    fixedTaskId = newTask.id,
                    titulo = cleanTitle,
                    categoria = category,
                    pontos = points,
                    deadline = deadline ?: TaskDeadline.DOMINGO,
                    assigneeUid = null,
                )
                assignments = listOf(newAssignment) + assignments
                showMessage("Atividade recorrente \"$cleanTitle\" criada e aberta na semana")
            } else {
                showMessage("Atividade recorrente \"$cleanTitle\" criada")
            }
        } else {
            fixedTasks = fixedTasks.map { task ->
                if (task.id == editingId) {
                    task.copy(
                        titulo = cleanTitle,
                        categoria = category,
                        pontos = points,
                        frequencia = frequency,
                        iconHint = defaultIconFor(category, cleanTitle),
                    )
                } else {
                    task
                }
            }
            showMessage("Alterações salvas")
        }
    }

    fun deleteFixedTask(fixedTaskId: String) {
        if (!isAdmin) return
        fixedTasks = fixedTasks.filterNot { it.id == fixedTaskId }
        showMessage("Tarefa fixa excluída")
    }

    fun promoteToAdmin(memberUid: String) {
        if (!isAdmin) return
        val target = members.firstOrNull { it.uid == memberUid } ?: return
        members = members.map { m ->
            when (m.uid) {
                memberUid -> m.copy(role = HouseRole.ADMIN)
                currentUser?.uid -> m.copy(role = HouseRole.MEMBER)
                else -> m
            }
        }
        showMessage("${target.nome} agora é Admin da casa")
    }

    fun removeMember(memberUid: String) {
        if (!isAdmin || memberUid == currentUser?.uid) return
        val target = members.firstOrNull { it.uid == memberUid } ?: return
        members = members.filterNot { it.uid == memberUid }
        assignments = assignments.map { item ->
            if (item.assigneeUid == memberUid && !item.isDone) {
                item.copy(assigneeUid = null)
            } else {
                item
            }
        }
        showMessage("${target.nome} foi removido(a) da casa")
    }

    // --- Perfil / Configurações da Casa ---

    fun renameHouse(newName: String) {
        if (!isAdmin) return
        val clean = newName.trim()
        if (clean.isEmpty()) return
        currentHouse = currentHouse?.copy(nome = clean)
        showMessage("Casa renomeada para \"$clean\"")
    }

    fun transferAdminAndLeave(newAdminUid: String) {
        val uid = currentUser?.uid ?: return
        members = members
            .filterNot { it.uid == uid }
            .map { if (it.uid == newAdminUid) it.copy(role = HouseRole.ADMIN) else it }
        clearHouseForCurrentUser("Você saiu da casa")
    }

    fun leaveHouseAsMember() {
        val uid = currentUser?.uid ?: return
        members = members.filterNot { it.uid == uid }
        clearHouseForCurrentUser("Você saiu da casa")
    }

    fun destroyHouse() {
        if (!isAdmin) return
        clearHouseForCurrentUser("Casa excluída")
    }

    private fun clearHouseForCurrentUser(feedback: String) {
        currentHouse = null
        members = emptyList()
        fixedTasks = emptyList()
        assignments = emptyList()
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

    private fun nextId(): Int {
        nextIdCounter += 1
        return nextIdCounter
    }

    private fun loadDemoHouseForUser(user: UiUser, customCode: String = "SV-7824") {
        currentUser = user
        currentHouse = UiHouse(
            id = "house_demo",
            nome = "Casa dos amigos",
            codigoConvite = customCode,
            semanaNumero = 43,
        )

        val f1 = UiFixedTask("f_1", "Lavar banheiro", TaskCategory.LIMPEZA, 8, TaskFrequency.SEMANAL, TaskIconHint.DROP)
        val f2 = UiFixedTask("f_2", "Comprar sabão", TaskCategory.COMPRAS, 5, TaskFrequency.QUINZENAL, TaskIconHint.CART)
        val f3 = UiFixedTask("f_3", "Pagar conta de luz", TaskCategory.CONTAS, 3, TaskFrequency.MENSAL, TaskIconHint.RECEIPT)
        val f4 = UiFixedTask("f_4", "Tirar o lixo", TaskCategory.LIMPEZA, 2, TaskFrequency.DIARIA, TaskIconHint.TRASH)
        val f5 = UiFixedTask("f_5", "Limpar a geladeira", TaskCategory.LIMPEZA, 5, TaskFrequency.SEMANAL, TaskIconHint.FRIDGE)
        val f6 = UiFixedTask("f_6", "Organizar cozinha", TaskCategory.LIMPEZA, 8, TaskFrequency.SEMANAL, TaskIconHint.DROP)
        val f7 = UiFixedTask("f_7", "Passar pano na sala", TaskCategory.LIMPEZA, 5, TaskFrequency.SEMANAL, TaskIconHint.DROP)
        val f8 = UiFixedTask("f_8", "Regar as plantas", TaskCategory.OUTRAS, 3, TaskFrequency.SEMANAL, TaskIconHint.LEAF)
        fixedTasks = listOf(f1, f2, f3, f4, f5, f6, f7, f8)

        val anaPrefs = mapOf(
            "f_1" to PreferenceLevel.ODEIO,
            "f_2" to PreferenceLevel.TANTO_FAZ,
            "f_3" to PreferenceLevel.TANTO_FAZ,
            "f_4" to PreferenceLevel.GOSTO,
            "f_5" to PreferenceLevel.TANTO_FAZ,
            "f_6" to PreferenceLevel.ODEIO,
            "f_7" to PreferenceLevel.TANTO_FAZ,
            "f_8" to PreferenceLevel.GOSTO,
        )

        members = listOf(
            UiMember(
                uid = TestProfile.ADMIN.uid,
                nome = if (user.uid == TestProfile.ADMIN.uid) user.nome else "Admin",
                role = HouseRole.ADMIN,
                monthlyPoints = 31, // + 3 concluídos na semana = 34 pts
                preferences = anaPrefs,
            ),
            UiMember(
                uid = TestProfile.MEMBER.uid,
                nome = if (user.uid == TestProfile.MEMBER.uid) user.nome else "Membro",
                role = HouseRole.MEMBER,
                monthlyPoints = 18, // + 14 concluídos = 32 pts
                preferences = mapOf("f_1" to PreferenceLevel.GOSTO),
            ),
            UiMember(
                uid = "u_carla",
                nome = if (user.uid == "u_carla") user.nome else "Carla",
                role = HouseRole.MEMBER,
                monthlyPoints = 18, // + 13 concluídos = 31 pts
                preferences = mapOf("f_4" to PreferenceLevel.ODEIO),
            ),
        )

        assignments = listOf(
            // Admin (2 pendentes + 1 concluída)
            UiAssignment(
                id = "a_1",
                fixedTaskId = "f_1",
                titulo = "Lavar o banheiro",
                categoria = TaskCategory.LIMPEZA,
                pontos = 8,
                deadline = TaskDeadline.SABADO,
                assigneeUid = TestProfile.ADMIN.uid,
                isDone = false,
                iconHint = TaskIconHint.DROP,
            ),
            UiAssignment(
                id = "a_2",
                fixedTaskId = "f_4",
                titulo = "Tirar o lixo",
                categoria = TaskCategory.LIMPEZA,
                pontos = 2,
                deadline = TaskDeadline.HOJE,
                subtitleOverride = "diária",
                assigneeUid = TestProfile.ADMIN.uid,
                isDone = false,
                iconHint = TaskIconHint.TRASH,
            ),
            UiAssignment(
                id = "a_3",
                fixedTaskId = "f_2",
                titulo = "Comprar sabão",
                categoria = TaskCategory.COMPRAS,
                pontos = 3,
                deadline = TaskDeadline.HOJE,
                subtitleOverride = "feita ontem",
                assigneeUid = TestProfile.ADMIN.uid,
                isDone = true,
                iconHint = TaskIconHint.CART,
            ),
            // Tarefas abertas (3)
            UiAssignment(
                id = "a_open_1",
                fixedTaskId = "f_5",
                titulo = "Limpar a geladeira",
                categoria = TaskCategory.LIMPEZA,
                pontos = 5,
                deadline = TaskDeadline.DOMINGO,
                assigneeUid = null,
                isDone = false,
                iconHint = TaskIconHint.FRIDGE,
            ),
            UiAssignment(
                id = "a_open_2",
                fixedTaskId = "f_3",
                titulo = "Pagar conta de luz",
                categoria = TaskCategory.CONTAS,
                pontos = 3,
                deadline = TaskDeadline.HOJE,
                subtitleOverride = "até sexta",
                assigneeUid = null,
                isDone = false,
                iconHint = TaskIconHint.RECEIPT,
            ),
            UiAssignment(
                id = "a_open_3",
                fixedTaskId = "f_8",
                titulo = "Regar as plantas",
                categoria = TaskCategory.OUTRAS,
                pontos = 1,
                deadline = TaskDeadline.SEM_PRAZO,
                subtitleOverride = "sem prazo",
                assigneeUid = null,
                isDone = false,
                iconHint = TaskIconHint.LEAF,
            ),
            // Concluídas dos outros moradores para compor o ritmo da casa (13 de 18 = 72% -> 14 ao concluir mais 1)
            UiAssignment("a_b1", "f_6", "Organizar cozinha", TaskCategory.LIMPEZA, 8, TaskDeadline.HOJE, "feita", TestProfile.MEMBER.uid, isDone = true, iconHint = TaskIconHint.DROP),
            UiAssignment("a_b2", "f_4", "Tirar o lixo (seg)", TaskCategory.LIMPEZA, 2, TaskDeadline.HOJE, "feita", TestProfile.MEMBER.uid, isDone = true, iconHint = TaskIconHint.TRASH),
            UiAssignment("a_b3", "f_4", "Tirar o lixo (ter)", TaskCategory.LIMPEZA, 2, TaskDeadline.HOJE, "feita", TestProfile.MEMBER.uid, isDone = true, iconHint = TaskIconHint.TRASH),
            UiAssignment("a_b4", "f_8", "Regar jardim", TaskCategory.OUTRAS, 2, TaskDeadline.HOJE, "feita", TestProfile.MEMBER.uid, isDone = true, iconHint = TaskIconHint.LEAF),
            UiAssignment("a_b5", "f_4", "Reciclar embalagens", TaskCategory.LIMPEZA, 2, TaskDeadline.HOJE, "feita", TestProfile.MEMBER.uid, isDone = true, iconHint = TaskIconHint.TRASH),
            UiAssignment("a_b6", null, "Trocar filtro de água", TaskCategory.OUTRAS, 2, TaskDeadline.HOJE, "feita", TestProfile.MEMBER.uid, isDone = true, iconHint = TaskIconHint.DROP),
            UiAssignment("a_c1", "f_7", "Passar pano na sala", TaskCategory.LIMPEZA, 5, TaskDeadline.HOJE, "feita", "u_carla", isDone = true, iconHint = TaskIconHint.DROP),
            UiAssignment("a_c2", "f_2", "Comprar detergente", TaskCategory.COMPRAS, 3, TaskDeadline.HOJE, "feita", "u_carla", isDone = true, iconHint = TaskIconHint.CART),
            UiAssignment("a_c3", "f_3", "Pagar internet", TaskCategory.CONTAS, 3, TaskDeadline.HOJE, "feita", "u_carla", isDone = true, iconHint = TaskIconHint.RECEIPT),
            UiAssignment("a_c4", "f_4", "Tirar o lixo (qua)", TaskCategory.LIMPEZA, 2, TaskDeadline.HOJE, "feita", "u_carla", isDone = true, iconHint = TaskIconHint.TRASH),
            UiAssignment("a_c5", "f_8", "Adubar vasos", TaskCategory.OUTRAS, 2, TaskDeadline.HOJE, "feita", "u_carla", isDone = true, iconHint = TaskIconHint.LEAF),
            UiAssignment("a_c6", null, "Limpar varanda", TaskCategory.LIMPEZA, 2, TaskDeadline.HOJE, "feita", "u_carla", isDone = true, iconHint = TaskIconHint.DROP),
        )
    }

    private fun defaultFixedTasksSeed(): List<UiFixedTask> = listOf(
        UiFixedTask("f_1", "Lavar banheiro", TaskCategory.LIMPEZA, 8, TaskFrequency.SEMANAL, TaskIconHint.DROP),
        UiFixedTask("f_2", "Comprar sabão", TaskCategory.COMPRAS, 5, TaskFrequency.QUINZENAL, TaskIconHint.CART),
        UiFixedTask("f_3", "Pagar conta de luz", TaskCategory.CONTAS, 3, TaskFrequency.MENSAL, TaskIconHint.RECEIPT),
        UiFixedTask("f_4", "Tirar o lixo", TaskCategory.LIMPEZA, 2, TaskFrequency.DIARIA, TaskIconHint.TRASH),
    )
}
