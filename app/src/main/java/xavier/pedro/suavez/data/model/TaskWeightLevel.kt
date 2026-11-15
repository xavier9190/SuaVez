package xavier.pedro.suavez.data.model

enum class TaskWeightLevel(val points: Int, val label: String) {
    LIGHT(2, "Leve · 2 pts"),
    MEDIUM(5, "Média · 5 pts"),
    HEAVY(8, "Pesada · 8 pts");

    companion object {
        fun fromPoints(points: Int): TaskWeightLevel {
            return when {
                points <= 3 -> LIGHT
                points <= 6 -> MEDIUM
                else -> HEAVY
            }
        }

        fun fromString(value: String?): TaskWeightLevel {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: MEDIUM
        }
    }
}
