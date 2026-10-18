package nl.baasmail.seenvideo.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import nl.baasmail.seenvideo.R
import nl.baasmail.seenvideo.ui.channels.ChannelManagementScreen
import nl.baasmail.seenvideo.ui.home.HomeViewModel
import nl.baasmail.seenvideo.ui.home.VideoListScreen
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    homeViewModel: HomeViewModel = hiltViewModel()
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val context = LocalContext.current

    var showSettingsSheet by remember { mutableStateOf(false) }
    var showApiKeyDialog by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState()

    val backupMessage by homeViewModel.backupMessage.collectAsState()
    val currentLang by homeViewModel.currentLanguage.collectAsState()
    val notificationsEnabled by homeViewModel.notificationsEnabled.collectAsState()
    val notificationHour by homeViewModel.notificationHour.collectAsState()
    val notificationMinute by homeViewModel.notificationMinute.collectAsState()
    val currentApiKey by homeViewModel.apiKey.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        homeViewModel.setNotificationsEnabled(isGranted)
    }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            homeViewModel.exportBackupToUri(context, uri)
        }
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            homeViewModel.importBackupFromUri(context, uri)
        }
    }

    backupMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { homeViewModel.clearBackupMessage() },
            confirmButton = {
                TextButton(onClick = { homeViewModel.clearBackupMessage() }) {
                    Text("OK")
                }
            },
            title = { Text("Backup Status") },
            text = { Text(msg) }
        )
    }

    if (showApiKeyDialog) {
        var tempApiKey by remember(currentApiKey) { mutableStateOf(currentApiKey) }

        AlertDialog(
            onDismissRequest = { showApiKeyDialog = false },
            title = { Text(homeViewModel.t("YouTube API Key", "YouTube API Key")) },
            text = {
                Column {
                    Text(
                        text = homeViewModel.t(
                            "Voer je eigen YouTube Data API v3 sleutel in. Laat leeg om de openbare RSS/web-fallback te gebruiken.",
                            "Enter your own YouTube Data API v3 key. Leave empty to use the public RSS/web fallback."
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = tempApiKey,
                        onValueChange = { tempApiKey = it },
                        label = { Text("API Key") },
                        singleLine = true,
                        placeholder = { Text(homeViewModel.t("Leeg = RSS/web fallback", "Empty = RSS/web fallback")) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    homeViewModel.setApiKey(tempApiKey)
                    showApiKeyDialog = false
                }) {
                    Text(homeViewModel.t("Opslaan", "Save"))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    tempApiKey = ""
                    homeViewModel.setApiKey("")
                    showApiKeyDialog = false
                }) {
                    Text(homeViewModel.t("Wissen", "Clear"))
                }
            }
        )
    }

    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = notificationHour,
            initialMinute = notificationMinute,
            is24Hour = true
        )

        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text(homeViewModel.t("Selecteer tijdstip", "Select time")) },
            text = {
                TimePicker(state = timePickerState)
            },
            confirmButton = {
                TextButton(onClick = {
                    homeViewModel.setNotificationTime(timePickerState.hour, timePickerState.minute)
                    showTimePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text(homeViewModel.t("Annuleren", "Cancel"))
                }
            }
        )
    }

    if (showSettingsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSettingsSheet = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = homeViewModel.t("Instellingen", "Settings"),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                )

                // Language Setting
                ListItem(
                    headlineContent = { Text(homeViewModel.t("Taal / Language", "Language")) },
                    supportingContent = { Text(if (currentLang == "en") "English" else "Nederlands") },
                    trailingContent = {
                        Row {
                            TextButton(onClick = { homeViewModel.setLanguage("nl") }) {
                                Text("NL", fontWeight = if (currentLang == "nl") FontWeight.Bold else FontWeight.Normal)
                            }
                            TextButton(onClick = { homeViewModel.setLanguage("en") }) {
                                Text("EN", fontWeight = if (currentLang == "en") FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                )

                // Notifications Setting
                ListItem(
                    leadingContent = { Icon(Icons.Default.Notifications, contentDescription = null) },
                    headlineContent = { Text(homeViewModel.t("Dagelijkse Notificatie", "Daily Notification")) },
                    supportingContent = {
                        Text(
                            if (notificationsEnabled) {
                                String.format(Locale.getDefault(), "%s: %02d:%02d", homeViewModel.t("Tijdstip", "Time"), notificationHour, notificationMinute)
                            } else {
                                homeViewModel.t("Uitgeschakeld", "Disabled")
                            }
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = notificationsEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    homeViewModel.setNotificationsEnabled(enabled)
                                }
                            }
                        )
                    },
                    modifier = Modifier.clickable {
                        if (notificationsEnabled) {
                            showTimePicker = true
                        }
                    }
                )

                // YouTube API Key Setting
                ListItem(
                    leadingContent = { Icon(Icons.Default.Key, contentDescription = null) },
                    headlineContent = { Text(homeViewModel.t("YouTube API Key", "YouTube API Key")) },
                    supportingContent = {
                        Text(
                            if (currentApiKey.isNotEmpty()) {
                                "Key: ${currentApiKey.take(6)}...${currentApiKey.takeLast(4)}"
                            } else {
                                homeViewModel.t("Web / RSS Fallback (Geen API Key)", "Web / RSS Fallback (No API Key)")
                            }
                        )
                    },
                    modifier = Modifier.clickable {
                        showApiKeyDialog = true
                    }
                )

                // Export History
                ListItem(
                    headlineContent = { Text(homeViewModel.t("Exporteer Geschiedenis", "Export History")) },
                    supportingContent = { Text(homeViewModel.t("Sla kanalen, groepen en vinkjes op als JSON-bestand", "Save channels, groups and watches as JSON file")) },
                    leadingContent = { Icon(Icons.Default.FileUpload, contentDescription = null) },
                    modifier = Modifier.clickable {
                        showSettingsSheet = false
                        createDocumentLauncher.launch("seenvideo_backup.json")
                    }
                )

                // Import History
                ListItem(
                    headlineContent = { Text(homeViewModel.t("Importeer Geschiedenis", "Import History")) },
                    supportingContent = { Text(homeViewModel.t("Herstel kanalen, groepen en vinkjes uit een JSON-bestand", "Restore channels, groups and watches from JSON file")) },
                    leadingContent = { Icon(Icons.Default.FileDownload, contentDescription = null) },
                    modifier = Modifier.clickable {
                        showSettingsSheet = false
                        openDocumentLauncher.launch(arrayOf("application/json", "*/*"))
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "SeenVideo v1.4",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    Image(
                        painter = painterResource(id = R.drawable.ic_seen_video_logo),
                        contentDescription = null,
                        modifier = Modifier
                            .padding(start = 16.dp)
                            .size(32.dp)
                    )
                },
                title = {
                    Text(
                        text = buildAnnotatedString {
                            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                append("Seen")
                            }
                            append("Video")
                        },
                        modifier = Modifier.padding(start = 8.dp)
                    )
                },
                actions = {
                    IconButton(onClick = { showSettingsSheet = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = homeViewModel.t("Instellingen", "Settings")
                        )
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                    label = { Text(homeViewModel.t("Videos", "Videos")) },
                    selected = currentDestination?.route == "home",
                    onClick = { navController.navigate("home") }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.List, contentDescription = "Kanalen") },
                    label = { Text(homeViewModel.t("Kanalen", "Channels")) },
                    selected = currentDestination?.route == "channels",
                    onClick = { navController.navigate("channels") }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("home") { VideoListScreen() }
            composable("channels") { ChannelManagementScreen() }
        }
    }
}
