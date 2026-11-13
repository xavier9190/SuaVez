package xavier.pedro.suavez.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp

data class User(
    @DocumentId
    val uid: String = "",
    val nome: String = "",
    val email: String = "",
    val fotoUrl: String? = null,
    val currentHouseId: String? = null,
    @ServerTimestamp
    val criadoEm: Timestamp? = null
)
