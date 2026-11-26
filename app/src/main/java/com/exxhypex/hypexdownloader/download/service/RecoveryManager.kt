package com.exxhypex.hypexdownloader.worker

import android.content.Context
import com.exxhypex.hypexdownloader.data.repository.DownloadRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RecoveryManager(private val context: Context) {
    private val repository = DownloadRepository(context)

    fun recoverOnStartup() {
        CoroutineScope(Dispatchers.IO).launch {
            repository.reconcilePendingTasks()
            repository.engineBridge.initializeEngine()
        }
    }
}
