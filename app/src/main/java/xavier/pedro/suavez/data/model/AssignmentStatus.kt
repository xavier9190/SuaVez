package xavier.pedro.suavez.data.model

enum class AssignmentStatus(val label: String) {
    PENDENTE("Pendente"),
    ATRIBUIDA("Atribuída"),
    CONCLUIDA("Concluída");

    companion object {
        fun fromString(value: String?): AssignmentStatus {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: PENDENTE
        }
    }
}
