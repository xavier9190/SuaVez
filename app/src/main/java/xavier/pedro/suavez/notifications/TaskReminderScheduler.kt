package xavier.pedro.suavez.notifications

data class ScheduledReminder(
    val assignmentId: String,
    val taskTitle: String,
    val triggerAtMillis: Long,
)

interface TaskReminderScheduler {
    fun scheduleOrReplace(
        assignmentId: String,
        taskTitle: String,
        triggerAtMillis: Long,
    )

    fun cancel(assignmentId: String)

    fun cancelAll()

    fun triggerImmediateTestNotification(taskTitle: String = DEFAULT_TEST_TASK_TITLE)

    companion object {
        const val DEFAULT_TEST_TASK_TITLE = "Lavar o banheiro"
    }
}

object NoOpTaskReminderScheduler : TaskReminderScheduler {
    override fun scheduleOrReplace(
        assignmentId: String,
        taskTitle: String,
        triggerAtMillis: Long,
    ) = Unit

    override fun cancel(assignmentId: String) = Unit

    override fun cancelAll() = Unit

    override fun triggerImmediateTestNotification(taskTitle: String) = Unit
}
