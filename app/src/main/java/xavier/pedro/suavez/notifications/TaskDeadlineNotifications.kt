package xavier.pedro.suavez.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import xavier.pedro.suavez.R

object TaskDeadlineNotifications {
    const val CHANNEL_ID = "task_deadlines"
    const val WORK_TAG = "task_deadline_reminder"
    const val TEST_WORK_TAG = "task_deadline_test_tag"
    const val TEST_WORK_NAME = "task_deadline_test"
    const val KEY_ASSIGNMENT_ID = "assignment_id"
    const val KEY_TASK_TITLE = "task_title"
    const val TEST_NOTIFICATION_ID = 9001

    fun uniqueWorkName(assignmentId: String): String = "task_deadline_$assignmentId"

    fun notificationBody(taskTitle: String): String =
        "Falta 1 dia para o prazo de $taskTitle acabar"

    fun notificationIdFor(assignmentId: String): Int {
        val raw = assignmentId.hashCode() and 0x7FFFFFFF
        return if (raw == TEST_NOTIFICATION_ID) raw + 1 else raw
    }

    fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_deadlines_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notification_channel_deadlines_desc)
        }
        manager.createNotificationChannel(channel)
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }
}
