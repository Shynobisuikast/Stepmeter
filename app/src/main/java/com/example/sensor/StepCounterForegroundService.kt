package com.example.sensor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.di.StepmeterContainer
import com.example.domain.DateUtils
import com.example.domain.FormatUtils
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Foreground service (foregroundServiceType="health") keeping Android's SensorManager
 * TYPE_STEP_COUNTER / TYPE_STEP_DETECTOR listener active in real-time while the app is in background.
 */
class StepCounterForegroundService : Service() {

  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private var notificationUpdateJob: Job? = null
  private lateinit var container: StepmeterContainer
  private lateinit var openPendingIntent: PendingIntent
  private lateinit var stopPendingIntent: PendingIntent
  private var lastNotifyElapsedMs: Long = 0L

  override fun onCreate() {
    super.onCreate()
    container = StepmeterContainer.getInstance(applicationContext)
    createNotificationChannel()

    val openIntent =
      Intent(this, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
      }
    openPendingIntent =
      PendingIntent.getActivity(
        this,
        0,
        openIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val stopIntent =
      Intent(this, StepCounterForegroundService::class.java).apply {
        action = ACTION_STOP_TRACKING
      }
    stopPendingIntent =
      PendingIntent.getService(
        this,
        1,
        stopIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent?.action == ACTION_STOP_TRACKING) {
      stopTracking()
      return START_NOT_STICKY
    }

    val initialNotification = buildNotification(steps = 0, distanceKm = 0.0, caloriesKcal = 0.0)
    val fgsType =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
      } else {
        0
      }

    ServiceCompat.startForeground(this, NOTIFICATION_ID, initialNotification, fgsType)
    container.isBackgroundServiceRunning.value = true
    container.sensorTracker.start()

    observeTodayStepsForNotification()
    return START_STICKY
  }

  private fun observeTodayStepsForNotification() {
    notificationUpdateJob?.cancel()
    notificationUpdateJob =
      serviceScope.launch {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        container.repository
          .observeDay(DateUtils.todayEpochDay())
          .map { Triple(it?.totalSteps ?: 0, it?.distanceKm ?: 0.0, it?.caloriesKcal ?: 0.0) }
          .distinctUntilChanged()
          .collectLatest { (steps, distance, calories) ->
            val now = SystemClock.elapsedRealtime()
            val sinceLast = now - lastNotifyElapsedMs
            if (lastNotifyElapsedMs != 0L && sinceLast < NOTIFY_THROTTLE_MS) {
              delay(NOTIFY_THROTTLE_MS - sinceLast)
            }
            lastNotifyElapsedMs = SystemClock.elapsedRealtime()
            manager.notify(NOTIFICATION_ID, buildNotification(steps, distance, calories))
          }
      }
  }

  private fun stopTracking() {
    notificationUpdateJob?.cancel()
    container.sensorTracker.stop()
    container.isBackgroundServiceRunning.value = false
    ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    stopSelf()
  }

  override fun onDestroy() {
    notificationUpdateJob?.cancel()
    container.sensorTracker.stop()
    container.isBackgroundServiceRunning.value = false
    serviceScope.cancel()
    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder? = null

  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel =
        NotificationChannel(
            CHANNEL_ID,
            getString(R.string.fgs_channel_name),
            NotificationManager.IMPORTANCE_LOW,
          )
          .apply {
            description = getString(R.string.fgs_channel_desc)
            setShowBadge(false)
          }
      val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      manager.createNotificationChannel(channel)
    }
  }

  private fun buildNotification(steps: Int, distanceKm: Double, caloriesKcal: Double): Notification {
    val formattedSteps = FormatUtils.formatStepsTr(steps)
    val contentText =
      String.format(Locale.US, "%s adım · %.2f km · %.0f kcal", formattedSteps, distanceKm, caloriesKcal)

    return NotificationCompat.Builder(this, CHANNEL_ID)
      .setSmallIcon(R.mipmap.ic_launcher)
      .setContentTitle(getString(R.string.fgs_notification_title))
      .setContentText(contentText)
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .setContentIntent(openPendingIntent)
      .addAction(0, getString(R.string.fgs_action_stop), stopPendingIntent)
      .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
      .build()
  }

  companion object {
    private const val NOTIFY_THROTTLE_MS = 2_000L
    const val CHANNEL_ID = "stepmeter_live_tracking"
    const val NOTIFICATION_ID = 1001
    const val ACTION_START_TRACKING = "com.example.sensor.ACTION_START_TRACKING"
    const val ACTION_STOP_TRACKING = "com.example.sensor.ACTION_STOP_TRACKING"

    fun start(context: Context) {
      val intent =
        Intent(context, StepCounterForegroundService::class.java).apply {
          action = ACTION_START_TRACKING
        }
      ContextCompat.startForegroundService(context, intent)
    }

    fun stop(context: Context) {
      val intent =
        Intent(context, StepCounterForegroundService::class.java).apply {
          action = ACTION_STOP_TRACKING
        }
      context.startService(intent)
    }
  }
}
