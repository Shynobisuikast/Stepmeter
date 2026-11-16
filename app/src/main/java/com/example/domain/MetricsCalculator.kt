package com.example.domain

import kotlin.math.roundToInt

/**
 * Pure physiological calculation engine for walking distance, calorie burn, and active minutes.
 * Runs off the main thread when aggregating batches.
 */
object MetricsCalculator {
  /**
   * Estimates stride length in meters from height (default ~0.415 * height_m).
   */
  fun effectiveStrideMeters(profile: UserProfile): Double {
    val cm =
      if (profile.useCustomStride && profile.customStrideCm in 40..130) {
        profile.customStrideCm.toDouble()
      } else {
        (profile.heightCm.coerceIn(120, 230) * 0.415)
      }
    return cm / 100.0
  }

  fun calculateDistanceKm(steps: Int, profile: UserProfile): Double {
    if (steps <= 0) return 0.0
    val meters = steps * effectiveStrideMeters(profile)
    return meters / 1000.0
  }

  /**
   * Walking calorie expenditure using weight-calibrated energy cost (~0.75 kcal per kg per km)
   * which matches ~3.5 MET brisk walking at ~4.8 km/h.
   */
  fun calculateCaloriesKcal(steps: Int, profile: UserProfile): Double {
    if (steps <= 0) return 0.0
    val distanceKm = calculateDistanceKm(steps, profile)
    val weightKg = profile.weightKg.coerceIn(30f, 220f).toDouble()
    return distanceKm * weightKg * 0.78
  }

  fun estimateActiveMinutes(steps: Int): Int {
    if (steps <= 0) return 0
    // Average cadence ~100 steps/min
    return (steps / 100.0).roundToInt().coerceAtLeast(1)
  }
}
