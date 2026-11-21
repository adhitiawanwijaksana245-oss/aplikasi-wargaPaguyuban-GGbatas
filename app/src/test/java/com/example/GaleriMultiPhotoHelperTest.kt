package com.example

import com.example.data.model.GaleriItem
import com.example.util.GaleriMultiPhotoHelper
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class GaleriMultiPhotoHelperTest {

    @Test
    fun expandRowsForSingleColumnSheet_splitsMultiPhotoIntoOrderedSheetRows() {
        val item = GaleriItem(
            id = "GAL-1794515132963",
            tanggal = "2026-11-12",
            judul = "Kerja Bakti Minggu",
            kategori = "Dokumentasi",
            keterangan = "Pembersihan saluran air",
            foto = "https://iili.io/1.jpg,https://iili.io/2.jpg,https://iili.io/3.jpg",
            petugas = "Seksi Kebersihan"
        )
        val formulas = listOf(
            "=IMAGE(\"https://iili.io/1.jpg\")",
            "=IMAGE(\"https://iili.io/2.jpg\")",
            "=IMAGE(\"https://iili.io/3.jpg\")"
        )

        val rows = GaleriMultiPhotoHelper.expandRowsForSingleColumnSheet(item, formulas)

        assertEquals(3, rows.size)
        assertEquals("GAL-1794515132963", rows[0].rowId)
        assertEquals("GAL-1794515132963_2", rows[1].rowId)
        assertEquals("GAL-1794515132963_3", rows[2].rowId)

        assertEquals("Kerja Bakti Minggu (Foto 1/3)", rows[0].json.getString("judul"))
        assertEquals("Kerja Bakti Minggu (Foto 2/3)", rows[1].json.getString("judul"))
        assertEquals("Kerja Bakti Minggu (Foto 3/3)", rows[2].json.getString("judul"))

        assertEquals("=IMAGE(\"https://iili.io/1.jpg\")", rows[0].json.getString("foto"))
        assertEquals("=IMAGE(\"https://iili.io/2.jpg\")", rows[1].json.getString("foto"))
        assertEquals("=IMAGE(\"https://iili.io/3.jpg\")", rows[2].json.getString("foto"))
    }

    @Test
    fun mergeGroupedGaleriRows_combinesSplitRowsBackIntoSingleCardInOrder() {
        val rows = listOf(
            GaleriItem(
                id = "GAL-1794515132963_3",
                tanggal = "2026-11-12",
                judul = "Kerja Bakti Minggu (Foto 3/3)",
                kategori = "Dokumentasi",
                keterangan = "Pembersihan saluran air",
                foto = "https://iili.io/3.jpg",
                petugas = "Pak Budi"
            ),
            GaleriItem(
                id = "GAL-1794515132963",
                tanggal = "2026-11-12",
                judul = "Kerja Bakti Minggu (Foto 1/3)",
                kategori = "Dokumentasi",
                keterangan = "Pembersihan saluran air",
                foto = "https://iili.io/1.jpg",
                petugas = "Pak Budi"
            ),
            GaleriItem(
                id = "GAL-1794515132963_2",
                tanggal = "2026-11-12",
                judul = "Kerja Bakti Minggu (Foto 2/3)",
                kategori = "Dokumentasi",
                keterangan = "Pembersihan saluran air",
                foto = "https://iili.io/2.jpg",
                petugas = "Pak Budi"
            ),
            GaleriItem(
                id = "GAL-999",
                tanggal = "2026-11-10",
                judul = "Rapat RT",
                kategori = "Dokumentasi",
                keterangan = "Rapat bulanan",
                foto = "https://iili.io/single.jpg",
                petugas = "Sekretaris"
            )
        )

        val merged = GaleriMultiPhotoHelper.mergeGroupedGaleriRows(rows)

        assertEquals(2, merged.size)
        assertEquals("GAL-1794515132963", merged[0].id)
        assertEquals("Kerja Bakti Minggu", merged[0].judul)
        assertEquals(
            "https://iili.io/1.jpg,https://iili.io/2.jpg,https://iili.io/3.jpg",
            merged[0].foto
        )
        assertEquals("GAL-999", merged[1].id)
        assertEquals("Rapat RT", merged[1].judul)
        assertEquals("https://iili.io/single.jpg", merged[1].foto)
    }

    @Test
    fun buildCombinedRowPayload_includesAllFivePhotoColumnsForV2Script() {
        val item = GaleriItem(
            id = "GAL-555",
            tanggal = "2026-11-12",
            judul = "Lomba 17an (Foto 1/5)",
            kategori = "Dokumentasi",
            keterangan = "Warga berkumpul",
            petugas = "Karang Taruna"
        )
        val formulas = (1..5).map { "=IMAGE(\"https://iili.io/$it.jpg\")" }

        val json = GaleriMultiPhotoHelper.buildCombinedRowPayload(item, formulas)

        assertEquals("GAL-555", json.getString("id"))
        assertEquals("Lomba 17an", json.getString("judul"))
        assertEquals("=IMAGE(\"https://iili.io/1.jpg\")", json.getString("foto"))
        assertEquals("=IMAGE(\"https://iili.io/2.jpg\")", json.getString("foto2"))
        assertEquals("=IMAGE(\"https://iili.io/3.jpg\")", json.getString("foto3"))
        assertEquals("=IMAGE(\"https://iili.io/4.jpg\")", json.getString("foto4"))
        assertEquals("=IMAGE(\"https://iili.io/5.jpg\")", json.getString("foto5"))
        assertEquals("", json.getString("foto6"))
    }

    @Test
    fun buildCombinedRowPayload_includesAllTenPhotoColumns() {
        val item = GaleriItem(
            id = "GAL-777",
            tanggal = "2026-11-14",
            judul = "Karnaval RT 005",
            kategori = "Dokumentasi",
            keterangan = "Sepuluh foto kegiatan",
            petugas = "Seksi Dokumentasi"
        )
        val formulas = (1..10).map { "=IMAGE(\"https://iili.io/$it.jpg\")" }

        val json = GaleriMultiPhotoHelper.buildCombinedRowPayload(item, formulas)

        assertEquals("GAL-777", json.getString("id"))
        for (i in 1..10) {
            val key = if (i == 1) "foto" else "foto$i"
            assertEquals("=IMAGE(\"https://iili.io/$i.jpg\")", json.getString(key))
        }
    }

    @Test
    fun chunkPhotosForFiveColumnSheet_splitsTenPhotosIntoTwoRowsAndMergesBack() {
        val item = GaleriItem(
            id = "GAL-888",
            tanggal = "2026-11-14",
            judul = "Kerja Bakti Akbar",
            kategori = "Dokumentasi",
            keterangan = "10 foto lengkap",
            petugas = "Pengurus"
        )
        val formulas = (1..10).map { "=IMAGE(\"https://iili.io/$it.jpg\")" }

        val chunks = GaleriMultiPhotoHelper.chunkPhotosForFiveColumnSheet(item, formulas)

        assertEquals(2, chunks.size)
        assertEquals("GAL-888", chunks[0].first)
        assertEquals("GAL-888_6", chunks[1].first)
        assertEquals("=IMAGE(\"https://iili.io/1.jpg\")", chunks[0].second.getString("foto"))
        assertEquals("=IMAGE(\"https://iili.io/5.jpg\")", chunks[0].second.getString("foto5"))
        assertEquals("=IMAGE(\"https://iili.io/6.jpg\")", chunks[1].second.getString("foto"))
        assertEquals("=IMAGE(\"https://iili.io/10.jpg\")", chunks[1].second.getString("foto5"))

        val rowItems = chunks.map { (rowId, json) ->
            val fotos = listOf("foto", "foto2", "foto3", "foto4", "foto5")
                .map { com.example.util.PhotoStorageHelper.extractUrlFromFormula(json.optString(it, "")) }
                .filter { it.isNotBlank() }
            item.copy(id = rowId, foto = fotos.joinToString(","))
        }
        val merged = GaleriMultiPhotoHelper.mergeGroupedGaleriRows(rowItems)
        assertEquals(1, merged.size)
        assertEquals(
            (1..10).joinToString(",") { "https://iili.io/$it.jpg" },
            merged[0].foto
        )
    }

    @Test
    fun candidateRowIdsForDeletion_returnsBaseAndSuffixes() {
        assertEquals(
            listOf("GAL-100", "GAL-100_2", "GAL-100_3"),
            GaleriMultiPhotoHelper.candidateRowIdsForDeletion("GAL-100", photoCount = 3)
        )
        assertEquals(
            10,
            GaleriMultiPhotoHelper.candidateRowIdsForDeletion("GAL-100", photoCount = 10).size
        )
    }
}
