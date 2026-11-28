package xavier.pedro.suavez.ui

import xavier.pedro.suavez.ui.state.TaskCategory
import xavier.pedro.suavez.ui.state.TaskDeadline
import xavier.pedro.suavez.ui.state.TaskFrequency
import xavier.pedro.suavez.ui.state.UiAssignment
import xavier.pedro.suavez.ui.state.UiRecurringTask

enum class ActivityFormMode {
    CREATE,
    EDIT_ASSIGNMENT,
    EDIT_RECURRING,
}

enum class ActivityFormRecurrence {
    UNICA,
    RECORRENTE,
}

data class ActivityFormErrors(
    val titleRequired: Boolean = false,
    val categoryRequired: Boolean = false,
    val effortRequired: Boolean = false,
    val recurrenceRequired: Boolean = false,
    val frequencyRequired: Boolean = false,
    val openNowRequired: Boolean = false,
    val deadlineRequired: Boolean = false,
) {
    val hasErrors: Boolean
        get() = titleRequired || categoryRequired || effortRequired ||
            recurrenceRequired || frequencyRequired || openNowRequired || deadlineRequired
}

sealed interface ActivityFormSubmission {
    data class CreateWeekly(
        val title: String,
        val category: TaskCategory,
        val points: Int,
        val deadline: TaskDeadline,
    ) : ActivityFormSubmission

    data class UpdateWeekly(
        val assignmentId: String,
        val title: String,
        val category: TaskCategory,
        val points: Int,
        val deadline: TaskDeadline,
    ) : ActivityFormSubmission

    data class SaveRecurring(
        val editingId: String?,
        val title: String,
        val category: TaskCategory,
        val points: Int,
        val frequency: TaskFrequency,
        val openInCurrentWeek: Boolean,
        val deadline: TaskDeadline?,
    ) : ActivityFormSubmission
}

sealed interface ActivityFormValidationResult {
    data class Valid(val submission: ActivityFormSubmission) : ActivityFormValidationResult
    data class Invalid(val errors: ActivityFormErrors) : ActivityFormValidationResult
    data object Unauthorized : ActivityFormValidationResult
}

data class ActivityFormDraft(
    val mode: ActivityFormMode,
    val isAdmin: Boolean,
    val title: String = "",
    val category: TaskCategory? = null,
    val effortPoints: Int? = null,
    val recurrence: ActivityFormRecurrence? = null,
    val frequency: TaskFrequency? = null,
    val openInCurrentWeek: Boolean? = null,
    val deadline: TaskDeadline? = null,
) {
    val isEditing: Boolean
        get() = mode != ActivityFormMode.CREATE

    val effectiveRecurrence: ActivityFormRecurrence?
        get() = when {
            mode == ActivityFormMode.EDIT_RECURRING -> ActivityFormRecurrence.RECORRENTE
            mode == ActivityFormMode.EDIT_ASSIGNMENT -> ActivityFormRecurrence.UNICA
            !isAdmin -> ActivityFormRecurrence.UNICA
            else -> recurrence
        }

    val showTypeSelector: Boolean
        get() = !isEditing && isAdmin

    val showFrequencySection: Boolean
        get() = effectiveRecurrence == ActivityFormRecurrence.RECORRENTE

    val showOpenNowSection: Boolean
        get() = !isEditing && effectiveRecurrence == ActivityFormRecurrence.RECORRENTE

    val showDeadlineSection: Boolean
        get() = effectiveRecurrence == ActivityFormRecurrence.UNICA ||
            (showOpenNowSection && openInCurrentWeek == true)

    val canDelete: Boolean
        get() = isEditing && isAdmin

    fun withRecurrence(selected: ActivityFormRecurrence): ActivityFormDraft =
        copy(recurrence = selected)

    fun withOpenInCurrentWeek(openNow: Boolean): ActivityFormDraft =
        copy(
            openInCurrentWeek = openNow,
            deadline = if (openNow) deadline else null,
        )

    fun validate(targetId: String? = null): ActivityFormValidationResult {
        if (!isAdmin && (isEditing || recurrence == ActivityFormRecurrence.RECORRENTE || effectiveRecurrence == ActivityFormRecurrence.RECORRENTE)) {
            return ActivityFormValidationResult.Unauthorized
        }
        val cleanTitle = title.trim()
        val chosenRecurrence = effectiveRecurrence
        val errors = ActivityFormErrors(
            titleRequired = cleanTitle.isEmpty(),
            categoryRequired = category == null,
            effortRequired = effortPoints == null || effortPoints !in VALID_EFFORT_POINTS,
            recurrenceRequired = showTypeSelector && chosenRecurrence == null,
            frequencyRequired = showFrequencySection && frequency == null,
            openNowRequired = showOpenNowSection && openInCurrentWeek == null,
            deadlineRequired = showDeadlineSection && deadline == null,
        )
        if (errors.hasErrors) return ActivityFormValidationResult.Invalid(errors)

        val chosenCategory = category!!
        val chosenPoints = effortPoints!!
        val submission = when (chosenRecurrence!!) {
            ActivityFormRecurrence.UNICA -> {
                if (mode == ActivityFormMode.EDIT_ASSIGNMENT) {
                    val id = targetId?.takeIf { it.isNotBlank() }
                        ?: return ActivityFormValidationResult.Unauthorized
                    ActivityFormSubmission.UpdateWeekly(
                        assignmentId = id,
                        title = cleanTitle,
                        category = chosenCategory,
                        points = chosenPoints,
                        deadline = deadline!!,
                    )
                } else {
                    ActivityFormSubmission.CreateWeekly(
                        title = cleanTitle,
                        category = chosenCategory,
                        points = chosenPoints,
                        deadline = deadline!!,
                    )
                }
            }
            ActivityFormRecurrence.RECORRENTE -> ActivityFormSubmission.SaveRecurring(
                editingId = if (mode == ActivityFormMode.EDIT_RECURRING) {
                    targetId?.takeIf { it.isNotBlank() }
                        ?: return ActivityFormValidationResult.Unauthorized
                } else {
                    null
                },
                title = cleanTitle,
                category = chosenCategory,
                points = chosenPoints,
                frequency = frequency!!,
                openInCurrentWeek = openInCurrentWeek ?: false,
                deadline = deadline,
            )
        }
        return ActivityFormValidationResult.Valid(submission)
    }

    companion object {
        val VALID_EFFORT_POINTS = setOf(2, 5, 8)

        fun forCreate(isAdmin: Boolean): ActivityFormDraft = ActivityFormDraft(
            mode = ActivityFormMode.CREATE,
            isAdmin = isAdmin,
            recurrence = if (isAdmin) null else ActivityFormRecurrence.UNICA,
        )

        fun forEditAssignment(assignment: UiAssignment, isAdmin: Boolean): ActivityFormDraft =
            ActivityFormDraft(
                mode = ActivityFormMode.EDIT_ASSIGNMENT,
                isAdmin = isAdmin,
                title = assignment.titulo,
                category = assignment.categoria,
                effortPoints = assignment.effortLevel.points,
                recurrence = ActivityFormRecurrence.UNICA,
                deadline = assignment.deadline ?: TaskDeadline.SEM_PRAZO,
            )

        fun forEditRecurring(task: UiRecurringTask, isAdmin: Boolean): ActivityFormDraft =
            ActivityFormDraft(
                mode = ActivityFormMode.EDIT_RECURRING,
                isAdmin = isAdmin,
                title = task.titulo,
                category = task.categoria,
                effortPoints = task.effortLevel.points,
                recurrence = ActivityFormRecurrence.RECORRENTE,
                frequency = task.frequencia,
            )
    }
}
