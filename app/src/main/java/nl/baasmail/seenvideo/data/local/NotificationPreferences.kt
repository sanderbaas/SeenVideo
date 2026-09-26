package nl.baasmail.seenvideo.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("notification_prefs", Context.MODE_PRIVATE)

    private val _notificationsEnabled = MutableStateFlow(getNotificationsEnabled())
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled.asStateFlow()

    private val _notificationHour = MutableStateFlow(getNotificationHour())
    val notificationHour: StateFlow<Int> = _notificationHour.asStateFlow()

    private val _notificationMinute = MutableStateFlow(getNotificationMinute())
    val notificationMinute: StateFlow<Int> = _notificationMinute.asStateFlow()

    fun getNotificationsEnabled(): Boolean {
        return prefs.getBoolean("notifications_enabled", false)
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        prefs.edit { 
            putBoolean("notifications_enabled", enabled)
            if (enabled) {
                putLong("last_notification_check_time", 0L)
            }
        }
        _notificationsEnabled.value = enabled
    }

    fun getNotificationHour(): Int {
        return prefs.getInt("notification_hour", 18)
    }

    fun getNotificationMinute(): Int {
        return prefs.getInt("notification_minute", 0)
    }

    fun setNotificationTime(hour: Int, minute: Int) {
        prefs.edit {
            putInt("notification_hour", hour)
            putInt("notification_minute", minute)
            putLong("last_notification_check_time", 0L)
        }
        _notificationHour.value = hour
        _notificationMinute.value = minute
    }

    fun getLastNotificationCheckTime(): Long {
        return prefs.getLong("last_notification_check_time", 0L)
    }

    fun setLastNotificationCheckTime(timestamp: Long) {
        prefs.edit { putLong("last_notification_check_time", timestamp) }
    }
}
