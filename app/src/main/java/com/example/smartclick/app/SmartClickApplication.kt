package com.example.smartclick.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SmartClickApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialization if needed
    }
}
