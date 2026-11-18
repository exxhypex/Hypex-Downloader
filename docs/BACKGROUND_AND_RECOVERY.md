# Background Execution & Crash Recovery

HYPEX Downloader executes long-running media transfers outside the UI lifecycle using an Android **Foreground Service** paired with startup database reconciliation.

---

## 1. Foreground Service Architecture (`DownloadForegroundService`)

Declared in `AndroidManifest.xml` with `android:exported="false"` and `android:foregroundServiceType="dataSync"` (alongside `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_DATA_SYNC` permissions), the service decouples network I/O from Activity destruction.

### Lifecycle & Resource Management
- **Coroutine Supervision**: Uses `CoroutineScope(SupervisorJob() + Dispatchers.IO)` (`serviceScope`) so task errors do not tear down sibling coroutines unpredictably, and cleans up `currentDownloadJob` + `serviceScope` in `onDestroy()`.
- **CPU Wake Lock**: Acquires a `PowerManager.PARTIAL_WAKE_LOCK` (`HYPEX::DownloadWakeLock`) with a safety **10-minute timeout** in `onCreate()` and releases it safely in `onDestroy()`.
- **Delivery Mode**: Returns `START_NOT_STICKY` from `onStartCommand`, delegating crash recovery explicitly to `RecoveryManager` on the next clean startup rather than letting the OS blindly restart uncoordinated intents.
- **API 26+ Startup**: Helper `DownloadForegroundService.startDownload(context, taskId)` automatically dispatches via `context.startForegroundService(intent)` on Android 8.0+ and `context.startService(intent)` below API 26.

---

## 2. Intent Actions & Notification Controls

The service handles four explicit action intents keyed by `EXTRA_TASK_ID`:

```text
  HypexViewModel / Notification Action
        │
        ├──► ACTION_START / ACTION_RESUME
        │       ├─ Promotes service to foreground (NOTIFICATION_ID = 1001)
        │       ├─ Marks Room status = DOWNLOADING
        │       ├─ Runs engineBridge.startDownload(task) with live progress callback
        │       └─ Updates Room DB + progress notification (Pause & Cancel actions)
        │
        ├──► ACTION_PAUSE
        │       ├─ Sets taskId in pausedTasks set ──► Loop closes file, keeps cache chunk
        │       ├─ Marks Room status = PAUSED
        │       └─ Switches notification action button to "Resume"
        │
        └──► ACTION_CANCEL
                ├─ Sets taskId in cancelledTasks set ──► Loop deletes cache chunk
                ├─ Marks Room status = CANCELLED & removes from queue table
                └─ Calls stopForeground(STOP_FOREGROUND_REMOVE) + stopSelf()
```

### Notification Channel & Terminal Alerts
- **Channel**: `hypex_downloader_channel` (`NotificationManager.IMPORTANCE_LOW`) so live progress updates do not buzz or chime repeatedly.
- **Interactive PendingIntents**: Every ongoing notification uses `FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE` actions allowing users to pause, resume, or cancel directly from the Android notification shade or tap through to `MainActivity`.
- **Terminal Notifications**:
  - Success emits `NOTIFICATION_ID + 1` (`"Download Complete"`) after `repository.markCompleted(taskId, file.absolutePath)`.
  - Real transfer errors emit `NOTIFICATION_ID + 2` (`"Download Failed"`), while intentional `PAUSED` or `CANCELLED` signals suppress failure alerts so users are never shown false error notifications.

---

## 3. Startup Crash & Process-Kill Recovery (`RecoveryManager`)

When Android kills the process due to memory pressure, battery restrictions, or a crash while a download is mid-stream, Room database rows can remain stuck in `DOWNLOADING` or `PROCESSING`.

```text
  OS Kills Process Mid-Download
        │ (Partial bytes remain safely in cacheDir/temp_<taskId>.<ext>)
        ▼
  Next App Launch ──► HypexApp.onCreate() ──► RecoveryManager.recoverOnStartup()
        │
        ├─► DownloadRepository.reconcilePendingTasks()
        │     ├─ Queries getActiveAndQueuedTasks()
        │     └─ Transitions DOWNLOADING / PROCESSING rows ──► PAUSED
        │
        └─► engineBridge.initializeEngine()
              └─ Ensures filesDir/downloads and cacheDir/temp_downloads exist
```

### Why Recovery Transitions to `PAUSED` Instead of Auto-Starting
1. Prevents unexpected cellular data usage immediately on app launch before the user confirms network state.
2. Preserves the partial file in `cacheDir/temp_<taskId>.<ext>` so tapping **Resume** in `DownloadsScreen` picks up directly via HTTP `Range: bytes=<offset>-` without re-downloading prior megabytes.
