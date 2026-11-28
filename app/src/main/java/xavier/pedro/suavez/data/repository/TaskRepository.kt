package xavier.pedro.suavez.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import xavier.pedro.suavez.data.model.Assignment
import xavier.pedro.suavez.data.model.AssignmentStatus
import xavier.pedro.suavez.data.model.Category
import xavier.pedro.suavez.data.model.Task
import xavier.pedro.suavez.data.model.TaskWeightLevel
import xavier.pedro.suavez.ui.state.TaskCategory
import xavier.pedro.suavez.ui.state.TaskDeadline
import xavier.pedro.suavez.ui.state.TaskFrequency
import java.util.Locale

class TaskRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {

    private fun houseDoc(houseId: String) =
        firestore.collection(HouseRepository.COLLECTION_HOUSES).document(houseId)

    private fun categoriesCol(houseId: String) =
        houseDoc(houseId).collection(HouseRepository.SUBCOLLECTION_CATEGORIES)

    private fun tasksCol(houseId: String) =
        houseDoc(houseId).collection(HouseRepository.SUBCOLLECTION_TASKS)

    private fun assignmentsCol(houseId: String) =
        houseDoc(houseId).collection(HouseRepository.SUBCOLLECTION_ASSIGNMENTS)

    fun observeCategories(houseId: String): Flow<List<Category>> = callbackFlow {
        val registration = categoriesCol(houseId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val categories = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Category::class.java)?.copy(id = doc.id)
                }.orEmpty()
                trySend(categories)
            }
        awaitClose { registration.remove() }
    }

    fun observeTasks(houseId: String, onlyActive: Boolean = true): Flow<List<Task>> = callbackFlow {
        val query = if (onlyActive) {
            tasksCol(houseId).whereEqualTo("ativa", true)
        } else {
            tasksCol(houseId)
        }
        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            val tasks = snapshot?.documents?.mapNotNull { doc ->
                doc.toObject(Task::class.java)?.copy(id = doc.id)
            }.orEmpty()
            trySend(tasks)
        }
        awaitClose { registration.remove() }
    }

    suspend fun saveCategory(houseId: String, category: Category): Result<Category> = runCatching {
        val cleanName = category.nome.trim()
        require(cleanName.isNotEmpty()) { "O nome da categoria não pode ser vazio." }

        val col = categoriesCol(houseId)
        val docRef = if (category.id.isBlank()) col.document() else col.document(category.id)
        val saved = category.copy(id = docRef.id, nome = cleanName)
        docRef.set(saved, SetOptions.merge()).await()
        saved
    }

    suspend fun deleteCategory(houseId: String, categoryId: String): Result<Unit> = runCatching {
        categoriesCol(houseId).document(categoryId).delete().await()
    }

    suspend fun saveRecurringTask(
        houseId: String,
        editingId: String?,
        title: String,
        category: TaskCategory,
        points: Int,
        frequency: TaskFrequency = TaskFrequency.SEMANAL,
        diasSemana: List<Int> = emptyList(),
        horario: String? = null,
        intervaloValor: Int = 1,
        intervaloUnidade: String = Task.INTERVAL_UNIT_WEEKS,
        diaDoMes: Int? = null,
        semanaReferencia: String = DateCycleUtils.currentIsoWeekKey(),
        createdByUid: String,
        descricao: String = "",
        semanaAtual: String = DateCycleUtils.currentIsoWeekKey(),
    ): Result<Task> = runCatching {
        val cleanTitle = title.trim()
        require(cleanTitle.isNotEmpty()) { "O título da tarefa recorrente não pode ser vazio." }

        val weightLevel = TaskWeightLevel.fromPoints(points)
        val categoriaId = category.name.lowercase(Locale.ROOT)
        val categoriaNome = category.label
        val normalizedDays = diasSemana.filter { it in 1..7 }.distinct()
        val normalizedTime = horario?.trim()?.takeIf { it.isNotEmpty() }
        val normalizedInterval = intervaloValor.coerceAtLeast(1)
        val normalizedUnit = intervaloUnidade.ifBlank { Task.INTERVAL_UNIT_WEEKS }
        val normalizedRef = semanaReferencia.ifBlank { semanaAtual }
        val taskId = editingId?.takeIf { it.isNotBlank() } ?: tasksCol(houseId).document().id
        val taskDoc = tasksCol(houseId).document(taskId)
        val savedTask = Task(
            id = taskId,
            titulo = cleanTitle,
            descricao = descricao.trim(),
            peso = points,
            pesoNivel = weightLevel.name,
            frequencia = frequency.name,
            diasSemana = normalizedDays,
            horario = normalizedTime,
            intervaloValor = normalizedInterval,
            intervaloUnidade = normalizedUnit,
            diaDoMes = diaDoMes,
            semanaReferencia = normalizedRef,
            categoriaId = categoriaId,
            categoriaNome = categoriaNome,
            ativa = true,
            criadaPorUid = createdByUid,
        )
        val occurrences = DateCycleUtils.calculateOccurrencesInWeek(savedTask, semanaAtual)
        val batch = firestore.batch()

        if (editingId.isNullOrBlank()) {
            batch.set(taskDoc, savedTask)
            occurrences.forEach { dateKey ->
                val assignment = AssignmentRepository.buildRecurringOccurrenceAssignment(
                    weekKey = semanaAtual,
                    task = savedTask,
                    dateKey = dateKey,
                    createdByUid = createdByUid,
                )
                batch.set(assignmentsCol(houseId).document(assignment.id), assignment)
            }
        } else {
            val updates = mapOf(
                "titulo" to cleanTitle,
                "descricao" to descricao.trim(),
                "peso" to points,
                "pesoNivel" to weightLevel.name,
                "frequencia" to frequency.name,
                "diasSemana" to normalizedDays,
                "horario" to normalizedTime,
                "intervaloValor" to normalizedInterval,
                "intervaloUnidade" to normalizedUnit,
                "diaDoMes" to diaDoMes,
                "semanaReferencia" to normalizedRef,
                "categoriaId" to categoriaId,
                "categoriaNome" to categoriaNome,
            )
            batch.set(taskDoc, updates, SetOptions.merge())

            val occurrenceSet = occurrences.toSet()
            val currentWeekAssignments = assignmentsCol(houseId)
                .whereEqualTo("semana", semanaAtual)
                .whereEqualTo("taskId", editingId)
                .get()
                .await()

            val existingByDate = mutableMapOf<String, String>()
            currentWeekAssignments.documents.forEach { doc ->
                val rawDate = doc.getString("dataAgendada")
                // Preserva atribuição legada sem dataAgendada quando há 1 ocorrência prevista
                val matchedDate = when {
                    rawDate != null && rawDate in occurrenceSet -> rawDate
                    rawDate == null && occurrences.size == 1 && occurrences.first() !in existingByDate -> occurrences.first()
                    else -> null
                }
                val isDone = doc.getString("status") == AssignmentStatus.CONCLUIDA.name
                if (matchedDate != null) {
                    existingByDate[matchedDate] = doc.id
                    if (!isDone) {
                        batch.update(
                            doc.reference,
                            mapOf(
                                "titulo" to cleanTitle,
                                "descricao" to descricao.trim(),
                                "peso" to points,
                                "dataAgendada" to matchedDate,
                                "horario" to normalizedTime,
                                "categoriaId" to categoriaId,
                                "categoriaNome" to categoriaNome,
                            ),
                        )
                    }
                } else if (!isDone) {
                    batch.delete(doc.reference)
                }
            }

            occurrences.filter { it !in existingByDate }.forEach { dateKey ->
                val assignment = AssignmentRepository.buildRecurringOccurrenceAssignment(
                    weekKey = semanaAtual,
                    task = savedTask,
                    dateKey = dateKey,
                    createdByUid = createdByUid,
                )
                batch.set(assignmentsCol(houseId).document(assignment.id), assignment)
            }
        }

        batch.commit().await()
        savedTask
    }

    suspend fun deleteRecurringTask(
        houseId: String,
        recurringTaskId: String,
        hardDelete: Boolean = false,
        semanaAtual: String = DateCycleUtils.currentIsoWeekKey(),
    ): Result<Unit> = runCatching {
        val docRef = tasksCol(houseId).document(recurringTaskId)
        val batch = firestore.batch()
        if (hardDelete) {
            batch.delete(docRef)
        } else {
            batch.update(docRef, "ativa", false)
        }

        val currentWeekAssignments = assignmentsCol(houseId)
            .whereEqualTo("semana", semanaAtual)
            .whereEqualTo("taskId", recurringTaskId)
            .get()
            .await()

        currentWeekAssignments.documents.forEach { doc ->
            if (doc.getString("status") != AssignmentStatus.CONCLUIDA.name) {
                batch.delete(doc.reference)
            }
        }

        batch.commit().await()
    }
}
