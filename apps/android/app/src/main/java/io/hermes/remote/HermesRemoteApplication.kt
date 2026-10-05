package io.hermes.remote

import android.app.Application
import android.util.Log

class HermesRemoteApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.i("HermesRemote", "Application started (v${BuildConfig.VERSION_NAME})")
    }
}
