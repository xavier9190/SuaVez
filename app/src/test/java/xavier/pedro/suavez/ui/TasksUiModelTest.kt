package xavier.pedro.suavez.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xavier.pedro.suavez.data.model.HouseRole
import xavier.pedro.suavez.ui.state.SuaVezUiState
import xavier.pedro.suavez.ui.state.TaskCategory
import xavier.pedro.suavez.ui.state.TaskDeadline
import xavier.pedro.suavez.ui.state.TaskFrequency
import xavier.pedro.suavez.ui.state.UiAssignment
import xavier.pedro.suavez.ui.state.UiHouse
import xavier.pedro.suavez.ui.state.UiMember
import xavier.pedro.suavez.ui.state.UiRecurringTask
import xavier.pedro.suavez.ui.state.UiUser

class TasksUiModelTest {

    private val user = UiUser(uid = "u1", nome = "Ana Clara Silva", email = "ana@example.com")
    private val house = UiHouse(id = "h1", nome = "República Sol", codigoConvite = "SV-1234", semanaNumero = 14)

    @Test
    fun returnsNullWhenUserOrHouseMissing() {
        assertNull(SuaVezUiState(currentUser = null, currentHouse = house).toTasksUiModel())
        assertNull(SuaVezUiState(currentUser = user, currentHouse = null).toTasksUiModel())
    }

    @Test
    fun greetingWeekAndMemberCountDerivedCleanly() {
        val state = SuaVezUiState(
            currentUser = user,
            currentHouse = house,
            members = listOf(
                UiMember("u1", "Ana Clara", HouseRole.ADMIN, 10),
                UiMember("u2", "Bruno", HouseRole.MEMBER, 5),
                UiMember("u3", "Carla", HouseRole.MEMBER, 8),
            ),
        )

        val model = state.toTasksUiModel()!!
        assertEquals("Ana", model.firstName)
        assertEquals(14, model.weekNumber)
        assertEquals(3, model.memberCount)
        assertEquals("República Sol", model.houseName)
    }

    @Test
    fun quotaFractionsHandleNormalZeroAndCompletedCases() {
        val zeroState = SuaVezUiState(currentUser = user, currentHouse = house)
        val zeroModel = zeroState.toTasksUiModel()!!
        assertEquals(0, zeroModel.totalPoints)
        assertEquals(0f, zeroModel.doneFraction, 0.0001f)
        assertEquals(0f, zeroModel.pendingFraction, 0.0001f)
        assertTrue(zeroModel.isQuotaCompleted)

        val partialState = SuaVezUiState(
            currentUser = user,
            currentHouse = house,
            members = listOf(UiMember("u1", "Ana", HouseRole.ADMIN, 5)),
            recurringTasks = listOf(
                UiRecurringTask("r1", "Lavar louça", TaskCategory.LIMPEZA, 10, TaskFrequency.SEMANAL),
            ),
            assignments = listOf(
                assignment("a1", TaskDeadline.HOJE, pontos = 5, assigneeUid = "u1", isDone = true),
                assignment("a2", TaskDeadline.SABADO, pontos = 5, assigneeUid = "u1", isDone = false),
            ),
        )
        val partialModel = partialState.toTasksUiModel()!!
        assertEquals(5, partialModel.donePoints)
        assertEquals(10, partialModel.totalPoints)
        assertEquals(5, partialModel.pendingPoints)
        assertEquals(0.5f, partialModel.doneFraction, 0.0001f)
        assertEquals(0.5f, partialModel.pendingFraction, 0.0001f)
        assertFalse(partialModel.isQuotaCompleted)

        val completedState = partialState.copy(
            assignments = listOf(
                assignment("a1", TaskDeadline.HOJE, pontos = 8, assigneeUid = "u1", isDone = true),
                assignment("a2", TaskDeadline.SABADO, pontos = 2, assigneeUid = "u1", isDone = true),
            ),
        )
        val completedModel = completedState.toTasksUiModel()!!
        assertEquals(10, completedModel.donePoints)
        assertEquals(0, completedModel.pendingPoints)
        assertEquals(1f, completedModel.doneFraction, 0.0001f)
        assertEquals(0f, completedModel.pendingFraction, 0.0001f)
        assertTrue(completedModel.isQuotaCompleted)
    }

    @Test
    fun myTasksOrderedByUrgencyAndSuaVezMarksOnlyFirstPending() {
        val state = SuaVezUiState(
            currentUser = user,
            currentHouse = house,
            assignments = listOf(
                assignment("done_hoje", TaskDeadline.HOJE, pontos = 2, assigneeUid = "u1", isDone = true),
                assignment("pending_domingo", TaskDeadline.DOMINGO, pontos = 5, assigneeUid = "u1", isDone = false),
                assignment("pending_sem_prazo", null, pontos = 2, assigneeUid = "u1", isDone = false),
                assignment("pending_hoje", TaskDeadline.HOJE, pontos = 8, assigneeUid = "u1", isDone = false),
                assignment("other_member", TaskDeadline.HOJE, pontos = 5, assigneeUid = "u2", isDone = false),
            ),
        )

        val model = state.toTasksUiModel()!!
        assertEquals(
            listOf("pending_hoje", "pending_domingo", "pending_sem_prazo", "done_hoje"),
            model.myTasks.map { it.assignment.id },
        )
        assertEquals(3, model.pendingCount)
        assertTrue(model.myTasks[0].isSuaVez)
        assertFalse(model.myTasks[1].isSuaVez)
        assertFalse(model.myTasks[2].isSuaVez)
        assertFalse(model.myTasks[3].isSuaVez)
    }

    @Test
    fun allDoneTasksYieldZeroPendingAndNoSuaVez() {
        val state = SuaVezUiState(
            currentUser = user,
            currentHouse = house,
            assignments = listOf(
                assignment("a1", TaskDeadline.HOJE, pontos = 2, assigneeUid = "u1", isDone = true),
            ),
        )

        val model = state.toTasksUiModel()!!
        assertEquals(0, model.pendingCount)
        assertTrue(model.myTasks.none { it.isSuaVez })
    }

    @Test
    fun houseRhythmCapsAvatarsAtFiveAndOpenPreviewAtTwo() {
        val members = (1..7).map { i ->
            UiMember("u$i", "Morador $i", if (i == 1) HouseRole.ADMIN else HouseRole.MEMBER, i * 2)
        }
        val assignments = listOf(
            assignment("done1", TaskDeadline.HOJE, pontos = 2, assigneeUid = "u1", isDone = true),
            assignment("done2", TaskDeadline.HOJE, pontos = 5, assigneeUid = "u2", isDone = true),
            assignment("mine_pending", TaskDeadline.SABADO, pontos = 5, assigneeUid = "u1", isDone = false),
            assignment("open1", TaskDeadline.HOJE, pontos = 2, assigneeUid = null, isDone = false),
            assignment("open2", TaskDeadline.SABADO, pontos = 5, assigneeUid = null, isDone = false),
            assignment("open3", TaskDeadline.DOMINGO, pontos = 8, assigneeUid = null, isDone = false),
            assignment("open4", TaskDeadline.SEM_PRAZO, pontos = 2, assigneeUid = null, isDone = false),
        )

        val state = SuaVezUiState(
            currentUser = user,
            currentHouse = house,
            members = members,
            assignments = assignments,
        )

        val model = state.toTasksUiModel()!!
        assertEquals(5, model.visibleAvatars.size)
        assertEquals(listOf("u1", "u2", "u3", "u4", "u5"), model.visibleAvatars.map { it.uid })
        assertEquals(2, model.houseDoneCount)
        assertEquals(7, model.houseTotalCount)
        assertEquals(5, model.houseRemainingCount)
        assertEquals(29, model.houseCompletionPercent)
        assertEquals(2f / 7f, model.houseProgressFraction, 0.0001f)
        assertEquals(4, model.openTasksCount)
        assertEquals(listOf("open1", "open2"), model.openPreviewTasks.map { it.id })
        assertEquals(2, model.extraOpenTasksCount)
    }

    private fun assignment(
        id: String,
        deadline: TaskDeadline?,
        pontos: Int,
        assigneeUid: String?,
        isDone: Boolean,
    ): UiAssignment = UiAssignment(
        id = id,
        recurringTaskId = null,
        titulo = "Tarefa $id",
        categoria = TaskCategory.LIMPEZA,
        pontos = pontos,
        deadline = deadline,
        assigneeUid = assigneeUid,
        isDone = isDone,
    )
}
