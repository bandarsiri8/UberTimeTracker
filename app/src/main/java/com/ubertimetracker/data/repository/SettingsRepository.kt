package com.ubertimetracker.data.repository

import com.ubertimetracker.data.local.SettingsDao
import com.ubertimetracker.data.model.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val settingsDao: SettingsDao
) {

    fun getSettings(): Flow<AppSettings> = settingsDao.getSettings().map { 
        it ?: AppSettings()
    }

    suspend fun getSettingsSync(): AppSettings = 
        settingsDao.getSettingsSync() ?: AppSettings()

    suspend fun updateDarkMode(isDarkMode: Boolean?) {
        ensureSettingsExist()
        settingsDao.updateDarkMode(isDarkMode)
    }

    suspend fun updateAutoSync(enabled: Boolean) {
        ensureSettingsExist()
        settingsDao.updateAutoSync(enabled)
    }

    suspend fun updateLanguage(language: String) {
        ensureSettingsExist()
        settingsDao.updateLanguage(language)
    }

    suspend fun updateOfflineCache(enabled: Boolean) {
        ensureSettingsExist()
        settingsDao.updateOfflineCache(enabled)
    }

    suspend fun updateCloudSync(enabled: Boolean) {
        ensureSettingsExist()
        settingsDao.updateCloudSync(enabled)
    }

    suspend fun updateSettings(settings: AppSettings) {
        settingsDao.insertSettings(settings)
    }

    private suspend fun ensureSettingsExist() {
        if (settingsDao.getSettingsSync() == null) {
            settingsDao.insertSettings(AppSettings())
        }
    }
}
