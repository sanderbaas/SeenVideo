package nl.baasmail.seenvideo.ui.channels

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import nl.baasmail.seenvideo.data.local.ChannelEntity
import nl.baasmail.seenvideo.data.repository.ChannelSearchResult
import kotlinx.coroutines.flow.collectLatest

@Composable
fun ChannelManagementScreen(
    viewModel: ChannelsViewModel = hiltViewModel()
) {
    val channels by viewModel.channels.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.errorEvents.collectLatest { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp)) {
            Button(
                onClick = { showAddDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Kanaal Toevoegen")
            }

            Spacer(Modifier.height(16.dp))

            LazyColumn {
                items(channels) { channel ->
                    ChannelItem(
                        channel = channel,
                        onUpdate = { viewModel.updateSettings(it) },
                        onDelete = { viewModel.removeChannel(it) }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddChannelDialog(
            viewModel = viewModel,
            onDismiss = { showAddDialog = false }
        )
    }
}

@Composable
fun ChannelItem(
    channel: ChannelEntity,
    onUpdate: (ChannelEntity) -> Unit,
    onDelete: (ChannelEntity) -> Unit
) {
    var keywordsState by remember(channel.id) { mutableStateOf(channel.safeKeywords) }
    
    // Sync local state if external data changes (e.g. from sync)
    // but only if we are not currently typing (to avoid focus/cursor issues)
    // Actually, for simplicity in this case, we just use the local state for typing
    // and push updates upwards.

    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = channel.name, style = MaterialTheme.typography.titleMedium)
                    if (channel.handle.isNotEmpty()) {
                        Text(text = channel.handle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
                IconButton(onClick = { onDelete(channel) }) {
                    Icon(Icons.Default.Delete, contentDescription = "Verwijderen")
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = channel.showOnHome, onCheckedChange = { onUpdate(channel.copy(showOnHome = it)) })
                Text("Toon op Home", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.width(16.dp))
                Checkbox(checked = channel.showShorts, onCheckedChange = { onUpdate(channel.copy(showShorts = it)) })
                Text("Toon Shorts", style = MaterialTheme.typography.bodyMedium)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = !channel.showThumbnails, onCheckedChange = { onUpdate(channel.copy(showThumbnails = !it)) })
                Text("Blur thumbnails", style = MaterialTheme.typography.bodyMedium)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = channel.blurTitles, onCheckedChange = { onUpdate(channel.copy(blurTitles = it)) })
                Text("Blur titels", style = MaterialTheme.typography.bodyMedium)
            }
            TextField(
                value = keywordsState,
                onValueChange = { 
                    keywordsState = it
                    onUpdate(channel.copy(safeKeywords = it))
                },
                label = { Text("Veilige keywords (komma gescheiden)") },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun AddChannelDialog(
    viewModel: ChannelsViewModel,
    onDismiss: () -> Unit
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Kanaal toevoegen") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = searchQuery, 
                    onValueChange = { viewModel.onSearchQueryChange(it) }, 
                    label = { Text("Zoek kanaal of voer handle in") },
                    placeholder = { Text("@google of kanaalnaam") },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        if (isSearching) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Search, contentDescription = null)
                        }
                    },
                    singleLine = true
                )

                if (searchResults.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Suggesties:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                        items(searchResults) { result ->
                            ListItem(
                                headlineContent = { Text(result.title) },
                                leadingContent = {
                                    AsyncImage(
                                        model = result.thumbnailUrl,
                                        contentDescription = null,
                                        modifier = Modifier.size(32.dp).clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                },
                                modifier = Modifier.clickable {
                                    viewModel.addChannelFromSearch(result)
                                    onDismiss()
                                }
                            )
                        }
                    }
                } else if (searchQuery.startsWith("@") && searchQuery.length > 3) {
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            viewModel.addChannel(searchQuery)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Voeg exact toe: $searchQuery")
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Sluiten") }
        }
    )
}
