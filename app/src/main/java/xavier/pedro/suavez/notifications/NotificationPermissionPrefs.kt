package xavier.pedro.suavez.notifications

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.VisibleForTesting
import androidx.core.content.edit

object NotificationPermissionPrefs {
    private const val PREFS_NAME = "suavez_notifications"
    private const val KEY_HAS_PROMPTED = "has_prompted_post_notifications"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun hasPrompted(context: Context): Boolean =
        prefs(context).getBoolean(KEY_HAS_PROMPTED, false)

    fun markPrompted(context: Context) {
        prefs(context).edit {
            putBoolean(KEY_HAS_PROMPTED, true)
        }
    }

    @VisibleForTesting
    fun reset(context: Context) {
        prefs(context).edit {
            clear()
        }
    }
}
