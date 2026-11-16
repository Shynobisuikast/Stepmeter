package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.DailyActivityEntity
import com.example.data.NotificationLogEntity
import com.example.di.StepmeterContainer
import com.example.domain.AnalyticsMetric
import com.example.domain.DailyChartPoint
import com.example.domain.DateUtils
import com.example.domain.DecryptedFeedbackItem
import com.example.domain.FeedbackCategory
import com.example.domain.GoalMetric
import com.example.domain.GoalPeriod
import com.example.domain.GoalProgress
import com.example.domain.InAppGoalAlert
import com.example.domain.LocalBackupInfo
import com.example.domain.ShareCardTheme
import com.example.domain.SharePeriod
import com.example.domain.ThemeMode
import com.example.domain.UserProfile
import com.example.sensor.StepCounterForegroundService
import com.example.share.ProgressCardRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {
  private val container = StepmeterContainer.getInstance(application)
  private val preferences = container.preferences
  private val repository = container.repository
  private val backupManager = container.backupManager
  val sensorTracker = container.sensorTracker

  val isBackgroundTracking: StateFlow<Boolean> =
    container.isBackgroundServiceRunning.asStateFlow()

  private val todayEpochDay = DateUtils.todayEpochDay()
  private val todayCivil = DateUtils.epochDayToCivil(todayEpochDay)

  private val _selectedYearMonth = MutableStateFlow(todayCivil.year to todayCivil.month)
  val selectedYearMonth: StateFlow<Pair<Int, Int>> = _selectedYearMonth.asStateFlow()

  private val _analyticsMetric = MutableStateFlow(AnalyticsMetric.STEPS)
  val analyticsMetric: StateFlow<AnalyticsMetric> = _analyticsMetric.asStateFlow()

  private val _localBackups = MutableStateFlow<List<LocalBackupInfo>>(emptyList())
  val localBackups: StateFlow<List<LocalBackupInfo>> = _localBackups.asStateFlow()

  private val _statusBanner = MutableSharedFlow<String>(extraBufferCapacity = 8)
  val statusBanner: SharedFlow<String> = _statusBanner.asSharedFlow()

  private val _activeGoalAlert = MutableStateFlow<InAppGoalAlert?>(null)
  val activeGoalAlert: StateFlow<InAppGoalAlert?> = _activeGoalAlert.asStateFlow()

  val themeMode: StateFlow<ThemeMode> =
    preferences.themeModeFlow.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = ThemeMode.DARK,
    )

  val userProfile: StateFlow<UserProfile> =
    preferences.userProfileFlow.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = UserProfile(),
    )

  val todayActivity: StateFlow<DailyActivityEntity?> =
    repository
      .observeDay(todayEpochDay)
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null,
      )

  private val weekBounds = DateUtils.weekRange(todayEpochDay)
  val weekActivities: StateFlow<List<DailyActivityEntity>> =
    repository
      .observeRange(weekBounds.first, weekBounds.second)
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
      )

  val last30DaysChartPoints: StateFlow<List<DailyChartPoint>> =
    repository
      .observeRange(todayEpochDay - 29, todayEpochDay)
      .map { rows ->
        withContext(Dispatchers.Default) {
          val mapByDay = rows.associateBy { it.epochDay }
          (0 until 30).map { offset ->
            val epoch = (todayEpochDay - 29) + offset
            val row = mapByDay[epoch]
            val civil = DateUtils.epochDayToCivil(epoch)
            DailyChartPoint(
              epochDay = epoch,
              dayNumber = civil.day,
              label = DateUtils.formatShortDateTr(epoch),
              steps = row?.totalSteps ?: 0,
              distanceKm = row?.distanceKm ?: 0.0,
              caloriesKcal = row?.caloriesKcal ?: 0.0,
            )
          }
        }
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
      )

  val monthlyChartPoints: StateFlow<List<DailyChartPoint>> =
    _selectedYearMonth
      .flatMapLatest { (year, month) ->
        repository.observeMonth(year, month).map { rows ->
          withContext(Dispatchers.Default) {
            val mapByDay = rows.associateBy { it.epochDay }
            val daysCount = DateUtils.daysInMonth(year, month)
            (1..daysCount).map { day ->
              val epoch = DateUtils.civilToEpochDay(year, month, day)
              val row = mapByDay[epoch]
              DailyChartPoint(
                epochDay = epoch,
                dayNumber = day,
                label = day.toString(),
                steps = row?.totalSteps ?: 0,
                distanceKm = row?.distanceKm ?: 0.0,
                caloriesKcal = row?.caloriesKcal ?: 0.0,
              )
            }
          }
        }
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
      )

  val goalProgressList: StateFlow<List<GoalProgress>> =
    combine(
        repository.observeGoals(),
        repository.observeRange(
          DateUtils.monthRange(todayCivil.year, todayCivil.month).first.coerceAtMost(weekBounds.first),
          DateUtils.monthRange(todayCivil.year, todayCivil.month).second.coerceAtLeast(weekBounds.second),
        ),
      ) { goals, rows ->
        withContext(Dispatchers.Default) {
          repository.mapGoalProgress(goals, rows, todayEpochDay)
        }
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
      )

  val notifications: StateFlow<List<NotificationLogEntity>> =
    repository
      .observeNotifications()
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
      )

  val unreadNotificationCount: StateFlow<Int> =
    repository
      .observeUnreadNotificationCount()
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0,
      )

  val feedbacks: StateFlow<List<DecryptedFeedbackItem>> =
    repository
      .observeFeedbacks()
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
      )

  init {
    viewModelScope.launch {
      repository.ensureDefaultGoals()
      refreshLocalBackups()
    }
    viewModelScope.launch {
      repository.inAppAlerts.collect { alert -> _activeGoalAlert.value = alert }
    }
  }

  fun dismissActiveGoalAlert() {
    _activeGoalAlert.value = null
  }

  fun startStepTracking(enableBackgroundService: Boolean = true) {
    if (enableBackgroundService) {
      StepCounterForegroundService.start(getApplication())
    } else {
      sensorTracker.start()
    }
  }

  fun setBackgroundTrackingEnabled(enabled: Boolean) {
    if (enabled) {
      StepCounterForegroundService.start(getApplication())
      _statusBanner.tryEmit("Arka planda canlı adım takibi başlatıldı")
    } else {
      StepCounterForegroundService.stop(getApplication())
      _statusBanner.tryEmit("Arka plan servisi durduruldu")
    }
  }

  fun shiftSelectedMonth(deltaMonths: Int) {
    val (year, month) = _selectedYearMonth.value
    var newMonth = month + deltaMonths
    var newYear = year
    while (newMonth > 12) {
      newMonth -= 12
      newYear += 1
    }
    while (newMonth < 1) {
      newMonth += 12
      newYear -= 1
    }
    _selectedYearMonth.value = newYear to newMonth
  }

  fun setAnalyticsMetric(metric: AnalyticsMetric) {
    _analyticsMetric.value = metric
  }

  fun saveGoal(period: GoalPeriod, metric: GoalMetric, target: Int) {
    viewModelScope.launch {
      repository.saveGoal(period, metric, target)
      _statusBanner.tryEmit("${period.labelTr} ${metric.labelTr} hedefi eklendi")
    }
  }

  fun toggleGoalActive(goalId: Long, active: Boolean) {
    viewModelScope.launch { repository.toggleGoalActive(goalId, active) }
  }

  fun deleteGoal(goalId: Long) {
    viewModelScope.launch { repository.deleteGoal(goalId) }
  }

  fun markAllNotificationsRead() {
    viewModelScope.launch { repository.markAllNotificationsRead() }
  }

  fun setThemeMode(mode: ThemeMode) {
    viewModelScope.launch { preferences.setThemeMode(mode) }
  }

  fun saveProfile(profile: UserProfile) {
    viewModelScope.launch {
      preferences.saveProfile(profile)
      repository.recalculateTodayMetricsForProfile(profile)
      _statusBanner.tryEmit("Profil ve kalori kalibrasyonu güncellendi")
    }
  }

  fun refreshLocalBackups() {
    viewModelScope.launch { _localBackups.value = backupManager.listLocalBackups() }
  }

  fun createLocalBackup(password: String?) {
    viewModelScope.launch {
      runCatching { backupManager.createLocalEncryptedBackup(password) }
        .onSuccess { info ->
          refreshLocalBackups()
          val typeLabel = if (info.isPasswordProtected) "Parola korumalı" else "Keystore şifreli"
          _statusBanner.tryEmit("$typeLabel CSV yedeği oluşturuldu: ${info.fileName}")
        }
        .onFailure { err -> _statusBanner.tryEmit(err.message ?: "Yedek oluşturulamadı") }
    }
  }

  fun exportEncryptedBackupToUri(uri: Uri, password: String?) {
    viewModelScope.launch {
      runCatching { backupManager.exportEncryptedCsvToUri(uri, password) }
        .onSuccess { _statusBanner.tryEmit("Şifreli CSV yedeği cihaza aktarıldı") }
        .onFailure { err -> _statusBanner.tryEmit(err.message ?: "Dışa aktarma başarısız") }
    }
  }

  fun restoreFromLocalBackup(fileName: String, password: String?) {
    viewModelScope.launch {
      runCatching { backupManager.restoreFromLocalFile(fileName, password) }
        .onSuccess { res ->
          _statusBanner.tryEmit(
            "Geri yüklendi: ${res.restoredActivitiesCount} gün, ${res.restoredGoalsCount} hedef"
          )
        }
        .onFailure { err -> _statusBanner.tryEmit(err.message ?: "Geri yükleme hatası") }
    }
  }

  fun restoreFromUri(uri: Uri, password: String?) {
    viewModelScope.launch {
      runCatching { backupManager.restoreFromUri(uri, password) }
        .onSuccess { res ->
          _statusBanner.tryEmit(
            "Dosyadan geri yüklendi: ${res.restoredActivitiesCount} gün, ${res.restoredGoalsCount} hedef"
          )
        }
        .onFailure { err -> _statusBanner.tryEmit(err.message ?: "Dosya çözülemedi") }
    }
  }

  fun deleteLocalBackup(fileName: String) {
    viewModelScope.launch {
      backupManager.deleteLocalBackup(fileName)
      refreshLocalBackups()
      _statusBanner.tryEmit("Yedek dosyası silindi")
    }
  }

  fun submitFeedback(category: FeedbackCategory, rating: Int, message: String) {
    viewModelScope.launch {
      runCatching { repository.submitFeedback(category, rating, message) }
        .onSuccess { _statusBanner.tryEmit("Geri bildiriminiz şifreli olarak cihazınıza kaydedildi") }
        .onFailure { err -> _statusBanner.tryEmit(err.message ?: "Kaydedilemedi") }
    }
  }

  fun deleteFeedback(id: Long) {
    viewModelScope.launch { repository.deleteFeedback(id) }
  }

  fun buildShareCardData(period: SharePeriod, theme: ShareCardTheme): ProgressCardRenderer.ShareCardData {
    val today = todayActivity.value
    val week = weekActivities.value
    val goals = goalProgressList.value
    return when (period) {
      SharePeriod.DAILY -> {
        val steps = today?.totalSteps ?: 0
        val dailyGoal = goals.firstOrNull { it.period == GoalPeriod.DAILY && it.metric == GoalMetric.STEPS && it.isActive }
        val badge =
          if (dailyGoal != null) {
            if (dailyGoal.isCompleted) "Günlük ${dailyGoal.targetValue} Adım Hedefi Tamamlandı!"
            else "Günlük Hedef: %${dailyGoal.percent} (${dailyGoal.targetValue} adım)"
          } else {
            "Günlük Aktivite Özeti"
          }
        val bars =
          if (week.isEmpty()) {
            listOf(DateUtils.dayOfWeekShortTr(todayEpochDay) to steps)
          } else {
            (weekBounds.first..weekBounds.second).map { day ->
              val row = week.firstOrNull { it.epochDay == day }
              DateUtils.dayOfWeekShortTr(day) to (row?.totalSteps ?: 0)
            }
          }
        ProgressCardRenderer.ShareCardData(
          period = period,
          theme = theme,
          dateRangeLabel = DateUtils.formatFullDateTr(todayEpochDay),
          totalSteps = steps,
          distanceKm = today?.distanceKm ?: 0.0,
          caloriesKcal = today?.caloriesKcal ?: 0.0,
          goalBadgeText = badge,
          dailyBars = bars,
        )
      }
      SharePeriod.WEEKLY -> {
        val totalSteps = week.sumOf { it.totalSteps }
        val totalDistance = week.sumOf { it.distanceKm }
        val totalCalories = week.sumOf { it.caloriesKcal }
        val weeklyGoal = goals.firstOrNull { it.period == GoalPeriod.WEEKLY && it.metric == GoalMetric.STEPS && it.isActive }
        val badge =
          if (weeklyGoal != null) {
            if (weeklyGoal.isCompleted) "Haftalık ${weeklyGoal.targetValue} Adım Hedefi Tamamlandı!"
            else "Haftalık Hedef: %${weeklyGoal.percent} (${weeklyGoal.targetValue} adım)"
          } else {
            "Haftalık Aktivite Özeti"
          }
        val bars =
          (weekBounds.first..weekBounds.second).map { day ->
            val row = week.firstOrNull { it.epochDay == day }
            DateUtils.dayOfWeekShortTr(day) to (row?.totalSteps ?: 0)
          }
        ProgressCardRenderer.ShareCardData(
          period = period,
          theme = theme,
          dateRangeLabel =
            "${DateUtils.formatShortDateTr(weekBounds.first)} - ${DateUtils.formatShortDateTr(weekBounds.second)}",
          totalSteps = totalSteps,
          distanceKm = totalDistance,
          caloriesKcal = totalCalories,
          goalBadgeText = badge,
          dailyBars = bars,
        )
      }
    }
  }

  fun shareProgressCard(data: ProgressCardRenderer.ShareCardData) {
    viewModelScope.launch {
      val ctx = getApplication<Application>()
      runCatching { ProgressCardRenderer.renderAndGetUri(ctx, data) }
        .onSuccess { uri ->
          ProgressCardRenderer.launchShareIntent(ctx, uri, data, asNewTask = true)
        }
        .onFailure { err -> _statusBanner.tryEmit(err.message ?: "Paylaşım kartı oluşturulamadı") }
    }
  }

  override fun onCleared() {
    // Only unregister if foreground service is not actively holding the sensor in background
    if (!container.isBackgroundServiceRunning.value) {
      sensorTracker.stop()
    }
    super.onCleared()
  }
}
