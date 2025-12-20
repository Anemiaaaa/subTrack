package com.example.subtracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.subtracker.data.local.entity.PaymentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PaymentDao {

    @Query("SELECT * FROM payments WHERE familyCode = :familyCode ORDER BY paidAt DESC")
    fun observeFamilyPayments(familyCode: String): Flow<List<PaymentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<PaymentEntity>)

    @Query("DELETE FROM payments WHERE familyCode = :familyCode AND id NOT IN (:keepIds)")
    suspend fun deleteMissingForFamily(familyCode: String, keepIds: List<String>)
}
