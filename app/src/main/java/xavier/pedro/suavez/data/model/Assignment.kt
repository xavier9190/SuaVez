package xavier.pedro.suavez.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude

data class Assignment(
    @DocumentId
    val id: String = "",
    val semana: String = "",
    val taskId: String? = null,
    val titulo: String = "",
    val descricao: String = "",
    val peso: Int = TaskWeightLevel.MEDIUM.points,
    val categoriaId: String = "",
    val categoriaNome: String = "",
    val status: String = AssignmentStatus.PENDENTE.name,
    val responsavelUid: String? = null,
    val responsavelNome: String? = null,
    val ehAvulsa: Boolean = false,
    val ehRollover: Boolean = false,
    val urgente: Boolean = false,
    val semanaOrigem: String? = null,
    val distribuidaAutomaticamente: Boolean = false,
    val criadaPorUid: String = "",
    val concluidaEm: Timestamp? = null
) {
    @Exclude
    fun toStatusEnum(): AssignmentStatus = AssignmentStatus.fromString(status)

    @Exclude
    fun isPendente(): Boolean = toStatusEnum() == AssignmentStatus.PENDENTE

    @Exclude
    fun isAtribuida(): Boolean = toStatusEnum() == AssignmentStatus.ATRIBUIDA

    @Exclude
    fun isConcluida(): Boolean = toStatusEnum() == AssignmentStatus.CONCLUIDA
}
