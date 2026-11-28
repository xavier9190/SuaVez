package xavier.pedro.suavez.data.repository

import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import xavier.pedro.suavez.data.model.Assignment
import xavier.pedro.suavez.data.model.AssignmentStatus
import xavier.pedro.suavez.data.model.House
import xavier.pedro.suavez.data.model.HouseRole
import xavier.pedro.suavez.data.model.Member
import xavier.pedro.suavez.data.model.PreferenceLevel
import xavier.pedro.suavez.data.model.User
import xavier.pedro.suavez.ui.state.TaskCategory
import java.util.Locale
import kotlin.random.Random

class HouseRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {

    private val housesCollection
        get() = firestore.collection(COLLECTION_HOUSES)

    private val usersCollection
        get() = firestore.collection(AuthRepository.COLLECTION_USERS)

    fun observeHouse(houseId: String): Flow<House?> = callbackFlow {
        val registration = housesCollection.document(houseId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val house = snapshot?.toObject(House::class.java)?.copy(id = snapshot.id)
                trySend(house)
            }
        awaitClose { registration.remove() }
    }

    fun observeMembers(houseId: String): Flow<List<Member>> = callbackFlow {
        val registration = housesCollection.document(houseId)
            .collection(SUBCOLLECTION_MEMBERS)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val members = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Member::class.java)?.copy(uid = doc.id)
                }.orEmpty()
                trySend(members)
            }
        awaitClose { registration.remove() }
    }

    suspend fun getHouse(houseId: String): Result<House?> = runCatching {
        val snapshot = housesCollection.document(houseId).get().await()
        if (!snapshot.exists()) null
        else snapshot.toObject(House::class.java)?.copy(id = snapshot.id)
    }

    suspend fun loadHouseAndMembers(houseId: String): Result<Pair<House, List<Member>>?> = runCatching {
        val house = getHouse(houseId).getOrThrow() ?: return@runCatching null
        val members = fetchMembers(house.id)
        house to members
    }

    private suspend fun fetchMembers(houseId: String): List<Member> {
        val membersSnap = housesCollection.document(houseId)
            .collection(SUBCOLLECTION_MEMBERS)
            .get()
            .await()
        return membersSnap.documents.mapNotNull { doc ->
            doc.toObject(Member::class.java)?.copy(uid = doc.id)
        }
    }

    suspend fun createHouse(
        houseName: String,
        creator: User,
        semanaAtual: String = DateCycleUtils.currentIsoWeekKey(),
        monthKey: String = DateCycleUtils.currentMonthKey(),
    ): Result<House> = runCatching {
        val cleanName = houseName.trim()
        require(cleanName.isNotEmpty()) { "O nome da casa não pode ser vazio." }

        val houseDoc = housesCollection.document()
        val inviteCode = generateUniqueInviteCode()

        val house = House(
            id = houseDoc.id,
            nome = cleanName,
            codigoConvite = inviteCode,
            criadaPorUid = creator.uid,
            semanaAtual = semanaAtual,
        )

        val adminMember = Member(
            uid = creator.uid,
            nome = creator.nome,
            fotoUrl = creator.fotoUrl,
            papel = HouseRole.ADMIN.name,
            pontosPorMes = mapOf(monthKey to 0),
            preferences = emptyMap(),
        )

        val batch = firestore.batch()
        batch.set(houseDoc, house)

        val memberDoc = houseDoc.collection(SUBCOLLECTION_MEMBERS).document(creator.uid)
        batch.set(memberDoc, adminMember)

        // Semear apenas as 4 categorias padrão da casa (tarefas começam vazias para o Admin cadastrar)
        TaskCategory.entries.forEach { taskCat ->
            val categoryModel = taskCat.toDefaultCategoryModel()
            val catDoc = houseDoc.collection(SUBCOLLECTION_CATEGORIES).document(categoryModel.id)
            batch.set(catDoc, categoryModel)
        }

        val userDoc = usersCollection.document(creator.uid)
        batch.set(
            userDoc,
            mapOf(
                "uid" to creator.uid,
                "nome" to creator.nome,
                "email" to creator.email,
                "fotoUrl" to creator.fotoUrl,
                "currentHouseId" to houseDoc.id,
            ),
            SetOptions.merge(),
        )

        batch.commit().await()
        house
    }

    suspend fun findHouseByInviteCode(inviteCode: String): Result<House?> = runCatching {
        val normalized = normalizeInviteCode(inviteCode)
        if (normalized.isEmpty()) return@runCatching null

        val querySnapshot = housesCollection
            .whereEqualTo("codigoConvite", normalized)
            .limit(1)
            .get()
            .await()

        val doc = querySnapshot.documents.firstOrNull() ?: return@runCatching null
        doc.toObject(House::class.java)?.copy(id = doc.id)
    }

    suspend fun joinHouseByCode(
        inviteCode: String,
        user: User,
        monthKey: String = DateCycleUtils.currentMonthKey(),
    ): Result<House> = runCatching {
        val house = findHouseByInviteCode(inviteCode).getOrThrow()
            ?: error("Nenhuma casa encontrada para o código informado.")

        val houseDoc = housesCollection.document(house.id)
        val memberDoc = houseDoc.collection(SUBCOLLECTION_MEMBERS).document(user.uid)
        val userDoc = usersCollection.document(user.uid)

        firestore.runTransaction { transaction ->
            // Reads-before-writes obrigatório no SDK do Firestore
            val freshHouseSnap = transaction.get(houseDoc)
            val freshHouse = freshHouseSnap.toObject(House::class.java)?.copy(id = freshHouseSnap.id)
                ?: error("A casa informada não está mais disponível.")
            check(freshHouse.codigoConvite == normalizeInviteCode(inviteCode)) {
                "O código de convite não é mais válido."
            }
            val existingMember = transaction.get(memberDoc)

            if (!existingMember.exists()) {
                val newMember = Member(
                    uid = user.uid,
                    nome = user.nome,
                    fotoUrl = user.fotoUrl,
                    papel = HouseRole.MEMBER.name,
                    pontosPorMes = mapOf(monthKey to 0),
                    preferences = emptyMap(),
                )
                transaction.set(memberDoc, newMember)
            } else {
                transaction.set(
                    memberDoc,
                    mapOf(
                        "nome" to user.nome,
                        "fotoUrl" to user.fotoUrl,
                    ),
                    SetOptions.merge(),
                )
            }

            transaction.set(
                userDoc,
                mapOf(
                    "uid" to user.uid,
                    "nome" to user.nome,
                    "email" to user.email,
                    "fotoUrl" to user.fotoUrl,
                    "currentHouseId" to freshHouse.id,
                ),
                SetOptions.merge(),
            )

            freshHouse
        }.await()
    }

    suspend fun updatePreference(
        houseId: String,
        uid: String,
        taskId: String,
        level: PreferenceLevel,
    ): Result<Unit> = runCatching {
        val memberDoc = housesCollection.document(houseId)
            .collection(SUBCOLLECTION_MEMBERS)
            .document(uid)

        memberDoc.update(FieldPath.of("preferences", taskId), level.name).await()
    }

    suspend fun renameHouse(houseId: String, newName: String): Result<Unit> = runCatching {
        val clean = newName.trim()
        require(clean.isNotEmpty()) { "O nome da casa não pode ser vazio." }
        housesCollection.document(houseId)
            .update("nome", clean)
            .await()
    }

    suspend fun promoteToAdmin(
        houseId: String,
        currentAdminUid: String,
        targetMemberUid: String,
    ): Result<Unit> = runCatching {
        val membersCol = housesCollection.document(houseId).collection(SUBCOLLECTION_MEMBERS)
        val batch = firestore.batch()
        batch.update(membersCol.document(targetMemberUid), "papel", HouseRole.ADMIN.name)
        if (currentAdminUid != targetMemberUid) {
            batch.update(membersCol.document(currentAdminUid), "papel", HouseRole.MEMBER.name)
        }
        batch.commit().await()
    }

    suspend fun removeMember(houseId: String, memberUid: String): Result<Unit> =
        removeMemberInternal(houseId = houseId, memberUid = memberUid)

    suspend fun transferAdminAndLeave(
        houseId: String,
        leavingAdminUid: String,
        newAdminUid: String,
    ): Result<Unit> = removeMemberInternal(
        houseId = houseId,
        memberUid = leavingAdminUid,
        newAdminUid = newAdminUid,
    )

    private suspend fun removeMemberInternal(
        houseId: String,
        memberUid: String,
        newAdminUid: String? = null,
    ): Result<Unit> = runCatching {
        val houseDoc = housesCollection.document(houseId)
        val houseSnap = houseDoc.get().await()
        val currentWeek = houseSnap.getString("semanaAtual")
            ?.takeIf { it.isNotBlank() }
            ?: DateCycleUtils.currentIsoWeekKey()

        val candidateAssignments = houseDoc.collection(SUBCOLLECTION_ASSIGNMENTS)
            .whereEqualTo("responsavelUid", memberUid)
            .whereEqualTo("semana", currentWeek)
            .get()
            .await()

        val membersCol = houseDoc.collection(SUBCOLLECTION_MEMBERS)
        val newAdminRef = newAdminUid?.takeIf { it.isNotBlank() }?.let { membersCol.document(it) }
        val removedMemberRef = membersCol.document(memberUid)
        val userRef = usersCollection.document(memberUid)

        firestore.runTransaction { transaction ->
            // Reads-before-writes obrigatório no SDK do Firestore
            val freshHouseSnap = transaction.get(houseDoc)
            val effectiveWeek = freshHouseSnap.getString("semanaAtual")
                ?.takeIf { it.isNotBlank() }
                ?: currentWeek
            check(effectiveWeek == currentWeek) {
                "A semana da casa mudou durante a remoção ($effectiveWeek != $currentWeek)."
            }

            if (newAdminRef != null) {
                val newAdminSnap = transaction.get(newAdminRef)
                check(newAdminSnap.exists()) { "Novo administrador não encontrado na casa." }
            }

            val refreshedCandidates = candidateAssignments.documents.map { doc ->
                transaction.get(doc.reference)
            }

            if (newAdminRef != null) {
                transaction.update(newAdminRef, "papel", HouseRole.ADMIN.name)
            }

            val reopenFields = reopenedAssignmentUpdateMap()
            refreshedCandidates.forEach { snap ->
                val assignment = snap.toObject(Assignment::class.java)?.copy(id = snap.id) ?: return@forEach
                if (shouldReopenAssignmentOnMemberRemoval(assignment, memberUid, effectiveWeek)) {
                    transaction.update(snap.reference, reopenFields)
                }
            }

            transaction.delete(removedMemberRef)
            transaction.set(
                userRef,
                mapOf("currentHouseId" to null),
                SetOptions.merge(),
            )
            Unit
        }.await()
    }

    suspend fun leaveHouseAsMember(houseId: String, memberUid: String): Result<Unit> =
        removeMember(houseId = houseId, memberUid = memberUid)

    suspend fun destroyHouse(houseId: String): Result<Unit> = runCatching {
        val houseDoc = housesCollection.document(houseId)
        val membersSnap = houseDoc.collection(SUBCOLLECTION_MEMBERS).get().await()
        val categoriesSnap = houseDoc.collection(SUBCOLLECTION_CATEGORIES).get().await()
        val tasksSnap = houseDoc.collection(SUBCOLLECTION_TASKS).get().await()
        val assignmentsSnap = houseDoc.collection(SUBCOLLECTION_ASSIGNMENTS).get().await()

        val batch = firestore.batch()
        membersSnap.documents.forEach { memberDoc ->
            batch.set(
                usersCollection.document(memberDoc.id),
                mapOf("currentHouseId" to null),
                SetOptions.merge(),
            )
            batch.delete(memberDoc.reference)
        }
        categoriesSnap.documents.forEach { batch.delete(it.reference) }
        tasksSnap.documents.forEach { batch.delete(it.reference) }
        assignmentsSnap.documents.forEach { batch.delete(it.reference) }
        batch.delete(houseDoc)
        batch.commit().await()
    }

    suspend fun regenerateInviteCode(houseId: String): Result<String> = runCatching {
        val newCode = generateUniqueInviteCode()
        housesCollection.document(houseId)
            .update("codigoConvite", newCode)
            .await()
        newCode
    }

    private suspend fun generateUniqueInviteCode(): String {
        repeat(5) {
            val candidate = formatInviteCode(Random.nextInt(1000, 10000))
            val existing = housesCollection
                .whereEqualTo("codigoConvite", candidate)
                .limit(1)
                .get()
                .await()
            if (existing.isEmpty) return candidate
        }
        return formatInviteCode(Random.nextInt(1000, 10000))
    }

    companion object {
        const val COLLECTION_HOUSES = "houses"
        const val SUBCOLLECTION_MEMBERS = "members"
        const val SUBCOLLECTION_CATEGORIES = "categories"
        const val SUBCOLLECTION_TASKS = "tasks"
        const val SUBCOLLECTION_ASSIGNMENTS = "assignments"
        private val INVITE_CODE_REGEX = Regex("^SV-[A-Z0-9]{4}$")

        fun formatInviteCode(suffix: Int): String =
            String.format(Locale.US, "SV-%04d", suffix.coerceIn(0, 9999))

        fun normalizeInviteCode(raw: String): String {
            val trimmed = raw.trim().uppercase(Locale.ROOT)
            if (trimmed.isEmpty()) return ""
            return if (trimmed.startsWith("SV") && !trimmed.startsWith("SV-") && trimmed.length == 6) {
                "SV-${trimmed.substring(2)}"
            } else {
                trimmed
            }
        }

        fun isValidInviteCode(raw: String): Boolean =
            INVITE_CODE_REGEX.matches(normalizeInviteCode(raw))

        fun shouldReopenAssignmentOnMemberRemoval(
            assignment: Assignment,
            removedMemberUid: String,
            currentWeekKey: String,
        ): Boolean =
            assignment.responsavelUid == removedMemberUid &&
                assignment.semana == currentWeekKey &&
                !assignment.isConcluida()

        fun reopenAssignmentOnMemberRemoval(assignment: Assignment): Assignment =
            assignment.copy(
                responsavelUid = null,
                responsavelNome = null,
                status = AssignmentStatus.PENDENTE.name,
                distribuidaAutomaticamente = false,
            )

        fun reopenedAssignmentUpdateMap(): Map<String, Any?> {
            val reopened = reopenAssignmentOnMemberRemoval(Assignment())
            return mapOf(
                "responsavelUid" to reopened.responsavelUid,
                "responsavelNome" to reopened.responsavelNome,
                "status" to reopened.status,
                "distribuidaAutomaticamente" to reopened.distribuidaAutomaticamente,
            )
        }
    }
}
