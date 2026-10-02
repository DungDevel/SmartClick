package com.example.smartclick.app.features.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.smartclick.app.features.onboarding.LanguageScreen

@Composable
fun SettingsScreen() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "main") {
        composable("main") {
            SettingsMainScreen(
                onNavigate = { route -> navController.navigate(route) }
            )
        }
        composable("account") {
            AccountInformationScreen(onBack = { navController.popBackStack() })
        }
        composable("language") {
            val settingsVM: SettingsViewModel = androidx.hilt.navigation.compose.hiltViewModel()
            val uiState by settingsVM.uiState.collectAsState()
            val currentLang = uiState.settings?.languageTag ?: "en"

            // Using existing LanguageScreen. onNext acts as save & back.
            LanguageScreen(
                currentLanguage = currentLang,
                onSelectAndNext = { lang ->
                    settingsVM.setLanguage(lang)
                    navController.popBackStack()
                }
            )
        }
        composable("privacy") {
            PrivacyPolicyScreen(onBack = { navController.popBackStack() })
        }
        composable("terms") {
            TermsAndConditionScreen(onBack = { navController.popBackStack() })
        }
        composable("appearance") {
            AppearanceScreen(onBack = { navController.popBackStack() })
        }
        composable("haptic") {
            HapticFeedbackScreen(onBack = { navController.popBackStack() })
        }
        composable("premium") {
            UpdateToPremiumScreen(onBack = { navController.popBackStack() })
        }
    }
}
