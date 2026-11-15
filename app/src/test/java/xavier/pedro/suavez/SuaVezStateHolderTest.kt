package xavier.pedro.suavez

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xavier.pedro.suavez.data.model.House
import xavier.pedro.suavez.data.model.HouseRole
import xavier.pedro.suavez.data.model.Member
import xavier.pedro.suavez.data.model.PreferenceLevel
import xavier.pedro.suavez.data.model.TaskWeightLevel
import xavier.pedro.suavez.data.model.User
import xavier.pedro.suavez.ui.state.AppDestination
import xavier.pedro.suavez.ui.state.MainTab
import xavier.pedro.suavez.ui.state.SuaVezStateHolder
import xavier.pedro.suavez.ui.state.TaskCategory
import xavier.pedro.suavez.ui.state.TaskDeadline
import xavier.pedro.suavez.ui.state.TaskFrequency
import xavier.pedro.suavez.ui.state.TestProfile
import xavier.pedro.suavez.ui.state.TestProfileGateway
import xavier.pedro.suavez.ui.state.TestProfileSession
import xavier.pedro.suavez.ui.state.isHouseBalanced

class SuaVezStateHolderTest {

    @Test
    fun loginAndNavigationFlow() = runBlocking {
        val state = SuaVezStateHolder()
        assertEquals(AppDestination.Login, state.destination)

        state.signInAs(TestProfile.ADMIN)
        assertEquals(AppDestination.Main, state.destination)
        assertEquals(MainTab.TASKS, state.activeTab)
        assertEquals(TestProfile.ADMIN.uid, state.currentUser?.uid)
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

        // Troca de perfil via Sair da conta
        state.signOut()
        assertEquals(AppDestination.Login, state.destination)
        assertNull(state.currentUser)

        state.signInAs(TestProfile.MEMBER)
        assertEquals(AppDestination.Main, state.destination)
        assertEquals(TestProfile.MEMBER.uid, state.currentUser?.uid)
        assertFalse(state.isAdmin)
    }

    @Test
    fun testProfileWithoutHouseNavigatesToNoHouse() = runBlocking {
        val gateway = TestProfileGateway { profile ->
            Result.success(
                TestProfileSession(
                    user = User(
                        uid = profile.uid,
                        nome = profile.nome,
                        email = profile.email,
                        currentHouseId = null,
                    ),
                    house = null,
                    members = emptyList(),
                ),
            )
        }
        val state = SuaVezStateHolder(testProfileGateway = gateway)

        state.signInAs(TestProfile.MEMBER)

        assertEquals(AppDestination.NoHouse, state.destination)
        assertEquals(MainTab.HOUSE, state.activeTab)
        assertNull(state.currentHouse)
        assertEquals(TestProfile.MEMBER.uid, state.currentUser?.uid)
    }

    @Test
    fun testProfileWithPersistedHouseLoadsHouseAndRoleFromGateway() = runBlocking {
        val gateway = TestProfileGateway { profile ->
            val house = House(
                id = "house_suavez_teste",
                nome = "Casa Teste",
                codigoConvite = "SV-1000",
                criadaPorUid = TestProfile.ADMIN.uid,
                semanaAtual = "2026-W46",
            )
            val members = listOf(
                Member(uid = TestProfile.ADMIN.uid, nome = TestProfile.ADMIN.nome, papel = HouseRole.ADMIN.name),
                Member(uid = TestProfile.MEMBER.uid, nome = TestProfile.MEMBER.nome, papel = HouseRole.MEMBER.name),
            )
            Result.success(
                TestProfileSession(
                    user = User(
                        uid = profile.uid,
                        nome = profile.nome,
                        email = profile.email,
                        currentHouseId = house.id,
                    ),
                    house = house,
                    members = members,
                ),
            )
        }
        val state = SuaVezStateHolder(testProfileGateway = gateway)

        state.signInAs(TestProfile.ADMIN)
        assertEquals(AppDestination.Main, state.destination)
        assertEquals("Casa Teste", state.currentHouse?.nome)
        assertTrue(state.isAdmin)
        assertEquals(2, state.members.size)
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
    fun togglingTaskMovesCompletedToEndAndRecalculatesQuota() = runBlocking {
        val state = SuaVezStateHolder()
        state.signInAs(TestProfile.ADMIN)

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
    fun claimingOpenTaskAndDistributingPendingTasksRespectsPermissionsAndPreferences() = runBlocking {
        val state = SuaVezStateHolder()
        state.signInAs(TestProfile.ADMIN)

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
    fun nonAdminCannotModifyRecurringTasksOrDistribute() = runBlocking {
        val state = SuaVezStateHolder()
        state.signInAs(TestProfile.MEMBER)

        assertFalse(state.isAdmin)
        val initialRecurringCount = state.recurringTasks.size
        state.saveRecurringTask(null, "Teste", TaskCategory.OUTRAS, 2, TaskFrequency.SEMANAL)
        assertEquals(initialRecurringCount, state.recurringTasks.size)

        val initialOpenCount = state.openAssignments.size
        state.distributePendingTasks()
        assertEquals(initialOpenCount, state.openAssignments.size)
    }

    @Test
    fun preferenceAndBalanceRulesMatchSpec() = runBlocking {
        val state = SuaVezStateHolder()
        state.signInAs(TestProfile.ADMIN)

        val firstRecurring = state.recurringTasks.first()
        state.setPreference(firstRecurring.id, PreferenceLevel.GOSTO)
        assertEquals(PreferenceLevel.GOSTO, state.getMyPreference(firstRecurring.id))

        assertTrue(isHouseBalanced(listOf(34, 32, 31)))
        assertFalse(isHouseBalanced(listOf(40, 20)))

        assertEquals(TaskWeightLevel.LIGHT, TaskWeightLevel.fromPoints(2))
        assertEquals(TaskWeightLevel.MEDIUM, TaskWeightLevel.fromPoints(5))
        assertEquals(TaskWeightLevel.HEAVY, TaskWeightLevel.fromPoints(8))
    }

    @Test
    fun adminCanCreateRecurringTaskOptionallyOpeningInCurrentWeek() = runBlocking {
        val state = SuaVezStateHolder()
        state.signInAs(TestProfile.ADMIN)

        val initialRecurringCount = state.recurringTasks.size
        val initialOpenCount = state.openAssignments.size

        state.saveRecurringTask(
            editingId = null,
            title = "Limpar fogão",
            category = TaskCategory.LIMPEZA,
            points = 5,
            frequency = TaskFrequency.SEMANAL,
            openInCurrentWeek = false,
            deadline = null,
        )
        assertEquals(initialRecurringCount + 1, state.recurringTasks.size)
        assertEquals(initialOpenCount, state.openAssignments.size)

        state.saveRecurringTask(
            editingId = null,
            title = "Aspirar sala",
            category = TaskCategory.LIMPEZA,
            points = 5,
            frequency = TaskFrequency.SEMANAL,
            openInCurrentWeek = true,
            deadline = TaskDeadline.SABADO,
        )
        assertEquals(initialRecurringCount + 2, state.recurringTasks.size)
        assertEquals(initialOpenCount + 1, state.openAssignments.size)
        val opened = state.openAssignments.first()
        assertEquals("Aspirar sala", opened.titulo)
        assertEquals(TaskDeadline.SABADO, opened.deadline)
        assertEquals(state.recurringTasks.last().id, opened.recurringTaskId)
    }

    @Test
    fun adminCanTransferAndLeaveOrDestroyHouse() = runBlocking {
        val state = SuaVezStateHolder()
        state.signInAs(TestProfile.ADMIN)

        val bruno = state.members.first { it.role == HouseRole.MEMBER }
        state.transferAdminAndLeave(bruno.uid)

        assertEquals(AppDestination.NoHouse, state.destination)
        assertNull(state.currentHouse)
    }
}
