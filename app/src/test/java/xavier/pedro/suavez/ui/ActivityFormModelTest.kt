package xavier.pedro.suavez.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xavier.pedro.suavez.ui.state.TaskCategory
import xavier.pedro.suavez.ui.state.TaskDeadline
import xavier.pedro.suavez.ui.state.TaskFrequency
import xavier.pedro.suavez.ui.state.UiAssignment
import xavier.pedro.suavez.ui.state.UiRecurringTask

class ActivityFormModelTest {

    @Test
    fun nonAdminCreateDefaultsToUnicaHidesAdminSectionsAndCreatesWeekly() {
        val draft = ActivityFormDraft.forCreate(isAdmin = false)
        assertEquals(ActivityFormRecurrence.UNICA, draft.effectiveRecurrence)
        assertFalse(draft.showTypeSelector)
        assertFalse(draft.showFrequencySection)
        assertFalse(draft.showOpenNowSection)
        assertTrue(draft.showDeadlineSection)
        assertFalse(draft.canDelete)

        val result = draft.copy(
            title = "  Limpar geladeira  ",
            category = TaskCategory.LIMPEZA,
            effortPoints = 5,
            deadline = TaskDeadline.SABADO,
        ).validate()

        assertEquals(
            ActivityFormValidationResult.Valid(
                ActivityFormSubmission.CreateWeekly(
                    title = "Limpar geladeira",
                    category = TaskCategory.LIMPEZA,
                    points = 5,
                    deadline = TaskDeadline.SABADO,
                ),
            ),
            result,
        )
    }

    @Test
    fun adminCreateOneOffRequiresRecurrenceAndProducesCreateWeekly() {
        val initial = ActivityFormDraft.forCreate(isAdmin = true)
        assertNull(initial.recurrence)
        assertTrue(initial.showTypeSelector)
        assertFalse(initial.showFrequencySection)
        assertFalse(initial.showOpenNowSection)
        assertFalse(initial.showDeadlineSection)

        val oneOff = initial.withRecurrence(ActivityFormRecurrence.UNICA)
        assertFalse(oneOff.showFrequencySection)
        assertFalse(oneOff.showOpenNowSection)
        assertTrue(oneOff.showDeadlineSection)

        val result = oneOff.copy(
            title = "Pagar conta de luz",
            category = TaskCategory.CONTAS,
            effortPoints = 2,
            deadline = TaskDeadline.HOJE,
        ).validate()

        assertEquals(
            ActivityFormValidationResult.Valid(
                ActivityFormSubmission.CreateWeekly(
                    title = "Pagar conta de luz",
                    category = TaskCategory.CONTAS,
                    points = 2,
                    deadline = TaskDeadline.HOJE,
                ),
            ),
            result,
        )
    }

    @Test
    fun adminCreateRecurringOpenNowTrueRequiresDeadline() {
        val recurring = ActivityFormDraft.forCreate(isAdmin = true)
            .withRecurrence(ActivityFormRecurrence.RECORRENTE)
            .copy(
                title = "Lavar banheiro",
                category = TaskCategory.LIMPEZA,
                effortPoints = 8,
                frequency = TaskFrequency.SEMANAL,
            )
            .withOpenInCurrentWeek(true)

        assertTrue(recurring.showFrequencySection)
        assertTrue(recurring.showOpenNowSection)
        assertTrue(recurring.showDeadlineSection)

        val missingDeadline = recurring.validate() as ActivityFormValidationResult.Invalid
        assertTrue(missingDeadline.errors.deadlineRequired)
        assertFalse(missingDeadline.errors.frequencyRequired)

        val valid = recurring.copy(deadline = TaskDeadline.DOMINGO).validate()
        assertEquals(
            ActivityFormValidationResult.Valid(
                ActivityFormSubmission.SaveRecurring(
                    editingId = null,
                    title = "Lavar banheiro",
                    category = TaskCategory.LIMPEZA,
                    points = 8,
                    frequency = TaskFrequency.SEMANAL,
                    openInCurrentWeek = true,
                    deadline = TaskDeadline.DOMINGO,
                ),
            ),
            valid,
        )
    }

    @Test
    fun adminCreateRecurringOpenNowFalseClearsDeadlineAndSkipsDeadlineValidation() {
        val recurring = ActivityFormDraft.forCreate(isAdmin = true)
            .withRecurrence(ActivityFormRecurrence.RECORRENTE)
            .copy(
                title = "Comprar café",
                category = TaskCategory.COMPRAS,
                effortPoints = 2,
                frequency = TaskFrequency.MENSAL,
                deadline = TaskDeadline.HOJE,
            )
            .withOpenInCurrentWeek(false)

        assertNull(recurring.deadline)
        assertFalse(recurring.showDeadlineSection)

        val valid = recurring.validate()
        assertEquals(
            ActivityFormValidationResult.Valid(
                ActivityFormSubmission.SaveRecurring(
                    editingId = null,
                    title = "Comprar café",
                    category = TaskCategory.COMPRAS,
                    points = 2,
                    frequency = TaskFrequency.MENSAL,
                    openInCurrentWeek = false,
                    deadline = null,
                ),
            ),
            valid,
        )
    }

    @Test
    fun editAssignmentPrefillsNormalizedLegacyEffortAndSemPrazoFallback() {
        val legacyAssignment = UiAssignment(
            id = "a42",
            recurringTaskId = null,
            titulo = "Organizar despensa",
            categoria = TaskCategory.OUTRAS,
            pontos = 3, // legado <=3 normalizado para 2 pts
            deadline = null,
            assigneeUid = null,
        )

        val draft = ActivityFormDraft.forEditAssignment(legacyAssignment, isAdmin = true)
        assertEquals(ActivityFormMode.EDIT_ASSIGNMENT, draft.mode)
        assertEquals("Organizar despensa", draft.title)
        assertEquals(TaskCategory.OUTRAS, draft.category)
        assertEquals(2, draft.effortPoints)
        assertEquals(TaskDeadline.SEM_PRAZO, draft.deadline)
        assertFalse(draft.showTypeSelector)
        assertFalse(draft.showFrequencySection)
        assertFalse(draft.showOpenNowSection)
        assertTrue(draft.showDeadlineSection)
        assertTrue(draft.canDelete)

        val valid = draft.copy(title = "Organizar armário").validate(targetId = "a42")
        assertEquals(
            ActivityFormValidationResult.Valid(
                ActivityFormSubmission.UpdateWeekly(
                    assignmentId = "a42",
                    title = "Organizar armário",
                    category = TaskCategory.OUTRAS,
                    points = 2,
                    deadline = TaskDeadline.SEM_PRAZO,
                ),
            ),
            valid,
        )
    }

    @Test
    fun editRecurringPrefillsFrequencyAndHidesOpenNowAndDeadline() {
        val recurringTask = UiRecurringTask(
            id = "r9",
            titulo = "Tirar lixo",
            categoria = TaskCategory.LIMPEZA,
            pontos = 4, // legado <=6 normalizado para 5 pts
            frequencia = TaskFrequency.DIARIA,
        )

        val draft = ActivityFormDraft.forEditRecurring(recurringTask, isAdmin = true)
        assertEquals(ActivityFormMode.EDIT_RECURRING, draft.mode)
        assertEquals(5, draft.effortPoints)
        assertEquals(TaskFrequency.DIARIA, draft.frequency)
        assertFalse(draft.showTypeSelector)
        assertTrue(draft.showFrequencySection)
        assertFalse(draft.showOpenNowSection)
        assertFalse(draft.showDeadlineSection)
        assertTrue(draft.canDelete)

        val valid = draft.validate(targetId = "r9")
        assertEquals(
            ActivityFormValidationResult.Valid(
                ActivityFormSubmission.SaveRecurring(
                    editingId = "r9",
                    title = "Tirar lixo",
                    category = TaskCategory.LIMPEZA,
                    points = 5,
                    frequency = TaskFrequency.DIARIA,
                    openInCurrentWeek = false,
                    deadline = null,
                ),
            ),
            valid,
        )
    }

    @Test
    fun validateReportsAllMissingFieldsSimultaneously() {
        val adminEmpty = ActivityFormDraft.forCreate(isAdmin = true)
        val invalidAdmin = adminEmpty.validate() as ActivityFormValidationResult.Invalid
        assertTrue(invalidAdmin.errors.titleRequired)
        assertTrue(invalidAdmin.errors.categoryRequired)
        assertTrue(invalidAdmin.errors.effortRequired)
        assertTrue(invalidAdmin.errors.recurrenceRequired)
        assertFalse(invalidAdmin.errors.frequencyRequired)
        assertFalse(invalidAdmin.errors.openNowRequired)
        assertFalse(invalidAdmin.errors.deadlineRequired)
        assertTrue(invalidAdmin.errors.hasErrors)

        val recurringEmpty = adminEmpty.withRecurrence(ActivityFormRecurrence.RECORRENTE)
        val invalidRecurring = recurringEmpty.validate() as ActivityFormValidationResult.Invalid
        assertTrue(invalidRecurring.errors.titleRequired)
        assertTrue(invalidRecurring.errors.categoryRequired)
        assertTrue(invalidRecurring.errors.effortRequired)
        assertFalse(invalidRecurring.errors.recurrenceRequired)
        assertTrue(invalidRecurring.errors.frequencyRequired)
        assertTrue(invalidRecurring.errors.openNowRequired)
        assertFalse(invalidRecurring.errors.deadlineRequired)
    }

    @Test
    fun validateRejectsTrimmedBlankTitle() {
        val draft = ActivityFormDraft.forCreate(isAdmin = false).copy(
            title = "   \t\n ",
            category = TaskCategory.LIMPEZA,
            effortPoints = 2,
            deadline = TaskDeadline.HOJE,
        )

        val result = draft.validate() as ActivityFormValidationResult.Invalid
        assertTrue(result.errors.titleRequired)
        assertFalse(result.errors.categoryRequired)
        assertFalse(result.errors.effortRequired)
        assertFalse(result.errors.deadlineRequired)
    }

    @Test
    fun nonAdminIsUnauthorizedForEditOrRecurring() {
        val editAssignment = ActivityFormDraft(
            mode = ActivityFormMode.EDIT_ASSIGNMENT,
            isAdmin = false,
            title = "Algo",
            category = TaskCategory.LIMPEZA,
            effortPoints = 2,
            deadline = TaskDeadline.HOJE,
        )
        assertEquals(ActivityFormValidationResult.Unauthorized, editAssignment.validate(targetId = "a1"))

        val editRecurring = ActivityFormDraft(
            mode = ActivityFormMode.EDIT_RECURRING,
            isAdmin = false,
            title = "Algo",
            category = TaskCategory.LIMPEZA,
            effortPoints = 2,
            frequency = TaskFrequency.SEMANAL,
        )
        assertEquals(ActivityFormValidationResult.Unauthorized, editRecurring.validate(targetId = "r1"))

        val createRecurring = ActivityFormDraft(
            mode = ActivityFormMode.CREATE,
            isAdmin = false,
            title = "Algo",
            category = TaskCategory.LIMPEZA,
            effortPoints = 2,
            recurrence = ActivityFormRecurrence.RECORRENTE,
            frequency = TaskFrequency.SEMANAL,
            openInCurrentWeek = false,
        )
        assertEquals(ActivityFormValidationResult.Unauthorized, createRecurring.validate())
    }
}
