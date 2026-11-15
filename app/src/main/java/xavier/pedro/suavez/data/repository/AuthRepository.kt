package xavier.pedro.suavez.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import xavier.pedro.suavez.data.model.User

class AuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {

    private val usersCollection
        get() = firestore.collection(COLLECTION_USERS)

    val currentUid: String?
        get() = auth.currentUser?.uid

    val isLoggedIn: Boolean
        get() = auth.currentUser != null

    fun observeAuthUid(): Flow<String?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser?.uid)
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    fun observeUserProfile(uid: String): Flow<User?> = callbackFlow {
        val registration = usersCollection.document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val user = snapshot?.toObject(User::class.java)?.copy(uid = snapshot.id)
                trySend(user)
            }
        awaitClose { registration.remove() }
    }

    suspend fun getCurrentUserProfile(): Result<User?> = runCatching {
        val firebaseUser = auth.currentUser ?: return@runCatching null
        val docRef = usersCollection.document(firebaseUser.uid)
        val snapshot = docRef.get().await()
        if (snapshot.exists()) {
            snapshot.toObject(User::class.java)?.copy(uid = snapshot.id)
        } else {
            val fallbackName = firebaseUser.displayName?.takeIf { it.isNotBlank() }
                ?: firebaseUser.email?.substringBefore("@")
                ?: "Morador"
            syncUserDocument(
                uid = firebaseUser.uid,
                nome = fallbackName,
                email = firebaseUser.email.orEmpty(),
                fotoUrl = firebaseUser.photoUrl?.toString(),
            ).getOrThrow()
        }
    }

    suspend fun signInWithEmail(email: String, password: String): Result<User> = runCatching {
        val cleanEmail = email.trim()
        val authResult = auth.signInWithEmailAndPassword(cleanEmail, password).await()
        val firebaseUser = authResult.user
            ?: error("Falha ao recuperar usuário autenticado.")

        val fallbackName = firebaseUser.displayName?.takeIf { it.isNotBlank() }
            ?: cleanEmail.substringBefore("@")
        syncUserDocument(
            uid = firebaseUser.uid,
            nome = fallbackName,
            email = firebaseUser.email ?: cleanEmail,
            fotoUrl = firebaseUser.photoUrl?.toString(),
        ).getOrThrow()
    }

    suspend fun createAccount(
        name: String,
        email: String,
        password: String,
    ): Result<User> = runCatching {
        val cleanName = name.trim()
        val cleanEmail = email.trim()
        val authResult = auth.createUserWithEmailAndPassword(cleanEmail, password).await()
        val firebaseUser = authResult.user
            ?: error("Falha ao criar usuário no Firebase Auth.")

        val profileUpdates = UserProfileChangeRequest.Builder()
            .setDisplayName(cleanName)
            .build()
        firebaseUser.updateProfile(profileUpdates).await()

        val docRef = usersCollection.document(firebaseUser.uid)
        val newUserData = mapOf(
            "uid" to firebaseUser.uid,
            "nome" to cleanName,
            "email" to cleanEmail,
            "fotoUrl" to firebaseUser.photoUrl?.toString(),
            "currentHouseId" to null,
            "criadoEm" to FieldValue.serverTimestamp(),
        )
        docRef.set(newUserData).await()

        User(
            uid = firebaseUser.uid,
            nome = cleanName,
            email = cleanEmail,
            fotoUrl = firebaseUser.photoUrl?.toString(),
            currentHouseId = null,
        )
    }

    suspend fun syncUserDocument(
        uid: String,
        nome: String,
        email: String,
        fotoUrl: String? = null,
    ): Result<User> = runCatching {
        val docRef = usersCollection.document(uid)
        val snapshot = docRef.get().await()
        if (snapshot.exists()) {
            val existing = snapshot.toObject(User::class.java)?.copy(uid = snapshot.id)
                ?: User(uid = uid, nome = nome, email = email, fotoUrl = fotoUrl)
            val updatedName = nome.takeIf { it.isNotBlank() } ?: existing.nome
            val updatedEmail = email.takeIf { it.isNotBlank() } ?: existing.email
            val updates = mutableMapOf<String, Any?>(
                "uid" to uid,
                "nome" to updatedName,
                "email" to updatedEmail,
            )
            if (fotoUrl != null) {
                updates["fotoUrl"] = fotoUrl
            }
            docRef.set(updates, SetOptions.merge()).await()
            existing.copy(
                nome = updatedName,
                email = updatedEmail,
                fotoUrl = fotoUrl ?: existing.fotoUrl,
            )
        } else {
            val payload = mapOf(
                "uid" to uid,
                "nome" to nome.trim(),
                "email" to email.trim(),
                "fotoUrl" to fotoUrl,
                "currentHouseId" to null,
                "criadoEm" to FieldValue.serverTimestamp(),
            )
            docRef.set(payload).await()
            User(
                uid = uid,
                nome = nome.trim(),
                email = email.trim(),
                fotoUrl = fotoUrl,
                currentHouseId = null,
            )
        }
    }

    suspend fun updateCurrentHouseId(uid: String, houseId: String?): Result<Unit> = runCatching {
        usersCollection.document(uid)
            .set(mapOf("currentHouseId" to houseId), SetOptions.merge())
            .await()
    }

    fun signOut() {
        auth.signOut()
    }

    companion object {
        const val COLLECTION_USERS = "users"
    }
}
