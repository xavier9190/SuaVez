package xavier.pedro.suavez.notifications

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

class WorkManagerTaskReminderScheduler(
    private val context: Context,
    private val nowProvider: () -> Long = System::currentTimeMillis,
) : TaskReminderScheduler {

    private val workManager: WorkManager by lazy {
        WorkManager.getInstance(context.applicationContext)
    }

    override fun scheduleOrReplace(
        assignmentId: String,
        taskTitle: String,
        triggerAtMillis: Long,
    ) {
        val delayMillis = (triggerAtMillis - nowProvider()).coerceAtLeast(0L)
        enqueueReminderWork(
            uniqueWorkName = TaskDeadlineNotifications.uniqueWorkName(assignmentId),
            assignmentId = assignmentId,
            taskTitle = taskTitle,
            delayMillis = delayMillis,
            tag = TaskDeadlineNotifications.WORK_TAG,
        )
    }

    override fun cancel(assignmentId: String) {
        workManager.cancelUniqueWork(TaskDeadlineNotifications.uniqueWorkName(assignmentId))
    }

    override fun cancelAll() {
        workManager.cancelAllWorkByTag(TaskDeadlineNotifications.WORK_TAG)
    }

    override fun triggerImmediateTestNotification(taskTitle: String) {
        enqueueReminderWork(
            uniqueWorkName = TaskDeadlineNotifications.TEST_WORK_NAME,
            assignmentId = null,
            taskTitle = taskTitle,
            delayMillis = 0L,
            tag = TaskDeadlineNotifications.TEST_WORK_TAG,
        )
    }

    private fun enqueueReminderWork(
        uniqueWorkName: String,
        assignmentId: String?,
        taskTitle: String,
        delayMillis: Long,
        tag: String,
    ) {
        val builder = OneTimeWorkRequestBuilder<TaskDeadlineReminderWorker>()
            .setInputData(
                workDataOf(
                    TaskDeadlineNotifications.KEY_ASSIGNMENT_ID to assignmentId,
                    TaskDeadlineNotifications.KEY_TASK_TITLE to taskTitle,
                ),
            )
            .addTag(tag)
        if (delayMillis > 0L) {
            builder.setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
        }
        workManager.enqueueUniqueWork(
            uniqueWorkName,
            ExistingWorkPolicy.REPLACE,
            builder.build(),
        )
    }
}
