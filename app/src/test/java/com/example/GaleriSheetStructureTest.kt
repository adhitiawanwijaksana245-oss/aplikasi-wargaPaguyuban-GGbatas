package com.example

import com.example.data.model.GaleriItem
import com.example.data.source.GoogleAppsScriptTemplate
import com.example.data.source.GoogleSheetsService
import com.example.util.GaleriMultiPhotoHelper
import com.example.util.XlsxImageFormulaExtractor
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GaleriSheetStructureTest {

    @Test
    fun appsScriptTemplate_definesExact16ColumnsForGaleriAtoP() {
        val script = GoogleAppsScriptTemplate.SCRIPT_CODE
        val expectedHeaderBlock = """
            var GALERI_HEADERS = [
              "ID", "Tanggal", "Judul", "Kategori", "Keterangan",
              "Foto_1", "Foto_2", "Foto_3", "Foto_4", "Foto_5",
              "Foto_6", "Foto_7", "Foto_8", "Foto_9", "Foto_10",
              "Petugas"
            ];
        """.trimIndent()
        assertTrue(script.contains(expectedHeaderBlock))
        assertTrue(script.contains("function migrateGaleriSheetLayout(sheet)"))
        assertTrue(script.contains("sheet.setColumnWidths(6, 10, 150)"))
    }

    @Test
    fun buildCombinedRowPayload_emitsFoto1ToFoto10AndPetugas() {
        val item = GaleriItem(
            id = "GAL-900",
            tanggal = "2026-11-17",
            judul = "Kerja Bakti RT 005",
            kategori = "Kebersihan",
            keterangan = "Pembersihan selokan bersama warga",
            petugas = "Seksi Dokumentasi"
        )
        val formulas = (1..10).map { "=IMAGE(\"https://drive.google.com/uc?id=g$it\")" }

        val json = GaleriMultiPhotoHelper.buildCombinedRowPayload(item, formulas)

        assertEquals("GAL-900", json.getString("id"))
        assertEquals("=IMAGE(\"https://drive.google.com/uc?id=g1\")", json.getString("foto"))
        for (i in 1..10) {
            assertEquals("=IMAGE(\"https://drive.google.com/uc?id=g$i\")", json.getString("foto$i"))
        }
        assertEquals("Seksi Dokumentasi", json.getString("petugas"))
    }

    @Test
    fun googleSheetsService_parses16ColumnGaleriResponseFoto1ToFoto10AndPetugas() {
        val row = JSONObject().apply {
            put("ID", "GAL-888")
            put("Tanggal", "2026-11-17")
            put("Judul", "Posyandu Balita")
            put("Kategori", "Kesehatan")
            put("Keterangan", "Pemeriksaan rutin bulanan")
            for (i in 1..10) {
                put("Foto_$i", "=IMAGE(\"https://drive.google.com/uc?id=gal$i\")")
            }
            put("Petugas", "Bu RT 005")
        }
        val payload = JSONObject().apply {
            put("status", "success")
            put("version", 8)
            put("galeri", JSONArray().apply { put(row) })
        }

        val parsed = GoogleSheetsService().parseSheetResponse(payload.toString())
        val item = parsed.galeri?.singleOrNull()
        assertNotNull(item)
        assertEquals("GAL-888", item!!.id)
        assertEquals("2026-11-17", item.tanggal)
        assertEquals("Posyandu Balita", item.judul)
        assertEquals("Kesehatan", item.kategori)
        assertEquals("Pemeriksaan rutin bulanan", item.keterangan)
        assertEquals(
            (1..10).joinToString(",") { "https://drive.google.com/uc?id=gal$it" },
            item.foto
        )
        assertEquals("Bu RT 005", item.petugas)
    }

    @Test
    fun xlsxExtractor_extractsExactColumnsFtoOInGaleriWithPetugasInP() {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            fun addEntry(path: String, xml: String) {
                zos.putNextEntry(ZipEntry(path))
                zos.write(xml.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }

            addEntry(
                "xl/workbook.xml",
                """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                  <sheets>
                    <sheet name="Galeri" sheetId="1" r:id="rId1"/>
                  </sheets>
                </workbook>
                """.trimIndent()
            )
            addEntry(
                "xl/_rels/workbook.xml.rels",
                """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="worksheet" Target="worksheets/sheet1.xml"/>
                </Relationships>
                """.trimIndent()
            )
            addEntry(
                "xl/sharedStrings.xml",
                """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <si><t>ID</t></si>
                  <si><t>Foto_1</t></si>
                  <si><t>Foto_10</t></si>
                  <si><t>Petugas</t></si>
                  <si><t>GAL-700</t></si>
                  <si><t>Pak Budi</t></si>
                </sst>
                """.trimIndent()
            )
            addEntry(
                "xl/worksheets/sheet1.xml",
                """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <sheetData>
                    <row r="1">
                      <c r="A1" t="s"><v>0</v></c>
                      <c r="F1" t="s"><v>1</v></c>
                      <c r="O1" t="s"><v>2</v></c>
                      <c r="P1" t="s"><v>3</v></c>
                    </row>
                    <row r="2">
                      <c r="A2" t="s"><v>4</v></c>
                      <c r="F2" t="str"><f>IMAGE(&quot;https://drive.google.com/uc?id=gf1&quot;)</f><v></v></c>
                      <c r="O2" t="str"><f>IMAGE(&quot;https://drive.google.com/uc?id=gf10&quot;)</f><v></v></c>
                      <c r="P2" t="s"><v>5</v></c>
                    </row>
                  </sheetData>
                </worksheet>
                """.trimIndent()
            )
        }

        val extracted = XlsxImageFormulaExtractor.extract(ByteArrayInputStream(baos.toByteArray()))
        val row = extracted["Galeri"]?.byRowId?.get("GAL-700")
        assertNotNull(row)
        assertEquals("https://drive.google.com/uc?id=gf1", row!!["Foto_1"])
        assertEquals("https://drive.google.com/uc?id=gf10", row["Foto_10"])
    }
}
