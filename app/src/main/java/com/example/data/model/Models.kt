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
data class PengurusAccount(
    val id: String = "",
    val namaAsli: String = "",
    val username: String = "",
    val password: String = "",
    val aktif: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

object AspirasiSheetColumns {
    const val COL_ID = 0
    const val COL_TANGGAL = 1
    const val COL_NAMA = 2
    const val COL_KATEGORI = 3
    const val COL_PESAN = 4
    const val COL_FOTO_MASALAH_1 = 5
    const val COL_FOTO_MASALAH_2 = 6
    const val COL_FOTO_MASALAH_3 = 7
    const val COL_FOTO_MASALAH_4 = 8
    const val COL_FOTO_MASALAH_5 = 9
    const val COL_STATUS = 10
    const val COL_NAMA_PENGURUS = 11
    const val COL_TANGGAPAN_PENGURUS = 12
    const val COL_FOTO_PENGERJAAN_1 = 13
    const val COL_FOTO_PENGERJAAN_2 = 14
    const val COL_FOTO_PENGERJAAN_3 = 15
    const val COL_FOTO_PENGERJAAN_4 = 16
    const val COL_FOTO_PENGERJAAN_5 = 17
    const val MAX_PHOTO_SLOTS = 5

    val HEADERS: List<String> = listOf(
        "ID",
        "Tanggal",
        "Nama",
        "Kategori",
        "Pesan",
        "Foto_Masalah_1",
        "Foto_Masalah_2",
        "Foto_Masalah_3",
        "Foto_Masalah_4",
        "Foto_Masalah_5",
        "Status",
        "Nama_Pengurus",
        "Tanggapan_pengurus",
        "Foto_pengerjaan_1",
        "Foto_pengerjaan_2",
        "Foto_pengerjaan_3",
        "Foto_pengerjaan_4",
        "Foto_pengerjaan_5"
    )

    fun splitPhotoSlots(raw: String, maxSlots: Int = MAX_PHOTO_SLOTS): List<String> {
        return raw.split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(maxSlots)
    }
}

@JsonClass(generateAdapter = true)
data class AspirasiItem(
    @Json(name = "ID") val id: String = "",
    @Json(name = "Tanggal") val tanggal: String = "",
    @Json(name = "Nama") val nama: String = "",
    @Json(name = "Kategori") val kategori: String = "Masukan/Saran", // "Masukan/Saran" or "Kritik"
    @Json(name = "Pesan") val pesan: String = "",
    @Json(name = "Foto_Masalah_1") val fotoMasalah: String = "",
    @Json(name = "Status") val status: String = "Menunggu", // "Menunggu", "Diproses", "Selesai"
    @Json(name = "Nama_Pengurus") val penanggapNama: String = "",
    @Json(name = "Tanggapan_pengurus") val tanggapanPengurus: String = "",
    @Json(name = "Foto_pengerjaan_1") val fotoPengerjaan: String = "",
    @Json(name = "No_Rumah") val noRumah: String = "",
    @Json(name = "Sembunyikan_Nama") val sembunyikanNama: Boolean = false,
    @Json(name = "Penanggap_Username") val penanggapUsername: String = "",
    @Json(name = "Penanggap_Nama_Asli") val penanggapNamaAsli: String = "",
    @Json(name = "Penanggap_Peran") val penanggapPeran: String = "",
    @Json(name = "Waktu_Tanggapan") val waktuTanggapan: String = ""
) {
    val fotoMasalahSlots: List<String>
        get() = AspirasiSheetColumns.splitPhotoSlots(fotoMasalah)

    val fotoPengerjaanSlots: List<String>
        get() = AspirasiSheetColumns.splitPhotoSlots(fotoPengerjaan)

    val fotoMasalah1: String get() = fotoMasalahSlots.getOrElse(0) { "" }
    val fotoMasalah2: String get() = fotoMasalahSlots.getOrElse(1) { "" }
    val fotoMasalah3: String get() = fotoMasalahSlots.getOrElse(2) { "" }
    val fotoMasalah4: String get() = fotoMasalahSlots.getOrElse(3) { "" }
    val fotoMasalah5: String get() = fotoMasalahSlots.getOrElse(4) { "" }

    val fotoPengerjaan1: String get() = fotoPengerjaanSlots.getOrElse(0) { "" }
    val fotoPengerjaan2: String get() = fotoPengerjaanSlots.getOrElse(1) { "" }
    val fotoPengerjaan3: String get() = fotoPengerjaanSlots.getOrElse(2) { "" }
    val fotoPengerjaan4: String get() = fotoPengerjaanSlots.getOrElse(3) { "" }
    val fotoPengerjaan5: String get() = fotoPengerjaanSlots.getOrElse(4) { "" }
}

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
    val driveAuthorized: Boolean = false,
    val hasExplicitVersion: Boolean = false,
    val hasExplicitDriveAuth: Boolean = false,
    val supportsDriveUpload: Boolean = false,
    val maxGaleriPhotos: Int = 0
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
