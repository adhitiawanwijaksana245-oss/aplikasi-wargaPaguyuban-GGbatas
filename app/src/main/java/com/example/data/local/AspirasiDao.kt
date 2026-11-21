package com.example.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AspirasiDao {
    @Query("SELECT * FROM aspirasi WHERE syncStatus != 'PENDING_DELETE' ORDER BY tanggal DESC, id DESC")
    fun getAllAspirasiFlow(): Flow<List<AspirasiEntity>>

    @Query("SELECT * FROM aspirasi WHERE syncStatus != 'PENDING_DELETE' ORDER BY tanggal DESC, id DESC")
    suspend fun getAllAspirasiList(): List<AspirasiEntity>

    @Query("SELECT * FROM aspirasi WHERE syncStatus IN ('PENDING_UPLOAD', 'PENDING_DELETE')")
    suspend fun getPendingSyncAspirasi(): List<AspirasiEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAspirasi(item: AspirasiEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAspirasiList(items: List<AspirasiEntity>)

    @Query("UPDATE aspirasi SET syncStatus = 'PENDING_DELETE' WHERE id = :id")
    suspend fun markAspirasiPendingDelete(id: String)

    @Query("DELETE FROM aspirasi WHERE id = :id")
    suspend fun deleteAspirasiById(id: String)

    @Query("DELETE FROM aspirasi")
    suspend fun clearAllAspirasi()
}
