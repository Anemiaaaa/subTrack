package com.example.subtracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.subtracker.data.local.entity.PendingActionEntity

@Dao
interface PendingActionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(action: PendingActionEntity)

    @Query("SELECT * FROM pending_actions ORDER BY createdAt ASC LIMIT :limit")
    suspend fun getNext(limit: Int): List<PendingActionEntity>

    @Query("DELETE FROM pending_actions WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("SELECT COUNT(*) FROM pending_actions")
    suspend fun count(): Int

    // ✅ нужно для logout / сброса очереди
    @Query("DELETE FROM pending_actions")
    suspend fun deleteAll()
}
