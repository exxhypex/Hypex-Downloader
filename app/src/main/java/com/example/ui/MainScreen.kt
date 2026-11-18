package com.example.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.model.DownloadStatus
import com.example.ui.downloads.DownloadsScreen
import com.example.ui.extraction.ExtractionBottomSheet
import com.example.ui.home.HomeScreen
import com.example.ui.playlists.PlaylistsScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.theme.AmoledBlack
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.SurfaceCardBorder
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary

enum class AppDestination(val title: String) {
    HOME("Home"),
    DOWNLOADS("Downloads"),
    PLAYLISTS("Playlists"),
    SETTINGS("Settings")
}

@Composable
fun MainScreen(
    viewModel: HypexViewModel,
    modifier: Modifier = Modifier
) {
    var currentDestination by remember { mutableStateOf(AppDestination.HOME) }
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val extractedMedia by viewModel.extractedMedia.collectAsStateWithLifecycle()

    val activeDownloadsCount = downloads.count { it.status == DownloadStatus.DOWNLOADING }

    // When media is extracted, show extraction bottom sheet
    if (extractedMedia != null) {
        ExtractionBottomSheet(
            viewModel = viewModel,
            onDismiss = { viewModel.dismissExtraction() }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = AmoledBlack,
        bottomBar = {
            NavigationBar(
                modifier = Modifier
                    .navigationBarsPadding()
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .border(
                        1.dp,
                        SurfaceCardBorder,
                        RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                    ),
                containerColor = SurfaceDark,
                contentColor = TextSecondary
            ) {
                NavigationBarItem(
                    selected = currentDestination == AppDestination.HOME,
                    onClick = { currentDestination = AppDestination.HOME },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                    label = { Text("Home", fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.Black,
                        selectedTextColor = NeonCyan,
                        indicatorColor = NeonCyan,
                        unselectedIconColor = TextMuted,
                        unselectedTextColor = TextMuted
                    ),
                    modifier = Modifier.testTag("nav_home")
                )

                NavigationBarItem(
                    selected = currentDestination == AppDestination.DOWNLOADS,
                    onClick = { currentDestination = AppDestination.DOWNLOADS },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (activeDownloadsCount > 0) {
                                    Badge(
                                        containerColor = NeonCyan,
                                        contentColor = Color.Black
                                    ) {
                                        Text("$activeDownloadsCount")
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Default.Download, contentDescription = "Downloads")
                        }
                    },
                    label = { Text("Downloads", fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.Black,
                        selectedTextColor = NeonCyan,
                        indicatorColor = NeonCyan,
                        unselectedIconColor = TextMuted,
                        unselectedTextColor = TextMuted
                    ),
                    modifier = Modifier.testTag("nav_downloads")
                )

                NavigationBarItem(
                    selected = currentDestination == AppDestination.PLAYLISTS,
                    onClick = { currentDestination = AppDestination.PLAYLISTS },
                    icon = { Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = "Playlists") },
                    label = { Text("Playlists", fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.Black,
                        selectedTextColor = NeonCyan,
                        indicatorColor = NeonCyan,
                        unselectedIconColor = TextMuted,
                        unselectedTextColor = TextMuted
                    ),
                    modifier = Modifier.testTag("nav_playlists")
                )

                NavigationBarItem(
                    selected = currentDestination == AppDestination.SETTINGS,
                    onClick = { currentDestination = AppDestination.SETTINGS },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings", fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.Black,
                        selectedTextColor = NeonCyan,
                        indicatorColor = NeonCyan,
                        unselectedIconColor = TextMuted,
                        unselectedTextColor = TextMuted
                    ),
                    modifier = Modifier.testTag("nav_settings")
                )
            }
        }
    ) { innerPadding ->
        Crossfade(
            targetState = currentDestination,
            modifier = Modifier.padding(innerPadding)
        ) { destination ->
            when (destination) {
                AppDestination.HOME -> HomeScreen(
                    viewModel = viewModel,
                    onNavigateToDownloads = { currentDestination = AppDestination.DOWNLOADS }
                )
                AppDestination.DOWNLOADS -> DownloadsScreen(
                    viewModel = viewModel
                )
                AppDestination.PLAYLISTS -> PlaylistsScreen(
                    viewModel = viewModel
                )
                AppDestination.SETTINGS -> SettingsScreen(
                    viewModel = viewModel
                )
            }
        }
    }
}
