package xavier.pedro.suavez

import android.app.Application
import androidx.annotation.VisibleForTesting
import xavier.pedro.suavez.data.repository.FirestoreSuaVezDataSource
import xavier.pedro.suavez.notifications.TaskDeadlineNotifications
import xavier.pedro.suavez.notifications.WorkManagerTaskReminderScheduler
import xavier.pedro.suavez.ui.state.SuaVezAppContainer
import xavier.pedro.suavez.ui.state.SuaVezContainerProvider

class SuaVezApplication : Application(), SuaVezContainerProvider {

    @Volatile
    private var testContainerOverride: SuaVezAppContainer? = null

    private val defaultContainer: SuaVezAppContainer by lazy {
        SuaVezAppContainer(
            dataSource = FirestoreSuaVezDataSource(),
            reminderScheduler = WorkManagerTaskReminderScheduler(applicationContext),
        )
    }

    override val container: SuaVezAppContainer
        get() = testContainerOverride ?: defaultContainer

    override fun onCreate() {
        super.onCreate()
        TaskDeadlineNotifications.ensureNotificationChannel(this)
    }

    @VisibleForTesting
    fun setContainerForTesting(testContainer: SuaVezAppContainer?) {
        testContainerOverride = testContainer
    }
}
