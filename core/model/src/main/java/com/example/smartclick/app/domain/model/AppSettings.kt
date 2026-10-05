package com.example.smartclick.app.domain.model

enum class ThemeMode { DARK, LIGHT, SYSTEM }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val hapticEnabled: Boolean = true,
    val languageTag: String? = null,
    val onboardingCompleted: Boolean = false,
    val remoteMode: String = "DPAD",
)
