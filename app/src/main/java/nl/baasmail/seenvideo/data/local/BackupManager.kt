package nl.baasmail.seenvideo.data.local

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class SeenVideoBackup(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val groups: List<ChannelGroupEntity> = emptyList(),
    val channels: List<ChannelEntity> = emptyList(),
    val videos: List<VideoEntity> = emptyList()
)

@Singleton
class BackupManager @Inject constructor(
    private val channelDao: ChannelDao,
    private val videoDao: VideoDao
) {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    suspend fun createBackupJson(): String = withContext(Dispatchers.IO) {
        val groups = channelDao.getAllGroups().first()
        val channels = channelDao.getAllChannels().first()
        val videos = videoDao.getAllVideos().first()

        val backup = SeenVideoBackup(
            version = 1,
            exportedAt = System.currentTimeMillis(),
            groups = groups,
            channels = channels,
            videos = videos
        )

        gson.toJson(backup)
    }

    suspend fun restoreBackupJson(jsonString: String): String = withContext(Dispatchers.IO) {
        try {
            val backup = gson.fromJson(jsonString, SeenVideoBackup::class.java)
                ?: return@withContext "Fout: Ongeldig backup bestand"

            backup.groups.forEach { group ->
                channelDao.insertGroup(group)
            }

            backup.channels.forEach { channel ->
                channelDao.insertChannel(channel)
            }

            if (backup.videos.isNotEmpty()) {
                videoDao.insertVideos(backup.videos)
            }

            "Backup hersteld: ${backup.channels.size} kanalen en ${backup.videos.size} video's geïmporteerd!"
        } catch (e: Exception) {
            "Fout bij herstellen backup: ${e.message}"
        }
    }
}
