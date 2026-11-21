package com.example.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface KasDao {
    @Query("SELECT * FROM kas_rt WHERE syncStatus != 'PENDING_DELETE' ORDER BY tanggal ASC, id ASC")
    fun getAllKasFlow(): Flow<List<KasEntity>>

    @Query("SELECT * FROM kas_rt WHERE syncStatus != 'PENDING_DELETE' ORDER BY tanggal ASC, id ASC")
    suspend fun getAllKasList(): List<KasEntity>

    @Query("SELECT * FROM kas_rt WHERE syncStatus IN ('PENDING_UPLOAD', 'PENDING_DELETE')")
    suspend fun getPendingSyncKas(): List<KasEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKas(item: KasEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKasList(items: List<KasEntity>)

    @Query("UPDATE kas_rt SET syncStatus = 'PENDING_DELETE' WHERE id = :id")
    suspend fun markKasPendingDelete(id: String)

    @Query("DELETE FROM kas_rt WHERE id = :id")
    suspend fun deleteKasById(id: String)

    @Query("DELETE FROM kas_rt")
    suspend fun clearAllKas()
}
