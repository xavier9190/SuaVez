package xavier.pedro.suavez.data.model

enum class HouseRole {
    ADMIN,
    MEMBER;

    companion object {
        fun fromString(value: String?): HouseRole {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: MEMBER
        }
    }
}
