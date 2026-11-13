package xavier.pedro.suavez.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.ServerTimestamp

data class Task(
    @DocumentId
    val id: String = "",
    val titulo: String = "",
    val descricao: String = "",
    val peso: Int = TaskWeightLevel.MEDIUM.points,
    val pesoNivel: String = TaskWeightLevel.MEDIUM.name,
    val categoriaId: String = "",
    val categoriaNome: String = "",
    val ativa: Boolean = true,
    val criadaPorUid: String = "",
    @ServerTimestamp
    val criadaEm: Timestamp? = null
) {
    @Exclude
    fun getWeightLevel(): TaskWeightLevel = TaskWeightLevel.fromString(pesoNivel)
}
