package xavier.pedro.suavez

import android.content.Intent
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withTagValue
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.not
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import xavier.pedro.suavez.data.model.Assignment
import xavier.pedro.suavez.data.model.AssignmentStatus
import xavier.pedro.suavez.data.model.House
import xavier.pedro.suavez.data.model.HouseRole
import xavier.pedro.suavez.data.model.Member
import xavier.pedro.suavez.data.model.PreferenceLevel
import xavier.pedro.suavez.data.model.Task
import xavier.pedro.suavez.data.model.User
import xavier.pedro.suavez.data.repository.AuthSession
import xavier.pedro.suavez.data.repository.DateCycleUtils
import xavier.pedro.suavez.data.repository.GreedyDecision
import xavier.pedro.suavez.data.repository.RolloverPlan
import xavier.pedro.suavez.data.repository.SuaVezDataSource
import xavier.pedro.suavez.notifications.NotificationPermissionPrefs
import xavier.pedro.suavez.notifications.ScheduledReminder
import xavier.pedro.suavez.notifications.TaskDeadlineNotifications
import xavier.pedro.suavez.notifications.TaskReminderScheduler
import xavier.pedro.suavez.ui.state.SuaVezAppContainer
import xavier.pedro.suavez.ui.state.TaskCategory
import xavier.pedro.suavez.ui.state.TaskDeadline
import xavier.pedro.suavez.ui.state.TaskFrequency

@RunWith(AndroidJUnit4::class)
class SuaVezNotificationAndNavigationInstrumentedTest {

    private lateinit var app: SuaVezApplication
    private lateinit var testScope: CoroutineScope

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        NotificationPermissionPrefs.reset(app)
        testScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }

    @After
    fun tearDown() {
        app.setContainerForTesting(null)
        NotificationPermissionPrefs.reset(app)
        testScope.cancel()
    }

    private fun restoreSessionSync(container: SuaVezAppContainer) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            runBlocking { container.stateHolder.restoreSession() }
        }
    }

    @Test
    fun mainNavigationSwitchesTabsAndNoHouseShowsOnboarding() {
        val fakeDataSource = InstrumentedFakeDataSource(startWithHouse = true)
        val fakeScheduler = InstrumentedFakeReminderScheduler()
        val container = SuaVezAppContainer(
            dataSource = fakeDataSource,
            appScope = testScope,
            reminderScheduler = fakeScheduler,
        )
        app.setContainerForTesting(container)
        restoreSessionSync(container)

        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.tv_greeting)).check(matches(isDisplayed()))

            onView(withId(R.id.preferencesFragment)).perform(click())
            onView(withId(R.id.tv_preferences_title)).check(matches(isDisplayed()))

            onView(withId(R.id.houseFragment)).perform(click())
            onView(withText(R.string.house_eyebrow)).check(matches(isDisplayed()))

            onView(withId(R.id.profileFragment)).perform(click())
            onView(withId(R.id.tv_profile_title)).check(matches(isDisplayed()))

            onView(withId(R.id.tasksFragment)).perform(click())
            onView(withId(R.id.tv_greeting)).check(matches(isDisplayed()))
        }

        val noHouseSource = InstrumentedFakeDataSource(startWithHouse = false)
        val noHouseScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        try {
            val noHouseContainer = SuaVezAppContainer(
                dataSource = noHouseSource,
                appScope = noHouseScope,
                reminderScheduler = fakeScheduler,
            )
            app.setContainerForTesting(noHouseContainer)
            restoreSessionSync(noHouseContainer)

            ActivityScenario.launch(MainActivity::class.java).use {
                onView(withId(R.id.btn_create_house)).check(matches(isDisplayed()))
                onView(withId(R.id.tasksFragment)).check(matches(not(isDisplayed())))
                onView(withId(R.id.preferencesFragment)).check(matches(not(isDisplayed())))
            }
        } finally {
            noHouseScope.cancel()
        }
    }

    @Test
    fun fallbackWithoutNotificationPermissionDoesNotCrashTaskFlow() {
        val fakeDataSource = InstrumentedFakeDataSource(startWithHouse = true)
        val fakeScheduler = InstrumentedFakeReminderScheduler()
        val container = SuaVezAppContainer(
            dataSource = fakeDataSource,
            appScope = testScope,
            reminderScheduler = fakeScheduler,
        )
        app.setContainerForTesting(container)
        restoreSessionSync(container)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                container.stateHolder.addWeeklyTask(
                    title = "Lavar quintal",
                    category = TaskCategory.LIMPEZA,
                    points = 5,
                    deadline = TaskDeadline.SABADO,
                )
                val openId = container.stateHolder.uiState.value.openAssignments.first().id
                container.stateHolder.claimOpenTask(openId)
            }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
                !TaskDeadlineNotifications.hasNotificationPermission(app)
            ) {
                onView(withText(R.string.action_not_now)).perform(click())
            }

            onView(withText("Lavar quintal")).check(matches(isDisplayed()))
            onView(withId(R.id.profileFragment)).perform(click())
            onView(withId(R.id.btn_test_notification)).perform(scrollTo(), click())

            assertEquals(listOf("Lavar quintal"), fakeScheduler.immediateTestTitles)
        }
    }

    @Test
    fun deepLinkOpensTasksScrollsHighlightsAndHandlesMissingOrLoggedOutGracefully() {
        val weekKey = DateCycleUtils.currentIsoWeekKey()
        val tasks = (1..10).map { index ->
            Assignment(
                id = "assign_$index",
                semana = weekKey,
                titulo = "Tarefa da semana $index",
                peso = 3,
                deadline = TaskDeadline.SABADO.name,
                categoriaId = "limpeza",
                categoriaNome = "Limpeza",
                status = AssignmentStatus.ATRIBUIDA.name,
                responsavelUid = InstrumentedFakeDataSource.UID,
                responsavelNome = "Ana Teste",
            )
        }
        val fakeDataSource = InstrumentedFakeDataSource(
            startWithHouse = true,
            initialAssignments = tasks,
        )
        val container = SuaVezAppContainer(
            dataSource = fakeDataSource,
            appScope = testScope,
            reminderScheduler = InstrumentedFakeReminderScheduler(),
        )
        app.setContainerForTesting(container)
        restoreSessionSync(container)

        // 1. Cold start na MainActivity com sessão já restaurada + scroll até card fora da tela
        val deepLinkBottomIntent = TaskDeadlineNotifications.createDeepLinkIntent(app, "assign_10")
        ActivityScenario.launch<MainActivity>(deepLinkBottomIntent).use { scenario ->
            onView(withId(R.id.tv_greeting)).check(matches(isDisplayed()))
            onView(withTagValue(equalTo("assign_10"))).check(matches(isDisplayed()))
            onView(withTagValue(equalTo("assign_10"))).check { view: View, noViewFound ->
                if (noViewFound != null) throw noViewFound
                val card = view as MaterialCardView
                assertTrue(card.strokeWidth > (app.resources.displayMetrics.density * 1.5f).toInt())
            }

            // 2. App em segundo plano / outra aba recebendo onNewIntent via singleTop
            onView(withId(R.id.profileFragment)).perform(click())
            onView(withId(R.id.tv_profile_title)).check(matches(isDisplayed()))

            scenario.onActivity { activity ->
                activity.startActivity(TaskDeadlineNotifications.createDeepLinkIntent(app, "assign_2"))
            }

            onView(withId(R.id.tv_greeting)).check(matches(isDisplayed()))
            onView(withTagValue(equalTo("assign_2"))).check(matches(isDisplayed()))
            onView(withTagValue(equalTo("assign_2"))).check { view: View, noViewFound ->
                if (noViewFound != null) throw noViewFound
                val card = view as MaterialCardView
                assertTrue(card.strokeWidth > (app.resources.displayMetrics.density * 1.5f).toInt())
            }
        }

        // 3. Tarefa inexistente abre a aba Tarefas sem crash
        val deletedIntent = TaskDeadlineNotifications.createDeepLinkIntent(app, "assign_deleted")
        ActivityScenario.launch<MainActivity>(deletedIntent).use {
            onView(withId(R.id.tv_greeting)).check(matches(isDisplayed()))
        }

        // 4. Cold start via LoginActivity restaurando sessão e encaminhando deep link para Tarefas
        val coldLoginScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        try {
            val coldContainer = SuaVezAppContainer(
                dataSource = InstrumentedFakeDataSource(startWithHouse = true, initialAssignments = tasks),
                appScope = coldLoginScope,
                reminderScheduler = InstrumentedFakeReminderScheduler(),
            )
            app.setContainerForTesting(coldContainer)
            val loginDeepLinkIntent = Intent(app, LoginActivity::class.java).apply {
                TaskDeadlineNotifications.copyDeepLinkExtras(deepLinkBottomIntent, this)
            }
            ActivityScenario.launch<LoginActivity>(loginDeepLinkIntent).use {
                onView(withId(R.id.tv_greeting)).check(matches(isDisplayed()))
                onView(withTagValue(equalTo("assign_10"))).check(matches(isDisplayed()))
                InstrumentationRegistry.getInstrumentation().runOnMainSync {
                    coldContainer.stateHolder.signOut()
                }
            }
        } finally {
            coldLoginScope.cancel()
        }

        // 5. Sessão encerrada redireciona para LoginActivity sem crash
        val loggedOutScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        try {
            app.setContainerForTesting(
                SuaVezAppContainer(
                    dataSource = InstrumentedFakeDataSource(startWithHouse = false, hasActiveSession = false),
                    appScope = loggedOutScope,
                    reminderScheduler = InstrumentedFakeReminderScheduler(),
                ),
            )
            ActivityScenario.launch<MainActivity>(deepLinkBottomIntent).use {
                onView(withId(R.id.btn_sign_in_google)).check(matches(isDisplayed()))
            }
        } finally {
            loggedOutScope.cancel()
        }
    }
}

private class InstrumentedFakeReminderScheduler : TaskReminderScheduler {
    val scheduled = linkedMapOf<String, ScheduledReminder>()
    val immediateTestTitles = mutableListOf<String>()

    override fun scheduleOrReplace(assignmentId: String, taskTitle: String, triggerAtMillis: Long) {
        scheduled[assignmentId] = ScheduledReminder(assignmentId, taskTitle, triggerAtMillis)
    }

    override fun cancel(assignmentId: String) {
        scheduled.remove(assignmentId)
    }

    override fun cancelAll() {
        scheduled.clear()
    }

    override fun triggerImmediateTestNotification(taskTitle: String) {
        immediateTestTitles += taskTitle
    }
}

private class InstrumentedFakeDataSource(
    startWithHouse: Boolean,
    private val hasActiveSession: Boolean = true,
    initialAssignments: List<Assignment> = emptyList(),
) : SuaVezDataSource {

    companion object {
        const val UID = "u_inst"
        const val HOUSE_ID = "h_inst"
    }

    private var seq = 1
    private val weekKey = DateCycleUtils.currentIsoWeekKey()
    private val monthKey = DateCycleUtils.currentMonthKey()
    private val user = User(
        uid = UID,
        nome = "Ana Teste",
        email = "ana@suavez.app",
        currentHouseId = if (startWithHouse) HOUSE_ID else null,
    )
    private val houseFlow = MutableStateFlow(
        if (startWithHouse) {
            House(
                id = HOUSE_ID,
                nome = "Casa Teste",
                codigoConvite = "SV-1234",
                criadaPorUid = UID,
                semanaAtual = weekKey,
            )
        } else {
            null
        },
    )
    private val membersFlow = MutableStateFlow(
        if (startWithHouse) {
            listOf(
                Member(
                    uid = UID,
                    nome = user.nome,
                    papel = HouseRole.ADMIN.name,
                    pontosPorMes = mapOf(monthKey to 0),
                ),
            )
        } else {
            emptyList()
        },
    )
    private val tasksFlow = MutableStateFlow<List<Task>>(emptyList())
    private val assignmentsFlow = MutableStateFlow(initialAssignments)

    override fun observeHouse(houseId: String): Flow<House?> = houseFlow.map { if (it?.id == houseId) it else null }
    override fun observeMembers(houseId: String): Flow<List<Member>> = membersFlow
    override fun observeTasks(houseId: String): Flow<List<Task>> = tasksFlow
    override fun observeWeekAssignments(houseId: String, semana: String): Flow<List<Assignment>> =
        assignmentsFlow.map { list -> list.filter { it.semana == semana } }

    override suspend fun restoreSession(): Result<AuthSession?> = runCatching {
        if (!hasActiveSession) return@runCatching null
        AuthSession(user = user, house = houseFlow.value, members = membersFlow.value)
    }

    override suspend fun signInWithGoogleIdToken(idToken: String): Result<AuthSession> =
        Result.success(AuthSession(user = user, house = houseFlow.value, members = membersFlow.value))

    override suspend fun signOut(): Result<Unit> = Result.success(Unit)
    override suspend fun createHouse(houseName: String, creator: User): Result<House> =
        Result.failure(UnsupportedOperationException())
    override suspend fun joinHouseByCode(inviteCode: String, user: User): Result<House> =
        Result.failure(UnsupportedOperationException())
    override suspend fun renameHouse(houseId: String, newName: String): Result<Unit> = Result.success(Unit)
    override suspend fun promoteToAdmin(houseId: String, currentAdminUid: String, targetMemberUid: String): Result<Unit> =
        Result.success(Unit)
    override suspend fun removeMember(houseId: String, memberUid: String): Result<Unit> = Result.success(Unit)
    override suspend fun transferAdminAndLeave(houseId: String, leavingAdminUid: String, newAdminUid: String): Result<Unit> =
        Result.success(Unit)
    override suspend fun leaveHouseAsMember(houseId: String, memberUid: String): Result<Unit> = Result.success(Unit)
    override suspend fun destroyHouse(houseId: String): Result<Unit> = Result.success(Unit)
    override suspend fun updatePreference(houseId: String, uid: String, taskId: String, level: PreferenceLevel): Result<Unit> =
        Result.success(Unit)
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
    ): Result<Task> = Result.failure(UnsupportedOperationException())
    override suspend fun deleteRecurringTask(houseId: String, recurringTaskId: String, semanaAtual: String): Result<Unit> =
        Result.success(Unit)
    override suspend fun ensureCurrentWeekAndRollover(houseId: String): Result<RolloverPlan?> = Result.success(null)

    override suspend fun addWeeklyTask(
        houseId: String,
        title: String,
        category: TaskCategory,
        points: Int,
        deadline: TaskDeadline,
        createdByUid: String,
        semana: String,
    ): Result<Assignment> = runCatching {
        val created = Assignment(
            id = "inst_assign_${seq++}",
            semana = semana,
            titulo = title,
            peso = points,
            deadline = deadline.name,
            categoriaId = category.name.lowercase(),
            categoriaNome = category.label,
            status = AssignmentStatus.PENDENTE.name,
            ehAvulsa = true,
            criadaPorUid = createdByUid,
        )
        assignmentsFlow.value = listOf(created) + assignmentsFlow.value
        created
    }

    override suspend fun updateWeeklyAssignment(
        houseId: String,
        assignmentId: String,
        title: String,
        category: TaskCategory,
        points: Int,
        deadline: TaskDeadline,
    ): Result<Unit> = Result.success(Unit)

    override suspend fun deleteAssignment(houseId: String, assignmentId: String): Result<Unit> = Result.success(Unit)

    override suspend fun claimOpenTask(
        houseId: String,
        assignmentId: String,
        memberUid: String,
        memberName: String,
    ): Result<Unit> = runCatching {
        assignmentsFlow.value = assignmentsFlow.value.map {
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
    }

    override suspend fun setTaskCompleted(houseId: String, assignment: Assignment, completed: Boolean): Result<Unit> =
        Result.success(Unit)

    override suspend fun distributePendingTasks(houseId: String, semana: String): Result<List<GreedyDecision>> =
        Result.success(emptyList())
}
