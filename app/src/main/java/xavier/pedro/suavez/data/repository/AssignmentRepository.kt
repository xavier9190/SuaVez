package xavier.pedro.suavez.data.repository

import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import xavier.pedro.suavez.data.model.Assignment
import xavier.pedro.suavez.data.model.AssignmentStatus
import xavier.pedro.suavez.data.model.House
import xavier.pedro.suavez.data.model.Member
import xavier.pedro.suavez.data.model.PreferenceLevel
import xavier.pedro.suavez.data.model.Task
import xavier.pedro.suavez.ui.state.TaskCategory
import xavier.pedro.suavez.ui.state.TaskDeadline
import xavier.pedro.suavez.ui.state.TaskFrequency
import java.util.Locale
import kotlin.math.roundToInt

data class GreedyDecision(
    val assignmentId: String,
    val memberUid: String,
    val memberName: String,
)

data class RolloverPlan(
    val newWeekKey: String,
    val rolledOverAssignments: List<Assignment>,
    val catalogAssignments: List<Assignment>,
)

class AssignmentRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {

    private fun houseDoc(houseId: String) =
        firestore.collection(HouseRepository.COLLECTION_HOUSES).document(houseId)

    private fun assignmentsCol(houseId: String) =
        houseDoc(houseId).collection(HouseRepository.SUBCOLLECTION_ASSIGNMENTS)

    private fun membersCol(houseId: String) =
        houseDoc(houseId).collection(HouseRepository.SUBCOLLECTION_MEMBERS)

    private fun tasksCol(houseId: String) =
        houseDoc(houseId).collection(HouseRepository.SUBCOLLECTION_TASKS)

    fun observeWeekAssignments(
        houseId: String,
        semana: String = DateCycleUtils.currentIsoWeekKey(),
    ): Flow<List<Assignment>> = callbackFlow {
        val registration = assignmentsCol(houseId)
            .whereEqualTo("semana", semana)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val items = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Assignment::class.java)?.copy(id = doc.id)
                }.orEmpty()
                trySend(items)
            }
        awaitClose { registration.remove() }
    }

    suspend fun claimOpenTask(
        houseId: String,
        assignmentId: String,
        memberUid: String,
        memberName: String,
    ): Result<Unit> = runCatching {
        assignmentsCol(houseId).document(assignmentId)
            .update(
                mapOf(
                    "status" to AssignmentStatus.ATRIBUIDA.name,
                    "responsavelUid" to memberUid,
                    "responsavelNome" to memberName,
                    "distribuidaAutomaticamente" to false,
                ),
            )
            .await()
    }

    suspend fun unclaimTask(
        houseId: String,
        assignmentId: String,
    ): Result<Unit> = runCatching {
        assignmentsCol(houseId).document(assignmentId)
            .update(
                mapOf(
                    "status" to AssignmentStatus.PENDENTE.name,
                    "responsavelUid" to null,
                    "responsavelNome" to null,
                    "distribuidaAutomaticamente" to false,
                ),
            )
            .await()
    }

    suspend fun addWeeklyTask(
        houseId: String,
        title: String,
        category: TaskCategory,
        points: Int,
        deadline: TaskDeadline,
        createdByUid: String,
        semana: String = DateCycleUtils.currentIsoWeekKey(),
        descricao: String = "",
    ): Result<Assignment> = runCatching {
        val cleanTitle = title.trim()
        require(cleanTitle.isNotEmpty()) { "O título da tarefa não pode ser vazio." }

        val docRef = assignmentsCol(houseId).document()
        val assignment = Assignment(
            id = docRef.id,
            semana = semana,
            taskId = null,
            titulo = cleanTitle,
            descricao = descricao.trim(),
            peso = points,
            deadline = deadline.name,
            categoriaId = category.name.lowercase(Locale.ROOT),
            categoriaNome = category.label,
            status = AssignmentStatus.PENDENTE.name,
            responsavelUid = null,
            responsavelNome = null,
            ehAvulsa = true,
            ehRollover = false,
            urgente = deadline == TaskDeadline.HOJE,
            semanaOrigem = null,
            distribuidaAutomaticamente = false,
            criadaPorUid = createdByUid,
        )
        docRef.set(assignment).await()
        assignment
    }

    suspend fun setTaskCompleted(
        houseId: String,
        assignment: Assignment,
        completed: Boolean,
        monthKey: String = DateCycleUtils.currentMonthKey(),
    ): Result<Unit> = runCatching {
        if (assignment.isConcluida() == completed) return@runCatching
        val assigneeUid = assignment.responsavelUid
        val assignmentRef = assignmentsCol(houseId).document(assignment.id)
        val batch = firestore.batch()

        if (completed) {
            batch.update(
                assignmentRef,
                mapOf(
                    "status" to AssignmentStatus.CONCLUIDA.name,
                    "concluidaEm" to FieldValue.serverTimestamp(),
                ),
            )
            if (!assigneeUid.isNullOrBlank()) {
                val memberRef = membersCol(houseId).document(assigneeUid)
                batch.update(
                    memberRef,
                    FieldPath.of("pontosPorMes", monthKey),
                    FieldValue.increment(assignment.peso.toLong()),
                )
            }
        } else {
            val revertedStatus = if (assigneeUid.isNullOrBlank()) {
                AssignmentStatus.PENDENTE.name
            } else {
                AssignmentStatus.ATRIBUIDA.name
            }
            batch.update(
                assignmentRef,
                mapOf(
                    "status" to revertedStatus,
                    "concluidaEm" to null,
                ),
            )
            if (!assigneeUid.isNullOrBlank()) {
                val memberRef = membersCol(houseId).document(assigneeUid)
                batch.update(
                    memberRef,
                    FieldPath.of("pontosPorMes", monthKey),
                    FieldValue.increment(-assignment.peso.toLong()),
                )
            }
        }

        batch.commit().await()
    }

    suspend fun distributePendingTasks(
        houseId: String,
        semana: String = DateCycleUtils.currentIsoWeekKey(),
        monthKey: String = DateCycleUtils.currentMonthKey(),
    ): Result<List<GreedyDecision>> = runCatching {
        val membersSnap = membersCol(houseId).get().await()
        val members = membersSnap.documents.mapNotNull { doc ->
            doc.toObject(Member::class.java)?.copy(uid = doc.id)
        }
        if (members.isEmpty()) return@runCatching emptyList()

        val tasksSnap = tasksCol(houseId).whereEqualTo("ativa", true).get().await()
        val recurringTasks = tasksSnap.documents.mapNotNull { doc ->
            doc.toObject(Task::class.java)?.copy(id = doc.id)
        }

        val assignmentsSnap = assignmentsCol(houseId)
            .whereEqualTo("semana", semana)
            .get()
            .await()
        val weekAssignments = assignmentsSnap.documents.mapNotNull { doc ->
            doc.toObject(Assignment::class.java)?.copy(id = doc.id)
        }

        val decisions = computeGreedyDistribution(
            weekAssignments = weekAssignments,
            recurringTasks = recurringTasks,
            members = members,
            monthKey = monthKey,
        )
        if (decisions.isEmpty()) return@runCatching emptyList()

        val batch = firestore.batch()
        decisions.forEach { decision ->
            val ref = assignmentsCol(houseId).document(decision.assignmentId)
            batch.update(
                ref,
                mapOf(
                    "status" to AssignmentStatus.ATRIBUIDA.name,
                    "responsavelUid" to decision.memberUid,
                    "responsavelNome" to decision.memberName,
                    "distribuidaAutomaticamente" to true,
                ),
            )
        }
        batch.commit().await()
        decisions
    }

    suspend fun ensureCurrentWeekAndRollover(
        houseId: String,
        targetWeekKey: String = DateCycleUtils.currentIsoWeekKey(),
    ): Result<RolloverPlan?> = runCatching {
        val houseRef = houseDoc(houseId)
        val houseSnap = houseRef.get().await()
        if (!houseSnap.exists()) return@runCatching null

        val house = houseSnap.toObject(House::class.java)?.copy(id = houseSnap.id)
            ?: return@runCatching null
        val previousWeek = house.semanaAtual

        if (previousWeek == targetWeekKey) {
            return@runCatching null
        }

        val previousAssignments = if (previousWeek.isNotBlank()) {
            assignmentsCol(houseId)
                .whereEqualTo("semana", previousWeek)
                .get()
                .await()
                .documents
                .mapNotNull { doc -> doc.toObject(Assignment::class.java)?.copy(id = doc.id) }
        } else {
            emptyList()
        }

        val existingNewWeekAssignments = assignmentsCol(houseId)
            .whereEqualTo("semana", targetWeekKey)
            .get()
            .await()
            .documents
            .mapNotNull { doc -> doc.toObject(Assignment::class.java)?.copy(id = doc.id) }

        val activeCatalogTasks = tasksCol(houseId)
            .whereEqualTo("ativa", true)
            .get()
            .await()
            .documents
            .mapNotNull { doc -> doc.toObject(Task::class.java)?.copy(id = doc.id) }

        val plan = buildWeekRolloverPlan(
            previousWeekKey = previousWeek,
            newWeekKey = targetWeekKey,
            previousWeekAssignments = previousAssignments,
            activeCatalogTasks = activeCatalogTasks,
            existingNewWeekAssignments = existingNewWeekAssignments,
            createdByUid = house.criadaPorUid,
        )

        val batch = firestore.batch()

        // Remover ou substituir pendentes da semana anterior ao fazer rollover para a nova semana
        previousAssignments
            .filter { !it.isConcluida() }
            .forEach { oldIncomplete ->
                batch.delete(assignmentsCol(houseId).document(oldIncomplete.id))
            }

        plan.rolledOverAssignments.forEach { rolloverItem ->
            val docRef = if (rolloverItem.id.isNotBlank()) {
                assignmentsCol(houseId).document(rolloverItem.id)
            } else {
                assignmentsCol(houseId).document()
            }
            batch.set(docRef, rolloverItem.copy(id = docRef.id))
        }

        plan.catalogAssignments.forEach { catalogItem ->
            val docRef = if (catalogItem.id.isNotBlank()) {
                assignmentsCol(houseId).document(catalogItem.id)
            } else {
                assignmentsCol(houseId).document()
            }
            batch.set(docRef, catalogItem.copy(id = docRef.id))
        }

        batch.update(houseRef, "semanaAtual", targetWeekKey)
        batch.commit().await()
        plan
    }

    companion object {

        fun computeGreedyDistribution(
            weekAssignments: List<Assignment>,
            recurringTasks: List<Task>,
            members: List<Member>,
            monthKey: String = DateCycleUtils.currentMonthKey(),
        ): List<GreedyDecision> {
            if (members.isEmpty()) return emptyList()

            val open = weekAssignments
                .filter { it.responsavelUid.isNullOrBlank() && !it.isConcluida() }
                .sortedByDescending { it.peso }
            if (open.isEmpty()) return emptyList()

            val totalCatalogPoints = recurringTasks.sumOf { it.peso }
            val quota = if (totalCatalogPoints > 0) {
                (totalCatalogPoints.toDouble() / members.size).roundToInt().toDouble()
            } else {
                val totalWeekPoints = weekAssignments.sumOf { it.peso }
                (totalWeekPoints.toDouble() / members.size).roundToInt().toDouble()
            }
            val toleranceLimit = quota * 1.10

            val currentLoad = members.associate { m ->
                val load = weekAssignments
                    .filter { it.responsavelUid == m.uid }
                    .sumOf { it.peso }
                m.uid to load
            }.toMutableMap()

            val decisions = mutableListOf<GreedyDecision>()

            for (task in open) {
                val refTaskId = task.taskId
                fun prefOf(m: Member): PreferenceLevel =
                    if (!refTaskId.isNullOrBlank()) m.getPreferenceFor(refTaskId)
                    else PreferenceLevel.TANTO_FAZ

                var candidates = members.filter { m ->
                    prefOf(m) != PreferenceLevel.ODEIO &&
                        ((currentLoad[m.uid] ?: 0) + task.peso) <= toleranceLimit
                }
                if (candidates.isEmpty()) {
                    candidates = members.filter { m -> prefOf(m) != PreferenceLevel.ODEIO }
                }
                if (candidates.isEmpty()) {
                    candidates = members
                }

                val chosen = candidates.sortedWith(
                    compareByDescending<Member> { m -> quota - (currentLoad[m.uid] ?: 0) }
                        .thenByDescending { m -> if (prefOf(m) == PreferenceLevel.GOSTO) 1 else 0 }
                        .thenBy { m -> m.getPointsForMonth(monthKey) }
                ).first()

                decisions.add(
                    GreedyDecision(
                        assignmentId = task.id,
                        memberUid = chosen.uid,
                        memberName = chosen.nome,
                    ),
                )
                currentLoad[chosen.uid] = (currentLoad[chosen.uid] ?: 0) + task.peso
            }

            return decisions
        }

        fun buildWeekRolloverPlan(
            previousWeekKey: String,
            newWeekKey: String,
            previousWeekAssignments: List<Assignment>,
            activeCatalogTasks: List<Task>,
            existingNewWeekAssignments: List<Assignment> = emptyList(),
            createdByUid: String = "",
        ): RolloverPlan {
            val rolledOver = previousWeekAssignments
                .filter { !it.isConcluida() }
                .map { old ->
                    old.copy(
                        id = if (old.id.isNotBlank()) "${newWeekKey}_rollover_${old.id}" else "",
                        semana = newWeekKey,
                        deadline = TaskDeadline.HOJE.name,
                        status = AssignmentStatus.PENDENTE.name,
                        responsavelUid = null,
                        responsavelNome = null,
                        ehRollover = true,
                        urgente = true,
                        semanaOrigem = old.semanaOrigem ?: previousWeekKey.ifBlank { null },
                        distribuidaAutomaticamente = false,
                        concluidaEm = null,
                    )
                }

            val existingCatalogTaskIds = existingNewWeekAssignments
                .mapNotNull { it.taskId }
                .toSet()

            val catalogInstances = activeCatalogTasks
                .filter { it.ativa && it.id !in existingCatalogTaskIds }
                .mapIndexed { index, task ->
                    val freq = task.frequencia.toTaskFrequency()
                    val defaultDeadline = when {
                        freq == TaskFrequency.DIARIA -> TaskDeadline.HOJE
                        index == 0 -> TaskDeadline.SABADO
                        else -> TaskDeadline.DOMINGO
                    }
                    Assignment(
                        id = if (task.id.isNotBlank()) "${newWeekKey}_task_${task.id}" else "",
                        semana = newWeekKey,
                        taskId = task.id,
                        titulo = task.titulo,
                        descricao = task.descricao,
                        peso = task.peso,
                        deadline = defaultDeadline.name,
                        categoriaId = task.categoriaId,
                        categoriaNome = task.categoriaNome,
                        status = AssignmentStatus.PENDENTE.name,
                        responsavelUid = null,
                        responsavelNome = null,
                        ehAvulsa = false,
                        ehRollover = false,
                        urgente = freq == TaskFrequency.DIARIA,
                        semanaOrigem = null,
                        distribuidaAutomaticamente = false,
                        criadaPorUid = task.criadaPorUid.ifBlank { createdByUid },
                        concluidaEm = null,
                    )
                }

            return RolloverPlan(
                newWeekKey = newWeekKey,
                rolledOverAssignments = rolledOver,
                catalogAssignments = catalogInstances,
            )
        }
    }
}
