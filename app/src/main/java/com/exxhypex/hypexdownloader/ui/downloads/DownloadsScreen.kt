package com.exxhypex.hypexdownloader.ui.downloads

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.exxhypex.hypexdownloader.core.model.DownloadStatus
import com.exxhypex.hypexdownloader.core.model.DownloadTask
import com.exxhypex.hypexdownloader.ui.HypexViewModel
import com.exxhypex.hypexdownloader.ui.components.GlassCard
import com.exxhypex.hypexdownloader.ui.components.StatusBadge
import com.exxhypex.hypexdownloader.ui.theme.AccentAmber
import com.exxhypex.hypexdownloader.ui.theme.AccentGreen
import com.exxhypex.hypexdownloader.ui.theme.AccentRed
import com.exxhypex.hypexdownloader.ui.theme.ElectricViolet
import com.exxhypex.hypexdownloader.ui.theme.NeonCyan
import com.exxhypex.hypexdownloader.ui.theme.SurfaceCard
import com.exxhypex.hypexdownloader.ui.theme.SurfaceCardBorder
import com.exxhypex.hypexdownloader.ui.theme.SurfaceDark
import com.exxhypex.hypexdownloader.ui.theme.SurfaceVariantDark
import com.exxhypex.hypexdownloader.ui.theme.TextMuted
import com.exxhypex.hypexdownloader.ui.theme.TextPrimary
import com.exxhypex.hypexdownloader.ui.theme.TextSecondary

@Composable
fun DownloadsScreen(
    viewModel: HypexViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    var selectedFilter by remember { mutableStateOf("ALL") }
    var showClearConfirm by remember { mutableStateOf(false) }

    val filterOptions = listOf("ALL", "DOWNLOADING", "QUEUED", "COMPLETED", "FAILED")

    val filteredList = when (selectedFilter) {
        "ALL" -> downloads
        "DOWNLOADING" -> downloads.filter { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.PROCESSING }
        "QUEUED" -> downloads.filter { it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.PAUSED }
        "COMPLETED" -> downloads.filter { it.status == DownloadStatus.COMPLETED }
        "FAILED" -> downloads.filter { it.status == DownloadStatus.FAILED || it.status == DownloadStatus.CANCELLED }
        else -> downloads
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear Download History", fontWeight = FontWeight.Bold, color = TextPrimary) },
            text = { Text("Are you sure you want to clear finished and failed downloads from history? This does not delete actual files on your device.", color = TextSecondary) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearHistory()
                        showClearConfirm = false
                    }
                ) {
                    Text("Clear History", color = AccentRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = SurfaceDark
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Title and Action Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Download Manager",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "${downloads.size} total tasks recorded",
                    fontSize = 12.sp,
                    color = TextMuted
                )
            }

            if (downloads.any { it.status == DownloadStatus.COMPLETED || it.status == DownloadStatus.FAILED }) {
                IconButton(onClick = { showClearConfirm = true }) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = "Clear History",
                        tint = TextSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Filter chips
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filterOptions) { filter ->
                val isSelected = selectedFilter == filter
                val count = when (filter) {
                    "ALL" -> downloads.size
                    "DOWNLOADING" -> downloads.count { it.status == DownloadStatus.DOWNLOADING }
                    "QUEUED" -> downloads.count { it.status == DownloadStatus.QUEUED }
                    "COMPLETED" -> downloads.count { it.status == DownloadStatus.COMPLETED }
                    "FAILED" -> downloads.count { it.status == DownloadStatus.FAILED }
                    else -> 0
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (isSelected) NeonCyan.copy(alpha = 0.2f) else SurfaceCard,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isSelected) NeonCyan else SurfaceCardBorder
                    ),
                    modifier = Modifier.clickable { selectedFilter = filter }
                ) {
                    Text(
                        text = "$filter ($count)",
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) NeonCyan else TextSecondary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (filteredList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 80.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No downloads in this category",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Paste a media link on the Home tab to begin",
                        fontSize = 13.sp,
                        color = TextMuted
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredList, key = { it.id }) { task ->
                    DownloadItemCard(
                        task = task,
                        onPause = { viewModel.pauseTask(task.id) },
                        onResume = { viewModel.resumeTask(task.id) },
                        onCancel = { viewModel.cancelTask(task.id) },
                        onRetry = { viewModel.retryTask(task.id) },
                        onDelete = { viewModel.deleteTask(task.id, deleteFile = false) },
                        onOpenFile = { viewModel.openDownloadedFile(context, task) },
                        onShareFile = { viewModel.shareDownloadedFile(context, task) }
                    )
                }
            }
        }
    }
}

@Composable
fun DownloadItemCard(
    task: DownloadTask,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
    onOpenFile: () -> Unit,
    onShareFile: () -> Unit
) {
    GlassCard {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                // Thumbnail with play icon
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(SurfaceVariantDark)
                ) {
                    AsyncImage(
                        model = task.thumbnail,
                        contentDescription = task.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        maxLines = 2
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusBadge(
                            text = task.status.name,
                            color = when (task.status) {
                                DownloadStatus.COMPLETED -> AccentGreen
                                DownloadStatus.DOWNLOADING -> NeonCyan
                                DownloadStatus.PROCESSING -> ElectricViolet
                                DownloadStatus.FAILED -> AccentRed
                                DownloadStatus.PAUSED -> AccentAmber
                                else -> TextMuted
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = task.formatDescription,
                            fontSize = 11.sp,
                            color = TextMuted,
                            maxLines = 1
                        )
                    }
                }
            }

            // Progress bar and speed info for downloading/paused
            if (task.status == DownloadStatus.DOWNLOADING || task.status == DownloadStatus.PAUSED) {
                Spacer(modifier = Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { (task.progress / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = if (task.status == DownloadStatus.DOWNLOADING) NeonCyan else AccentAmber,
                    trackColor = SurfaceVariantDark
                )

                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${task.formattedDownloaded} / ${task.formattedTotal} (${task.progress.toInt()}%)",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                    Text(
                        text = if (task.status == DownloadStatus.DOWNLOADING) "${task.formattedSpeed} • ETA ${task.formattedEta}" else "Paused",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Error display
            if (task.status == DownloadStatus.FAILED && task.errorMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Error: ${task.errorMessage}",
                    fontSize = 11.sp,
                    color = AccentRed,
                    maxLines = 2
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (task.status) {
                    DownloadStatus.DOWNLOADING -> {
                        IconButton(onClick = onPause) {
                            Icon(Icons.Default.Pause, contentDescription = "Pause", tint = AccentAmber, modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = onCancel) {
                            Icon(Icons.Default.Cancel, contentDescription = "Cancel", tint = AccentRed, modifier = Modifier.size(20.dp))
                        }
                    }
                    DownloadStatus.PAUSED -> {
                        IconButton(onClick = onResume) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Resume", tint = NeonCyan, modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = onCancel) {
                            Icon(Icons.Default.Cancel, contentDescription = "Cancel", tint = AccentRed, modifier = Modifier.size(20.dp))
                        }
                    }
                    DownloadStatus.COMPLETED -> {
                        IconButton(onClick = onOpenFile) {
                            Icon(Icons.Default.FileOpen, contentDescription = "Open File", tint = NeonCyan, modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = onShareFile) {
                            Icon(Icons.Default.Share, contentDescription = "Share File", tint = ElectricViolet, modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = TextMuted, modifier = Modifier.size(18.dp))
                        }
                    }
                    DownloadStatus.FAILED, DownloadStatus.CANCELLED -> {
                        IconButton(onClick = onRetry) {
                            Icon(Icons.Default.Refresh, contentDescription = "Retry", tint = NeonCyan, modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = TextMuted, modifier = Modifier.size(18.dp))
                        }
                    }
                    else -> {
                        IconButton(onClick = onCancel) {
                            Icon(Icons.Default.Cancel, contentDescription = "Cancel", tint = TextMuted, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}
