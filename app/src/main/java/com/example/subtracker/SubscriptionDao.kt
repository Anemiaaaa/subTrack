package com.example.subtracker

import androidx.room.*

@Dao
interface SubscriptionDao {
    @Insert
    suspend fun insert(subscription: SubscriptionEntity)

    @Query("SELECT * FROM subscriptions WHERE familyCode = :familyCode")
    suspend fun getByFamily(familyCode: String): List<SubscriptionEntity>

    @Delete
    suspend fun delete(subscription: SubscriptionEntity)

    @Query("SELECT * FROM subscriptions WHERE id = :userId")
    fun getSubscriptionsByUser(userId: Int): List<SubscriptionEntity>

    @Query("SELECT * FROM subscriptions WHERE familyCode = :code")
    fun getSubscriptionsByFamily(code: String): List<SubscriptionEntity>


}
