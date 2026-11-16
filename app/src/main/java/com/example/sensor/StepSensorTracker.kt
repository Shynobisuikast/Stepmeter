package com.example.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import com.example.data.PreferencesRepository
import com.example.data.StepRepository
import com.example.domain.DateUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Hardware step counter tracker using Android's SensorManager (TYPE_STEP_COUNTER with
 * TYPE_STEP_DETECTOR fallback). Uses an in-memory accumulator with threshold/time-based batching
 * (flush every 15 steps, every 10s, on day change, or on stop()) to minimize flash I/O and battery drain.
 */
class StepSensorTracker(
  context: Context,
  private val preferences: PreferencesRepository,
  private val repository: StepRepository,
) : SensorEventListener {

  private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
  private val stepCounterSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
  private val stepDetectorSensor: Sensor? =
    if (stepCounterSensor == null) sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR) else null

  val hasHardwareSensor: Boolean = stepCounterSensor != null || stepDetectorSensor != null

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private val stateMutex = Mutex()

  @Volatile private var isRegistered = false
  private val _isListening = MutableStateFlow(false)
  val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

  private var initializedFromDisk = false
  private var cachedDay: Long = -1L
  private var cachedBaseline: Int = -1
  private var cachedLastTotal: Int = -1
  private var cachedTodaySteps: Int = 0
  private var pendingStepsDelta: Int = 0
  private var lastFlushElapsedMs: Long = 0L
  private var hasDirtyState: Boolean = false

  @Synchronized
  fun start(): Boolean {
    if (isRegistered) return true
    val sensor = stepCounterSensor ?: stepDetectorSensor ?: return false
    // maxReportLatencyUs = 0 ensures real-time delivery instead of hardware FIFO batching delay
    isRegistered =
      sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL, 0)
    _isListening.value = isRegistered
    return isRegistered
  }

  @Synchronized
  fun stop() {
    if (!isRegistered) return
    sensorManager.unregisterListener(this)
    isRegistered = false
    _isListening.value = false
    scope.launch(NonCancellable) {
      stateMutex.withLock {
        flushLocked(force = true)
      }
    }
  }

  override fun onSensorChanged(event: SensorEvent) {
    val value = event.values.firstOrNull()?.toInt() ?: return
    val sensorType = event.sensor.type
    scope.launch {
      stateMutex.withLock {
        ensureLoadedLocked()
        val today = DateUtils.todayEpochDay()
        when (sensorType) {
          Sensor.TYPE_STEP_COUNTER -> handleCumulativeCounterLocked(today, value)
          Sensor.TYPE_STEP_DETECTOR -> handleStepDetectorPulseLocked(today, value.coerceAtLeast(1))
        }
      }
    }
  }

  private suspend fun ensureLoadedLocked() {
    if (initializedFromDisk) return
    val (savedDay, savedBaseline, lastTotal) = preferences.getSensorState()
    cachedDay = savedDay
    cachedBaseline = savedBaseline
    cachedLastTotal = lastTotal
    initializedFromDisk = true
  }

  private suspend fun handleStepDetectorPulseLocked(today: Long, increment: Int) {
    val dayRolledOver = cachedDay != today || cachedLastTotal < 0
    cachedDay = today
    cachedBaseline = 0
    val updated = if (dayRolledOver) increment else cachedLastTotal + increment
    cachedLastTotal = updated
    cachedTodaySteps = updated
    pendingStepsDelta += increment
    hasDirtyState = true
    flushLocked(force = dayRolledOver || lastFlushElapsedMs == 0L)
  }

  private suspend fun handleCumulativeCounterLocked(today: Long, cumulativeSinceBoot: Int) {
    val dayChanged = cachedDay != today || cachedBaseline < 0
    val newBaseline: Int
    val todaySteps: Int

    when {
      dayChanged -> {
        newBaseline = cumulativeSinceBoot
        todaySteps = 0
      }
      cumulativeSinceBoot < cachedLastTotal -> {
        // Device rebooted mid-day; preserve steps already accumulated today
        val accumulatedBeforeReboot = (cachedLastTotal - cachedBaseline).coerceAtLeast(0)
        newBaseline = cumulativeSinceBoot - accumulatedBeforeReboot
        todaySteps = accumulatedBeforeReboot
      }
      else -> {
        newBaseline = cachedBaseline
        todaySteps = (cumulativeSinceBoot - cachedBaseline).coerceAtLeast(0)
      }
    }

    val delta = (todaySteps - cachedTodaySteps).coerceAtLeast(0)
    cachedDay = today
    cachedBaseline = newBaseline
    cachedLastTotal = cumulativeSinceBoot
    cachedTodaySteps = todaySteps
    pendingStepsDelta += delta.coerceAtLeast(if (dayChanged) 1 else 0)
    hasDirtyState = true

    flushLocked(force = dayChanged || lastFlushElapsedMs == 0L)
  }

  private suspend fun flushLocked(force: Boolean) {
    if (!hasDirtyState || cachedDay < 0) return
    val now = SystemClock.elapsedRealtime()
    val elapsed = now - lastFlushElapsedMs
    if (force || pendingStepsDelta >= FLUSH_STEP_THRESHOLD || elapsed >= FLUSH_INTERVAL_MS) {
      pendingStepsDelta = 0
      lastFlushElapsedMs = now
      hasDirtyState = false
      preferences.saveSensorState(cachedDay, cachedBaseline, cachedLastTotal)
      repository.updateSensorSteps(cachedDay, cachedTodaySteps)
    }
  }

  override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

  companion object {
    private const val FLUSH_STEP_THRESHOLD = 15
    private const val FLUSH_INTERVAL_MS = 10_000L
  }
}
