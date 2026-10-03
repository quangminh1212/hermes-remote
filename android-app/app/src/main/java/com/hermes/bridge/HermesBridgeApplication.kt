package com.hermes.bridge

import android.app.Application

class HermesBridgeApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize global components
        // AccessibilityBridgeService.instance will be set when service starts
        
        // TODO: Add Hilt dependency injection if needed
        // TODO: Add logging initialization
    }
}
