package com.example.smartclick

import android.graphics.Color
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.smartclick.app.core.ui.theme.SmartClickTheme
import com.example.smartclick.app.domain.model.ThemeMode
import com.example.smartclick.app.features.main.AppNavigation
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private val viewModel: MainActivityViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Optional: Install splash screen for Android 12+ API
        installSplashScreen().setKeepOnScreenCondition { viewModel.settings.value == null }
        super.onCreate(savedInstanceState)

        // Cho app vẽ kín toàn màn hình, thanh trạng thái và thanh điều hướng trong suốt
        enableEdgeToEdge()

        setContent {
            val settings by viewModel.settings.collectAsState()

            val isDarkTheme = when (settings?.themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM, null -> isSystemInDarkTheme()
            }

            // Đổi màu biểu tượng đồng hồ, pin... theo giao diện sáng/tối của app (không chỉ theo hệ thống)
            DisposableEffect(isDarkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { isDarkTheme },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { isDarkTheme }
                )
                // enableEdgeToEdge có thể làm thanh trạng thái hiện lại, nên ẩn lần nữa
                hideStatusBar()
                onDispose { }
            }

            SmartClickTheme(darkTheme = isDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (settings != null) {
                        AppNavigation(
                            startDestination = "onboarding",
                            onboardingCompleted = settings!!.onboardingCompleted
                        )
                    }
                }
            }
        }
    }

    /**
     * Ẩn thanh trạng thái (giờ, wifi, pin) để nội dung sát mép trên.
     * Vuốt từ cạnh trên xuống sẽ hiện thanh tạm thời rồi tự ẩn lại.
     */
    private fun hideStatusBar() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.statusBars())
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideStatusBar()
    }
}
