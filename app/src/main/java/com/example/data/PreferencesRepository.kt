package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.ThemeMode
import com.example.domain.UserProfile
import com.example.security.CryptoManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "stepmeter_prefs")

class PreferencesRepository(private val context: Context) {
  private object Keys {
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val DISPLAY_NAME_ENC = stringPreferencesKey("display_name_enc")
    val HEIGHT_CM = intPreferencesKey("height_cm")
    val WEIGHT_KG = floatPreferencesKey("weight_kg")
    val CUSTOM_STRIDE_CM = intPreferencesKey("custom_stride_cm")
    val USE_CUSTOM_STRIDE = booleanPreferencesKey("use_custom_stride")
    val SENSOR_BASELINE_DAY = longPreferencesKey("sensor_baseline_day")
    val SENSOR_BASELINE_STEPS = intPreferencesKey("sensor_baseline_steps")
    val LAST_SENSOR_TOTAL = intPreferencesKey("last_sensor_total")
    val GOALS_SEEDED = booleanPreferencesKey("goals_seeded")
  }

  val themeModeFlow: Flow<ThemeMode> =
    context.dataStore.data.map { prefs ->
      val raw = prefs[Keys.THEME_MODE] ?: ThemeMode.DARK.name
      runCatching { ThemeMode.valueOf(raw) }.getOrDefault(ThemeMode.DARK)
    }

  val userProfileFlow: Flow<UserProfile> =
    context.dataStore.data.map { prefs ->
      val encryptedName = prefs[Keys.DISPLAY_NAME_ENC]
      val decodedName =
        if (encryptedName.isNullOrBlank()) {
          "Yürüyüşçü"
        } else {
          runCatching { CryptoManager.decryptStringKeystore(encryptedName) }.getOrDefault("Yürüyüşçü")
        }
      UserProfile(
        displayName = decodedName,
        heightCm = prefs[Keys.HEIGHT_CM] ?: 172,
        weightKg = prefs[Keys.WEIGHT_KG] ?: 72f,
        customStrideCm = prefs[Keys.CUSTOM_STRIDE_CM] ?: 72,
        useCustomStride = prefs[Keys.USE_CUSTOM_STRIDE] ?: false,
      )
    }

  suspend fun getProfileSync(): UserProfile = userProfileFlow.first()

  suspend fun getThemeModeSync(): ThemeMode = themeModeFlow.first()

  suspend fun setThemeMode(mode: ThemeMode) {
    context.dataStore.edit { prefs -> prefs[Keys.THEME_MODE] = mode.name }
  }

  suspend fun saveProfile(profile: UserProfile) {
    val cleanName = profile.displayName.trim().ifEmpty { "Yürüyüşçü" }
    val encryptedName = CryptoManager.encryptStringKeystore(cleanName)
    context.dataStore.edit { prefs ->
      prefs[Keys.DISPLAY_NAME_ENC] = encryptedName
      prefs[Keys.HEIGHT_CM] = profile.heightCm.coerceIn(120, 230)
      prefs[Keys.WEIGHT_KG] = profile.weightKg.coerceIn(30f, 220f)
      prefs[Keys.CUSTOM_STRIDE_CM] = profile.customStrideCm.coerceIn(40, 130)
      prefs[Keys.USE_CUSTOM_STRIDE] = profile.useCustomStride
    }
  }

  suspend fun getSensorState(): Triple<Long, Int, Int> {
    val prefs = context.dataStore.data.first()
    return Triple(
      prefs[Keys.SENSOR_BASELINE_DAY] ?: -1L,
      prefs[Keys.SENSOR_BASELINE_STEPS] ?: -1,
      prefs[Keys.LAST_SENSOR_TOTAL] ?: -1,
    )
  }

  suspend fun saveSensorState(baselineDay: Long, baselineSteps: Int, lastTotal: Int) {
    context.dataStore.edit { prefs ->
      prefs[Keys.SENSOR_BASELINE_DAY] = baselineDay
      prefs[Keys.SENSOR_BASELINE_STEPS] = baselineSteps
      prefs[Keys.LAST_SENSOR_TOTAL] = lastTotal
    }
  }

  suspend fun areGoalsSeeded(): Boolean {
    return context.dataStore.data.first()[Keys.GOALS_SEEDED] ?: false
  }

  suspend fun markGoalsSeeded() {
    context.dataStore.edit { prefs -> prefs[Keys.GOALS_SEEDED] = true }
  }
}
