package xavier.pedro.suavez.data.repository

import xavier.pedro.suavez.data.model.Assignment
import xavier.pedro.suavez.data.model.AssignmentStatus
import xavier.pedro.suavez.data.model.Category
import xavier.pedro.suavez.data.model.House
import xavier.pedro.suavez.data.model.Member
import xavier.pedro.suavez.data.model.PreferenceLevel
import xavier.pedro.suavez.data.model.Task
import xavier.pedro.suavez.data.model.TaskWeightLevel
import xavier.pedro.suavez.data.model.User
import xavier.pedro.suavez.ui.state.TaskCategory
import xavier.pedro.suavez.ui.state.TaskDeadline
import xavier.pedro.suavez.ui.state.TaskFrequency
import xavier.pedro.suavez.ui.state.UiAssignment
import xavier.pedro.suavez.ui.state.UiRecurringTask
import xavier.pedro.suavez.ui.state.UiHouse
import xavier.pedro.suavez.ui.state.UiMember
import xavier.pedro.suavez.ui.state.UiUser
import xavier.pedro.suavez.ui.state.defaultIconFor
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone

object DateCycleUtils {

    private const val REMINDER_HOUR = 9
    private val ISO_WEEK_KEY_REGEX = Regex("^(\\d{4})-W(\\d{1,2})$", RegexOption.IGNORE_CASE)

    fun currentIsoWeekKey(calendar: Calendar = Calendar.getInstance()): String {
        val isoCal = (calendar.clone() as Calendar).apply {
            firstDayOfWeek = Calendar.MONDAY
            minimalDaysInFirstWeek = 4
        }
        val week = isoCal.get(Calendar.WEEK_OF_YEAR)
        val month = isoCal.get(Calendar.MONTH)
        var year = isoCal.get(Calendar.YEAR)
        if (month == Calendar.JANUARY && week >= 52) {
            year -= 1
        } else if (month == Calendar.DECEMBER && week == 1) {
            year += 1
        }
        return String.format(Locale.US, "%04d-W%02d", year, week)
    }

    fun currentMonthKey(calendar: Calendar = Calendar.getInstance()): String {
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1
        return String.format(Locale.US, "%04d-%02d", year, month)
    }

    fun parseWeekNumber(semanaKey: String?, fallbackWeek: Int = 1): Int {
        if (semanaKey.isNullOrBlank()) return fallbackWeek
        val match = Regex("W(\\d{1,2})", RegexOption.IGNORE_CASE).find(semanaKey)
        return match?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: semanaKey.trim().toIntOrNull()?.takeIf { it in 1..53 }
            ?: fallbackWeek
    }

    /**
     * Calcula o instante (epoch millis) do lembrete fixo às 09:00 da véspera do prazo:
     * - [TaskDeadline.SABADO] -> sexta-feira da semana ISO às 09:00
     * - [TaskDeadline.DOMINGO] -> sábado da semana ISO às 09:00
     * - [TaskDeadline.HOJE], [TaskDeadline.SEM_PRAZO], nulo, chave inválida ou marco <= [nowMillis] -> `null`
     */
    fun computeReminderTriggerAtMillis(
        semanaKey: String?,
        deadline: TaskDeadline?,
        nowMillis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault(),
    ): Long? {
        if (semanaKey.isNullOrBlank()) return null
        val targetDayOfWeek = when (deadline) {
            TaskDeadline.SABADO -> Calendar.FRIDAY
            TaskDeadline.DOMINGO -> Calendar.SATURDAY
            TaskDeadline.HOJE, TaskDeadline.SEM_PRAZO, null -> return null
        }

        val match = ISO_WEEK_KEY_REGEX.matchEntire(semanaKey.trim()) ?: return null
        val isoYear = match.groupValues[1].toIntOrNull() ?: return null
        val isoWeek = match.groupValues[2].toIntOrNull()?.takeIf { it in 1..53 } ?: return null

        val cal = GregorianCalendar(timeZone, Locale.US).apply {
            firstDayOfWeek = Calendar.MONDAY
            minimalDaysInFirstWeek = 4
            clear()
            setWeekDate(isoYear, isoWeek, targetDayOfWeek)
        }
        if (cal.weekYear != isoYear || cal.get(Calendar.WEEK_OF_YEAR) != isoWeek) {
            return null
        }
        cal.set(Calendar.HOUR_OF_DAY, REMINDER_HOUR)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        val triggerAtMillis = cal.timeInMillis
        return triggerAtMillis.takeIf { it > nowMillis }
    }
}

fun String?.toTaskCategory(): TaskCategory {
    if (this.isNullOrBlank()) return TaskCategory.OUTRAS
    val normalized = this.trim()
    return TaskCategory.entries.firstOrNull {
        it.name.equals(normalized, ignoreCase = true) ||
            it.label.equals(normalized, ignoreCase = true)
    } ?: TaskCategory.OUTRAS
}

fun String?.toTaskFrequency(): TaskFrequency {
    if (this.isNullOrBlank()) return TaskFrequency.SEMANAL
    val normalized = this.trim()
    return TaskFrequency.entries.firstOrNull {
        it.name.equals(normalized, ignoreCase = true) ||
            it.label.equals(normalized, ignoreCase = true)
    } ?: TaskFrequency.SEMANAL
}

fun String?.toTaskDeadline(): TaskDeadline? {
    if (this.isNullOrBlank()) return null
    val normalized = this.trim()
    return TaskDeadline.entries.firstOrNull {
        it.name.equals(normalized, ignoreCase = true) ||
            it.label.equals(normalized, ignoreCase = true) ||
            it.metaText.equals(normalized, ignoreCase = true)
    }
}

fun TaskCategory.toDefaultCategoryModel(): Category {
    return when (this) {
        TaskCategory.LIMPEZA -> Category(
            id = "limpeza",
            nome = label,
            icone = "cleaning_services",
            corHex = "#0D9488",
            padrao = true,
        )
        TaskCategory.COMPRAS -> Category(
            id = "compras",
            nome = label,
            icone = "shopping_cart",
            corHex = "#D97706",
            padrao = true,
        )
        TaskCategory.CONTAS -> Category(
            id = "contas",
            nome = label,
            icone = "receipt_long",
            corHex = "#2563EB",
            padrao = true,
        )
        TaskCategory.OUTRAS -> Category(
            id = "outras",
            nome = label,
            icone = "eco",
            corHex = "#4B5563",
            padrao = true,
        )
    }
}

fun User.toUiUser(): UiUser = UiUser(
    uid = uid,
    nome = nome,
    email = email,
    fotoUrl = fotoUrl,
)

fun UiUser.toFirestoreUser(
    currentHouseId: String? = null,
    fotoUrl: String? = this.fotoUrl,
): User = User(
    uid = uid,
    nome = nome,
    email = email,
    fotoUrl = fotoUrl,
    currentHouseId = currentHouseId,
)

fun House.toUiHouse(): UiHouse = UiHouse(
    id = id,
    nome = nome,
    codigoConvite = codigoConvite,
    semanaNumero = DateCycleUtils.parseWeekNumber(semanaAtual),
)

fun Member.toUiMember(monthKey: String = DateCycleUtils.currentMonthKey()): UiMember = UiMember(
    uid = uid,
    nome = nome,
    role = getRole(),
    monthlyPoints = getPointsForMonth(monthKey),
    preferences = preferences.mapValues { (_, rawLevel) -> PreferenceLevel.fromString(rawLevel) },
)

fun UiMember.toFirestoreMember(
    monthKey: String = DateCycleUtils.currentMonthKey(),
    existingPontosPorMes: Map<String, Int> = emptyMap(),
    fotoUrl: String? = null,
): Member = Member(
    uid = uid,
    nome = nome,
    fotoUrl = fotoUrl,
    papel = role.name,
    pontosPorMes = existingPontosPorMes + (monthKey to monthlyPoints),
    preferences = preferences.mapValues { (_, level) -> level.name },
)

fun Task.toUiRecurringTask(): UiRecurringTask {
    val category = categoriaNome.ifBlank { categoriaId }.toTaskCategory()
    val frequency = frequencia.toTaskFrequency()
    return UiRecurringTask(
        id = id,
        titulo = titulo,
        categoria = category,
        pontos = peso,
        frequencia = frequency,
        iconHint = defaultIconFor(category, titulo),
    )
}

fun UiRecurringTask.toFirestoreTask(
    criadaPorUid: String = "",
    descricao: String = "",
    ativa: Boolean = true,
): Task {
    val weightLevel = TaskWeightLevel.fromPoints(pontos)
    return Task(
        id = id,
        titulo = titulo,
        descricao = descricao,
        peso = pontos,
        pesoNivel = weightLevel.name,
        frequencia = frequencia.name,
        categoriaId = categoria.name.lowercase(Locale.ROOT),
        categoriaNome = categoria.label,
        ativa = ativa,
        criadaPorUid = criadaPorUid,
    )
}

fun Assignment.toUiAssignment(justClaimedInSession: Boolean = false): UiAssignment {
    val category = categoriaNome.ifBlank { categoriaId }.toTaskCategory()
    val parsedDeadline = deadline.toTaskDeadline()
        ?: if (urgente || ehRollover) TaskDeadline.HOJE else null
    val done = isConcluida()
    val subtitle = when {
        descricao.isNotBlank() -> descricao
        ehRollover && !done -> "acumulada"
        else -> null
    }
    return UiAssignment(
        id = id,
        recurringTaskId = taskId,
        titulo = titulo,
        categoria = category,
        pontos = peso,
        deadline = parsedDeadline,
        subtitleOverride = subtitle,
        assigneeUid = responsavelUid,
        isDone = done,
        justClaimedInSession = justClaimedInSession,
        iconHint = defaultIconFor(category, titulo),
    )
}

fun UiAssignment.toFirestoreAssignment(
    semana: String,
    criadaPorUid: String = "",
    responsavelNome: String? = null,
    ehRollover: Boolean = false,
    urgente: Boolean = deadline == TaskDeadline.HOJE,
    semanaOrigem: String? = null,
    distribuidaAutomaticamente: Boolean = false,
): Assignment {
    val statusEnum = when {
        isDone -> AssignmentStatus.CONCLUIDA
        assigneeUid != null -> AssignmentStatus.ATRIBUIDA
        else -> AssignmentStatus.PENDENTE
    }
    return Assignment(
        id = id,
        semana = semana,
        taskId = recurringTaskId,
        titulo = titulo,
        descricao = subtitleOverride.orEmpty(),
        peso = pontos,
        deadline = deadline?.name,
        categoriaId = categoria.name.lowercase(Locale.ROOT),
        categoriaNome = categoria.label,
        status = statusEnum.name,
        responsavelUid = assigneeUid,
        responsavelNome = responsavelNome,
        ehAvulsa = recurringTaskId == null,
        ehRollover = ehRollover,
        urgente = urgente,
        semanaOrigem = semanaOrigem,
        distribuidaAutomaticamente = distribuidaAutomaticamente,
        criadaPorUid = criadaPorUid,
    )
}
