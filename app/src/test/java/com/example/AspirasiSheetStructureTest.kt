package com.example

import com.example.data.source.GoogleAppsScriptTemplate
import com.example.data.source.GoogleSheetsService
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
class AspirasiSheetStructureTest {

    @Test
    fun appsScriptTemplate_definesExact17ColumnsForAspirasiAtoQ() {
        val script = GoogleAppsScriptTemplate.SCRIPT_CODE
        val expectedHeaderBlock = """
            var ASPIRASI_HEADERS = [
              "ID", "Tanggal", "Nama", "Kategori", "Pesan",
              "Foto_Masalah_1", "Foto_Masalah_2", "Foto_Masalah_3", "Foto_Masalah_4", "Foto_Masalah_5",
              "Status", "Tanggapan pengurus",
              "Foto_pengerjaan_1", "Foto_pengerjaan_2", "Foto_pengerjaan_3", "Foto_pengerjaan_4", "Foto_pengerjaan_5"
            ];
        """.trimIndent()
        assertTrue(script.contains(expectedHeaderBlock))
        assertTrue(script.contains("function migrateAspirasiSheetLayout(sheet)"))
        assertTrue(script.contains("sheet.getRange(rowNum, 11).setValue"))
        assertTrue(script.contains("sheet.getRange(rowNum, 12).setValue"))
        assertTrue(script.contains("sheet.getRange(rowNum, 13, 1, 5).setValues"))
    }

    @Test
    fun googleSheetsService_parses17ColumnAspirasiResponseIncludingAll5PengerjaanPhotos() {
        val row = JSONObject().apply {
            put("ID", "ASP-777")
            put("Tanggal", "2026-11-17")
            put("Nama", "Bapak Andi")
            put("Kategori", "Masukan/Saran")
            put("Pesan", "Perbaikan lampu gang")
            put("Foto_Masalah_1", "=IMAGE(\"https://drive.google.com/uc?id=m1\")")
            put("Foto_Masalah_2", "=IMAGE(\"https://drive.google.com/uc?id=m2\")")
            put("Foto_Masalah_3", "=IMAGE(\"https://drive.google.com/uc?id=m3\")")
            put("Foto_Masalah_4", "=IMAGE(\"https://drive.google.com/uc?id=m4\")")
            put("Foto_Masalah_5", "=IMAGE(\"https://drive.google.com/uc?id=m5\")")
            put("Status", "Selesai")
            put("Tanggapan pengurus", "Sudah diganti lampu LED baru")
            put("Foto_pengerjaan_1", "=IMAGE(\"https://drive.google.com/uc?id=p1\")")
            put("Foto_pengerjaan_2", "=IMAGE(\"https://drive.google.com/uc?id=p2\")")
            put("Foto_pengerjaan_3", "=IMAGE(\"https://drive.google.com/uc?id=p3\")")
            put("Foto_pengerjaan_4", "=IMAGE(\"https://drive.google.com/uc?id=p4\")")
            put("Foto_pengerjaan_5", "=IMAGE(\"https://drive.google.com/uc?id=p5\")")
        }
        val payload = JSONObject().apply {
            put("status", "success")
            put("version", 7)
            put("aspirasi", JSONArray().apply { put(row) })
        }

        val parsed = GoogleSheetsService().parseSheetResponse(payload.toString())
        val item = parsed.aspirasi?.singleOrNull()
        assertNotNull(item)
        assertEquals("ASP-777", item!!.id)
        assertEquals("2026-11-17", item.tanggal)
        assertEquals("Bapak Andi", item.nama)
        assertEquals("Masukan/Saran", item.kategori)
        assertEquals("Perbaikan lampu gang", item.pesan)
        assertEquals(
            "https://drive.google.com/uc?id=m1,https://drive.google.com/uc?id=m2,https://drive.google.com/uc?id=m3,https://drive.google.com/uc?id=m4,https://drive.google.com/uc?id=m5",
            item.fotoMasalah
        )
        assertEquals("Selesai", item.status)
        assertEquals("Sudah diganti lampu LED baru", item.tanggapanPengurus)
        assertEquals(
            "https://drive.google.com/uc?id=p1,https://drive.google.com/uc?id=p2,https://drive.google.com/uc?id=p3,https://drive.google.com/uc?id=p4,https://drive.google.com/uc?id=p5",
            item.fotoPengerjaan
        )
    }

    @Test
    fun xlsxExtractor_extractsExactColumnsFtoJAndMtoQInAspirasi() {
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
                    <sheet name="Aspirasi" sheetId="1" r:id="rId1"/>
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
                  <si><t>Foto_Masalah_1</t></si>
                  <si><t>Foto_Masalah_5</t></si>
                  <si><t>Foto_pengerjaan_1</t></si>
                  <si><t>Foto_pengerjaan_5</t></si>
                  <si><t>ASP-999</t></si>
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
                      <c r="J1" t="s"><v>2</v></c>
                      <c r="M1" t="s"><v>3</v></c>
                      <c r="Q1" t="s"><v>4</v></c>
                    </row>
                    <row r="2">
                      <c r="A2" t="s"><v>5</v></c>
                      <c r="F2" t="str"><f>IMAGE(&quot;https://drive.google.com/uc?id=fm1&quot;)</f><v></v></c>
                      <c r="J2" t="str"><f>IMAGE(&quot;https://drive.google.com/uc?id=fm5&quot;)</f><v></v></c>
                      <c r="M2" t="str"><f>IMAGE(&quot;https://drive.google.com/uc?id=fp1&quot;)</f><v></v></c>
                      <c r="Q2" t="str"><f>IMAGE(&quot;https://drive.google.com/uc?id=fp5&quot;)</f><v></v></c>
                    </row>
                  </sheetData>
                </worksheet>
                """.trimIndent()
            )
        }

        val extracted = XlsxImageFormulaExtractor.extract(ByteArrayInputStream(baos.toByteArray()))
        val row = extracted["Aspirasi"]?.byRowId?.get("ASP-999")
        assertNotNull(row)
        assertEquals("https://drive.google.com/uc?id=fm1", row!!["Foto_Masalah_1"])
        assertEquals("https://drive.google.com/uc?id=fm5", row["Foto_Masalah_5"])
        assertEquals("https://drive.google.com/uc?id=fp1", row["Foto_pengerjaan_1"])
        assertEquals("https://drive.google.com/uc?id=fp5", row["Foto_pengerjaan_5"])
    }
}
