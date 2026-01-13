package com.ubertimetracker.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ubertimetracker.data.model.AppSettings
import com.ubertimetracker.data.repository.SettingsRepository
import com.ubertimetracker.service.UberAccessibilityService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,

    private val sessionRepository: com.ubertimetracker.data.repository.SessionRepository,
    private val googleDriveManager: com.ubertimetracker.util.GoogleDriveManager
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.getSettings()
        .stateIn(viewModelScope, SharingStarted.Lazily, AppSettings())

    val isAccessibilityEnabled: StateFlow<Boolean> = UberAccessibilityService.isServiceEnabled

    private val _showThemeDialog = MutableStateFlow(false)
    val showThemeDialog: StateFlow<Boolean> = _showThemeDialog.asStateFlow()

    private val _showLanguageDialog = MutableStateFlow(false)

    val showLanguageDialog: StateFlow<Boolean> = _showLanguageDialog.asStateFlow()

    private val _isDriveSignedIn = MutableStateFlow(false)
    val isDriveSignedIn: StateFlow<Boolean> = _isDriveSignedIn.asStateFlow()

    init {
        checkDriveSignIn()
    }

    private val _signInError = MutableStateFlow<String?>(null)
    val signInError: StateFlow<String?> = _signInError.asStateFlow()

    fun checkDriveSignIn() {
        _isDriveSignedIn.value = googleDriveManager.getLastSignedInAccount() != null
    }

    fun getDriveSignInIntent(): Intent {
        return googleDriveManager.getSignInIntent()
    }

    fun handleSignInResult(result: androidx.activity.result.ActivityResult) {
        try {
            val task = com.google.android.gms.auth.api.signin.GoogleSignIn.getSignedInAccountFromIntent(result.data)
            val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
            checkDriveSignIn()
            _signInError.value = null
        } catch (e: com.google.android.gms.common.api.ApiException) {
            val sha1 = if (e.statusCode == 10) "\nSHA1: ${googleDriveManager.getSha1Fingerprint()}" else ""
            _signInError.value = "Sign-in failed: ${e.statusCode} ${e.message}$sha1"
            // Common codes: 10 (Developer Error - SHA1), 12500 (Sign in failed)
        }
    }

    fun disconnectDrive() {
        googleDriveManager.signOut {
            checkDriveSignIn()
        }
    }

    fun clearError() {
        _signInError.value = null
    }

    fun updateDarkMode(isDarkMode: Boolean?) {
        viewModelScope.launch {
            settingsRepository.updateDarkMode(isDarkMode)
        }
    }

    fun updateAutoSync(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateAutoSync(enabled)
        }
    }

    fun updateOfflineCache(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateOfflineCache(enabled)
        }
    }

    fun updateCloudSync(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateCloudSync(enabled)
        }
    }

    fun updateNotifications(enabled: Boolean) {
        viewModelScope.launch {
            val currentSettings = settingsRepository.getSettingsSync()
            settingsRepository.updateSettings(currentSettings.copy(notificationsEnabled = enabled))
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            sessionRepository.clearAllData()
        }
    }



    fun showThemeDialog() {
        _showThemeDialog.value = true
    }

    fun hideThemeDialog() {
        _showThemeDialog.value = false
    }

    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fallback?
        }
    }

    fun openNotificationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fallback
        }
    }
    fun getThemeText(isDarkMode: Boolean?): String {
        return when (isDarkMode) {
            true -> "Dark Mode"
            false -> "Light Mode"
            null -> "System Default"
        }
    }
}
