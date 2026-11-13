package xavier.pedro.suavez.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp

data class House(
    @DocumentId
    val id: String = "",
    val nome: String = "",
    val codigoConvite: String = "",
    val criadaPorUid: String = "",
    val semanaAtual: String = "",
    @ServerTimestamp
    val criadaEm: Timestamp? = null
)
