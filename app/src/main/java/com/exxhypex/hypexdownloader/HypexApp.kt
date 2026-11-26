package com.exxhypex.hypexdownloader

import android.app.Application
import com.exxhypex.hypexdownloader.worker.RecoveryManager

class HypexApp : Application() {
    override fun onCreate() {
        super.onCreate()
        RecoveryManager(this).recoverOnStartup()
    }
}
