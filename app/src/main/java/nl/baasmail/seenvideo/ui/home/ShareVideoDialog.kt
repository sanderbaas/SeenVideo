package nl.baasmail.seenvideo.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ShareVideoDialog(
    videoId: String,
    onAddToWatchLater: (String) -> Unit,
    onMarkAsWatched: (String) -> Unit,
    onDismiss: () -> Unit,
    t: (String, String) -> String
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Video Gedeeld vanuit YouTube", "Video Shared from YouTube")) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = t("Wat wil je doen met deze video?", "What do you want to do with this video?"),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        onAddToWatchLater(videoId)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(t("Toevoegen aan Kijk Later", "Add to Watch Later"))
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = {
                        onMarkAsWatched(videoId)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(t("Markeer als Bekeken", "Mark as Watched"))
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(t("Annuleren", "Cancel"))
            }
        }
    )
}
