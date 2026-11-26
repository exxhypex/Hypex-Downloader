package com.exxhypex.hypexdownloader.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.exxhypex.hypexdownloader.engine.updates.UpdateChannel
import com.exxhypex.hypexdownloader.engine.updates.UpdateInstallState
import com.exxhypex.hypexdownloader.ui.HypexViewModel
import com.exxhypex.hypexdownloader.ui.components.GlassCard
import com.exxhypex.hypexdownloader.ui.components.StatusBadge
import com.exxhypex.hypexdownloader.ui.cookies.CookieManagerDialog
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
fun SettingsScreen(
    viewModel: HypexViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val diagnostics by viewModel.engineDiagnostics.collectAsStateWithLifecycle()
    val selectedChannel by viewModel.selectedUpdateChannel.collectAsStateWithLifecycle()
    val installedState by viewModel.installedEngineState.collectAsStateWithLifecycle()
    val installState by viewModel.updateInstallState.collectAsStateWithLifecycle()
    val updateInfo by viewModel.engineUpdate.collectAsStateWithLifecycle()
    val isCheckingUpdate by viewModel.isCheckingUpdate.collectAsStateWithLifecycle()

    var showCookieDialog by remember { mutableStateOf(false) }
    var showBatteryGuidance by remember { mutableStateOf(false) }
    var showDiagnosticsDialog by remember { mutableStateOf(false) }
    var showLicensesDialog by remember { mutableStateOf(false) }

    var wifiOnly by remember { mutableStateOf(false) }
    val batteryManager = context.getSystemService(PowerManager::class.java)
    val batteryExemptionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
        batteryManager?.isIgnoringBatteryOptimizations(context.packageName) == true

    if (showCookieDialog) {
        CookieManagerDialog(viewModel = viewModel, onDismiss = { showCookieDialog = false })
    }

    if (showBatteryGuidance) {
        AlertDialog(
            onDismissRequest = { showBatteryGuidance = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.BatteryAlert, contentDescription = null, tint = NeonCyan)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Background Execution", fontWeight = FontWeight.Bold, color = TextPrimary)
                }
            },
            text = {
                Text(
                    text = if (batteryExemptionGranted) {
                        "Battery optimization is already disabled for HYPEX. If downloads are still interrupted, set Battery Usage to Unrestricted and disable OEM-specific background restrictions."
                    } else {
                        "Allow HYPEX to be exempt from Android battery optimization so long downloads are less likely to be paused. This does not disable battery saver globally. You can also set HYPEX Battery Usage to Unrestricted in system settings."
                    },
                    fontSize = 13.sp,
                    color = TextSecondary,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showBatteryGuidance = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !batteryExemptionGranted) {
                        val request = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:" + context.packageName)
                        }
                        runCatching { context.startActivity(request) }.onFailure {
                            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                        }
                    }
                }) {
                    Text(if (batteryExemptionGranted) "Done" else "Allow", color = NeonCyan)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showBatteryGuidance = false
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:" + context.packageName)
                    })
                }) {
                    Text("App settings", color = TextSecondary)
                }
            },
            containerColor = SurfaceDark
        )
    }

    if (showDiagnosticsDialog) {
        AlertDialog(
            onDismissRequest = { showDiagnosticsDialog = false },
            title = { Text("Engine Diagnostics", fontWeight = FontWeight.Bold, color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    diagnostics.forEach { (key, value) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = key, fontSize = 12.sp, color = TextSecondary)
                            Text(text = value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDiagnosticsDialog = false }) {
                    Text("Close", color = NeonCyan)
                }
            },
            containerColor = SurfaceDark
        )
    }

    if (showLicensesDialog) {
        AlertDialog(
            onDismissRequest = { showLicensesDialog = false },
            title = { Text("Open Source Licenses", fontWeight = FontWeight.Bold, color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("• yt-dlp — The Unlicense (Public Domain)", fontSize = 12.sp, color = TextPrimary)
                    Text("• Jetpack Compose & AndroidX — Apache 2.0", fontSize = 12.sp, color = TextSecondary)
                    Text("• OkHttp — Apache 2.0", fontSize = 12.sp, color = TextSecondary)
                    Text("• Coil Image Loader — Apache 2.0", fontSize = 12.sp, color = TextSecondary)
                    Text("• Room Persistence Library — Apache 2.0", fontSize = 12.sp, color = TextSecondary)
                }
            },
            confirmButton = {
                TextButton(onClick = { showLicensesDialog = false }) {
                    Text("Dismiss", color = NeonCyan)
                }
            },
            containerColor = SurfaceDark
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Settings & Engine",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = "Engine configuration, auth, and network settings",
                fontSize = 12.sp,
                color = TextMuted
            )
        }

        // Engine Status & Stable/Nightly Updater Card
        item {
            val isInstalling = installState is UpdateInstallState.Downloading ||
                installState is UpdateInstallState.Verifying ||
                installState is UpdateInstallState.Installing
            val activeBadgeColor = if (installedState.channel == UpdateChannel.NIGHTLY) {
                ElectricViolet
            } else {
                AccentGreen
            }

            GlassCard(borderColor = NeonCyan.copy(alpha = 0.4f)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.ElectricBolt,
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "yt-dlp Core Engine",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Version: ${installedState.version} • ${if (installedState.isBundled) "Bundled" else "SHA-256 Verified"}",
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                        StatusBadge(
                            text = installedState.channel.displayName.uppercase(),
                            color = activeBadgeColor
                        )
                    }

                    if (!installedState.isBundled && installedState.sha256.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Security,
                                contentDescription = null,
                                tint = AccentGreen,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Package digest: ${installedState.sha256.take(16)}…",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Release Channel Switcher: Stable vs Nightly
                    Text(
                        text = "Release Channel",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        UpdateChannel.entries.forEach { channel ->
                            val isSelected = selectedChannel == channel
                            val accent = if (channel == UpdateChannel.NIGHTLY) ElectricViolet else NeonCyan
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) accent.copy(alpha = 0.18f) else SurfaceVariantDark,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSelected) accent else SurfaceCardBorder
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable(enabled = !isCheckingUpdate && !isInstalling) {
                                        viewModel.selectUpdateChannel(channel)
                                    }
                                    .testTag(
                                        if (channel == UpdateChannel.STABLE) "channel_stable_button"
                                        else "channel_nightly_button"
                                    )
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                                ) {
                                    Text(
                                        text = channel.displayName,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) accent else TextPrimary
                                    )
                                    Text(
                                        text = if (channel == UpdateChannel.STABLE) "Official tested" else "Daily master",
                                        fontSize = 11.sp,
                                        color = TextMuted
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { viewModel.checkForUpdates() },
                        enabled = !isCheckingUpdate && !isInstalling,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("check_updates_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SurfaceVariantDark,
                            contentColor = NeonCyan
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (isCheckingUpdate) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = NeonCyan,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Checking ${selectedChannel.displayName} releases...", fontSize = 13.sp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Check ${selectedChannel.displayName} Updates",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Install / Download Progress UI
                    when (val state = installState) {
                        is UpdateInstallState.Downloading -> {
                            Spacer(modifier = Modifier.height(12.dp))
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(SurfaceVariantDark)
                                    .padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Downloading ${selectedChannel.displayName} package...",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = NeonCyan
                                    )
                                    Text(
                                        text = "${(state.progress * 100).toInt()}%",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = NeonCyan
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { state.progress.coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = NeonCyan,
                                    trackColor = SurfaceCard
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "${String.format("%.2f", state.downloadedBytes / (1024f * 1024f))} MB" +
                                        if (state.totalBytes > 0) " / ${String.format("%.2f", state.totalBytes / (1024f * 1024f))} MB" else "",
                                    fontSize = 11.sp,
                                    color = TextMuted
                                )
                            }
                        }
                        is UpdateInstallState.Verifying -> {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(SurfaceVariantDark)
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = AccentGreen,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(state.step, fontSize = 12.sp, color = TextPrimary)
                            }
                        }
                        is UpdateInstallState.Installing -> {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(SurfaceVariantDark)
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = NeonCyan,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Swapping active yt-dlp package...", fontSize = 12.sp, color = TextPrimary)
                            }
                        }
                        is UpdateInstallState.Success -> {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(AccentGreen.copy(alpha = 0.15f))
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(state.message, fontSize = 12.sp, color = AccentGreen, fontWeight = FontWeight.Medium)
                            }
                        }
                        is UpdateInstallState.Error -> {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(AccentRed.copy(alpha = 0.15f))
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = AccentRed, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(state.message, fontSize = 12.sp, color = AccentRed)
                            }
                        }
                        else -> Unit
                    }

                    // Release Info & Install Action Card
                    if (updateInfo != null && !isInstalling) {
                        Spacer(modifier = Modifier.height(12.dp))
                        val upd = updateInfo!!
                        val accentColor = if (upd.channel == UpdateChannel.NIGHTLY) ElectricViolet else NeonCyan

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (upd.hasUpdate) accentColor.copy(alpha = 0.12f)
                                    else SurfaceVariantDark
                                )
                                .border(
                                    1.dp,
                                    if (upd.hasUpdate) accentColor.copy(alpha = 0.4f) else SurfaceCardBorder,
                                    RoundedCornerShape(10.dp)
                                )
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.SystemUpdate,
                                        contentDescription = null,
                                        tint = accentColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = when {
                                            upd.isChannelSwitch -> "Switch to ${upd.channel.displayName} ${upd.latestVersion}"
                                            upd.hasUpdate -> "New ${upd.channel.displayName}: v${upd.latestVersion}"
                                            else -> "Up to date (${upd.latestVersion})"
                                        },
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                }
                                if (upd.assetSizeBytes > 0) {
                                    Text(
                                        text = String.format("%.1f MB", upd.assetSizeBytes / (1024f * 1024f)),
                                        fontSize = 11.sp,
                                        color = TextMuted
                                    )
                                }
                            }

                            if (upd.releaseDate.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Released: ${upd.releaseDate} • Asset: ${upd.assetName.ifBlank { "yt-dlp" }}",
                                    fontSize = 11.sp,
                                    color = TextMuted
                                )
                            }

                            if (upd.releaseNotes.isNotBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = upd.releaseNotes,
                                    fontSize = 11.sp,
                                    color = TextSecondary,
                                    maxLines = 4,
                                    lineHeight = 15.sp
                                )
                            }

                            if ((upd.hasUpdate || upd.isChannelSwitch) && upd.downloadUrl.isNotBlank()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = { viewModel.installEngineUpdate() },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("install_update_button"),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = accentColor,
                                        contentColor = if (upd.channel == UpdateChannel.NIGHTLY) Color.White else Color.Black
                                    ),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (upd.isChannelSwitch) {
                                            "Install ${upd.channel.displayName} Build"
                                        } else {
                                            "Download & Install Update"
                                        },
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // Rollback & Reset Controls
                    if (installedState.canRollback || !installedState.isBundled) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (installedState.canRollback) {
                                OutlinedButton(
                                    onClick = { viewModel.rollbackEngineUpdate() },
                                    enabled = !isInstalling,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("rollback_engine_button"),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentAmber),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Rollback (${installedState.backupVersion ?: "Prev"})",
                                        fontSize = 12.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                            if (!installedState.isBundled) {
                                OutlinedButton(
                                    onClick = { viewModel.resetEngineToBundled() },
                                    enabled = !isInstalling,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("reset_bundled_button"),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Bundled", fontSize = 12.sp, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section: Network & Execution
        item {
            Text(
                text = "Network & Execution",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary
            )
        }

        item {
            GlassCard {
                Column(modifier = Modifier.padding(14.dp)) {
                    // Wi-Fi only toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Wifi, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(text = "Download on Wi-Fi Only", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                                Text(text = "Prevent cellular data usage", fontSize = 11.sp, color = TextMuted)
                            }
                        }
                        Switch(
                            checked = wifiOnly,
                            onCheckedChange = { wifiOnly = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = NeonCyan,
                                checkedTrackColor = NeonCyan.copy(alpha = 0.4f)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Battery optimization guidance item
                    SettingsRowItem(
                        icon = Icons.Default.BatteryAlert,
                        title = "Battery & Background Execution",
                        subtitle = if (batteryExemptionGranted) "Battery optimization exemption enabled" else "Allow unrestricted background downloads",
                        onClick = { showBatteryGuidance = true }
                    )
                }
            }
        }

        // Section: Authentication & Storage
        item {
            Text(
                text = "Authentication & Security",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary
            )
        }

        item {
            GlassCard {
                Column(modifier = Modifier.padding(14.dp)) {
                    SettingsRowItem(
                        icon = Icons.Default.Cookie,
                        title = "Netscape Cookie Manager",
                        subtitle = "Import cookies.txt for age-restricted / private media",
                        onClick = { showCookieDialog = true }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    SettingsRowItem(
                        icon = Icons.Default.Folder,
                        title = "Download Output Directory",
                        subtitle = "Movies/HYPEX & Music/HYPEX (MediaStore)",
                        onClick = {}
                    )
                }
            }
        }

        // Section: Diagnostics & About
        item {
            Text(
                text = "Diagnostics & System",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary
            )
        }

        item {
            GlassCard {
                Column(modifier = Modifier.padding(14.dp)) {
                    SettingsRowItem(
                        icon = Icons.Default.Memory,
                        title = "Engine Diagnostics",
                        subtitle = "Inspect JVM memory, acceleration, and runtime stats",
                        onClick = { showDiagnosticsDialog = true }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    SettingsRowItem(
                        icon = Icons.Default.Info,
                        title = "Open Source Licenses",
                        subtitle = "yt-dlp, FFmpeg, Jetpack Compose, OkHttp",
                        onClick = { showLicensesDialog = true }
                    )
                }
            }
        }

        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "HYPEX Downloader v1.0.0 (ARM64)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextSecondary
                )
                Text(
                    text = "Clean Native Architecture • Powered by yt-dlp",
                    fontSize = 11.sp,
                    color = TextMuted
                )
            }
        }
    }
}

@Composable
fun SettingsRowItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = icon, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                Text(text = subtitle, fontSize = 11.sp, color = TextMuted)
            }
        }
        Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
    }
}
