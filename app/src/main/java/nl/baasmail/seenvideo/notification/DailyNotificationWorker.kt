package nl.baasmail.seenvideo.notification

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import nl.baasmail.seenvideo.data.local.NotificationPreferences
import nl.baasmail.seenvideo.data.repository.YouTubeRepository

@HiltWorker
class DailyNotificationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: YouTubeRepository,
    private val notificationPreferences: NotificationPreferences
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.d("DailyNotificationWorker", "Daily notification worker started")

        if (!notificationPreferences.getNotificationsEnabled()) {
            Log.d("DailyNotificationWorker", "Notifications disabled in preferences")
            return Result.success()
        }

        try {
            // Refresh videos
            repository.refreshAll()

            val allChannels = repository.allChannels.first()
            val notifyChannelIds = allChannels.filter { it.notifyNewVideos }.map { it.id }.toSet()

            var lastCheckTime = notificationPreferences.getLastNotificationCheckTime()
            val now = System.currentTimeMillis()

            if (lastCheckTime == 0L) {
                // Default to last 24 hours
                lastCheckTime = now - 24 * 60 * 60 * 1000L
            }

            val allVideos = repository.allVideos.first()
            val newVideos = allVideos.filter { video ->
                video.publishedAt > lastCheckTime && notifyChannelIds.contains(video.channelId)
            }

            if (newVideos.isNotEmpty()) {
                val newVideosByChannel = newVideos.groupBy { it.channelName }
                    .mapValues { entry -> entry.value.map { video -> video.title } }

                NotificationHelper.showSummaryNotification(applicationContext, newVideosByChannel)
            }

            notificationPreferences.setLastNotificationCheckTime(now)
        } catch (e: Exception) {
            Log.e("DailyNotificationWorker", "Failed to check or post daily notification", e)
        } finally {
            // Schedule next execution for tomorrow
            NotificationScheduler.scheduleDailyWorker(
                applicationContext,
                notificationPreferences.getNotificationHour(),
                notificationPreferences.getNotificationMinute()
            )
        }

        return Result.success()
    }
}
