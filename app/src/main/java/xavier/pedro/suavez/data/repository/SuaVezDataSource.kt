package xavier.pedro.suavez.data.repository

import kotlinx.coroutines.flow.Flow
import xavier.pedro.suavez.data.model.Assignment
import xavier.pedro.suavez.data.model.House
import xavier.pedro.suavez.data.model.Member
import xavier.pedro.suavez.data.model.PreferenceLevel
import xavier.pedro.suavez.data.model.Task
import xavier.pedro.suavez.data.model.User
import xavier.pedro.suavez.ui.state.TaskCategory
import xavier.pedro.suavez.ui.state.TaskDeadline
import xavier.pedro.suavez.ui.state.TaskFrequency
import xavier.pedro.suavez.ui.state.TestProfile
import xavier.pedro.suavez.ui.state.TestProfileSession

/**
 * Porta única de dados usada pelo [xavier.pedro.suavez.ui.state.SuaVezStateHolder]
 * para desacoplar os repositórios do Cloud Firestore dos testes unitários em JVM.
 */
interface SuaVezDataSource {
    fun observeHouse(houseId: String): Flow<House?>
    fun observeMembers(houseId: String): Flow<List<Member>>
    fun observeTasks(houseId: String): Flow<List<Task>>
    fun observeWeekAssignments(houseId: String, semana: String): Flow<List<Assignment>>

    suspend fun loadOrBootstrapTestProfile(profile: TestProfile): Result<TestProfileSession>
    suspend fun createHouse(houseName: String, creator: User): Result<House>
    suspend fun joinHouseByCode(inviteCode: String, user: User): Result<House>
    suspend fun renameHouse(houseId: String, newName: String): Result<Unit>
    suspend fun promoteToAdmin(houseId: String, currentAdminUid: String, targetMemberUid: String): Result<Unit>
    suspend fun removeMember(houseId: String, memberUid: String): Result<Unit>
    suspend fun transferAdminAndLeave(houseId: String, leavingAdminUid: String, newAdminUid: String): Result<Unit>
    suspend fun leaveHouseAsMember(houseId: String, memberUid: String): Result<Unit>
    suspend fun destroyHouse(houseId: String): Result<Unit>
    suspend fun updatePreference(houseId: String, uid: String, taskId: String, level: PreferenceLevel): Result<Unit>

    suspend fun saveRecurringTask(
        houseId: String,
        editingId: String?,
        title: String,
        category: TaskCategory,
        points: Int,
        frequency: TaskFrequency,
        createdByUid: String,
        semanaAtual: String,
        createInitialAssignmentInCurrentWeek: Boolean,
        initialDeadline: TaskDeadline?,
    ): Result<Task>

    suspend fun deleteRecurringTask(
        houseId: String,
        recurringTaskId: String,
        semanaAtual: String,
    ): Result<Unit>

    suspend fun ensureCurrentWeekAndRollover(houseId: String): Result<RolloverPlan?>

    suspend fun addWeeklyTask(
        houseId: String,
        title: String,
        category: TaskCategory,
        points: Int,
        deadline: TaskDeadline,
        createdByUid: String,
        semana: String,
    ): Result<Assignment>

    suspend fun claimOpenTask(
        houseId: String,
        assignmentId: String,
        memberUid: String,
        memberName: String,
    ): Result<Unit>

    suspend fun setTaskCompleted(
        houseId: String,
        assignment: Assignment,
        completed: Boolean,
    ): Result<Unit>

    suspend fun distributePendingTasks(
        houseId: String,
        semana: String,
    ): Result<List<GreedyDecision>>
}

class FirestoreSuaVezDataSource(
    private val houseRepository: HouseRepository = HouseRepository(),
    private val taskRepository: TaskRepository = TaskRepository(),
    private val assignmentRepository: AssignmentRepository = AssignmentRepository(),
) : SuaVezDataSource {

    override fun observeHouse(houseId: String): Flow<House?> =
        houseRepository.observeHouse(houseId)

    override fun observeMembers(houseId: String): Flow<List<Member>> =
        houseRepository.observeMembers(houseId)

    override fun observeTasks(houseId: String): Flow<List<Task>> =
        taskRepository.observeTasks(houseId, onlyActive = true)

    override fun observeWeekAssignments(houseId: String, semana: String): Flow<List<Assignment>> =
        assignmentRepository.observeWeekAssignments(houseId, semana)

    override suspend fun loadOrBootstrapTestProfile(profile: TestProfile): Result<TestProfileSession> =
        houseRepository.loadOrBootstrapTestProfile(profile)

    override suspend fun createHouse(houseName: String, creator: User): Result<House> =
        houseRepository.createHouse(houseName, creator)

    override suspend fun joinHouseByCode(inviteCode: String, user: User): Result<House> =
        houseRepository.joinHouseByCode(inviteCode, user)

    override suspend fun renameHouse(houseId: String, newName: String): Result<Unit> =
        houseRepository.renameHouse(houseId, newName)

    override suspend fun promoteToAdmin(
        houseId: String,
        currentAdminUid: String,
        targetMemberUid: String,
    ): Result<Unit> = houseRepository.promoteToAdmin(houseId, currentAdminUid, targetMemberUid)

    override suspend fun removeMember(houseId: String, memberUid: String): Result<Unit> =
        houseRepository.removeMember(houseId, memberUid)

    override suspend fun transferAdminAndLeave(
        houseId: String,
        leavingAdminUid: String,
        newAdminUid: String,
    ): Result<Unit> = houseRepository.transferAdminAndLeave(houseId, leavingAdminUid, newAdminUid)

    override suspend fun leaveHouseAsMember(houseId: String, memberUid: String): Result<Unit> =
        houseRepository.leaveHouseAsMember(houseId, memberUid)

    override suspend fun destroyHouse(houseId: String): Result<Unit> =
        houseRepository.destroyHouse(houseId)

    override suspend fun updatePreference(
        houseId: String,
        uid: String,
        taskId: String,
        level: PreferenceLevel,
    ): Result<Unit> = houseRepository.updatePreference(houseId, uid, taskId, level)

    override suspend fun saveRecurringTask(
        houseId: String,
        editingId: String?,
        title: String,
        category: TaskCategory,
        points: Int,
        frequency: TaskFrequency,
        createdByUid: String,
        semanaAtual: String,
        createInitialAssignmentInCurrentWeek: Boolean,
        initialDeadline: TaskDeadline?,
    ): Result<Task> = taskRepository.saveRecurringTask(
        houseId = houseId,
        editingId = editingId,
        title = title,
        category = category,
        points = points,
        frequency = frequency,
        createdByUid = createdByUid,
        semanaAtual = semanaAtual,
        createInitialAssignmentInCurrentWeek = createInitialAssignmentInCurrentWeek,
        initialDeadline = initialDeadline,
    )

    override suspend fun deleteRecurringTask(
        houseId: String,
        recurringTaskId: String,
        semanaAtual: String,
    ): Result<Unit> = taskRepository.deleteRecurringTask(
        houseId = houseId,
        recurringTaskId = recurringTaskId,
        hardDelete = true,
        semanaAtual = semanaAtual,
    )

    override suspend fun ensureCurrentWeekAndRollover(houseId: String): Result<RolloverPlan?> =
        assignmentRepository.ensureCurrentWeekAndRollover(houseId)

    override suspend fun addWeeklyTask(
        houseId: String,
        title: String,
        category: TaskCategory,
        points: Int,
        deadline: TaskDeadline,
        createdByUid: String,
        semana: String,
    ): Result<Assignment> = assignmentRepository.addWeeklyTask(
        houseId = houseId,
        title = title,
        category = category,
        points = points,
        deadline = deadline,
        createdByUid = createdByUid,
        semana = semana,
    )

    override suspend fun claimOpenTask(
        houseId: String,
        assignmentId: String,
        memberUid: String,
        memberName: String,
    ): Result<Unit> = assignmentRepository.claimOpenTask(
        houseId = houseId,
        assignmentId = assignmentId,
        memberUid = memberUid,
        memberName = memberName,
    )

    override suspend fun setTaskCompleted(
        houseId: String,
        assignment: Assignment,
        completed: Boolean,
    ): Result<Unit> = assignmentRepository.setTaskCompleted(
        houseId = houseId,
        assignment = assignment,
        completed = completed,
    )

    override suspend fun distributePendingTasks(
        houseId: String,
        semana: String,
    ): Result<List<GreedyDecision>> = assignmentRepository.distributePendingTasks(
        houseId = houseId,
        semana = semana,
    )
}
