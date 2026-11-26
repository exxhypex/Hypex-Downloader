package com.exxhypex.hypexdownloader.ui.extraction

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.exxhypex.hypexdownloader.core.model.FormatItem
import com.exxhypex.hypexdownloader.ui.HypexViewModel
import com.exxhypex.hypexdownloader.ui.formatselector.FormatSelectorDialog
import com.exxhypex.hypexdownloader.ui.theme.ElectricViolet
import com.exxhypex.hypexdownloader.ui.theme.NeonCyan
import com.exxhypex.hypexdownloader.ui.theme.SurfaceCard
import com.exxhypex.hypexdownloader.ui.theme.SurfaceCardBorder
import com.exxhypex.hypexdownloader.ui.theme.SurfaceDark
import com.exxhypex.hypexdownloader.ui.theme.SurfaceVariantDark
import com.exxhypex.hypexdownloader.ui.theme.TextMuted
import com.exxhypex.hypexdownloader.ui.theme.TextPrimary
import com.exxhypex.hypexdownloader.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtractionBottomSheet(
    viewModel: HypexViewModel,
    onDismiss: () -> Unit
) {
    val media by viewModel.extractedMedia.collectAsStateWithLifecycle()
    val selectedFormat by viewModel.selectedFormat.collectAsStateWithLifecycle()
    val isAudioOnly by viewModel.isAudioOnly.collectAsStateWithLifecycle()
    val selectedContainer by viewModel.selectedContainer.collectAsStateWithLifecycle()

    var showAdvancedSelector by remember { mutableStateOf(false) }

    if (media == null) return

    val currentMedia = media!!
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    if (showAdvancedSelector) {
        FormatSelectorDialog(
            formats = currentMedia.formats,
            selectedFormat = selectedFormat,
            onSelectFormat = {
                viewModel.selectFormat(it)
                showAdvancedSelector = false
            },
            onDismiss = { showAdvancedSelector = false }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceDark,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            // Header with close button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Media Formats Extracted",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Thumbnail Hero Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceCard)
            ) {
                AsyncImage(
                    model = currentMedia.thumbnail,
                    contentDescription = currentMedia.title,
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.Crop
                )

                // Duration Badge
                if (currentMedia.duration > 0) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(10.dp),
                        shape = RoundedCornerShape(6.dp),
                        color = Color.Black.copy(alpha = 0.8f)
                    ) {
                        Text(
                            text = currentMedia.formattedDuration,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Media Title & Author
            Text(
                text = currentMedia.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                maxLines = 2
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${currentMedia.author} • ${currentMedia.formats.size} streams available",
                fontSize = 13.sp,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Video vs Audio Mode Tabs
            TabRow(
                selectedTabIndex = if (isAudioOnly) 1 else 0,
                containerColor = SurfaceVariantDark,
                contentColor = NeonCyan,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[if (isAudioOnly) 1 else 0]),
                        color = if (isAudioOnly) ElectricViolet else NeonCyan
                    )
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, SurfaceCardBorder, RoundedCornerShape(12.dp))
            ) {
                Tab(
                    selected = !isAudioOnly,
                    onClick = { viewModel.toggleAudioOnly(false) },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Video & Audio", fontWeight = FontWeight.SemiBold)
                        }
                    }
                )
                Tab(
                    selected = isAudioOnly,
                    onClick = { viewModel.toggleAudioOnly(true) },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Audio Only", fontWeight = FontWeight.SemiBold)
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Quality Selection Chips
            Text(
                text = if (isAudioOnly) "Select Audio Bitrate / Codec" else "Select Video Resolution",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))

            val availableFormats = if (isAudioOnly) {
                currentMedia.formats.filter { it.isAudioOnly }
            } else {
                currentMedia.formats.filter { !it.isAudioOnly }
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(availableFormats) { format ->
                    val isSelected = selectedFormat?.formatId == format.formatId
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { viewModel.selectFormat(format) },
                        color = if (isSelected) NeonCyan.copy(alpha = 0.2f) else SurfaceCard,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) NeonCyan else SurfaceCardBorder
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                            Text(
                                text = format.resolution,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) NeonCyan else TextPrimary
                            )
                            Text(
                                text = "${format.ext.uppercase()} • ${if (format.filesize > 0) String.format("%.1f MB", format.filesize / (1024f * 1024f)) else "Dynamic size"}",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Container Selector (MP4, MKV, MP3, M4A)
            Text(
                text = "Target Container Format",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))

            val containerOptions = if (isAudioOnly) listOf("mp3", "m4a", "opus", "wav") else listOf("mp4", "mkv", "webm")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                containerOptions.forEach { container ->
                    val isSelected = selectedContainer.equals(container, ignoreCase = true)
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { viewModel.setContainer(container) },
                        color = if (isSelected) ElectricViolet.copy(alpha = 0.25f) else SurfaceCard,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) ElectricViolet else SurfaceCardBorder
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = container.uppercase(),
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) ElectricViolet else TextSecondary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Notice about stream muxing
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceVariantDark)
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Info, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "High resolution video and audio streams will be automatically muxed using native media hardware.",
                    fontSize = 11.sp,
                    color = TextMuted
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Advanced format button
            OutlinedButton(
                onClick = { showAdvancedSelector = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
            ) {
                Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Inspect All Streams & Codecs", fontSize = 13.sp)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Download Action Button
            Button(
                onClick = { viewModel.startDownloadExtracted() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("start_download_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonCyan,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Download ${selectedFormat?.resolution ?: "Selected Format"} (${selectedContainer.uppercase()})",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
