package com.example.data

import androidx.room.withTransaction
import com.example.domain.DateUtils
import com.example.domain.DecryptedFeedbackItem
import com.example.domain.FeedbackCategory
import com.example.domain.GoalMetric
import com.example.domain.GoalPeriod
import com.example.domain.GoalProgress
import com.example.domain.InAppGoalAlert
import com.example.domain.MetricsCalculator
import com.example.domain.UserProfile
import com.example.security.CryptoManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class StepRepository(
  private val db: AppDatabase,
  private val preferences: PreferencesRepository,
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
  private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
  private val activityDao = db.dailyActivityDao()
  private val goalDao = db.goalDao()
  private val notificationDao = db.notificationLogDao()
  private val feedbackDao = db.userFeedbackDao()

  private val _inAppAlerts = MutableSharedFlow<InAppGoalAlert>(extraBufferCapacity = 8)
  val inAppAlerts: SharedFlow<InAppGoalAlert> = _inAppAlerts.asSharedFlow()

  fun observeDay(epochDay: Long): Flow<DailyActivityEntity?> =
    activityDao.observeDay(epochDay).flowOn(ioDispatcher)

  fun observeMonth(year: Int, month: Int): Flow<List<DailyActivityEntity>> =
    activityDao.observeMonth(DateUtils.yearMonthOf(year, month)).flowOn(ioDispatcher)

  fun observeRange(startDay: Long, endDay: Long): Flow<List<DailyActivityEntity>> =
    activityDao.observeRange(startDay, endDay).flowOn(ioDispatcher)

  fun observeGoals(): Flow<List<GoalEntity>> = goalDao.observeAllGoals().flowOn(ioDispatcher)

  fun observeNotifications(): Flow<List<NotificationLogEntity>> =
    notificationDao.observeNotifications().flowOn(ioDispatcher)

  fun observeUnreadNotificationCount(): Flow<Int> =
    notificationDao.observeUnreadCount().flowOn(ioDispatcher)

  fun observeFeedbacks(): Flow<List<DecryptedFeedbackItem>> =
    feedbackDao.observeAllFeedback()
      .map { list ->
        list.map { item ->
          val plain =
            runCatching { CryptoManager.decryptStringKeystore(item.encryptedMessage) }
              .getOrDefault("[Şifreli kayıt çözülemedi]")
          val cat =
            runCatching { FeedbackCategory.valueOf(item.category) }
              .getOrDefault(FeedbackCategory.SUGGESTION)
          DecryptedFeedbackItem(
            id = item.id,
            category = cat,
            rating = item.rating,
            message = plain,
            createdAtMillis = item.createdAt,
          )
        }
      }
      .flowOn(defaultDispatcher)

  suspend fun ensureDefaultGoals() =
    withContext(ioDispatcher) {
      if (!preferences.areGoalsSeeded() && goalDao.getAllGoalsSync().isEmpty()) {
        val defaults =
          listOf(
            GoalEntity(
              periodType = GoalPeriod.DAILY.name,
              metricType = GoalMetric.STEPS.name,
              targetValue = 8000,
              isActive = true,
            ),
            GoalEntity(
              periodType = GoalPeriod.DAILY.name,
              metricType = GoalMetric.CALORIES.name,
              targetValue = 300,
              isActive = true,
            ),
            GoalEntity(
              periodType = GoalPeriod.WEEKLY.name,
              metricType = GoalMetric.STEPS.name,
              targetValue = 50000,
              isActive = true,
            ),
            GoalEntity(
              periodType = GoalPeriod.MONTHLY.name,
              metricType = GoalMetric.STEPS.name,
              targetValue = 200000,
              isActive = true,
            ),
          )
        goalDao.upsertAll(defaults)
        preferences.markGoalsSeeded()
      }
    }

  suspend fun updateSensorSteps(epochDay: Long, sensorStepsForDay: Int) =
    withContext(ioDispatcher) {
      if (sensorStepsForDay < 0) return@withContext
      val profile = preferences.getProfileSync()
      db.withTransaction {
        val existing = activityDao.getDaySync(epochDay)
        if (existing != null && existing.totalSteps == sensorStepsForDay) {
          return@withTransaction
        }
        val (distance, calories, minutes) = computeMetrics(sensorStepsForDay, profile)
        activityDao.upsert(
          DailyActivityEntity(
            id = existing?.id ?: 0L,
            epochDay = epochDay,
            yearMonth = DateUtils.epochDayToYearMonth(epochDay),
            totalSteps = sensorStepsForDay,
            distanceKm = distance,
            caloriesKcal = calories,
            activeMinutes = minutes,
            updatedAt = System.currentTimeMillis(),
          )
        )
      }
      evaluateActiveGoals(epochDay)
    }

  suspend fun recalculateTodayMetricsForProfile(profile: UserProfile) =
    withContext(ioDispatcher) {
      val today = DateUtils.todayEpochDay()
      val existing = activityDao.getDaySync(today) ?: return@withContext
      val (distance, calories, minutes) = computeMetrics(existing.totalSteps, profile)
      activityDao.upsert(
        existing.copy(
          distanceKm = distance,
          caloriesKcal = calories,
          activeMinutes = minutes,
          updatedAt = System.currentTimeMillis(),
        )
      )
      evaluateActiveGoals(today)
    }

  private suspend fun computeMetrics(steps: Int, profile: UserProfile): Triple<Double, Double, Int> =
    withContext(defaultDispatcher) {
      Triple(
        MetricsCalculator.calculateDistanceKm(steps, profile),
        MetricsCalculator.calculateCaloriesKcal(steps, profile),
        MetricsCalculator.estimateActiveMinutes(steps),
      )
    }

  fun mapGoalProgress(
    goals: List<GoalEntity>,
    rows: List<DailyActivityEntity>,
    referenceDay: Long = DateUtils.todayEpochDay(),
  ): List<GoalProgress> {
    if (goals.isEmpty()) return emptyList()

    val (weekStart, weekEnd) = DateUtils.weekRange(referenceDay)
    val civil = DateUtils.epochDayToCivil(referenceDay)
    val (monthStart, monthEnd) = DateUtils.monthRange(civil.year, civil.month)

    var daySteps = 0
    var dayCal = 0.0
    var weekSteps = 0
    var weekCal = 0.0
    var monthSteps = 0
    var monthCal = 0.0

    for (r in rows) {
      if (r.epochDay == referenceDay) {
        daySteps += r.totalSteps
        dayCal += r.caloriesKcal
      }
      if (r.epochDay in weekStart..weekEnd) {
        weekSteps += r.totalSteps
        weekCal += r.caloriesKcal
      }
      if (r.epochDay in monthStart..monthEnd) {
        monthSteps += r.totalSteps
        monthCal += r.caloriesKcal
      }
    }

    return goals.map { goal ->
      val period = runCatching { GoalPeriod.valueOf(goal.periodType) }.getOrDefault(GoalPeriod.DAILY)
      val metric = runCatching { GoalMetric.valueOf(goal.metricType) }.getOrDefault(GoalMetric.STEPS)
      val current =
        when (period to metric) {
          GoalPeriod.DAILY to GoalMetric.STEPS -> daySteps
          GoalPeriod.DAILY to GoalMetric.CALORIES -> dayCal.toInt()
          GoalPeriod.WEEKLY to GoalMetric.STEPS -> weekSteps
          GoalPeriod.WEEKLY to GoalMetric.CALORIES -> weekCal.toInt()
          GoalPeriod.MONTHLY to GoalMetric.STEPS -> monthSteps
          GoalPeriod.MONTHLY to GoalMetric.CALORIES -> monthCal.toInt()
          else -> 0
        }
      GoalProgress(
        id = goal.id,
        period = period,
        metric = metric,
        targetValue = goal.targetValue,
        currentValue = current,
        isActive = goal.isActive,
      )
    }
  }

  suspend fun computeGoalProgressList(referenceDay: Long = DateUtils.todayEpochDay()): List<GoalProgress> =
    withContext(ioDispatcher) {
      val goals = goalDao.getAllGoalsSync()
      if (goals.isEmpty()) return@withContext emptyList()

      val (weekStart, weekEnd) = DateUtils.weekRange(referenceDay)
      val civil = DateUtils.epochDayToCivil(referenceDay)
      val (monthStart, monthEnd) = DateUtils.monthRange(civil.year, civil.month)
      val rows =
        activityDao.getRangeSync(
          minOf(referenceDay, weekStart, monthStart),
          maxOf(referenceDay, weekEnd, monthEnd),
        )
      mapGoalProgress(goals, rows, referenceDay)
    }

  suspend fun evaluateActiveGoals(referenceDay: Long = DateUtils.todayEpochDay()) =
    withContext(ioDispatcher) {
      val progressList = computeGoalProgressList(referenceDay)
      val civil = DateUtils.epochDayToCivil(referenceDay)
      for (item in progressList) {
        if (!item.isCompleted) continue
        val periodKey =
          when (item.period) {
            GoalPeriod.DAILY -> "D-$referenceDay"
            GoalPeriod.WEEKLY -> DateUtils.isoWeekKey(referenceDay)
            GoalPeriod.MONTHLY -> "M-${DateUtils.yearMonthOf(civil.year, civil.month)}"
          }
        val title = "${item.period.labelTr} ${item.metric.labelTr} Hedefi Tamamlandı!"
        val message =
          "${item.targetValue} ${item.metric.unitTr} hedefine ulaştınız (Mevcut: ${item.currentValue} ${item.metric.unitTr})."
        val insertedId =
          notificationDao.insertIfAbsent(
            NotificationLogEntity(
              goalId = item.id,
              periodKey = periodKey,
              title = title,
              message = message,
              periodType = item.period.name,
              metricType = item.metric.name,
              achievedValue = item.currentValue,
              targetValue = item.targetValue,
              isRead = false,
              createdAt = System.currentTimeMillis(),
            )
          )
        if (insertedId > 0L) {
          _inAppAlerts.tryEmit(
            InAppGoalAlert(
              id = insertedId,
              title = title,
              message = message,
              period = item.period,
              metric = item.metric,
            )
          )
        }
      }
    }

  suspend fun saveGoal(period: GoalPeriod, metric: GoalMetric, targetValue: Int) =
    withContext(ioDispatcher) {
      val cleanTarget = targetValue.coerceAtLeast(50)
      goalDao.upsertGoal(
        GoalEntity(
          periodType = period.name,
          metricType = metric.name,
          targetValue = cleanTarget,
          isActive = true,
        )
      )
      evaluateActiveGoals(DateUtils.todayEpochDay())
    }

  suspend fun toggleGoalActive(goalId: Long, isActive: Boolean) =
    withContext(ioDispatcher) { goalDao.setGoalActive(goalId, isActive) }

  suspend fun deleteGoal(goalId: Long) = withContext(ioDispatcher) { goalDao.deleteGoal(goalId) }

  suspend fun markAllNotificationsRead() =
    withContext(ioDispatcher) { notificationDao.markAllRead() }

  suspend fun submitFeedback(category: FeedbackCategory, rating: Int, plainMessage: String) =
    withContext(ioDispatcher) {
      val trimmed = plainMessage.trim()
      require(trimmed.isNotEmpty()) { "Geri bildirim mesajı boş olamaz." }
      val encrypted = CryptoManager.encryptStringKeystore(trimmed)
      feedbackDao.insert(
        UserFeedbackEntity(
          category = category.name,
          rating = rating.coerceIn(1, 5),
          encryptedMessage = encrypted,
          createdAt = System.currentTimeMillis(),
        )
      )
    }

  suspend fun deleteFeedback(id: Long) = withContext(ioDispatcher) { feedbackDao.deleteById(id) }
}
