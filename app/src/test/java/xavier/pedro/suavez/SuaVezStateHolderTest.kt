package xavier.pedro.suavez

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import xavier.pedro.suavez.data.model.Assignment
import xavier.pedro.suavez.data.model.AssignmentStatus
import xavier.pedro.suavez.data.model.House
import xavier.pedro.suavez.data.model.HouseRole
import xavier.pedro.suavez.data.model.Member
import xavier.pedro.suavez.data.model.PreferenceLevel
import xavier.pedro.suavez.data.model.Task
import xavier.pedro.suavez.data.model.TaskWeightLevel
import xavier.pedro.suavez.data.model.User
import xavier.pedro.suavez.data.repository.AssignmentRepository
import xavier.pedro.suavez.data.repository.DateCycleUtils
import xavier.pedro.suavez.data.repository.GreedyDecision
import xavier.pedro.suavez.data.repository.HouseRepository
import xavier.pedro.suavez.data.repository.RolloverPlan
import xavier.pedro.suavez.data.repository.SuaVezDataSource
import xavier.pedro.suavez.ui.MainShellDestination
import xavier.pedro.suavez.ui.state.AppDestination
import xavier.pedro.suavez.ui.state.MainTab
import xavier.pedro.suavez.ui.state.SuaVezAppContainer
import xavier.pedro.suavez.ui.state.SuaVezSharedViewModel
import xavier.pedro.suavez.ui.state.SuaVezStateHolder
import xavier.pedro.suavez.ui.state.SuaVezUiState
import xavier.pedro.suavez.ui.state.TaskCategory
import xavier.pedro.suavez.ui.state.TaskDeadline
import xavier.pedro.suavez.ui.state.TaskFrequency
import xavier.pedro.suavez.ui.state.TestProfile
import xavier.pedro.suavez.ui.state.TestProfileSession
import xavier.pedro.suavez.ui.state.UiUser
import xavier.pedro.suavez.ui.state.isHouseBalanced
import xavier.pedro.suavez.ui.toMainShellUiModel
import java.util.Locale

class SuaVezStateHolderTest {

    @Test
    fun loginAndNavigationFlow() = runBlocking {
        val fake = FakeSuaVezDataSource()
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val state = SuaVezStateHolder(dataSource = fake, scope = testScope)
        try {
            assertEquals(AppDestination.Login, state.uiState.value.destination)

            state.signInAs(TestProfile.ADMIN)
            assertEquals(AppDestination.Main, state.uiState.value.destination)
            assertEquals(MainTab.TASKS, state.uiState.value.activeTab)
            assertEquals(TestProfile.ADMIN.uid, state.uiState.value.currentUser?.uid)
            assertTrue(state.uiState.value.isAdmin)
            // Sem tarefas mockadas iniciais
            assertTrue(state.uiState.value.recurringTasks.isEmpty())
            assertTrue(state.uiState.value.assignments.isEmpty())

            state.openOpenTasks()
            assertEquals(AppDestination.OpenTasks, state.uiState.value.destination)
            assertEquals(MainTab.TASKS, state.uiState.value.activeTab)

            assertTrue(state.navigateBack())
            assertEquals(AppDestination.Main, state.uiState.value.destination)

            state.selectTab(MainTab.HOUSE)
            assertEquals(MainTab.HOUSE, state.uiState.value.activeTab)
            assertTrue(state.navigateBack())
            assertEquals(MainTab.TASKS, state.uiState.value.activeTab)

            state.signOut()
            assertEquals(AppDestination.Login, state.uiState.value.destination)
            assertNull(state.uiState.value.currentUser)

            state.signInAs(TestProfile.MEMBER)
            assertEquals(AppDestination.Main, state.uiState.value.destination)
            assertEquals(TestProfile.MEMBER.uid, state.uiState.value.currentUser?.uid)
            assertFalse(state.uiState.value.isAdmin)
        } finally {
            testScope.cancel()
        }
    }

    @Test
    fun testProfileWithoutHouseCanCreateOrJoinHouseWithEmptyTasks() = runBlocking {
        val fake = FakeSuaVezDataSource(startWithoutHouse = true)
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val state = SuaVezStateHolder(dataSource = fake, scope = testScope)
        try {
            state.signInAs(TestProfile.MEMBER)

            assertEquals(AppDestination.NoHouse, state.uiState.value.destination)
            assertEquals(MainTab.HOUSE, state.uiState.value.activeTab)
            assertNull(state.uiState.value.currentHouse)

            // Sem casa não navega para Tarefas nem Preferências
            state.selectTab(MainTab.TASKS)
            assertEquals(MainTab.HOUSE, state.uiState.value.activeTab)

            state.createHouse("República Sol")
            assertEquals(AppDestination.Main, state.uiState.value.destination)
            assertEquals(MainTab.TASKS, state.uiState.value.activeTab)
            assertEquals("República Sol", state.uiState.value.currentHouse?.nome)
            assertTrue(state.uiState.value.isAdmin)
            assertTrue(state.uiState.value.recurringTasks.isEmpty())
            assertTrue(state.uiState.value.assignments.isEmpty())
        } finally {
            testScope.cancel()
        }
    }

    @Test
    fun togglingTaskUpdatesMemberPointsAndUndoReverts() = runBlocking {
        val fake = FakeSuaVezDataSource()
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val state = SuaVezStateHolder(dataSource = fake, scope = testScope)
        try {
            state.signInAs(TestProfile.ADMIN)

            state.addWeeklyTask("Lavar louça", TaskCategory.LIMPEZA, 5, TaskDeadline.HOJE)
            val openTask = state.uiState.value.openAssignments.single()
            state.claimOpenTask(openTask.id)

            assertEquals(0, state.uiState.value.myDonePoints)
            assertEquals(5, state.uiState.value.myPendingPoints)
            assertEquals(1, state.uiState.value.myPendingCount)

            state.toggleTaskDone(openTask.id)

            assertEquals(5, state.uiState.value.myDonePoints)
            assertEquals(0, state.uiState.value.myPendingCount)
            assertEquals(5, state.uiState.value.memberWeeklyPoints(TestProfile.ADMIN.uid))
            assertEquals("Desfazer", state.uiState.value.snackbar?.actionLabel)

            state.uiState.value.snackbar?.onAction?.invoke()
            assertEquals(0, state.uiState.value.myDonePoints)
            assertEquals(1, state.uiState.value.myPendingCount)
            assertEquals(0, state.uiState.value.memberWeeklyPoints(TestProfile.ADMIN.uid))
        } finally {
            testScope.cancel()
        }
    }

    @Test
    fun claimingAndDistributingPendingTasksRespectsPreferencesAndLoad() = runBlocking {
        val fake = FakeSuaVezDataSource()
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val state = SuaVezStateHolder(dataSource = fake, scope = testScope)
        try {
            state.signInAs(TestProfile.ADMIN)

            state.saveRecurringTask(
                editingId = null,
                title = "Lavar banheiro",
                category = TaskCategory.LIMPEZA,
                points = 8,
                frequency = TaskFrequency.SEMANAL,
                openInCurrentWeek = true,
                deadline = TaskDeadline.SABADO,
            )
            val recurringId = state.uiState.value.recurringTasks.single().id
            state.setPreference(recurringId, PreferenceLevel.ODEIO)

            state.addWeeklyTask("Tirar o lixo", TaskCategory.LIMPEZA, 2, TaskDeadline.HOJE)
            assertEquals(2, state.uiState.value.openAssignments.size)

            state.distributePendingTasks()
            assertEquals(0, state.uiState.value.openAssignments.size)

            val bathroomAssignment = state.uiState.value.assignments.first { it.recurringTaskId == recurringId }
            assertEquals(TestProfile.MEMBER.uid, bathroomAssignment.assigneeUid)
        } finally {
            testScope.cancel()
        }
    }

    @Test
    fun nonAdminCannotModifyRecurringTasksOrDistribute() = runBlocking {
        val fake = FakeSuaVezDataSource()
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val state = SuaVezStateHolder(dataSource = fake, scope = testScope)
        try {
            state.signInAs(TestProfile.MEMBER)

            assertFalse(state.uiState.value.isAdmin)
            state.saveRecurringTask(null, "Teste", TaskCategory.OUTRAS, 2, TaskFrequency.SEMANAL)
            assertEquals(0, state.uiState.value.recurringTasks.size)

            state.addWeeklyTask("Avulsa", TaskCategory.OUTRAS, 2, TaskDeadline.DOMINGO)
            assertEquals(1, state.uiState.value.openAssignments.size)

            state.distributePendingTasks()
            assertEquals(1, state.uiState.value.openAssignments.size)
        } finally {
            testScope.cancel()
        }
    }

    @Test
    fun recurringTaskCrudAndDeletionRemovesPendingWeekAssignments() = runBlocking {
        val fake = FakeSuaVezDataSource()
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val state = SuaVezStateHolder(dataSource = fake, scope = testScope)
        try {
            state.signInAs(TestProfile.ADMIN)

            state.saveRecurringTask(
                editingId = null,
                title = "Limpar fogão",
                category = TaskCategory.LIMPEZA,
                points = 5,
                frequency = TaskFrequency.SEMANAL,
                openInCurrentWeek = false,
                deadline = null,
            )
            assertEquals(1, state.uiState.value.recurringTasks.size)
            assertEquals(0, state.uiState.value.openAssignments.size)

            state.saveRecurringTask(
                editingId = null,
                title = "Aspirar sala",
                category = TaskCategory.LIMPEZA,
                points = 5,
                frequency = TaskFrequency.SEMANAL,
                openInCurrentWeek = true,
                deadline = TaskDeadline.SABADO,
            )
            assertEquals(2, state.uiState.value.recurringTasks.size)
            assertEquals(1, state.uiState.value.openAssignments.size)
            val opened = state.uiState.value.openAssignments.single()
            assertEquals("Aspirar sala", opened.titulo)
            assertEquals(TaskDeadline.SABADO, opened.deadline)

            val aspirarId = opened.recurringTaskId!!
            state.saveRecurringTask(
                editingId = aspirarId,
                title = "Aspirar sala e tapete",
                category = TaskCategory.LIMPEZA,
                points = 8,
                frequency = TaskFrequency.QUINZENAL,
            )
            val editedTask = state.uiState.value.recurringTasks.first { it.id == aspirarId }
            assertEquals("Aspirar sala e tapete", editedTask.titulo)
            assertEquals(8, editedTask.pontos)
            assertEquals(TaskFrequency.QUINZENAL, editedTask.frequencia)
            assertEquals("Aspirar sala e tapete", state.uiState.value.openAssignments.single().titulo)

            state.deleteRecurringTask(aspirarId)
            assertEquals(1, state.uiState.value.recurringTasks.size)
            assertTrue(state.uiState.value.openAssignments.isEmpty())
        } finally {
            testScope.cancel()
        }
    }

    @Test
    fun preferenceAndBalanceRulesMatchSpec() = runBlocking {
        val fake = FakeSuaVezDataSource()
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val state = SuaVezStateHolder(dataSource = fake, scope = testScope)
        try {
            state.signInAs(TestProfile.ADMIN)
            state.saveRecurringTask(null, "Lavar louça", TaskCategory.LIMPEZA, 3, TaskFrequency.DIARIA)

            val taskId = state.uiState.value.recurringTasks.single().id
            state.setPreference(taskId, PreferenceLevel.GOSTO)
            assertEquals(PreferenceLevel.GOSTO, state.uiState.value.getMyPreference(taskId))

            assertTrue(isHouseBalanced(listOf(34, 32, 31)))
            assertFalse(isHouseBalanced(listOf(40, 20)))

            assertEquals(TaskWeightLevel.LIGHT, TaskWeightLevel.fromPoints(2))
            assertEquals(TaskWeightLevel.MEDIUM, TaskWeightLevel.fromPoints(5))
            assertEquals(TaskWeightLevel.HEAVY, TaskWeightLevel.fromPoints(8))
        } finally {
            testScope.cancel()
        }
    }

    @Test
    fun adminCanRenameTransferAndLeaveOrDestroyHouse() = runBlocking {
        val fake = FakeSuaVezDataSource()
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val state = SuaVezStateHolder(dataSource = fake, scope = testScope)
        try {
            state.signInAs(TestProfile.ADMIN)
            state.renameHouse("Casa Nova")
            assertEquals("Casa Nova", state.uiState.value.currentHouse?.nome)

            val member = state.uiState.value.members.first { it.role == HouseRole.MEMBER }
            state.transferAdminAndLeave(member.uid)

            assertEquals(AppDestination.NoHouse, state.uiState.value.destination)
            assertNull(state.uiState.value.currentHouse)

            // Membro agora é admin e pode destruir a casa
            state.signInAs(TestProfile.MEMBER)
            assertTrue(state.uiState.value.isAdmin)
            state.destroyHouse()
            assertEquals(AppDestination.NoHouse, state.uiState.value.destination)
            assertNull(state.uiState.value.currentHouse)
        } finally {
            testScope.cancel()
        }
    }

    @Test
    fun adminCanEditAndDeleteOpenWeeklyTaskButMemberCannot() = runBlocking {
        val fake = FakeSuaVezDataSource()
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val state = SuaVezStateHolder(dataSource = fake, scope = testScope)
        try {
            state.signInAs(TestProfile.ADMIN)
            state.addWeeklyTask("Lavar varanda", TaskCategory.LIMPEZA, 5, TaskDeadline.SABADO)
            val created = state.uiState.value.openAssignments.first { it.titulo == "Lavar varanda" }

            state.updateWeeklyAssignment(
                assignmentId = created.id,
                title = "Lavar varanda e quintal",
                category = TaskCategory.OUTRAS,
                points = 8,
                deadline = TaskDeadline.HOJE,
            )
            val updated = state.uiState.value.openAssignments.first { it.id == created.id }
            assertEquals("Lavar varanda e quintal", updated.titulo)
            assertEquals(TaskCategory.OUTRAS, updated.categoria)
            assertEquals(8, updated.pontos)
            assertEquals(TaskDeadline.HOJE, updated.deadline)

            // Membro comum não pode editar nem excluir tarefa aberta
            state.signOut()
            state.signInAs(TestProfile.MEMBER)
            assertFalse(state.uiState.value.isAdmin)
            state.updateWeeklyAssignment(created.id, "Hackeado", TaskCategory.LIMPEZA, 2, TaskDeadline.DOMINGO)
            state.deleteWeeklyAssignment(created.id)
            assertTrue(state.uiState.value.openAssignments.any { it.id == created.id && it.titulo == "Lavar varanda e quintal" })

            // Admin consegue excluir a tarefa aberta
            state.signOut()
            state.signInAs(TestProfile.ADMIN)
            state.deleteWeeklyAssignment(created.id)
            assertFalse(state.uiState.value.openAssignments.any { it.id == created.id })
            assertEquals("Atividade excluída", state.uiState.value.snackbar?.message)
        } finally {
            testScope.cancel()
        }
    }

    @Test
    fun sharedContainerAndViewModelPreserveStateAndObserversAcrossNavigation() = runBlocking {
        val fake = FakeSuaVezDataSource()
        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val container = SuaVezAppContainer(dataSource = fake, appScope = appScope)
        val factory = SuaVezSharedViewModel.factory { container }

        val loginStore = ViewModelStore()
        val mainStore = ViewModelStore()
        try {
            val loginVm = ViewModelProvider(loginStore, factory)[SuaVezSharedViewModel::class.java]
            val mainVm = ViewModelProvider(mainStore, factory)[SuaVezSharedViewModel::class.java]

            assertSame(loginVm.stateHolder, mainVm.stateHolder)
            assertSame(loginVm.uiState, mainVm.uiState)

            loginVm.stateHolder.signInAs(TestProfile.ADMIN)
            assertEquals(AppDestination.Main, mainVm.uiState.value.destination)

            // Simula LoginActivity.finish() limpando seu ViewModelStore: observadores continuam ativos
            loginStore.clear()

            mainVm.stateHolder.addWeeklyTask("Regar plantas", TaskCategory.OUTRAS, 2, TaskDeadline.HOJE)
            assertEquals(1, mainVm.uiState.value.openAssignments.size)
            assertEquals("Regar plantas", mainVm.uiState.value.openAssignments.single().titulo)
        } finally {
            mainStore.clear()
            appScope.cancel()
        }
    }

    @Test
    fun noHouseUserCanJoinExistingHouseByCodeAndHandleInvalidCode() = runBlocking {
        val fake = FakeSuaVezDataSource(startWithoutHouse = true)
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val state = SuaVezStateHolder(dataSource = fake, scope = testScope)
        try {
            state.signInAs(TestProfile.ADMIN)
            state.createHouse("Casa Compartilhada")
            val inviteCode = state.uiState.value.currentHouse?.codigoConvite
            assertNotNull(inviteCode)
            state.signOut()

            state.signInAs(TestProfile.MEMBER)
            assertEquals(AppDestination.NoHouse, state.uiState.value.destination)
            assertNull(state.uiState.value.currentHouse)

            state.joinHouseByCode("SV-9999")
            assertEquals(AppDestination.NoHouse, state.uiState.value.destination)
            assertNull(state.uiState.value.currentHouse)
            assertTrue(state.uiState.value.snackbar?.message?.contains("Nenhuma casa encontrada") == true)

            val rawNormalizedInput = inviteCode!!.replace("-", "").lowercase(Locale.ROOT)
            state.joinHouseByCode(rawNormalizedInput)
            assertEquals(AppDestination.Main, state.uiState.value.destination)
            assertEquals(MainTab.TASKS, state.uiState.value.activeTab)
            assertEquals(inviteCode, state.uiState.value.currentHouse?.codigoConvite)
            assertFalse(state.uiState.value.isAdmin)
            assertTrue(state.uiState.value.snackbar?.message?.contains("Você entrou na") == true)
        } finally {
            testScope.cancel()
        }
    }

    @Test
    fun noHouseProfileTabNavigationAndBackBehavior() = runBlocking {
        val fake = FakeSuaVezDataSource(startWithoutHouse = true)
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val state = SuaVezStateHolder(dataSource = fake, scope = testScope)
        try {
            state.signInAs(TestProfile.MEMBER)
            assertEquals(AppDestination.NoHouse, state.uiState.value.destination)
            assertEquals(MainTab.HOUSE, state.uiState.value.activeTab)

            state.selectTab(MainTab.PROFILE)
            assertEquals(AppDestination.NoHouse, state.uiState.value.destination)
            assertEquals(MainTab.PROFILE, state.uiState.value.activeTab)

            assertTrue(state.navigateBack())
            assertEquals(AppDestination.NoHouse, state.uiState.value.destination)
            assertEquals(MainTab.HOUSE, state.uiState.value.activeTab)

            assertFalse(state.navigateBack())
        } finally {
            testScope.cancel()
        }
    }

    @Test
    fun mainShellUiModelMapsTabsTopBarAndDestinationsCorrectly() = runBlocking {
        val fake = FakeSuaVezDataSource(startWithoutHouse = true)
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val state = SuaVezStateHolder(dataSource = fake, scope = testScope)
        try {
            state.signInAs(TestProfile.ADMIN)

            val noHouseShell = state.uiState.value.toMainShellUiModel()
            assertFalse(noHouseShell.hasHouse)
            assertEquals(listOf(MainTab.HOUSE, MainTab.PROFILE), noHouseShell.visibleTabs)
            assertEquals(MainTab.HOUSE, noHouseShell.selectedTab)
            assertEquals(MainShellDestination.NO_HOUSE, noHouseShell.destination)
            assertTrue(noHouseShell.showTopBar)
            assertNull(noHouseShell.houseTitle)
            assertNotNull(noHouseShell.avatarUser)

            state.selectTab(MainTab.PROFILE)
            val noHouseProfileShell = state.uiState.value.toMainShellUiModel()
            assertEquals(listOf(MainTab.HOUSE, MainTab.PROFILE), noHouseProfileShell.visibleTabs)
            assertEquals(MainTab.PROFILE, noHouseProfileShell.selectedTab)
            assertEquals(MainShellDestination.PROFILE, noHouseProfileShell.destination)
            assertNull(noHouseProfileShell.avatarUser)

            state.createHouse("Casa dos Amigos")
            val mainTasksShell = state.uiState.value.toMainShellUiModel()
            assertTrue(mainTasksShell.hasHouse)
            assertEquals(
                listOf(MainTab.TASKS, MainTab.PREFERENCES, MainTab.HOUSE, MainTab.PROFILE),
                mainTasksShell.visibleTabs,
            )
            assertEquals(MainTab.TASKS, mainTasksShell.selectedTab)
            assertEquals(MainShellDestination.TASKS, mainTasksShell.destination)
            assertTrue(mainTasksShell.showTopBar)
            assertEquals("Casa dos Amigos", mainTasksShell.houseTitle)
            assertNotNull(mainTasksShell.avatarUser)

            state.selectTab(MainTab.PROFILE)
            val mainProfileShell = state.uiState.value.toMainShellUiModel()
            assertEquals(MainShellDestination.PROFILE, mainProfileShell.destination)
            assertNull(mainProfileShell.avatarUser)

            state.openOpenTasks()
            val openTasksShell = state.uiState.value.toMainShellUiModel()
            assertEquals(MainShellDestination.OPEN_TASKS, openTasksShell.destination)
            assertEquals(MainTab.TASKS, openTasksShell.selectedTab)
            assertFalse(openTasksShell.showTopBar)
        } finally {
            testScope.cancel()
        }
    }

    @Test
    fun signInAsClearsPriorLoginErrorSnackbarOnSubsequentSuccess() = runBlocking {
        val fake = FakeSuaVezDataSource()
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val state = SuaVezStateHolder(dataSource = fake, scope = testScope)
        try {
            fake.nextSignInError = IllegalStateException("Falha simulada de rede")
            state.signInAs(TestProfile.ADMIN)
            assertNull(state.uiState.value.currentUser)
            assertTrue(state.uiState.value.snackbar?.message?.contains("Erro ao entrar") == true)

            state.signInAs(TestProfile.ADMIN)
            assertNotNull(state.uiState.value.currentUser)
            assertNull(state.uiState.value.snackbar)
        } finally {
            testScope.cancel()
        }
    }

    @Test
    fun signOutClearsActiveSnackbar() = runBlocking {
        val fake = FakeSuaVezDataSource()
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val state = SuaVezStateHolder(dataSource = fake, scope = testScope)
        try {
            state.signInAs(TestProfile.ADMIN)
            state.addWeeklyTask("Lavar louça", TaskCategory.LIMPEZA, 5, TaskDeadline.HOJE)
            val openTask = state.uiState.value.openAssignments.single()
            state.claimOpenTask(openTask.id)
            state.toggleTaskDone(openTask.id)
            assertNotNull(state.uiState.value.snackbar)

            state.signOut()
            assertNull(state.uiState.value.currentUser)
            assertNull(state.uiState.value.snackbar)
        } finally {
            testScope.cancel()
        }
    }

    @Test
    fun mainShellUiModelGuardsOpenTasksWithoutHouse() {
        val uiState = SuaVezUiState(
            currentUser = UiUser(uid = "u_1", nome = "Ana", email = "ana@suavez.app"),
            currentHouse = null,
            activeTab = MainTab.TASKS,
            destination = AppDestination.OpenTasks,
        )

        val shell = uiState.toMainShellUiModel()

        assertFalse(shell.hasHouse)
        assertEquals(MainTab.HOUSE, shell.selectedTab)
        assertEquals(MainShellDestination.NO_HOUSE, shell.destination)
        assertTrue(shell.showTopBar)
    }

    @Test
    fun startSignInCompletesRolloverAndStartsObservingHouseWhenLoginScopeIsCancelled() = runBlocking {
        val fake = FakeSuaVezDataSource()
        fake.addWeeklyTask(
            houseId = HouseRepository.DEFAULT_TEST_HOUSE_ID,
            title = "Limpar cozinha",
            category = TaskCategory.LIMPEZA,
            points = 5,
            deadline = TaskDeadline.HOJE,
            createdByUid = TestProfile.ADMIN.uid,
            semana = DateCycleUtils.currentIsoWeekKey(),
        )
        val rolloverStarted = CompletableDeferred<Unit>()
        val rolloverRelease = CompletableDeferred<Unit>()
        fake.rolloverStarted = rolloverStarted
        fake.rolloverRelease = rolloverRelease

        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val container = SuaVezAppContainer(dataSource = fake, appScope = appScope)
        val factory = SuaVezSharedViewModel.factory { container }
        val loginStore = ViewModelStore()
        val mainStore = ViewModelStore()
        val loginActivityScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val loginVm = ViewModelProvider(loginStore, factory)[SuaVezSharedViewModel::class.java]
            val mainVm = ViewModelProvider(mainStore, factory)[SuaVezSharedViewModel::class.java]

            // Simula LoginActivity observando currentUser != null e chamando finish() imediatamente
            loginActivityScope.launch {
                loginVm.uiState.collect { state ->
                    if (state.currentUser != null) {
                        loginStore.clear()
                        loginActivityScope.cancel()
                    }
                }
            }

            loginVm.stateHolder.startSignIn(TestProfile.ADMIN)
            rolloverStarted.await()

            assertNotNull(mainVm.uiState.value.currentUser)
            assertFalse(loginVm.viewModelScope.isActive)
            assertFalse(fake.ensureCurrentWeekCompleted)
            assertTrue(mainVm.uiState.value.assignments.isEmpty())

            rolloverRelease.complete(Unit)

            assertTrue(fake.ensureCurrentWeekCompleted)
            assertFalse(mainVm.uiState.value.isAuthenticating)
            assertNull(mainVm.uiState.value.snackbar)
            assertEquals(listOf("Limpar cozinha"), mainVm.uiState.value.assignments.map { it.titulo })
        } finally {
            loginActivityScope.cancel()
            loginStore.clear()
            mainStore.clear()
            appScope.cancel()
        }
    }
}

private class FakeSuaVezDataSource(
    startWithoutHouse: Boolean = false,
) : SuaVezDataSource {
    var nextSignInError: Throwable? = null
    var rolloverStarted: CompletableDeferred<Unit>? = null
    var rolloverRelease: CompletableDeferred<Unit>? = null
    var ensureCurrentWeekCompleted: Boolean = false
    private var idSeq = 1
    private val monthKey = DateCycleUtils.currentMonthKey()
    private val weekKey = DateCycleUtils.currentIsoWeekKey()

    private val users = mutableMapOf<String, User>()
    private val houses = MutableStateFlow<Map<String, House>>(emptyMap())
    private val membersByHouse = MutableStateFlow<Map<String, List<Member>>>(emptyMap())
    private val tasksByHouse = MutableStateFlow<Map<String, List<Task>>>(emptyMap())
    private val assignmentsByHouse = MutableStateFlow<Map<String, List<Assignment>>>(emptyMap())

    init {
        if (!startWithoutHouse) {
            val house = House(
                id = HouseRepository.DEFAULT_TEST_HOUSE_ID,
                nome = HouseRepository.DEFAULT_TEST_HOUSE_NAME,
                codigoConvite = HouseRepository.DEFAULT_TEST_INVITE_CODE,
                criadaPorUid = TestProfile.ADMIN.uid,
                semanaAtual = weekKey,
            )
            houses.value = mapOf(house.id to house)
            membersByHouse.value = mapOf(
                house.id to TestProfile.entries.map {
                    Member(
                        uid = it.uid,
                        nome = it.nome,
                        papel = it.role.name,
                        pontosPorMes = mapOf(monthKey to 0),
                    )
                },
            )
            TestProfile.entries.forEach {
                users[it.uid] = User(
                    uid = it.uid,
                    nome = it.nome,
                    email = it.email,
                    currentHouseId = house.id,
                )
            }
        }
    }

    override fun observeHouse(houseId: String): Flow<House?> =
        houses.map { it[houseId] }

    override fun observeMembers(houseId: String): Flow<List<Member>> =
        membersByHouse.map { it[houseId].orEmpty() }

    override fun observeTasks(houseId: String): Flow<List<Task>> =
        tasksByHouse.map { map -> map[houseId].orEmpty().filter { it.ativa } }

    override fun observeWeekAssignments(houseId: String, semana: String): Flow<List<Assignment>> =
        assignmentsByHouse.map { map -> map[houseId].orEmpty().filter { it.semana == semana } }

    override suspend fun loadOrBootstrapTestProfile(profile: TestProfile): Result<TestProfileSession> =
        runCatching {
            nextSignInError?.let { error ->
                nextSignInError = null
                throw error
            }
            val user = users.getOrPut(profile.uid) {
                User(uid = profile.uid, nome = profile.nome, email = profile.email, currentHouseId = null)
            }
            val house = user.currentHouseId?.let { houses.value[it] }
            val members = house?.let { membersByHouse.value[it.id].orEmpty() }.orEmpty()
            TestProfileSession(user = user, house = house, members = members)
        }

    override suspend fun createHouse(houseName: String, creator: User): Result<House> = runCatching {
        val clean = houseName.trim()
        require(clean.isNotEmpty())
        val id = "house_${idSeq++}"
        val house = House(
            id = id,
            nome = clean,
            codigoConvite = HouseRepository.formatInviteCode(1000 + idSeq),
            criadaPorUid = creator.uid,
            semanaAtual = weekKey,
        )
        houses.value = houses.value + (id to house)
        membersByHouse.value = membersByHouse.value + (
            id to listOf(
                Member(
                    uid = creator.uid,
                    nome = creator.nome,
                    papel = HouseRole.ADMIN.name,
                    pontosPorMes = mapOf(monthKey to 0),
                ),
            )
            )
        users[creator.uid] = creator.copy(currentHouseId = id)
        house
    }

    override suspend fun joinHouseByCode(inviteCode: String, user: User): Result<House> = runCatching {
        val normalized = HouseRepository.normalizeInviteCode(inviteCode)
        val house = houses.value.values.firstOrNull { it.codigoConvite == normalized }
            ?: error("Nenhuma casa encontrada para o código informado.")
        val current = membersByHouse.value[house.id].orEmpty()
        if (current.none { it.uid == user.uid }) {
            membersByHouse.value = membersByHouse.value + (
                house.id to current + Member(
                    uid = user.uid,
                    nome = user.nome,
                    papel = HouseRole.MEMBER.name,
                    pontosPorMes = mapOf(monthKey to 0),
                )
                )
        }
        users[user.uid] = user.copy(currentHouseId = house.id)
        house
    }

    override suspend fun renameHouse(houseId: String, newName: String): Result<Unit> = runCatching {
        val house = houses.value[houseId] ?: error("Casa não encontrada")
        houses.value = houses.value + (houseId to house.copy(nome = newName.trim()))
    }

    override suspend fun promoteToAdmin(
        houseId: String,
        currentAdminUid: String,
        targetMemberUid: String,
    ): Result<Unit> = runCatching {
        val updated = membersByHouse.value[houseId].orEmpty().map { member ->
            when (member.uid) {
                targetMemberUid -> member.copy(papel = HouseRole.ADMIN.name)
                currentAdminUid -> member.copy(papel = HouseRole.MEMBER.name)
                else -> member
            }
        }
        membersByHouse.value = membersByHouse.value + (houseId to updated)
    }

    override suspend fun removeMember(houseId: String, memberUid: String): Result<Unit> = runCatching {
        membersByHouse.value = membersByHouse.value + (
            houseId to membersByHouse.value[houseId].orEmpty().filterNot { it.uid == memberUid }
            )
        assignmentsByHouse.value = assignmentsByHouse.value + (
            houseId to assignmentsByHouse.value[houseId].orEmpty().map {
                if (it.responsavelUid == memberUid && !it.isConcluida()) {
                    it.copy(responsavelUid = null, responsavelNome = null, status = AssignmentStatus.PENDENTE.name)
                } else {
                    it
                }
            }
            )
        users[memberUid]?.let { users[memberUid] = it.copy(currentHouseId = null) }
    }

    override suspend fun transferAdminAndLeave(
        houseId: String,
        leavingAdminUid: String,
        newAdminUid: String,
    ): Result<Unit> = runCatching {
        val updated = membersByHouse.value[houseId].orEmpty()
            .filterNot { it.uid == leavingAdminUid }
            .map { if (it.uid == newAdminUid) it.copy(papel = HouseRole.ADMIN.name) else it }
        membersByHouse.value = membersByHouse.value + (houseId to updated)
        users[leavingAdminUid]?.let { users[leavingAdminUid] = it.copy(currentHouseId = null) }
    }

    override suspend fun leaveHouseAsMember(houseId: String, memberUid: String): Result<Unit> =
        removeMember(houseId, memberUid)

    override suspend fun destroyHouse(houseId: String): Result<Unit> = runCatching {
        membersByHouse.value[houseId].orEmpty().forEach { member ->
            users[member.uid]?.let { users[member.uid] = it.copy(currentHouseId = null) }
        }
        houses.value = houses.value - houseId
        membersByHouse.value = membersByHouse.value - houseId
        tasksByHouse.value = tasksByHouse.value - houseId
        assignmentsByHouse.value = assignmentsByHouse.value - houseId
    }

    override suspend fun updatePreference(
        houseId: String,
        uid: String,
        taskId: String,
        level: PreferenceLevel,
    ): Result<Unit> = runCatching {
        val updated = membersByHouse.value[houseId].orEmpty().map { member ->
            if (member.uid == uid) {
                member.copy(preferences = member.preferences + (taskId to level.name))
            } else {
                member
            }
        }
        membersByHouse.value = membersByHouse.value + (houseId to updated)
    }

    override suspend fun saveRecurringTask(
        houseId: String,
        editingId: String?,
        title: String,
        category: TaskCategory,
        points: Int,
        frequency: TaskFrequency,
        createdByUid: String,
        semanaAtual: String,
        createInitialAssignmentInCurrentWeek: Boolean,
        initialDeadline: TaskDeadline?,
    ): Result<Task> = runCatching {
        val clean = title.trim()
        val task = Task(
            id = editingId ?: "task_${idSeq++}",
            titulo = clean,
            peso = points,
            pesoNivel = TaskWeightLevel.fromPoints(points).name,
            frequencia = frequency.name,
            categoriaId = category.name.lowercase(Locale.ROOT),
            categoriaNome = category.label,
            ativa = true,
            criadaPorUid = createdByUid,
        )
        val currentTasks = tasksByHouse.value[houseId].orEmpty()
        val updatedTasks = if (editingId == null) {
            currentTasks + task
        } else {
            currentTasks.map { if (it.id == editingId) task else it }
        }
        tasksByHouse.value = tasksByHouse.value + (houseId to updatedTasks)

        if (editingId == null && createInitialAssignmentInCurrentWeek) {
            val deadlineName = (initialDeadline ?: TaskDeadline.DOMINGO).name
            val initialAssignment = Assignment(
                id = "assign_${idSeq++}",
                semana = semanaAtual,
                taskId = task.id,
                titulo = task.titulo,
                peso = task.peso,
                deadline = deadlineName,
                categoriaId = task.categoriaId,
                categoriaNome = task.categoriaNome,
                status = AssignmentStatus.PENDENTE.name,
                ehAvulsa = false,
                criadaPorUid = createdByUid,
            )
            assignmentsByHouse.value = assignmentsByHouse.value + (
                houseId to (listOf(initialAssignment) + assignmentsByHouse.value[houseId].orEmpty())
                )
        } else if (editingId != null) {
            val updatedAssignments = assignmentsByHouse.value[houseId].orEmpty().map {
                if (it.semana == semanaAtual && it.taskId == editingId && !it.isConcluida()) {
                    it.copy(
                        titulo = task.titulo,
                        peso = task.peso,
                        categoriaId = task.categoriaId,
                        categoriaNome = task.categoriaNome,
                    )
                } else {
                    it
                }
            }
            assignmentsByHouse.value = assignmentsByHouse.value + (houseId to updatedAssignments)
        }
        task
    }

    override suspend fun deleteRecurringTask(
        houseId: String,
        recurringTaskId: String,
        semanaAtual: String,
    ): Result<Unit> = runCatching {
        tasksByHouse.value = tasksByHouse.value + (
            houseId to tasksByHouse.value[houseId].orEmpty().filterNot { it.id == recurringTaskId }
            )
        assignmentsByHouse.value = assignmentsByHouse.value + (
            houseId to assignmentsByHouse.value[houseId].orEmpty().filterNot {
                it.semana == semanaAtual && it.taskId == recurringTaskId && !it.isConcluida()
            }
            )
    }

    override suspend fun ensureCurrentWeekAndRollover(houseId: String): Result<RolloverPlan?> {
        rolloverStarted?.complete(Unit)
        rolloverRelease?.await()
        ensureCurrentWeekCompleted = true
        return Result.success(null)
    }

    override suspend fun addWeeklyTask(
        houseId: String,
        title: String,
        category: TaskCategory,
        points: Int,
        deadline: TaskDeadline,
        createdByUid: String,
        semana: String,
    ): Result<Assignment> = runCatching {
        val assignment = Assignment(
            id = "assign_${idSeq++}",
            semana = semana,
            taskId = null,
            titulo = title.trim(),
            peso = points,
            deadline = deadline.name,
            categoriaId = category.name.lowercase(Locale.ROOT),
            categoriaNome = category.label,
            status = AssignmentStatus.PENDENTE.name,
            ehAvulsa = true,
            urgente = deadline == TaskDeadline.HOJE,
            criadaPorUid = createdByUid,
        )
        assignmentsByHouse.value = assignmentsByHouse.value + (
            houseId to (listOf(assignment) + assignmentsByHouse.value[houseId].orEmpty())
            )
        assignment
    }

    override suspend fun updateWeeklyAssignment(
        houseId: String,
        assignmentId: String,
        title: String,
        category: TaskCategory,
        points: Int,
        deadline: TaskDeadline,
    ): Result<Unit> = runCatching {
        assignmentsByHouse.value = assignmentsByHouse.value + (
            houseId to assignmentsByHouse.value[houseId].orEmpty().map { item ->
                if (item.id == assignmentId) {
                    item.copy(
                        titulo = title.trim(),
                        peso = points,
                        deadline = deadline.name,
                        categoriaId = category.name.lowercase(Locale.ROOT),
                        categoriaNome = category.label,
                        urgente = deadline == TaskDeadline.HOJE,
                    )
                } else {
                    item
                }
            }
            )
    }

    override suspend fun deleteAssignment(
        houseId: String,
        assignmentId: String,
    ): Result<Unit> = runCatching {
        assignmentsByHouse.value = assignmentsByHouse.value + (
            houseId to assignmentsByHouse.value[houseId].orEmpty().filterNot { it.id == assignmentId }
            )
    }

    override suspend fun claimOpenTask(
        houseId: String,
        assignmentId: String,
        memberUid: String,
        memberName: String,
    ): Result<Unit> = runCatching {
        val updated = assignmentsByHouse.value[houseId].orEmpty().map {
            if (it.id == assignmentId) {
                it.copy(
                    status = AssignmentStatus.ATRIBUIDA.name,
                    responsavelUid = memberUid,
                    responsavelNome = memberName,
                )
            } else {
                it
            }
        }
        assignmentsByHouse.value = assignmentsByHouse.value + (houseId to updated)
    }

    override suspend fun setTaskCompleted(
        houseId: String,
        assignment: Assignment,
        completed: Boolean,
    ): Result<Unit> = runCatching {
        if (assignment.isConcluida() == completed) return@runCatching
        val delta = if (completed) assignment.peso else -assignment.peso
        val updatedAssignments = assignmentsByHouse.value[houseId].orEmpty().map {
            if (it.id == assignment.id) {
                val status = when {
                    completed -> AssignmentStatus.CONCLUIDA.name
                    it.responsavelUid.isNullOrBlank() -> AssignmentStatus.PENDENTE.name
                    else -> AssignmentStatus.ATRIBUIDA.name
                }
                it.copy(status = status)
            } else {
                it
            }
        }
        assignmentsByHouse.value = assignmentsByHouse.value + (houseId to updatedAssignments)

        val uid = assignment.responsavelUid
        if (!uid.isNullOrBlank()) {
            val updatedMembers = membersByHouse.value[houseId].orEmpty().map { member ->
                if (member.uid == uid) {
                    val prev = member.getPointsForMonth(monthKey)
                    member.copy(pontosPorMes = member.pontosPorMes + (monthKey to (prev + delta)))
                } else {
                    member
                }
            }
            membersByHouse.value = membersByHouse.value + (houseId to updatedMembers)
        }
    }

    override suspend fun distributePendingTasks(
        houseId: String,
        semana: String,
    ): Result<List<GreedyDecision>> = runCatching {
        val members = membersByHouse.value[houseId].orEmpty()
        val tasks = tasksByHouse.value[houseId].orEmpty()
        val weekAssignments = assignmentsByHouse.value[houseId].orEmpty().filter { it.semana == semana }
        val decisions = AssignmentRepository.computeGreedyDistribution(
            weekAssignments = weekAssignments,
            recurringTasks = tasks,
            members = members,
            monthKey = monthKey,
        )
        val byId = decisions.associateBy { it.assignmentId }
        val updated = assignmentsByHouse.value[houseId].orEmpty().map { item ->
            val decision = byId[item.id]
            if (decision != null) {
                item.copy(
                    status = AssignmentStatus.ATRIBUIDA.name,
                    responsavelUid = decision.memberUid,
                    responsavelNome = decision.memberName,
                    distribuidaAutomaticamente = true,
                )
            } else {
                item
            }
        }
        assignmentsByHouse.value = assignmentsByHouse.value + (houseId to updated)
        decisions
    }
}
