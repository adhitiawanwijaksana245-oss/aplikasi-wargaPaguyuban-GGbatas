package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

enum class UserRole {
    WARGA,
    PENGURUS,
    ADMIN_UTAMA
}

@JsonClass(generateAdapter = true)
data class KasItem(
    @Json(name = "id") val id: String = "",
    @Json(name = "tanggal") val tanggal: String = "",
    @Json(name = "periode_bulan") val periodeBulan: String = "",
    @Json(name = "kategori") val kategori: String = "",
    @Json(name = "keterangan") val keterangan: String = "",
    @Json(name = "masuk") val masuk: Long = 0L,
    @Json(name = "keluar") val keluar: Long = 0L,
    @Json(name = "saldo") val saldo: Long = 0L,
    @Json(name = "jumlah") val jumlah: Long = 0L,
    @Json(name = "bukti_foto") val buktiFoto: String = ""
) {
    val effectiveSaldo: Long
        get() = if (saldo != 0L || jumlah == 0L) saldo else jumlah
}

@JsonClass(generateAdapter = true)
data class KasMonthlyRecap(
    val periodeBulan: String = "",
    val labelBulan: String = "",
    val totalMasuk: Long = 0L,
    val totalKeluar: Long = 0L,
    val arusKas: Long = 0L,
    val saldoAkhir: Long = 0L,
    val jumlahTransaksi: Int = 0
)

@JsonClass(generateAdapter = true)
data class KasSummaryTotals(
    val saldoKas: Long = 0L,
    val totalMasuk: Long = 0L,
    val totalKeluar: Long = 0L
)

@JsonClass(generateAdapter = true)
data class PengurusItem(
    @Json(name = "ID") val id: String = "",
    @Json(name = "Jabatan") val jabatan: String = "",
    @Json(name = "Nama") val nama: String = "",
    @Json(name = "No HP") val noHp: String = "",
    @Json(name = "Alamat") val alamat: String = "",
    @Json(name = "Masa_Bakti") val masaBakti: String = "",
    @Json(name = "Foto") val foto: String = ""
)

enum class ReminderMode {
    BEFORE_EVENT,
    FROM_NOW
}

@JsonClass(generateAdapter = true)
data class UndanganItem(
    @Json(name = "ID") val id: String = "",
    @Json(name = "Judul") val judul: String = "",
    @Json(name = "Tanggal") val tanggal: String = "",
    @Json(name = "Waktu") val waktu: String = "",
    @Json(name = "Lokasi") val lokasi: String = "",
    @Json(name = "Keterangan") val keterangan: String = "",
    @Json(name = "Tipe") val tipe: String = "Undangan",
    @Json(name = "Pengingat_Menit") val pengingatMenit: Int = 0,
    @Json(name = "Pengingat_Aktif") val pengingatAktif: Boolean = false,
    @Json(name = "Pengingat_Target_Millis") val pengingatTargetMillis: Long = 0L,
    @Json(name = "Pengingat_Aktif_Pada") val pengingatActivatedMillis: Long = 0L,
    @Json(name = "Foto") val foto: String = ""
) {
    val isPengumuman: Boolean
        get() = tipe.equals("Pengumuman", ignoreCase = true) || judul.startsWith("[Pengumuman]", ignoreCase = true)

    val cleanJudul: String
        get() = judul.replace(Regex("^\\[(Pengumuman|Undangan)\\]\\s*", RegexOption.IGNORE_CASE), "")

    val cleanKeterangan: String
        get() = com.example.util.ReminderCodec.stripMarker(keterangan)

    val effectiveReminderMenit: Int
        get() = if (pengingatMenit > 0) pengingatMenit else (com.example.util.ReminderCodec.parseMarker(keterangan)?.minutes ?: 0)

    val effectiveReminderTargetMillis: Long
        get() = if (pengingatTargetMillis > 0L) pengingatTargetMillis else (com.example.util.ReminderCodec.parseMarker(keterangan)?.targetMillis ?: 0L)

    val effectiveReminderActivatedMillis: Long
        get() = if (pengingatActivatedMillis > 0L) pengingatActivatedMillis else (com.example.util.ReminderCodec.parseMarker(keterangan)?.activatedMillis ?: 0L)

    val isReminderActive: Boolean
        get() = !isPengumuman && effectiveReminderMenit > 0 && effectiveReminderTargetMillis > 0L && (pengingatAktif || com.example.util.ReminderCodec.parseMarker(keterangan) != null)
}

@JsonClass(generateAdapter = true)
data class GaleriItem(
    @Json(name = "ID") val id: String = "",
    @Json(name = "Tanggal") val tanggal: String = "",
    @Json(name = "Judul") val judul: String = "",
    @Json(name = "Kategori") val kategori: String = "",
    @Json(name = "Keterangan") val keterangan: String = "",
    @Json(name = "Foto") val foto: String = "",
    @Json(name = "Petugas") val petugas: String = ""
)

@JsonClass(generateAdapter = true)
data class AspirasiItem(
    @Json(name = "ID") val id: String = "",
    @Json(name = "Tanggal") val tanggal: String = "",
    @Json(name = "Nama") val nama: String = "",
    @Json(name = "No_Rumah") val noRumah: String = "",
    @Json(name = "Sembunyikan_Nama") val sembunyikanNama: Boolean = false,
    @Json(name = "Kategori") val kategori: String = "Masukan/Saran", // "Masukan/Saran" or "Kritik"
    @Json(name = "Pesan") val pesan: String = "",
    @Json(name = "Foto_Masalah") val fotoMasalah: String = "",
    @Json(name = "Status") val status: String = "Menunggu", // "Menunggu", "Diproses", "Selesai"
    @Json(name = "Tanggapan_Pengurus") val tanggapanPengurus: String = "",
    @Json(name = "Foto_Pengerjaan") val fotoPengerjaan: String = ""
)

@JsonClass(generateAdapter = true)
data class SheetDataResponse(
    val kas: List<KasItem>? = null,
    val rolling12: List<KasMonthlyRecap>? = null,
    val rekapBulanan: List<KasMonthlyRecap>? = null,
    val kasSummary: KasSummaryTotals? = null,
    val pengurus: List<PengurusItem>? = null,
    val undangan: List<UndanganItem>? = null,
    val galeri: List<GaleriItem>? = null,
    val aspirasi: List<AspirasiItem>? = null,
    val status: String? = null,
    val message: String? = null,
    val hasUnreadableImageFormula: Boolean = false,
    val scriptVersion: Int = 1,
    val driveAuthorized: Boolean = false
)

data class CloudSaveResult(
    val localSaved: Boolean,
    val photoRequested: Boolean,
    val photoUploadedToCloud: Boolean,
    val sheetSynced: Boolean,
    val message: String
)

@JsonClass(generateAdapter = true)
data class SheetActionRequest(
    val action: String, // "add", "edit", "delete", "respond_aspirasi"
    val sheet: String,  // "Kas_RT", "Pengurus", "Undangan", "Galeri", "Aspirasi"
    val password: String = "",
    val kasData: KasItem? = null,
    val pengurusData: PengurusItem? = null,
    val undanganData: UndanganItem? = null,
    val galeriData: GaleriItem? = null,
    val aspirasiData: AspirasiItem? = null,
    val targetId: String? = null
)
