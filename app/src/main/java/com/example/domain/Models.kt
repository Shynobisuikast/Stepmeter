package com.example.domain

enum class ThemeMode(val labelTr: String) {
  DARK("Gece Modu"),
  LIGHT("Gündüz Modu"),
  SYSTEM("Sistem"),
}

enum class GoalPeriod(val labelTr: String) {
  DAILY("Günlük"),
  WEEKLY("Haftalık"),
  MONTHLY("Aylık"),
}

enum class GoalMetric(val labelTr: String, val unitTr: String) {
  STEPS("Adım", "adım"),
  CALORIES("Kalori", "kcal"),
}

enum class AnalyticsMetric(val labelTr: String, val unitTr: String) {
  STEPS("Adım", "adım"),
  DISTANCE("Mesafe", "km"),
  CALORIES("Kalori", "kcal"),
}

enum class SharePeriod(val labelTr: String) {
  DAILY("Günlük Özet"),
  WEEKLY("Haftalık Özet"),
}

enum class ShareCardTheme(val labelTr: String) {
  MIDNIGHT_MINT("Gece & Nane"),
  CORAL_PULSE("Mercan Enerji"),
  SKY_HORIZON("Elektrik Mavi"),
}

enum class FeedbackCategory(val labelTr: String) {
  SUGGESTION("Öneri"),
  BUG("Hata Bildirimi"),
  FEATURE("Özellik İsteği"),
  EXPERIENCE("Deneyim"),
}

data class UserProfile(
  val displayName: String = "Yürüyüşçü",
  val heightCm: Int = 172,
  val weightKg: Float = 72f,
  val customStrideCm: Int = 72,
  val useCustomStride: Boolean = false,
)

data class GoalProgress(
  val id: Long,
  val period: GoalPeriod,
  val metric: GoalMetric,
  val targetValue: Int,
  val currentValue: Int,
  val isActive: Boolean,
) {
  val fraction: Float
    get() = if (targetValue <= 0) 0f else (currentValue.toFloat() / targetValue.toFloat()).coerceIn(0f, 1f)

  val percent: Int
    get() = if (targetValue <= 0) 0 else ((currentValue.toFloat() / targetValue.toFloat()) * 100f).toInt()

  val isCompleted: Boolean
    get() = isActive && targetValue > 0 && currentValue >= targetValue
}

data class InAppGoalAlert(
  val id: Long,
  val title: String,
  val message: String,
  val period: GoalPeriod,
  val metric: GoalMetric,
)

data class DailyChartPoint(
  val epochDay: Long,
  val dayNumber: Int,
  val label: String,
  val steps: Int,
  val distanceKm: Double,
  val caloriesKcal: Double,
)

data class LocalBackupInfo(
  val fileName: String,
  val createdAtMillis: Long,
  val sizeBytes: Long,
  val isPasswordProtected: Boolean,
)

data class DecryptedFeedbackItem(
  val id: Long,
  val category: FeedbackCategory,
  val rating: Int,
  val message: String,
  val createdAtMillis: Long,
)
