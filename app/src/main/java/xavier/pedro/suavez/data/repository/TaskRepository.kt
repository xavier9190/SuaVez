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
        frequency: TaskFrequency,
        createdByUid: String,
        descricao: String = "",
        semanaAtual: String = DateCycleUtils.currentIsoWeekKey(),
        createInitialAssignmentInCurrentWeek: Boolean = true,
        initialDeadline: TaskDeadline? = null,
    ): Result<Task> = runCatching {
        val cleanTitle = title.trim()
        require(cleanTitle.isNotEmpty()) { "O título da tarefa recorrente não pode ser vazio." }

        val weightLevel = TaskWeightLevel.fromPoints(points)
        val categoriaId = category.name.lowercase(Locale.ROOT)
        val categoriaNome = category.label

        if (editingId.isNullOrBlank()) {
            val taskDoc = tasksCol(houseId).document()
            val newTask = Task(
                id = taskDoc.id,
                titulo = cleanTitle,
                descricao = descricao.trim(),
                peso = points,
                pesoNivel = weightLevel.name,
                frequencia = frequency.name,
                categoriaId = categoriaId,
                categoriaNome = categoriaNome,
                ativa = true,
                criadaPorUid = createdByUid,
            )

            val batch = firestore.batch()
            batch.set(taskDoc, newTask)

            if (createInitialAssignmentInCurrentWeek) {
                val deterministicId = AssignmentRepository.catalogAssignmentId(semanaAtual, newTask.id)
                val assignmentDoc = assignmentsCol(houseId).document(deterministicId)
                val defaultDeadline = initialDeadline?.name ?: when (frequency) {
                    TaskFrequency.DIARIA -> TaskDeadline.HOJE.name
                    else -> TaskDeadline.DOMINGO.name
                }
                val initialAssignment = Assignment(
                    id = assignmentDoc.id,
                    semana = semanaAtual,
                    taskId = newTask.id,
                    titulo = newTask.titulo,
                    descricao = newTask.descricao,
                    peso = newTask.peso,
                    deadline = defaultDeadline,
                    categoriaId = newTask.categoriaId,
                    categoriaNome = newTask.categoriaNome,
                    status = AssignmentStatus.PENDENTE.name,
                    responsavelUid = null,
                    responsavelNome = null,
                    ehAvulsa = false,
                    ehRollover = false,
                    urgente = frequency == TaskFrequency.DIARIA,
                    semanaOrigem = null,
                    distribuidaAutomaticamente = false,
                    criadaPorUid = createdByUid,
                )
                batch.set(assignmentDoc, initialAssignment)
            }

            batch.commit().await()
            newTask
        } else {
            val taskDoc = tasksCol(houseId).document(editingId)
            val updates = mapOf(
                "titulo" to cleanTitle,
                "descricao" to descricao.trim(),
                "peso" to points,
                "pesoNivel" to weightLevel.name,
                "frequencia" to frequency.name,
                "categoriaId" to categoriaId,
                "categoriaNome" to categoriaNome,
            )

            val batch = firestore.batch()
            batch.set(taskDoc, updates, SetOptions.merge())

            // Sincronizar atribuições ainda não concluídas da semana atual vinculadas a esta tarefa recorrente
            val currentWeekAssignments = assignmentsCol(houseId)
                .whereEqualTo("semana", semanaAtual)
                .whereEqualTo("taskId", editingId)
                .get()
                .await()

            currentWeekAssignments.documents.forEach { doc ->
                if (doc.getString("status") != AssignmentStatus.CONCLUIDA.name) {
                    batch.update(
                        doc.reference,
                        mapOf(
                            "titulo" to cleanTitle,
                            "peso" to points,
                            "categoriaId" to categoriaId,
                            "categoriaNome" to categoriaNome,
                        ),
                    )
                }
            }

            batch.commit().await()

            Task(
                id = editingId,
                titulo = cleanTitle,
                descricao = descricao.trim(),
                peso = points,
                pesoNivel = weightLevel.name,
                frequencia = frequency.name,
                categoriaId = categoriaId,
                categoriaNome = categoriaNome,
                ativa = true,
                criadaPorUid = createdByUid,
            )
        }
    }

    suspend fun deleteRecurringTask(
        houseId: String,
        recurringTaskId: String,
        hardDelete: Boolean = true,
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
