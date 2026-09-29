package com.example.smartclick.app.domain.model

data class AppLaunchConfig(
    val appId: String,
    val appName: String,
    val packageName: String? = null
)
