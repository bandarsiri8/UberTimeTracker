package com.ubertimetracker.di

import android.content.Context
import com.ubertimetracker.data.local.AppDatabase
import com.ubertimetracker.data.local.PauseDao
import com.ubertimetracker.data.local.SessionDao
import com.ubertimetracker.data.local.SettingsDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import com.ubertimetracker.util.ExportManager
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideExportManager(@ApplicationContext context: Context): ExportManager {
        return ExportManager(context)
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getDatabase(context)
    }

    @Provides
    @Singleton
    fun provideSessionDao(database: AppDatabase): SessionDao {
        return database.sessionDao()
    }

    @Provides
    @Singleton
    fun providePauseDao(database: AppDatabase): PauseDao {
        return database.pauseDao()
    }

    @Provides
    @Singleton
    fun provideSettingsDao(database: AppDatabase): SettingsDao {
        return database.settingsDao()
    }
}
