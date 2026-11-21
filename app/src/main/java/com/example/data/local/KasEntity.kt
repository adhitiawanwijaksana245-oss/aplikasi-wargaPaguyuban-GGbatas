package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.KasItem

@Entity(tableName = "kas_rt")
data class KasEntity(
    @PrimaryKey val id: String,
    val tanggal: String,
    val periodeBulan: String = "",
    val kategori: String = "",
    val keterangan: String,
    val masuk: Long,
    val keluar: Long,
    val saldo: Long = 0L,
    val jumlah: Long = 0L,
    val buktiFoto: String = "",
    val lastModified: Long = System.currentTimeMillis(),
    val syncStatus: String = SYNC_STATUS_SYNCED
) {
    fun toKasItem(): KasItem {
        val resolvedSaldo = if (saldo != 0L || jumlah == 0L) saldo else jumlah
        return KasItem(
            id = id,
            tanggal = tanggal,
            periodeBulan = periodeBulan,
            kategori = kategori,
            keterangan = keterangan,
            masuk = masuk,
            keluar = keluar,
            saldo = resolvedSaldo,
            jumlah = resolvedSaldo,
            buktiFoto = buktiFoto
        )
    }

    companion object {
        const val SYNC_STATUS_SYNCED = "SYNCED"
        const val SYNC_STATUS_PENDING_UPLOAD = "PENDING_UPLOAD"
        const val SYNC_STATUS_PENDING_DELETE = "PENDING_DELETE"

        fun fromKasItem(item: KasItem, status: String = SYNC_STATUS_SYNCED): KasEntity = KasEntity(
            id = item.id,
            tanggal = item.tanggal,
            periodeBulan = item.periodeBulan,
            kategori = item.kategori,
            keterangan = item.keterangan,
            masuk = item.masuk,
            keluar = item.keluar,
            saldo = item.effectiveSaldo,
            jumlah = item.effectiveSaldo,
            buktiFoto = item.buktiFoto,
            syncStatus = status
        )
    }
}
