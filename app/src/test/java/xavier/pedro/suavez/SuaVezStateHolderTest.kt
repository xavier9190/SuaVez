package xavier.pedro.suavez

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xavier.pedro.suavez.data.model.HouseRole
import xavier.pedro.suavez.data.model.PreferenceLevel
import xavier.pedro.suavez.data.model.TaskWeightLevel
import xavier.pedro.suavez.ui.state.AppDestination
import xavier.pedro.suavez.ui.state.MainTab
import xavier.pedro.suavez.ui.state.SuaVezStateHolder
import xavier.pedro.suavez.ui.state.TaskCategory
import xavier.pedro.suavez.ui.state.TaskDeadline
import xavier.pedro.suavez.ui.state.TaskFrequency
import xavier.pedro.suavez.ui.state.isHouseBalanced

class SuaVezStateHolderTest {

    @Test
    fun loginAndNavigationFlow() {
        val state = SuaVezStateHolder()
        assertEquals(AppDestination.Login, state.destination)

        state.signInWithGoogle()
        assertEquals(AppDestination.Main, state.destination)
        assertEquals(MainTab.TASKS, state.activeTab)
        assertTrue(state.isAdmin)

        state.openOpenTasks()
        assertEquals(AppDestination.OpenTasks, state.destination)
        assertEquals(MainTab.TASKS, state.activeTab)

        assertTrue(state.navigateBack())
        assertEquals(AppDestination.Main, state.destination)

        state.selectTab(MainTab.HOUSE)
        assertEquals(MainTab.HOUSE, state.activeTab)
        assertTrue(state.navigateBack())
        assertEquals(MainTab.TASKS, state.activeTab)
    }

    @Test
    fun signupCreatesNoHouseStateAndAllowsCreatingOrJoiningHouse() {
        val state = SuaVezStateHolder()
        state.createAccount("Lucas Lima", "lucas@suavez.app")

        assertEquals(AppDestination.NoHouse, state.destination)
        assertNull(state.currentHouse)
        assertEquals(MainTab.HOUSE, state.activeTab)

        // Sem casa não navega para Tarefas nem Preferências
        state.selectTab(MainTab.TASKS)
        assertEquals(MainTab.HOUSE, state.activeTab)

        state.createHouse("República Sol")
        assertEquals(AppDestination.Main, state.destination)
        assertEquals(MainTab.TASKS, state.activeTab)
        assertNotNull(state.currentHouse)
        assertTrue(state.isAdmin)
    }

    @Test
    fun togglingTaskMovesCompletedToEndAndRecalculatesQuota() {
        val state = SuaVezStateHolder()
        state.signInWithGoogle()

        assertEquals(3, state.myDonePoints)
        assertEquals(13, state.myTotalPoints)
        assertEquals(10, state.myPendingPoints)
        assertEquals(2, state.myPendingCount)
        assertFalse(state.myAssignments.first().isDone)
        assertTrue(state.myAssignments.last().isDone)

        val firstPending = state.myAssignments.first()
        state.toggleTaskDone(firstPending.id)

        assertEquals(3 + firstPending.pontos, state.myDonePoints)
        assertEquals(1, state.myPendingCount)
        assertTrue(state.myAssignments.last().isDone)
        assertEquals("Desfazer", state.snackbar?.actionLabel)

        // Desfazer restaura o estado anterior
        state.snackbar?.onAction?.invoke()
        assertEquals(3, state.myDonePoints)
        assertEquals(2, state.myPendingCount)
    }

    @Test
    fun claimingOpenTaskAndDistributingPendingTasksRespectsPermissionsAndPreferences() {
        val state = SuaVezStateHolder()
        state.signInWithGoogle()

        val initialOpenCount = state.openAssignments.size
        val openTask = state.openAssignments.first()
        state.claimOpenTask(openTask.id)

        assertEquals(initialOpenCount - 1, state.openAssignments.size)
        assertTrue(state.myAssignments.any { it.id == openTask.id })
        assertTrue(state.openScreenAssignments.any { it.id == openTask.id && it.justClaimedInSession })

        state.addWeeklyTask("Lavar janelas", TaskCategory.LIMPEZA, 5, TaskDeadline.DOMINGO)
        state.distributePendingTasks()
        assertEquals(0, state.openAssignments.size)
    }

    @Test
    fun nonAdminCannotModifyFixedTasksOrDistribute() {
        val state = SuaVezStateHolder()
        state.signInWithEmail("bruno@suavez.app")

        assertFalse(state.isAdmin)
        val initialFixedCount = state.fixedTasks.size
        state.saveFixedTask(null, "Teste", TaskCategory.OUTRAS, 2, TaskFrequency.SEMANAL)
        assertEquals(initialFixedCount, state.fixedTasks.size)

        val initialOpenCount = state.openAssignments.size
        state.distributePendingTasks()
        assertEquals(initialOpenCount, state.openAssignments.size)
    }

    @Test
    fun preferenceAndBalanceRulesMatchSpec() {
        val state = SuaVezStateHolder()
        state.signInWithGoogle()

        val firstFixed = state.fixedTasks.first()
        state.setPreference(firstFixed.id, PreferenceLevel.GOSTO)
        assertEquals(PreferenceLevel.GOSTO, state.getMyPreference(firstFixed.id))

        assertTrue(isHouseBalanced(listOf(34, 32, 31)))
        assertFalse(isHouseBalanced(listOf(40, 20)))

        assertEquals(TaskWeightLevel.LIGHT, TaskWeightLevel.fromPoints(2))
        assertEquals(TaskWeightLevel.MEDIUM, TaskWeightLevel.fromPoints(5))
        assertEquals(TaskWeightLevel.HEAVY, TaskWeightLevel.fromPoints(8))
    }

    @Test
    fun adminCanTransferAndLeaveOrDestroyHouse() {
        val state = SuaVezStateHolder()
        state.signInWithGoogle()

        val bruno = state.members.first { it.role == HouseRole.MEMBER }
        state.transferAdminAndLeave(bruno.uid)

        assertEquals(AppDestination.NoHouse, state.destination)
        assertNull(state.currentHouse)
    }
}
