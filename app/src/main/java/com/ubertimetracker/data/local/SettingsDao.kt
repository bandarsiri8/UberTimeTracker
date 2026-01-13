package com.ubertimetracker.data.local

import androidx.room.*
import com.ubertimetracker.data.model.AppSettings
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingsDao {

    @Query("SELECT * FROM settings WHERE id = 1")
    fun getSettings(): Flow<AppSettings?>

    @Query("SELECT * FROM settings WHERE id = 1")
    suspend fun getSettingsSync(): AppSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSettings(settings: AppSettings)

    @Update
    suspend fun updateSettings(settings: AppSettings)

    @Query("UPDATE settings SET isDarkMode = :isDarkMode WHERE id = 1")
    suspend fun updateDarkMode(isDarkMode: Boolean?)

    @Query("UPDATE settings SET autoSyncEnabled = :enabled WHERE id = 1")
    suspend fun updateAutoSync(enabled: Boolean)

    @Query("UPDATE settings SET selectedLanguage = :language WHERE id = 1")
    suspend fun updateLanguage(language: String)

    @Query("UPDATE settings SET offlineCacheEnabled = :enabled WHERE id = 1")
    suspend fun updateOfflineCache(enabled: Boolean)

    @Query("UPDATE settings SET cloudSyncEnabled = :enabled WHERE id = 1")
    suspend fun updateCloudSync(enabled: Boolean)
}
