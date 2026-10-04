package com.hermes.bridge

import android.app.Application
import android.util.Log

class HermesBridgeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.i("HermesBridge", "Application started (v${BuildConfig.VERSION_NAME})")
    }
}
