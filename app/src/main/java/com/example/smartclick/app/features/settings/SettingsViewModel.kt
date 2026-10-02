package com.example.smartclick.app.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.example.smartclick.app.domain.model.AppSettings
import com.example.smartclick.app.domain.model.ThemeMode
import com.example.smartclick.app.domain.model.UserAccount
import com.example.smartclick.app.domain.repository.IAuthRepository
import com.example.smartclick.app.domain.repository.ISettingsRepository
import com.example.smartclick.app.domain.repository.ISubscriptionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val user: UserAccount? = null,
    val isPremium: Boolean = false,
    val settings: AppSettings? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: ISettingsRepository,
    private val authRepository: IAuthRepository,
    private val subscriptionRepository: ISubscriptionRepository
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        authRepository.currentUser,
        subscriptionRepository.isPremium,
        settingsRepository.settings
    ) { user, isPremium, settings ->
        SettingsUiState(user, isPremium, settings)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState()
    )

    val settings = settingsRepository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null
    )

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            settingsRepository.setThemeMode(mode)
        }
    }

    fun setHapticEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setHapticEnabled(enabled)
        }
    }

    fun setLanguage(languageTag: String) {
        viewModelScope.launch {
            settingsRepository.setLanguageTag(languageTag)
        }
        val localeList = if (languageTag == "sys") LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(languageTag)
        AppCompatDelegate.setApplicationLocales(localeList)
    }

    fun login(userId: String = "1") {
        viewModelScope.launch {
            authRepository.login(userId)
            // FIXED 06: removed automatic subscription activation that created the Premium loophole
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
        }
    }
}
