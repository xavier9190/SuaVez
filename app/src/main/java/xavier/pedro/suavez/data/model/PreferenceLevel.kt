package xavier.pedro.suavez.data.model

enum class PreferenceLevel(val label: String) {
    ODEIO("Odeio"),
    TANTO_FAZ("Tanto faz"),
    GOSTO("Gosto");

    companion object {
        fun fromString(value: String?): PreferenceLevel {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: TANTO_FAZ
        }
    }
}
