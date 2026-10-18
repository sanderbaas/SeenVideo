package nl.baasmail.seenvideo.ui.channels

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import nl.baasmail.seenvideo.data.local.ChannelEntity
import nl.baasmail.seenvideo.data.local.ChannelGroupEntity
import nl.baasmail.seenvideo.data.repository.ChannelSearchResult
import kotlinx.coroutines.flow.collectLatest
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelManagementScreen(
    viewModel: ChannelsViewModel = hiltViewModel()
) {
    val channels by viewModel.channels.collectAsState()
    val groups by viewModel.groups.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var showAddGroupDialog by remember { mutableStateOf(false) }
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
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(viewModel.t("Kanaal", "Channel"))
                }
                
                Button(
                    onClick = { showAddGroupDialog = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(viewModel.t("Groep", "Group"))
                }
            }

            Spacer(Modifier.height(16.dp))

            LazyColumn {
                if (groups.isNotEmpty()) {
                    item {
                        Text(viewModel.t("Kanaalgroepen", "Channel Groups"), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                    }
                    items(groups) { group ->
                        GroupItem(
                            group = group,
                            onDelete = { viewModel.removeGroup(group) }
                        )
                    }
                    item {
                        Spacer(Modifier.height(16.dp))
                        Text(viewModel.t("Kanalen", "Channels"), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                    }
                }
                
                items(channels) { channel ->
                    ChannelItem(
                        channel = channel,
                        groups = groups,
                        viewModel = viewModel,
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

    if (showAddGroupDialog) {
        AddGroupDialog(
            viewModel = viewModel,
            onDismiss = { showAddGroupDialog = false },
            onAdd = { viewModel.addGroup(it) }
        )
    }
}

@Composable
fun GroupItem(group: ChannelGroupEntity, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = group.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = null)
            }
        }
    }
}

@Composable
fun ChannelItem(
    channel: ChannelEntity,
    groups: List<ChannelGroupEntity>,
    viewModel: ChannelsViewModel,
    onUpdate: (ChannelEntity) -> Unit,
    onDelete: (ChannelEntity) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Subscriptions,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp).padding(4.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = channel.name, style = MaterialTheme.typography.titleMedium)
                    if (channel.handle.isNotEmpty()) {
                        Text(text = channel.handle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = null)
                }
                IconButton(onClick = { onDelete(channel) }) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                }
            }
            
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                
                // Group selector
                var showGroupMenu by remember { mutableStateOf(false) }
                val currentGroup = groups.find { it.id == channel.groupId }
                
                Box {
                    OutlinedButton(
                        onClick = { showGroupMenu = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "${viewModel.t("Groep", "Group")}: ${currentGroup?.name ?: viewModel.t("Geen", "None")}")
                    }
                    DropdownMenu(expanded = showGroupMenu, onDismissRequest = { showGroupMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(viewModel.t("Geen groep", "No group")) },
                            onClick = { 
                                onUpdate(channel.copy(groupId = null))
                                showGroupMenu = false
                            }
                        )
                        groups.forEach { group ->
                            DropdownMenuItem(
                                text = { Text(group.name) },
                                onClick = { 
                                    onUpdate(channel.copy(groupId = group.id))
                                    showGroupMenu = false
                                }
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = channel.showOnHome, onCheckedChange = { onUpdate(channel.copy(showOnHome = it)) })
                    Text(viewModel.t("Toon op Home", "Show on Home"), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.width(16.dp))
                    Checkbox(checked = channel.showShorts, onCheckedChange = { onUpdate(channel.copy(showShorts = it)) })
                    Text(viewModel.t("Toon Shorts", "Show Shorts"), style = MaterialTheme.typography.bodyMedium)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = channel.notifyNewVideos, onCheckedChange = { onUpdate(channel.copy(notifyNewVideos = it)) })
                    Text(viewModel.t("Meldingen voor dit kanaal", "Notifications for this channel"), style = MaterialTheme.typography.bodyMedium)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = !channel.showThumbnails, onCheckedChange = { onUpdate(channel.copy(showThumbnails = !it)) })
                    Text(viewModel.t("Blur thumbnails", "Blur thumbnails"), style = MaterialTheme.typography.bodyMedium)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = channel.blurTitles, onCheckedChange = { onUpdate(channel.copy(blurTitles = it)) })
                    Text(viewModel.t("Blur titels", "Blur titles"), style = MaterialTheme.typography.bodyMedium)
                }
                TextField(
                    value = channel.safeKeywords,
                    onValueChange = { onUpdate(channel.copy(safeKeywords = it)) },
                    label = { Text(viewModel.t("Veilige keywords (komma gescheiden)", "Safe keywords (comma separated)")) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
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
        title = { Text(viewModel.t("Kanaal toevoegen", "Add Channel")) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = searchQuery, 
                    onValueChange = { viewModel.onSearchQueryChange(it) }, 
                    label = { Text(viewModel.t("Zoek kanaal of voer handle in", "Search channel or enter handle")) },
                    placeholder = { Text(viewModel.t("@google of kanaalnaam", "@google or channel name")) },
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
                    Text(viewModel.t("Suggesties:", "Suggestions:"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
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
                        Text("${viewModel.t("Voeg exact toe", "Add exact")}: $searchQuery")
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(viewModel.t("Sluiten", "Close")) }
        }
    )
}

@Composable
fun AddGroupDialog(
    viewModel: ChannelsViewModel,
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(viewModel.t("Nieuwe groep", "New Group")) },
        text = {
            TextField(
                value = name, 
                onValueChange = { name = it }, 
                label = { Text(viewModel.t("Naam van de groep", "Group Name")) },
                placeholder = { Text(viewModel.t("Bijv. Nieuws, Muziek", "E.g. News, Music")) }
            )
        },
        confirmButton = {
            Button(onClick = { 
                if (name.isNotEmpty()) {
                    onAdd(name)
                    onDismiss()
                }
            }) { Text(viewModel.t("Toevoegen", "Add")) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(viewModel.t("Annuleren", "Cancel")) }
        }
    )
}
