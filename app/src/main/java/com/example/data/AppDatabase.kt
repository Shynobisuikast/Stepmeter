package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
  entities = [
    DailyActivityEntity::class,
    GoalEntity::class,
    NotificationLogEntity::class,
    UserFeedbackEntity::class,
  ],
  version = 2,
  exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
  abstract fun dailyActivityDao(): DailyActivityDao
  abstract fun goalDao(): GoalDao
  abstract fun notificationLogDao(): NotificationLogDao
  abstract fun userFeedbackDao(): UserFeedbackDao

  companion object {
    @Volatile private var instance: AppDatabase? = null

    fun getInstance(context: Context): AppDatabase {
      return instance
        ?: synchronized(this) {
          instance
            ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "stepmeter_local.db",
              )
              .fallbackToDestructiveMigration(true)
              .build()
              .also { instance = it }
        }
    }
  }
}
