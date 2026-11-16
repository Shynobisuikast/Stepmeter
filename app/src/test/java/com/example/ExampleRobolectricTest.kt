package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.BackupManager
import com.example.data.PreferencesRepository
import com.example.data.StepRepository
import com.example.di.StepmeterContainer
import com.example.domain.DateUtils
import com.example.domain.FeedbackCategory
import com.example.domain.GoalMetric
import com.example.domain.GoalPeriod
import com.example.domain.MetricsCalculator
import com.example.domain.UserProfile
import com.example.security.CryptoManager
import com.example.sensor.StepCounterForegroundService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.robolectric.Robolectric
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  private lateinit var context: Context
  private lateinit var db: AppDatabase
  private lateinit var preferences: PreferencesRepository
  private lateinit var repository: StepRepository

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    preferences = PreferencesRepository(context)
    repository = StepRepository(db, preferences)
  }

  @After
  fun tearDown() {
    db.close()
  }

  @Test
  fun `app name matches localized title`() {
    assertEquals("Stepmeter", context.getString(R.string.app_name))
  }

  @Test
  fun `metrics calculator computes distance and calories accurately`() {
    val profile = UserProfile(heightCm = 175, weightKg = 70f, useCustomStride = false)
    val distance = MetricsCalculator.calculateDistanceKm(10_000, profile)
    val calories = MetricsCalculator.calculateCaloriesKcal(10_000, profile)
    assertTrue(distance in 7.0..7.5)
    assertTrue(calories in 380.0..420.0)
  }

  @Test
  fun `date utils round trips civil dates and month bounds`() {
    val epoch = DateUtils.civilToEpochDay(2026, 11, 16)
    val civil = DateUtils.epochDayToCivil(epoch)
    assertEquals(2026, civil.year)
    assertEquals(11, civil.month)
    assertEquals(16, civil.day)
    assertEquals(202611, DateUtils.epochDayToYearMonth(epoch))
    assertEquals(30, DateUtils.daysInMonth(2026, 11))
    assertEquals(29, DateUtils.daysInMonth(2024, 2))
  }

  @Test
  fun `crypto manager encrypts and decrypts with password and rejects wrong password`() {
    val secret = "ADIMIZI_TEST_CSV,2026,5000".toByteArray(Charsets.UTF_8)
    val encrypted = CryptoManager.encryptWithPassword(secret, "GucluParola123")
    assertTrue(CryptoManager.isPasswordProtectedPayload(encrypted))
    val decrypted = CryptoManager.decryptWithPassword(encrypted, "GucluParola123")
    assertEquals("ADIMIZI_TEST_CSV,2026,5000", String(decrypted, Charsets.UTF_8))

    var failed = false
    try {
      CryptoManager.decryptWithPassword(encrypted, "YanlisParola")
    } catch (_: Exception) {
      failed = true
    }
    assertTrue(failed)
  }

  @Test
  fun `step repository triggers single in-app notification per completed goal period`() = runBlocking {
    val today = DateUtils.todayEpochDay()
    repository.saveGoal(GoalPeriod.DAILY, GoalMetric.STEPS, 2000)

    repository.updateSensorSteps(today, 1200)
    assertEquals(0, db.notificationLogDao().observeUnreadCount().first())

    repository.updateSensorSteps(today, 2200)
    assertEquals(1, db.notificationLogDao().observeUnreadCount().first())

    // Updating with more sensor steps on the same day should not duplicate the daily notification
    repository.updateSensorSteps(today, 2700)
    assertEquals(1, db.notificationLogDao().observeUnreadCount().first())
  }

  @Test
  fun `backup manager exports gzip encrypted CSV to device storage and restores cleanly`() = runBlocking {
    val today = DateUtils.todayEpochDay()
    repository.saveGoal(GoalPeriod.DAILY, GoalMetric.STEPS, 6000)
    repository.updateSensorSteps(today, 4500)
    repository.submitFeedback(FeedbackCategory.SUGGESTION, 5, "Harika yerel uygulama!")

    val feedbackList = repository.observeFeedbacks().first()
    assertEquals(1, feedbackList.size)
    assertEquals("Harika yerel uygulama!", feedbackList.first().message)

    val backupManager = BackupManager(context, db, preferences)
    val backupInfo = backupManager.createLocalEncryptedBackup("TestParola99")
    assertTrue(backupInfo.isPasswordProtected)
    assertFalse(backupInfo.fileName.isBlank())

    // Wipe tables and restore from the compressed & encrypted local CSV file
    db.dailyActivityDao().clearAll()
    db.goalDao().clearAll()
    assertEquals(0, db.dailyActivityDao().getAllSync().size)

    val result = backupManager.restoreFromLocalFile(backupInfo.fileName, "TestParola99")
    assertEquals(1, result.restoredActivitiesCount)
    assertEquals(1, result.restoredGoalsCount)
    assertEquals(4500, db.dailyActivityDao().getDaySync(today)?.totalSteps)
  }

  @Test
  fun `foreground service toggles background tracking state cleanly`() {
    val controller = Robolectric.buildService(StepCounterForegroundService::class.java)
    val service = controller.create().get()
    val container = StepmeterContainer.getInstance(context)

    service.onStartCommand(
      android.content.Intent(context, StepCounterForegroundService::class.java).apply {
        action = StepCounterForegroundService.ACTION_START_TRACKING
      },
      0,
      1,
    )
    assertTrue(container.isBackgroundServiceRunning.value)

    service.onStartCommand(
      android.content.Intent(context, StepCounterForegroundService::class.java).apply {
        action = StepCounterForegroundService.ACTION_STOP_TRACKING
      },
      0,
      2,
    )
    assertFalse(container.isBackgroundServiceRunning.value)
  }
}
