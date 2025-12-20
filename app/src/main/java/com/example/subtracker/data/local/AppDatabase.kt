package com.example.subtracker.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.subtracker.data.local.dao.PaymentDao
import com.example.subtracker.data.local.dao.PendingActionDao
import com.example.subtracker.data.local.dao.SubscriptionDao
import com.example.subtracker.data.local.entity.PaymentEntity
import com.example.subtracker.data.local.entity.PendingActionEntity
import com.example.subtracker.data.local.entity.SubscriptionEntity

@Database(
    entities = [SubscriptionEntity::class, PaymentEntity::class, PendingActionEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun subscriptions(): SubscriptionDao
    abstract fun payments(): PaymentDao
    abstract fun pendingActions(): PendingActionDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "subtracker.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
