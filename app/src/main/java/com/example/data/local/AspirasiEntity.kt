package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.AspirasiItem
import com.example.data.model.AspirasiSheetColumns
import com.example.util.AspirasiReplyCodec

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
    val penanggapNama: String = "",
    val penanggapUsername: String = "",
    val penanggapNamaAsli: String = "",
    val penanggapPeran: String = "",
    val waktuTanggapan: String = "",
    val lastModified: Long = System.currentTimeMillis(),
    val syncStatus: String = SYNC_STATUS_SYNCED
) {
    fun toAspirasiItem(): AspirasiItem {
        val parsedMeta = AspirasiReplyCodec.parseAndStrip(tanggapanPengurus)
        return AspirasiItem(
            id = id,
            tanggal = tanggal,
            nama = nama,
            kategori = kategori.ifBlank { "Masukan/Saran" },
            pesan = pesan,
            fotoMasalah = AspirasiSheetColumns.splitPhotoSlots(fotoMasalah).joinToString(","),
            status = status.ifBlank { "Menunggu" },
            penanggapNama = penanggapNama.ifBlank { parsedMeta.penanggapNama },
            tanggapanPengurus = parsedMeta.cleanTanggapan,
            fotoPengerjaan = AspirasiSheetColumns.splitPhotoSlots(fotoPengerjaan).joinToString(","),
            noRumah = noRumah,
            sembunyikanNama = sembunyikanNama,
            penanggapUsername = penanggapUsername,
            penanggapNamaAsli = penanggapNamaAsli,
            penanggapPeran = penanggapPeran.ifBlank { parsedMeta.penanggapPeran },
            waktuTanggapan = waktuTanggapan.ifBlank { parsedMeta.waktuTanggapan }
        )
    }

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
            kategori = item.kategori.ifBlank { "Masukan/Saran" },
            pesan = item.pesan,
            fotoMasalah = AspirasiSheetColumns.splitPhotoSlots(item.fotoMasalah).joinToString(","),
            status = item.status.ifBlank { "Menunggu" },
            tanggapanPengurus = AspirasiReplyCodec.stripMarker(item.tanggapanPengurus),
            fotoPengerjaan = AspirasiSheetColumns.splitPhotoSlots(item.fotoPengerjaan).joinToString(","),
            penanggapNama = item.penanggapNama,
            penanggapUsername = item.penanggapUsername,
            penanggapNamaAsli = item.penanggapNamaAsli,
            penanggapPeran = item.penanggapPeran,
            waktuTanggapan = item.waktuTanggapan,
            syncStatus = status
        )
    }
}
