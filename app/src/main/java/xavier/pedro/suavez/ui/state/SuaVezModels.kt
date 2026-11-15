package xavier.pedro.suavez.ui.state

import xavier.pedro.suavez.data.model.HouseRole
import xavier.pedro.suavez.data.model.PreferenceLevel
import xavier.pedro.suavez.data.model.TaskWeightLevel
import kotlin.math.abs
import kotlin.math.roundToInt

enum class MainTab(val label: String) {
    TASKS("Tarefas"),
    PREFERENCES("Preferências"),
    HOUSE("Casa"),
    PROFILE("Perfil"),
}

sealed interface AppDestination {
    data object Login : AppDestination
    data object NoHouse : AppDestination
    data object Main : AppDestination
    data object OpenTasks : AppDestination
}

enum class TaskCategory(val label: String) {
    LIMPEZA("Limpeza"),
    COMPRAS("Compras"),
    CONTAS("Contas"),
    OUTRAS("Outras"),
}

enum class TaskFrequency(val label: String, val weeklyMultiplier: Double) {
    DIARIA("Diária", 1.0),
    SEMANAL("Semanal", 1.0),
    QUINZENAL("Quinzenal", 1.0),
    MENSAL("Mensal", 1.0),
}

enum class TaskDeadline(val label: String, val metaText: String, val urgencyOrder: Int) {
    HOJE("Hoje", "até hoje", 0),
    SABADO("Sábado", "até sábado", 1),
    DOMINGO("Domingo", "até domingo", 2),
    SEM_PRAZO("Sem prazo", "sem prazo", 3),
}

enum class TaskIconHint {
    DROP,
    TRASH,
    CART,
    RECEIPT,
    FRIDGE,
    LEAF,
    CALENDAR,
}

data class UiUser(
    val uid: String,
    val nome: String,
    val email: String,
)

data class UiHouse(
    val id: String,
    val nome: String,
    val codigoConvite: String,
    val semanaNumero: Int,
)

data class UiMember(
    val uid: String,
    val nome: String,
    val role: HouseRole,
    val monthlyPoints: Int,
    val preferences: Map<String, PreferenceLevel> = emptyMap(),
)

data class UiFixedTask(
    val id: String,
    val titulo: String,
    val categoria: TaskCategory,
    val pontos: Int,
    val frequencia: TaskFrequency,
    val iconHint: TaskIconHint = defaultIconFor(categoria, titulo),
) {
    val effortLevel: TaskWeightLevel
        get() = TaskWeightLevel.fromPoints(pontos)
}

data class UiAssignment(
    val id: String,
    val fixedTaskId: String?,
    val titulo: String,
    val categoria: TaskCategory,
    val pontos: Int,
    val deadline: TaskDeadline?,
    val subtitleOverride: String? = null,
    val assigneeUid: String?,
    val isDone: Boolean = false,
    val justClaimedInSession: Boolean = false,
    val iconHint: TaskIconHint = defaultIconFor(categoria, titulo),
) {
    val effortLevel: TaskWeightLevel
        get() = TaskWeightLevel.fromPoints(pontos)

    fun metaLabel(): String {
        val detail = subtitleOverride ?: deadline?.metaText ?: "sem prazo"
        return "${categoria.label} · $detail"
    }
}

fun defaultIconFor(category: TaskCategory, title: String): TaskIconHint {
    val lower = title.lowercase()
    return when {
        "lixo" in lower -> TaskIconHint.TRASH
        "geladeira" in lower -> TaskIconHint.FRIDGE
        "planta" in lower -> TaskIconHint.LEAF
        "banheiro" in lower -> TaskIconHint.DROP
        category == TaskCategory.LIMPEZA -> TaskIconHint.DROP
        category == TaskCategory.COMPRAS -> TaskIconHint.CART
        category == TaskCategory.CONTAS -> TaskIconHint.RECEIPT
        else -> TaskIconHint.LEAF
    }
}

fun initialsOf(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(2).uppercase()
        else -> "${parts[0].first()}${parts[1].first()}".uppercase()
    }
}

fun firstNameOf(name: String): String {
    return name.trim().split(Regex("\\s+")).firstOrNull()?.takeIf { it.isNotBlank() } ?: name
}

fun avatarIndexFor(id: String): Int {
    return abs(id.hashCode()) % 4
}

fun calculateWeeklyQuota(fixedTasks: List<UiFixedTask>, memberCount: Int): Int {
    if (memberCount <= 0) return 0
    val total = fixedTasks.sumOf { it.pontos }
    return (total.toDouble() / memberCount).roundToInt()
}

fun isHouseBalanced(scores: List<Int>): Boolean {
    if (scores.size <= 1) return true
    val max = scores.maxOrNull() ?: return true
    val min = scores.minOrNull() ?: return true
    if (max <= 0) return true
    return ((max - min).toDouble() / max.toDouble()) <= 0.10
}
