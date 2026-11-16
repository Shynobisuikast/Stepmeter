package com.example.di

import android.content.Context
import com.example.data.AppDatabase
import com.example.data.BackupManager
import com.example.data.PreferencesRepository
import com.example.data.StepRepository
import com.example.sensor.StepSensorTracker
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Application-scoped dependency container so both UI ViewModels and the background
 * StepCounterForegroundService share a single SensorManager listener and Room repository.
 */
class StepmeterContainer private constructor(context: Context) {
  private val appContext = context.applicationContext

  val database: AppDatabase = AppDatabase.getInstance(appContext)
  val preferences: PreferencesRepository = PreferencesRepository(appContext)
  val repository: StepRepository = StepRepository(database, preferences)
  val backupManager: BackupManager = BackupManager(appContext, database, preferences)
  val sensorTracker: StepSensorTracker = StepSensorTracker(appContext, preferences, repository)
  val isBackgroundServiceRunning = MutableStateFlow(false)

  companion object {
    @Volatile private var instance: StepmeterContainer? = null

    fun getInstance(context: Context): StepmeterContainer {
      return instance
        ?: synchronized(this) {
          instance ?: StepmeterContainer(context).also { instance = it }
        }
    }
  }
}
