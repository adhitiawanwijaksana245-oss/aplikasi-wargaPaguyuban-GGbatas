package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.AspirasiItem

@Entity(tableName = "aspirasi")
data class AspirasiEntity(
    @PrimaryKey val id: String,
    val tanggal: String,
    val nama: String,
    val noRumah: String = "",
    val sembunyikanNama: Boolean = false,
    val kategori: String = "Masukan/Saran",
    val pesan: String,
    val fotoMasalah: String = "",
    val status: String = "Menunggu",
    val tanggapanPengurus: String = "",
    val fotoPengerjaan: String = "",
    val lastModified: Long = System.currentTimeMillis(),
    val syncStatus: String = SYNC_STATUS_SYNCED
) {
    fun toAspirasiItem(): AspirasiItem = AspirasiItem(
        id = id,
        tanggal = tanggal,
        nama = nama,
        noRumah = noRumah,
        sembunyikanNama = sembunyikanNama,
        kategori = kategori,
        pesan = pesan,
        fotoMasalah = fotoMasalah,
        status = status,
        tanggapanPengurus = tanggapanPengurus,
        fotoPengerjaan = fotoPengerjaan
    )

    companion object {
        const val SYNC_STATUS_SYNCED = "SYNCED"
        const val SYNC_STATUS_PENDING_UPLOAD = "PENDING_UPLOAD"
        const val SYNC_STATUS_PENDING_DELETE = "PENDING_DELETE"

        fun fromAspirasiItem(item: AspirasiItem, status: String = SYNC_STATUS_SYNCED): AspirasiEntity = AspirasiEntity(
            id = item.id,
            tanggal = item.tanggal,
            nama = item.nama,
            noRumah = item.noRumah,
            sembunyikanNama = item.sembunyikanNama,
            kategori = item.kategori,
            pesan = item.pesan,
            fotoMasalah = item.fotoMasalah,
            status = item.status,
            tanggapanPengurus = item.tanggapanPengurus,
            fotoPengerjaan = item.fotoPengerjaan,
            syncStatus = status
        )
    }
}
