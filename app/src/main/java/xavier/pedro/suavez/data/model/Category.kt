package xavier.pedro.suavez.data.model

import com.google.firebase.firestore.DocumentId

data class Category(
    @DocumentId
    val id: String = "",
    val nome: String = "",
    val icone: String = "cleaning_services",
    val corHex: String = "#4CAF50",
    val padrao: Boolean = false
)
