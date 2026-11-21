package com.example.util

import com.example.data.model.GaleriItem
import org.json.JSONObject

data class GaleriSheetRowPayload(
    val rowId: String,
    val baseId: String,
    val partIndex: Int,
    val formula: String,
    val json: JSONObject
)

object GaleriMultiPhotoHelper {
    private val SUFFIX_ID_REGEX = Regex("""^(GAL-[^_]+)_(\d+)$""")
    private val PART_JUDUL_REGEX = Regex("""\s*\(Foto\s+\d+\s*/\s*\d+\)\s*$""", RegexOption.IGNORE_CASE)

    /**
     * Mengambil ID induk dari ID baris Galeri (mis. "GAL-12345_2" -> "GAL-12345").
     */
    fun baseGaleriId(rowId: String): String {
        val trimmed = rowId.trim()
        val match = SUFFIX_ID_REGEX.matchEntire(trimmed)
        return match?.groupValues?.get(1) ?: trimmed
    }

    /**
     * Mengambil urutan foto (1 untuk induk, 2..10 untuk baris tambahan _2.._10).
     */
    fun partIndex(rowId: String): Int {
        val trimmed = rowId.trim()
        val match = SUFFIX_ID_REGEX.matchEntire(trimmed)
        return match?.groupValues?.get(2)?.toIntOrNull()?.coerceAtLeast(1) ?: 1
    }

    /**
     * Menghapus label otomatis " (Foto X/Y)" dari judul saat digabungkan ke 1 kartu aplikasi.
     */
    fun stripPhotoSuffix(judul: String): String {
        return judul.replace(PART_JUDUL_REGEX, "").trim()
    }

    /**
     * Menghasilkan daftar ID baris (induk + anak _2.._N) untuk penghapusan bersih di Spreadsheet.
     */
    fun candidateRowIdsForDeletion(baseId: String, photoCount: Int = 10): List<String> {
        val cleanBase = baseGaleriId(baseId)
        if (cleanBase.isBlank()) return emptyList()
        val maxPart = photoCount.coerceIn(1, 10)
        val result = mutableListOf(cleanBase)
        for (i in 2..maxPart) {
            result.add("${cleanBase}_$i")
        }
        return result
    }

    /**
     * Membuat 1 payload baris lengkap (untuk Apps Script v3 yang mendukung kolom Foto .. Foto_10).
     */
    fun buildCombinedRowPayload(item: GaleriItem, sheetFormulas: List<String>): JSONObject {
        val cleanJudul = stripPhotoSuffix(item.judul)
        return JSONObject().apply {
            put("id", item.id.trim().ifBlank { baseGaleriId(item.id) })
            put("tanggal", item.tanggal)
            put("judul", cleanJudul)
            put("kategori", item.kategori)
            put("keterangan", item.keterangan)
            put("foto", sheetFormulas.getOrNull(0) ?: "")
            put("foto1", sheetFormulas.getOrNull(0) ?: "")
            put("foto2", sheetFormulas.getOrNull(1) ?: "")
            put("foto3", sheetFormulas.getOrNull(2) ?: "")
            put("foto4", sheetFormulas.getOrNull(3) ?: "")
            put("foto5", sheetFormulas.getOrNull(4) ?: "")
            put("foto6", sheetFormulas.getOrNull(5) ?: "")
            put("foto7", sheetFormulas.getOrNull(6) ?: "")
            put("foto8", sheetFormulas.getOrNull(7) ?: "")
            put("foto9", sheetFormulas.getOrNull(8) ?: "")
            put("foto10", sheetFormulas.getOrNull(9) ?: "")
            put("petugas", item.petugas)
        }
    }

    /**
     * Memecah 6..10 foto menjadi maksimal 2 baris (5 foto per baris) apabila Apps Script masih v2 (kolom Foto..Foto_5).
     */
    fun chunkPhotosForFiveColumnSheet(
        item: GaleriItem,
        sheetFormulas: List<String>
    ): List<Pair<String, JSONObject>> {
        val baseId = baseGaleriId(item.id)
        val cleanFormulas = sheetFormulas.map { it.trim() }.filter { it.isNotBlank() }.take(10)
        if (cleanFormulas.size <= 5) {
            return listOf(baseId to buildCombinedRowPayload(item.copy(id = baseId), cleanFormulas))
        }
        val firstBatch = cleanFormulas.take(5)
        val secondBatch = cleanFormulas.drop(5)
        val secondRowId = "${baseId}_6"
        return listOf(
            baseId to buildCombinedRowPayload(item.copy(id = baseId), firstBatch),
            secondRowId to buildCombinedRowPayload(item.copy(id = secondRowId), secondBatch)
        )
    }

    /**
     * Memecah 1 kegiatan multi-foto menjadi baris berurutan di kolom "Foto"
     * agar seluruh foto (2..5) tetap tersimpan rapi di Google Spreadsheet meskipun
     * Apps Script masih versi lama (yang hanya membaca kolom "Foto").
     */
    fun expandRowsForSingleColumnSheet(
        item: GaleriItem,
        sheetFormulas: List<String>
    ): List<GaleriSheetRowPayload> {
        val baseId = baseGaleriId(item.id)
        val cleanJudul = stripPhotoSuffix(item.judul)
        val cleanFormulas = sheetFormulas.map { it.trim() }.filter { it.isNotBlank() }

        if (cleanFormulas.size <= 1) {
            val singleFormula = cleanFormulas.firstOrNull() ?: ""
            val json = JSONObject().apply {
                put("id", baseId)
                put("tanggal", item.tanggal)
                put("judul", cleanJudul)
                put("kategori", item.kategori)
                put("keterangan", item.keterangan)
                put("foto", singleFormula)
                put("petugas", item.petugas)
            }
            return listOf(GaleriSheetRowPayload(baseId, baseId, 1, singleFormula, json))
        }

        val total = cleanFormulas.size
        return cleanFormulas.mapIndexed { index, formula ->
            val part = index + 1
            val rowId = if (part == 1) baseId else "${baseId}_$part"
            val labeledJudul = if (cleanJudul.isNotBlank()) {
                "$cleanJudul (Foto $part/$total)"
            } else {
                "Dokumentasi (Foto $part/$total)"
            }
            val json = JSONObject().apply {
                put("id", rowId)
                put("tanggal", item.tanggal)
                put("judul", labeledJudul)
                put("kategori", item.kategori)
                put("keterangan", item.keterangan)
                put("foto", formula)
                put("petugas", item.petugas)
            }
            GaleriSheetRowPayload(rowId, baseId, part, formula, json)
        }
    }

    /**
     * Menggabungkan kembali baris-baris foto bernomor (GAL-xxx, GAL-xxx_2.._5)
     * dari Spreadsheet menjadi 1 kartu GaleriItem yang rapi di dalam aplikasi.
     */
    fun mergeGroupedGaleriRows(rows: List<GaleriItem>): List<GaleriItem> {
        if (rows.isEmpty()) return emptyList()

        val orderedBaseIds = mutableListOf<String>()
        val grouped = LinkedHashMap<String, MutableList<GaleriItem>>()

        for (row in rows) {
            val baseId = baseGaleriId(row.id)
            if (!grouped.containsKey(baseId)) {
                orderedBaseIds.add(baseId)
                grouped[baseId] = mutableListOf()
            }
            grouped[baseId]!!.add(row)
        }

        return orderedBaseIds.mapNotNull { baseId ->
            val groupRows = grouped[baseId].orEmpty().sortedBy { partIndex(it.id) }
            val anchor = groupRows.firstOrNull { partIndex(it.id) == 1 }
                ?: groupRows.firstOrNull()
                ?: return@mapNotNull null

            val mergedPhotos = mutableListOf<String>()
            for (r in groupRows) {
                r.foto.split(",")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .forEach { photo ->
                        if (photo !in mergedPhotos) {
                            mergedPhotos.add(photo)
                        }
                    }
            }

            val cleanJudul = stripPhotoSuffix(anchor.judul).ifBlank {
                groupRows.firstNotNullOfOrNull { r ->
                    stripPhotoSuffix(r.judul).takeIf { it.isNotBlank() }
                } ?: ""
            }

            anchor.copy(
                id = baseId,
                judul = cleanJudul,
                tanggal = anchor.tanggal.ifBlank { groupRows.firstOrNull { it.tanggal.isNotBlank() }?.tanggal ?: "" },
                kategori = anchor.kategori.ifBlank { groupRows.firstOrNull { it.kategori.isNotBlank() }?.kategori ?: "Dokumentasi" },
                keterangan = anchor.keterangan.ifBlank { groupRows.firstOrNull { it.keterangan.isNotBlank() }?.keterangan ?: "" },
                petugas = anchor.petugas.ifBlank { groupRows.firstOrNull { it.petugas.isNotBlank() }?.petugas ?: "" },
                foto = mergedPhotos.joinToString(",")
            )
        }
    }
}
