package com.example

import android.app.Application
import com.example.worker.RecoveryManager

class HypexApp : Application() {
    override fun onCreate() {
        super.onCreate()
        RecoveryManager(this).recoverOnStartup()
    }
}
