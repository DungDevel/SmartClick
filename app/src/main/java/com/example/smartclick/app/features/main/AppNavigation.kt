package com.example.smartclick.app.features.main

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.navigation
import androidx.compose.runtime.remember
import com.example.smartclick.app.features.auth.LoginScreen
import com.example.smartclick.app.features.onboarding.BrandScreen
import com.example.smartclick.app.features.onboarding.DeviceFoundScreen
import com.example.smartclick.app.features.onboarding.NotFoundScreen
import com.example.smartclick.app.features.onboarding.ScanningScreen
import com.example.smartclick.app.features.devices.DevicesViewModel
import com.example.smartclick.app.features.onboarding.WelcomeScreen
import com.example.smartclick.app.features.onboarding.LanguageScreen
import com.example.smartclick.app.features.settings.UpdateToPremiumScreen

object NavRoutes {
    const val WELCOME = "welcome"
    const val LANGUAGE = "language"
    const val BRAND = "brand"
    const val SCANNING = "scanning"
    const val DEVICE_FOUND = "device_found"
    const val NOT_FOUND = "not_found"
    const val LOGIN = "login"
    const val MAIN = "main"
    const val PREMIUM = "premium"
}

@Composable
fun AppNavigation(
    startDestination: String = NavRoutes.WELCOME,
    viewModel: OnboardingStateViewModel = hiltViewModel()
) {
    val navController = rememberNavController()

    // Map "onboarding" string alias from MainActivity to WELCOME route
    val initialRoute = if (startDestination == "onboarding") NavRoutes.WELCOME else startDestination

    // Persist that onboarding is finished, then land on the main app.
    val goToMain: () -> Unit = {
        viewModel.markOnboardingCompleted()
        navController.navigate(NavRoutes.MAIN) {
            popUpTo(initialRoute) { inclusive = true }
        }
    }

    NavHost(
        navController = navController,
        startDestination = initialRoute
    ) {
        composable(NavRoutes.WELCOME) {
            WelcomeScreen(
                onNext = { navController.navigate(NavRoutes.LANGUAGE) }
            )
        }
        composable(NavRoutes.LANGUAGE) {
            LanguageScreen(
                onNext = { navController.navigate("scan_flow") }
            )
        }
        navigation(startDestination = NavRoutes.BRAND, route = "scan_flow") {
            composable(NavRoutes.BRAND) { backStackEntry ->
                val parentEntry = remember(backStackEntry) {
                    navController.getBackStackEntry("scan_flow")
                }
                val scannerViewModel: DevicesViewModel = hiltViewModel(parentEntry)
                BrandScreen(
                    onNext = { brandName ->
                        scannerViewModel.setTargetBrand(brandName)
                        navController.navigate(NavRoutes.SCANNING)
                    }
                )
            }
            composable(NavRoutes.SCANNING) { backStackEntry ->
                val parentEntry = remember(backStackEntry) {
                    navController.getBackStackEntry("scan_flow")
                }
                val scannerViewModel: DevicesViewModel = hiltViewModel(parentEntry)
                ScanningScreen(
                    viewModel = scannerViewModel,
                    onNext = { navController.navigate(NavRoutes.DEVICE_FOUND) },
                    onFail = { navController.navigate(NavRoutes.NOT_FOUND) }
                )
            }
            composable(NavRoutes.DEVICE_FOUND) { backStackEntry ->
                val parentEntry = remember(backStackEntry) {
                    navController.getBackStackEntry("scan_flow")
                }
                val scannerViewModel: DevicesViewModel = hiltViewModel(parentEntry)
                DeviceFoundScreen(
                    viewModel = scannerViewModel,
                    onNext = { navController.navigate(NavRoutes.LOGIN) },
                    onClose = { navController.navigate(NavRoutes.LOGIN) },
                    onLimitExceeded = { navController.navigate(NavRoutes.PREMIUM) }
                )
            }
            composable(NavRoutes.NOT_FOUND) { backStackEntry ->
                val parentEntry = remember(backStackEntry) {
                    navController.getBackStackEntry("scan_flow")
                }
                val scannerViewModel: DevicesViewModel = hiltViewModel(parentEntry)
                NotFoundScreen(
                    onRetry = { navController.popBackStack() },
                    onNext = {
                        navController.navigate(NavRoutes.LOGIN) {
                            popUpTo(NavRoutes.SCANNING) { inclusive = true }
                        }
                    },
                    onSkip = goToMain,
                    viewModel = scannerViewModel
                )
            }
        }
        composable(NavRoutes.PREMIUM) {
            UpdateToPremiumScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(NavRoutes.LOGIN) {
            LoginScreen(
                onLoginSuccess = goToMain,
                onSkip = goToMain
            )
        }
        composable(NavRoutes.MAIN) {
            MainAppScreen()
        }
    }
}
