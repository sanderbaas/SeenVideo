package nl.baasmail.seenvideo.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import nl.baasmail.seenvideo.data.local.VideoEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoListScreen(
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val userEmail by viewModel.userEmail.collectAsState()
    val accessToken by viewModel.accessToken.collectAsState()
    val listState = rememberLazyListState()
    val context = LocalContext.current

    // Fetch token if logged in but no token yet
    LaunchedEffect(userEmail, accessToken) {
        if (userEmail != null && accessToken == null) {
            viewModel.fetchYouTubeToken(context)
        }
    }

    // Infinite scrolling logic
    val shouldLoadMore = remember {
        derivedStateOf {
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()
                ?: return@derivedStateOf false
            lastVisibleItem.index >= listState.layoutInfo.totalItemsCount - 5
        }
    }

    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value && !isLoadingMore && !isRefreshing) {
            viewModel.loadMore()
        }
    }

    // Scroll to top when selection changes
    LaunchedEffect(uiState.selectedChannelId, uiState.isWatchLaterSelected) {
        listState.scrollToItem(0)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (userEmail == null) {
            Button(
                onClick = { viewModel.signIn(context) },
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Text("Inloggen met Google voor Playlist Sync")
            }
        }

        // Selection Pills
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                FilterChip(
                    selected = uiState.selectedChannelId == null && !uiState.isWatchLaterSelected,
                    onClick = { viewModel.selectChannel(null) },
                    label = { Text("Home") }
                )
            }
            item {
                FilterChip(
                    selected = uiState.isWatchLaterSelected,
                    onClick = { viewModel.selectWatchLater(!uiState.isWatchLaterSelected) },
                    label = { Text("Kijk later") }
                )
            }
            items(uiState.channels) { channel ->
                FilterChip(
                    selected = uiState.selectedChannelId == channel.id,
                    onClick = { viewModel.selectChannel(channel.id) },
                    label = { Text(channel.name) }
                )
            }
        }

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.weight(1f)
        ) {
            if (uiState.videos.isEmpty() && !isRefreshing) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    val message = when {
                        uiState.isWatchLaterSelected -> "Geen video's in Kijk later"
                        uiState.selectedChannelId != null -> "Geen video's gevonden voor dit kanaal"
                        uiState.channels.isEmpty() -> "Voeg een kanaal toe bij 'Kanalen'"
                        else -> "Geen video's om te tonen"
                    }
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(uiState.videos) { _, video ->
                    val channel = uiState.channels.find { it.id == video.channelId }
                    VideoItem(
                        video = video,
                        showThumbnail = channel?.showThumbnails ?: true,
                        blurTitle = channel?.blurTitles ?: false,
                        safeKeywords = channel?.safeKeywords ?: "",
                        relativeTime = viewModel.getRelativeTime(video.publishedAt),
                        onWatchedClick = { viewModel.markAsWatched(video.id, !video.isWatched) },
                        onWatchLaterClick = { viewModel.toggleWatchLater(video.id, video.watchLaterItemId == null) },
                        onPlayClick = { viewModel.playVideo(video) }
                    )
                }

                if (isLoadingMore) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VideoItem(
    video: VideoEntity,
    showThumbnail: Boolean,
    blurTitle: Boolean,
    safeKeywords: String,
    relativeTime: String,
    onWatchedClick: () -> Unit,
    onWatchLaterClick: () -> Unit,
    onPlayClick: () -> Unit
) {
    var titleRevealed by remember(video.id) { mutableStateOf(false) }
    
    val safeWords = remember(safeKeywords) {
        safeKeywords.split(Regex("[,;]")).map { it.trim() }.filter { it.isNotEmpty() }
    }
    
    val shouldBlurTitle = blurTitle && !video.isWatched && !titleRevealed
    val textColor = MaterialTheme.colorScheme.onSurface

    val annotatedTitle = remember(video.title, safeWords, shouldBlurTitle, textColor) {
        if (!shouldBlurTitle) {
            AnnotatedString(video.title)
        } else {
            buildAnnotatedString {
                val title = video.title
                val matches = safeWords
                    .filter { it.isNotEmpty() }
                    .flatMap { word ->
                        val escapedWord = Regex.escape(word)
                        Regex(escapedWord, RegexOption.IGNORE_CASE).findAll(title).map { it.range }
                    }
                    .sortedBy { it.first }

                val mergedMatches = mutableListOf<IntRange>()
                if (matches.isNotEmpty()) {
                    var currentStart = matches[0].first
                    var currentEnd = matches[0].last
                    for (i in 1 until matches.size) {
                        if (matches[i].first <= currentEnd + 1) {
                            currentEnd = maxOf(currentEnd, matches[i].last)
                        } else {
                            mergedMatches.add(currentStart..currentEnd)
                            currentStart = matches[i].first
                            currentEnd = matches[i].last
                        }
                    }
                    mergedMatches.add(currentStart..currentEnd)
                }

                var currentPos = 0
                val redactedStyle = SpanStyle(
                    background = Color.Gray.copy(alpha = 0.4f), 
                    color = Color.Transparent
                )

                mergedMatches.forEach { range ->
                    if (range.first > currentPos) {
                        withStyle(style = redactedStyle) {
                            append(title.substring(currentPos, range.first))
                        }
                    }
                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, color = textColor)) {
                        append(title.substring(range.first, range.last + 1))
                    }
                    currentPos = range.last + 1
                }
                
                if (currentPos < title.length) {
                    withStyle(style = redactedStyle) {
                        append(title.substring(currentPos))
                    }
                }
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clickable { onPlayClick() }
            ) {
                AsyncImage(
                    model = video.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (!showThumbnail) Modifier.blur(80.dp) else Modifier),
                    contentScale = ContentScale.Crop,
                    colorFilter = if (!showThumbnail) {
                        ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
                    } else null
                )
                
                // Badges overlay
                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                ) {
                    if (video.isWatched) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.7f),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = "BEKEKEN",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                if (video.duration.isNotEmpty()) {
                    Text(
                        text = video.duration,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .background(Color.Black.copy(alpha = 0.7f), shape = MaterialTheme.shapes.small)
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            Row(
                modifier = Modifier
                    .padding(8.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(enabled = shouldBlurTitle) { titleRevealed = true }
                ) {
                    Text(
                        text = annotatedTitle,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = video.channelName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        Spacer(Modifier.width(8.dp))
                        Text(text = "• $relativeTime", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
                IconButton(onClick = onWatchLaterClick) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Later bekijken",
                        tint = if (video.watchLaterItemId != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    )
                }
                IconButton(onClick = onWatchedClick) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Markeer als bekeken",
                        tint = if (video.isWatched) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}
