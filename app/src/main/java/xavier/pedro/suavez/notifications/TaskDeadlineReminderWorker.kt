package xavier.pedro.suavez.notifications

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import xavier.pedro.suavez.LoginActivity
import xavier.pedro.suavez.R

class TaskDeadlineReminderWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    @SuppressLint("MissingPermission")
    override suspend fun doWork(): Result {
        val isSignedIn = runCatching {
            FirebaseAuth.getInstance().currentUser != null
        }.getOrDefault(false)
        if (!isSignedIn) return Result.success()

        val taskTitle = inputData.getString(TaskDeadlineNotifications.KEY_TASK_TITLE)
            ?.trim()
            .orEmpty()
        if (taskTitle.isBlank()) return Result.success()

        TaskDeadlineNotifications.ensureNotificationChannel(applicationContext)
        if (!TaskDeadlineNotifications.hasNotificationPermission(applicationContext)) {
            return Result.success()
        }

        val assignmentId = inputData.getString(TaskDeadlineNotifications.KEY_ASSIGNMENT_ID).orEmpty()
        val notificationId = if (assignmentId.isBlank()) {
            TaskDeadlineNotifications.TEST_NOTIFICATION_ID
        } else {
            TaskDeadlineNotifications.notificationIdFor(assignmentId)
        }

        val launchIntent = Intent(applicationContext, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            notificationId,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val body = TaskDeadlineNotifications.notificationBody(taskTitle)
        val notification = NotificationCompat.Builder(applicationContext, TaskDeadlineNotifications.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_suavez_mark)
            .setContentTitle(applicationContext.getString(R.string.notification_deadline_title))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(applicationContext).notify(notificationId, notification)
        return Result.success()
    }
}
