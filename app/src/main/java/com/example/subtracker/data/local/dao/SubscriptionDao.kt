package com.example.subtracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.subtracker.data.local.entity.SubscriptionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SubscriptionDao {

    @Query("SELECT * FROM subscriptions WHERE familyCode = :familyCode ORDER BY nextPaymentDate ASC")
    fun observeFamilySubscriptions(familyCode: String): Flow<List<SubscriptionEntity>>

    @Query("SELECT * FROM subscriptions WHERE familyCode = :familyCode AND ownerUid = :ownerUid ORDER BY nextPaymentDate ASC")
    fun observeUserSubscriptions(familyCode: String, ownerUid: String): Flow<List<SubscriptionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<SubscriptionEntity>)

    @Query("DELETE FROM subscriptions WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM subscriptions WHERE familyCode = :familyCode AND id NOT IN (:keepIds)")
    suspend fun deleteMissingForFamily(familyCode: String, keepIds: List<String>)

    @Query("SELECT * FROM subscriptions WHERE familyCode = :familyCode")
    suspend fun getFamilyOnce(familyCode: String): List<SubscriptionEntity>
}
