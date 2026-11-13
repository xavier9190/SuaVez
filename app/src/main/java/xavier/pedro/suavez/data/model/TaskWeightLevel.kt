package xavier.pedro.suavez.data.model

enum class TaskWeightLevel(val points: Int, val label: String) {
    LIGHT(1, "Leve / Rápida (1 pt)"),
    MEDIUM(3, "Média / Moderada (3 pts)"),
    HEAVY(5, "Pesada / Demorada (5 pts)");

    companion object {
        fun fromPoints(points: Int): TaskWeightLevel {
            return entries.firstOrNull { it.points == points } ?: MEDIUM
        }

        fun fromString(value: String?): TaskWeightLevel {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: MEDIUM
        }
    }
}
