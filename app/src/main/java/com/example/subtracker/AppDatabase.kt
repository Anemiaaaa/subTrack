package com.example.subtracker

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [UserEntity::class, SubscriptionEntity::class],
    version = 4
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun subscriptionDao(): SubscriptionDao
}
