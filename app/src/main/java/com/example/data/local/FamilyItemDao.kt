package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FamilyItemDao {

    @Query("SELECT * FROM cached_family_items WHERE familyId = :familyId AND (pendingSyncAction IS NULL OR pendingSyncAction != 'DELETE') ORDER BY updatedAtMillis DESC")
    fun observeItemsForFamily(familyId: String): Flow<List<FamilyItemEntity>>

    @Query("SELECT COUNT(*) FROM cached_family_items WHERE familyId = :familyId AND pendingSyncAction IS NOT NULL")
    fun observePendingCount(familyId: String): Flow<Int>

    @Query("SELECT * FROM cached_family_items WHERE familyId = :familyId AND pendingSyncAction IS NOT NULL")
    suspend fun getPendingSyncItems(familyId: String): List<FamilyItemEntity>

    @Query("SELECT id FROM cached_family_items WHERE familyId = :familyId AND pendingSyncAction IS NOT NULL")
    suspend fun getPendingItemIds(familyId: String): List<String>

    @Query("SELECT * FROM cached_family_items WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): FamilyItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: FamilyItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<FamilyItemEntity>)

    @Query("DELETE FROM cached_family_items WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM cached_family_items WHERE familyId = :familyId AND pendingSyncAction IS NULL AND id NOT IN (:activeIds)")
    suspend fun deleteStaleSyncedItems(familyId: String, activeIds: List<String>)

    @Query("DELETE FROM cached_family_items WHERE familyId = :familyId AND pendingSyncAction IS NULL")
    suspend fun clearSyncedItemsForFamily(familyId: String)
}
