package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
  tableName = "daily_activities",
  indices = [
    Index(value = ["epochDay"], unique = true),
    Index(value = ["yearMonth"]),
    Index(value = ["updatedAt"]),
  ],
)
data class DailyActivityEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0L,
  val epochDay: Long,
  val yearMonth: Int,
  val totalSteps: Int = 0,
  val distanceKm: Double = 0.0,
  val caloriesKcal: Double = 0.0,
  val activeMinutes: Int = 0,
  val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
  tableName = "goals",
  indices = [
    Index(value = ["periodType", "metricType"]),
    Index(value = ["isActive"]),
  ],
)
data class GoalEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0L,
  val periodType: String,
  val metricType: String,
  val targetValue: Int,
  val isActive: Boolean = true,
  val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
  tableName = "notification_logs",
  indices = [
    Index(value = ["isRead", "createdAt"]),
    Index(value = ["goalId", "periodKey"], unique = true),
  ],
)
data class NotificationLogEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0L,
  val goalId: Long,
  val periodKey: String,
  val title: String,
  val message: String,
  val periodType: String,
  val metricType: String,
  val achievedValue: Int,
  val targetValue: Int,
  val isRead: Boolean = false,
  val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
  tableName = "user_feedback",
  indices = [Index(value = ["createdAt"])],
)
data class UserFeedbackEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0L,
  val category: String,
  val rating: Int,
  val encryptedMessage: String,
  val createdAt: Long = System.currentTimeMillis(),
)
