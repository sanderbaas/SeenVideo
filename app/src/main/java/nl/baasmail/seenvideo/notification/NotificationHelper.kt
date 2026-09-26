package nl.baasmail.seenvideo.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import nl.baasmail.seenvideo.MainActivity
import nl.baasmail.seenvideo.R

object NotificationHelper {
    const val CHANNEL_ID = "new_videos_channel"
    const val NOTIFICATION_ID = 1001

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Nieuwe Video's"
            val descriptionText = "Dagelijkse meldingen voor nieuwe video's op jouw kanalen"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showSummaryNotification(context: Context, channelVideosMap: Map<String, List<String>>) {
        createNotificationChannel(context)

        val totalVideos = channelVideosMap.values.sumOf { it.size }
        if (totalVideos == 0) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (totalVideos == 1) {
            "1 nieuwe video beschikbaar"
        } else {
            "$totalVideos nieuwe video's beschikbaar"
        }

        val inboxStyle = NotificationCompat.InboxStyle().setBigContentTitle(title)

        var lineCount = 0
        for ((channelName, videoTitles) in channelVideosMap) {
            for (videoTitle in videoTitles) {
                if (lineCount < 6) {
                    inboxStyle.addLine("$channelName: $videoTitle")
                    lineCount++
                }
            }
        }

        if (totalVideos > lineCount) {
            inboxStyle.setSummaryText("+${totalVideos - lineCount} meer video's")
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_seen_video_logo)
            .setContentTitle(title)
            .setContentText(
                if (channelVideosMap.size == 1) {
                    "${channelVideosMap.keys.first()}: ${channelVideosMap.values.first().firstOrNull() ?: ""}"
                } else {
                    "Nieuwe video's op ${channelVideosMap.size} kanalen"
                }
            )
            .setStyle(inboxStyle)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }
}
