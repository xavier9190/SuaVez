package xavier.pedro.suavez.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import xavier.pedro.suavez.data.model.User

class AuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {

    private val usersCollection
        get() = firestore.collection(COLLECTION_USERS)

    suspend fun getCurrentUserProfile(): Result<User?> = runCatching {
        val firebaseUser = auth.currentUser ?: return@runCatching null
        val docRef = usersCollection.document(firebaseUser.uid)
        val snapshot = docRef.get().await()
        if (snapshot.exists()) {
            snapshot.toObject(User::class.java)?.copy(uid = snapshot.id)
        } else {
            syncFirebaseUser(firebaseUser)
        }
    }

    suspend fun signInWithGoogleIdToken(idToken: String): Result<User> = runCatching {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val authResult = auth.signInWithCredential(credential).await()
        val firebaseUser = authResult.user
            ?: error("Falha ao recuperar usuário autenticado.")
        syncFirebaseUser(firebaseUser)
    }

    private suspend fun syncFirebaseUser(firebaseUser: FirebaseUser): User {
        val fallbackName = firebaseUser.displayName?.takeIf { it.isNotBlank() }
            ?: firebaseUser.email?.substringBefore("@")
            ?: "Morador"
        return syncUserDocument(
            uid = firebaseUser.uid,
            nome = fallbackName,
            email = firebaseUser.email.orEmpty(),
            fotoUrl = firebaseUser.photoUrl?.toString(),
        ).getOrThrow()
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
