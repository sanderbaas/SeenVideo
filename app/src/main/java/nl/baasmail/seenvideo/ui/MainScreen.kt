package nl.baasmail.seenvideo.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import nl.baasmail.seenvideo.ui.channels.ChannelManagementScreen
import nl.baasmail.seenvideo.ui.home.VideoListScreen

import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextDecoration

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.hilt.navigation.compose.hiltViewModel
import nl.baasmail.seenvideo.ui.home.HomeViewModel
import coil.compose.AsyncImage
import androidx.compose.runtime.collectAsState

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import nl.baasmail.seenvideo.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    homeViewModel: HomeViewModel = hiltViewModel()
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    
    val userEmail by homeViewModel.userEmail.collectAsState()
    val userName by homeViewModel.userName.collectAsState()
    val userPhoto by homeViewModel.userPhoto.collectAsState()

    val context = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.LaunchedEffect(Unit) {
        homeViewModel.autoSignIn(context)
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
                    if (userEmail != null) {
                        AsyncImage(
                            model = userPhoto,
                            contentDescription = "Profiel",
                            modifier = Modifier
                                .padding(end = 16.dp)
                                .size(32.dp)
                                .clip(CircleShape)
                                .clickable { homeViewModel.signOut() }
                        )
                    } else {
                        // User not logged in, maybe show a small icon or nothing
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                    label = { Text("Videos") },
                    selected = currentDestination?.route == "home",
                    onClick = { navController.navigate("home") }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Kanalen") },
                    label = { Text("Kanalen") },
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
