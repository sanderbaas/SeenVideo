package nl.baasmail.seenvideo

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.AndroidEntryPoint
import nl.baasmail.seenvideo.ui.MainScreen
import nl.baasmail.seenvideo.ui.home.SharedVideoManager
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var sharedVideoManager: SharedVideoManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleShareIntent(intent)

        setContent {
            SeenVideoTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    MainScreen()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            val videoId = SharedVideoManager.extractYouTubeVideoId(sharedText)
            if (videoId != null) {
                sharedVideoManager.onVideoShared(videoId)
            }
        }
    }
}

@Composable
fun SeenVideoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        content = content
    )
}
