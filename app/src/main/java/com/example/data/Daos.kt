package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyActivityDao {
  @Query("SELECT * FROM daily_activities WHERE epochDay = :epochDay LIMIT 1")
  fun observeDay(epochDay: Long): Flow<DailyActivityEntity?>

  @Query("SELECT * FROM daily_activities WHERE epochDay = :epochDay LIMIT 1")
  suspend fun getDaySync(epochDay: Long): DailyActivityEntity?

  @Query("SELECT * FROM daily_activities WHERE yearMonth = :yearMonth ORDER BY epochDay ASC")
  fun observeMonth(yearMonth: Int): Flow<List<DailyActivityEntity>>

  @Query("SELECT * FROM daily_activities WHERE epochDay BETWEEN :startDay AND :endDay ORDER BY epochDay ASC")
  fun observeRange(startDay: Long, endDay: Long): Flow<List<DailyActivityEntity>>

  @Query("SELECT * FROM daily_activities WHERE epochDay BETWEEN :startDay AND :endDay ORDER BY epochDay ASC")
  suspend fun getRangeSync(startDay: Long, endDay: Long): List<DailyActivityEntity>

  @Query("SELECT * FROM daily_activities ORDER BY epochDay ASC")
  suspend fun getAllSync(): List<DailyActivityEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(activity: DailyActivityEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertAll(activities: List<DailyActivityEntity>)

  @Query("DELETE FROM daily_activities")
  suspend fun clearAll()
}

@Dao
interface GoalDao {
  @Query("SELECT * FROM goals ORDER BY isActive DESC, periodType ASC, createdAt DESC")
  fun observeAllGoals(): Flow<List<GoalEntity>>

  @Query("SELECT * FROM goals WHERE isActive = 1")
  suspend fun getActiveGoalsSync(): List<GoalEntity>

  @Query("SELECT * FROM goals ORDER BY id ASC")
  suspend fun getAllGoalsSync(): List<GoalEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertGoal(goal: GoalEntity): Long

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertAll(goals: List<GoalEntity>)

  @Query("UPDATE goals SET isActive = :isActive WHERE id = :id")
  suspend fun setGoalActive(id: Long, isActive: Boolean)

  @Query("DELETE FROM goals WHERE id = :id")
  suspend fun deleteGoal(id: Long)

  @Query("DELETE FROM goals")
  suspend fun clearAll()
}

@Dao
interface NotificationLogDao {
  @Query("SELECT * FROM notification_logs ORDER BY createdAt DESC LIMIT 100")
  fun observeNotifications(): Flow<List<NotificationLogEntity>>

  @Query("SELECT COUNT(*) FROM notification_logs WHERE isRead = 0")
  fun observeUnreadCount(): Flow<Int>

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insertIfAbsent(notification: NotificationLogEntity): Long

  @Query("UPDATE notification_logs SET isRead = 1 WHERE isRead = 0")
  suspend fun markAllRead()

  @Query("UPDATE notification_logs SET isRead = 1 WHERE id = :id")
  suspend fun markRead(id: Long)

  @Query("DELETE FROM notification_logs")
  suspend fun clearAll()
}

@Dao
interface UserFeedbackDao {
  @Query("SELECT * FROM user_feedback ORDER BY createdAt DESC")
  fun observeAllFeedback(): Flow<List<UserFeedbackEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(feedback: UserFeedbackEntity): Long

  @Query("DELETE FROM user_feedback WHERE id = :id")
  suspend fun deleteById(id: Long)
}
