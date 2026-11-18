package com.example.worker

import android.content.Context
import com.example.data.repository.DownloadRepository
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
