package com.example.worker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.core.model.DownloadProgress
import com.example.core.model.DownloadStatus
import com.example.data.repository.DownloadRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

class DownloadForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "hypex_downloader_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.example.worker.START"
        const val ACTION_PAUSE = "com.example.worker.PAUSE"
        const val ACTION_RESUME = "com.example.worker.RESUME"
        const val ACTION_CANCEL = "com.example.worker.CANCEL"
        const val EXTRA_TASK_ID = "extra_task_id"

        fun startDownload(context: Context, taskId: Long) {
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TASK_ID, taskId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }

        fun pauseDownload(context: Context, taskId: Long) {
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                action = ACTION_PAUSE
                putExtra(EXTRA_TASK_ID, taskId)
            }
            context.startService(intent)
        }

        fun resumeDownload(context: Context, taskId: Long) {
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                action = ACTION_RESUME
                putExtra(EXTRA_TASK_ID, taskId)
            }
            context.startService(intent)
        }

        fun cancelDownload(context: Context, taskId: Long) {
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                action = ACTION_CANCEL
                putExtra(EXTRA_TASK_ID, taskId)
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var repository: DownloadRepository
    private var currentDownloadJob: Job? = null
    private var activeTaskId: Long = -1L

    override fun onCreate() {
        super.onCreate()
        repository = DownloadRepository(applicationContext)
        createNotificationChannel()
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "HYPEX::DownloadWakeLock")
        wakeLock?.acquire(10 * 60 * 1000L)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val taskId = intent?.getLongExtra(EXTRA_TASK_ID, -1L) ?: -1L
        when (intent?.action) {
            ACTION_START, ACTION_RESUME -> if (taskId != -1L) {
                activeTaskId = taskId
                startForeground(NOTIFICATION_ID, createNotification("Starting download...", 0, "--", "--"))
                executeDownload(taskId)
            }
            ACTION_PAUSE -> if (taskId != -1L) {
                serviceScope.launch {
                    repository.pauseDownload(taskId)
                    updateNotification("Download paused", 0, "", "", isPaused = true)
                }
            }
            ACTION_CANCEL -> if (taskId != -1L) {
                serviceScope.launch {
                    repository.cancelDownload(taskId)
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun executeDownload(taskId: Long) {
        // A second enqueue must not cancel the task already running. The active
        // worker drains queued tasks sequentially after each task reaches a terminal state.
        if (currentDownloadJob?.isActive == true) return

        currentDownloadJob = serviceScope.launch {
            var nextTaskId: Long? = taskId

            while (nextTaskId != null) {
                val task = repository.getDownloadById(nextTaskId)
                if (task != null &&
                    (task.status == DownloadStatus.QUEUED || task.status == DownloadStatus.PAUSED)
                ) {
                    activeTaskId = task.id
                    repository.markStatus(task.id, DownloadStatus.DOWNLOADING)
                    val lastPersistAt = AtomicLong(0L)
                    val lastNotificationAt = AtomicLong(0L)

                    val result = repository.engineBridge.startDownload(task) { progress: DownloadProgress ->
                        val now = SystemClock.elapsedRealtime()
                        val terminal = progress.status.equals("FINISHED", ignoreCase = true) ||
                            progress.status.equals("COMPLETED", ignoreCase = true)
                        val persist = terminal || now - lastPersistAt.get() >= 1_000L
                        val notify = terminal || now - lastNotificationAt.get() >= 1_500L

                        if (persist || notify) {
                            if (persist) lastPersistAt.set(now)
                            if (notify) lastNotificationAt.set(now)
                            serviceScope.launch {
                                if (persist) repository.updateProgress(progress)
                                if (notify) {
                                    val speedStr = String.format("%.1f MB/s", progress.speed / (1024f * 1024f))
                                    updateNotification(task.title, progress.progress.toInt(), speedStr, "${progress.eta}s")
                                }
                            }
                        }
                    }

                    if (result.isSuccess) {
                        val file = result.getOrThrow()
                        repository.markCompleted(task.id, file.absolutePath)
                        showCompletedNotification(task.title)
                    } else {
                        val error = result.exceptionOrNull()
                        val message = error?.message ?: "Download failed"
                        val latestStatus = repository.getDownloadById(task.id)?.status
                        if (latestStatus != DownloadStatus.PAUSED &&
                            latestStatus != DownloadStatus.CANCELLED &&
                            !message.contains("CANCELLED", ignoreCase = true) &&
                            !message.contains("PAUSED", ignoreCase = true)
                        ) {
                            repository.markFailed(task.id, "DOWNLOAD_ERROR", message)
                            showFailedNotification(task.title, message)
                        }
                    }
                }

                nextTaskId = repository.getNextQueuedTask()?.id
            }

            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "HYPEX Downloads", NotificationManager.IMPORTANCE_LOW)
                .apply { description = "Shows live download progress and media tasks" }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(
        title: String,
        progress: Int,
        speed: String,
        eta: String,
        isPaused: Boolean = false
    ): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val pendingOpen = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val pauseResumeIntent = Intent(this, DownloadForegroundService::class.java).apply {
            action = if (isPaused) ACTION_RESUME else ACTION_PAUSE
            putExtra(EXTRA_TASK_ID, activeTaskId)
        }
        val pendingPauseResume = PendingIntent.getService(
            this, 1, pauseResumeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val cancelIntent = Intent(this, DownloadForegroundService::class.java).apply {
            action = ACTION_CANCEL
            putExtra(EXTRA_TASK_ID, activeTaskId)
        }
        val pendingCancel = PendingIntent.getService(
            this, 2, cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val contentText = if (isPaused) "Paused" else "$progress% • $speed • ETA: $eta"
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress, progress == 0)
            .setOngoing(true)
            .setContentIntent(pendingOpen)
            .addAction(
                if (isPaused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause,
                if (isPaused) "Resume" else "Pause",
                pendingPauseResume
            )
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", pendingCancel)
            .build()
    }

    private fun updateNotification(title: String, progress: Int, speed: String, eta: String, isPaused: Boolean = false) {
        getSystemService(NotificationManager::class.java)
            ?.notify(NOTIFICATION_ID, createNotification(title, progress, speed, eta, isPaused))
    }

    private fun showCompletedNotification(title: String) {
        val openIntent = Intent(this, MainActivity::class.java)
        val pendingOpen = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Download Complete")
            .setContentText(title)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentIntent(pendingOpen)
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID + 1, notification)
    }

    private fun showFailedNotification(title: String, error: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Download Failed")
            .setContentText("$title: $error")
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID + 2, notification)
    }

    override fun onDestroy() {
        currentDownloadJob?.cancel()
        serviceScope.cancel()
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Exception) { }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
