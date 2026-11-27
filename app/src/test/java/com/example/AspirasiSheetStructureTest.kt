package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AspirasiEntity
import com.example.data.model.AspirasiItem
import com.example.data.model.AspirasiSheetColumns
import com.example.data.model.PengurusAccount
import com.example.data.model.UserRole
import com.example.data.repository.WargaRepository
import com.example.data.source.GoogleAppsScriptTemplate
import com.example.data.source.GoogleSheetsService
import com.example.util.AspirasiReplyCodec
import com.example.util.XlsxImageFormulaExtractor
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.util.concurrent.CopyOnWriteArrayList
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AspirasiSheetStructureTest {

    private suspend fun withLocalHttpServer(
        handler: (method: String, pathAndQuery: String, body: String) -> Pair<Int, String>,
        block: suspend (baseUrl: String) -> Unit
    ) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/exec") { exchange ->
            val requestBody = exchange.requestBody.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val uri = exchange.requestURI.toString()
            val (statusCode, responseBody) = handler(exchange.requestMethod, uri, requestBody)
            val bytes = responseBody.toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(statusCode, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val port = server.address.port
            block("http://127.0.0.1:$port/exec")
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun appsScriptTemplate_definesExact18ColumnsForAspirasiAtoRAndUpdatesKtoR() {
        val script = GoogleAppsScriptTemplate.SCRIPT_CODE
        val expectedHeaderBlock = """
            var ASPIRASI_HEADERS = [
              "ID", "Tanggal", "Nama", "Kategori", "Pesan",
              "Foto_Masalah_1", "Foto_Masalah_2", "Foto_Masalah_3", "Foto_Masalah_4", "Foto_Masalah_5",
              "Status", "Nama_Pengurus", "Tanggapan_pengurus",
              "Foto_pengerjaan_1", "Foto_pengerjaan_2", "Foto_pengerjaan_3", "Foto_pengerjaan_4", "Foto_pengerjaan_5"
            ];
        """.trimIndent()
        assertTrue(script.contains(expectedHeaderBlock))
        assertTrue(script.contains("function migrateAspirasiSheetLayout(sheet)"))
        assertTrue(script.contains("function normalizeAspirasiPhotoSlots(s1, s2, s3, s4, s5)"))
        assertTrue(script.contains("function extractAspirasiMetaName(raw)"))
        assertTrue(script.contains("function isImageOrUrlValue(val)"))
        assertTrue(script.contains("lastCol === 18"))
        assertTrue(script.contains("for (var h = 0; h < 18; h++)"))
        assertTrue(script.contains("cellHeader !== ASPIRASI_HEADERS[h]"))
        assertTrue(script.contains("sheet.getRange(rowNum, 11, 1, 8).setValues"))
        assertTrue(script.contains("mode: \"inserted\""))
    }

    @Test
    fun googleSheetsService_parses18ColumnAspirasiResponseAtoRExactly() {
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
            put("Nama_Pengurus", "Pak Budi")
            put("Tanggapan_pengurus", "Sudah diganti lampu LED baru")
            put("Foto_pengerjaan_1", "=IMAGE(\"https://drive.google.com/uc?id=p1\")")
            put("Foto_pengerjaan_2", "=IMAGE(\"https://drive.google.com/uc?id=p2\")")
            put("Foto_pengerjaan_3", "=IMAGE(\"https://drive.google.com/uc?id=p3\")")
            put("Foto_pengerjaan_4", "=IMAGE(\"https://drive.google.com/uc?id=p4\")")
            put("Foto_pengerjaan_5", "=IMAGE(\"https://drive.google.com/uc?id=p5\")")
        }
        val payload = JSONObject().apply {
            put("status", "success")
            put("version", 9)
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
        assertEquals("Pak Budi", item.penanggapNama)
        assertEquals("Sudah diganti lampu LED baru", item.tanggapanPengurus)
        assertEquals(
            "https://drive.google.com/uc?id=p1,https://drive.google.com/uc?id=p2,https://drive.google.com/uc?id=p3,https://drive.google.com/uc?id=p4,https://drive.google.com/uc?id=p5",
            item.fotoPengerjaan
        )
    }

    @Test
    fun aspirasiReplyCodec_roundTripsAndStripsMarkerWhenLegacyScriptOmitsMetaColumns() {
        val encoded = AspirasiReplyCodec.encodeIntoTanggapan(
            rawTanggapan = "Sudah kami jadwalkan kerja bakti",
            penanggapNama = "Sekretaris RT",
            penanggapPeran = "Admin",
            waktuTanggapan = "2026-11-24T09:15:00+07:00"
        )
        val row = JSONObject().apply {
            put("ID", "ASP-LEGACY")
            put("Tanggal", "2026-11-24")
            put("Nama", "Ibu Ani")
            put("Kategori", "Kritik")
            put("Pesan", "Selokan tersumbat")
            put("Status", "Diproses")
            put("Tanggapan pengurus", encoded)
        }
        val payload = JSONObject().apply {
            put("status", "success")
            put("version", 8)
            put("aspirasi", JSONArray().apply { put(row) })
        }

        val item = GoogleSheetsService().parseSheetResponse(payload.toString()).aspirasi?.singleOrNull()
        assertNotNull(item)
        assertEquals("Sudah kami jadwalkan kerja bakti", item!!.tanggapanPengurus)
        assertEquals("Sekretaris RT", item.penanggapNama)
        assertEquals("Admin", item.penanggapPeran)
        assertEquals("2026-11-24T09:15:00+07:00", item.waktuTanggapan)
        assertEquals("Tanggapan bersih", AspirasiReplyCodec.stripMarker("Tanggapan bersih\n[[META_BALASAN:n=X&r=Admin&t=2026]]"))
    }

    @Test
    fun submitAspirasiWarga_appendsNewRowAtoKWithoutOverwritingExistingRows() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("aplikasi_warga_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        com.example.data.local.AppDatabase.getDatabase(context).aspirasiDao().clearAllAspirasi()

        val remoteSheetRows = CopyOnWriteArrayList<JSONObject>()
        remoteSheetRows.add(
            JSONObject().apply {
                put("ID", "ASP-EXISTING")
                put("Tanggal", "2026-11-20")
                put("Nama", "Warga Lama")
                put("Kategori", "Kritik")
                put("Pesan", "Pesan lama jangan tertimpa")
                put("Status", "Selesai")
                put("Nama_Pengurus", "Ketua RT")
                put("Tanggapan_pengurus", "Sudah beres")
            }
        )

        withLocalHttpServer({ _, uri, body ->
            fun parseQueryParam(rawQuery: String, key: String): String {
                val queryPart = rawQuery.substringAfter("?", "")
                return queryPart.split("&")
                    .mapNotNull {
                        val idx = it.indexOf("=")
                        if (idx <= 0) null
                        else URLDecoder.decode(it.substring(0, idx), "UTF-8") to URLDecoder.decode(it.substring(idx + 1), "UTF-8")
                    }
                    .firstOrNull { it.first == key }?.second.orEmpty()
            }
            fun parseFormParam(formBody: String, key: String): String {
                return formBody.split("&")
                    .mapNotNull {
                        val idx = it.indexOf("=")
                        if (idx <= 0) null
                        else URLDecoder.decode(it.substring(0, idx), "UTF-8") to URLDecoder.decode(it.substring(idx + 1), "UTF-8")
                    }
                    .firstOrNull { it.first == key }?.second.orEmpty()
            }
            val action = parseQueryParam(uri, "action").ifBlank { parseFormParam(body, "action") }
            val dataStr = parseQueryParam(uri, "data").ifBlank { parseFormParam(body, "data") }
            when (action) {
                "add" -> {
                    val dataJson = JSONObject(dataStr)
                    remoteSheetRows.add(
                        JSONObject().apply {
                            put("ID", dataJson.optString("id"))
                            put("Tanggal", dataJson.optString("tanggal"))
                            put("Nama", dataJson.optString("nama"))
                            put("Kategori", dataJson.optString("kategori"))
                            put("Pesan", dataJson.optString("pesan"))
                            put("Foto_Masalah_1", dataJson.optString("fotoMasalah1"))
                            put("Foto_Masalah_2", dataJson.optString("fotoMasalah2"))
                            put("Foto_Masalah_3", dataJson.optString("fotoMasalah3"))
                            put("Foto_Masalah_4", dataJson.optString("fotoMasalah4"))
                            put("Foto_Masalah_5", dataJson.optString("fotoMasalah5"))
                            put("Status", dataJson.optString("status"))
                            put("Nama_Pengurus", dataJson.optString("namaPengurus"))
                            put("Tanggapan_pengurus", dataJson.optString("tanggapanPengurus"))
                        }
                    )
                    200 to """{"status":"success","message":"Berhasil"}"""
                }
                else -> {
                    val arr = JSONArray()
                    remoteSheetRows.forEach { arr.put(it) }
                    200 to JSONObject().apply {
                        put("status", "success")
                        put("version", 9)
                        put("driveAuthorized", true)
                        put("kas", JSONArray())
                        put("pengurus", JSONArray())
                        put("undangan", JSONArray())
                        put("galeri", JSONArray())
                        put("aspirasi", arr)
                    }.toString()
                }
            }
        }) { mockUrl ->
            val repo = WargaRepository(context)
            repo.updateSheetUrlOnly(mockUrl)
            val result = repo.submitAspirasiWarga(
                AspirasiItem(
                    id = "ASP-NEW-01",
                    tanggal = "2026-11-24",
                    nama = "Ibu Rina",
                    kategori = "Masukan/Saran",
                    pesan = "Mohon perbaikan tutup selokan blok D",
                    fotoMasalah = "https://drive.google.com/uc?id=m1,https://drive.google.com/uc?id=m2",
                    status = "Menunggu"
                )
            )
            assertTrue(result.sheetSynced)
            assertEquals(2, remoteSheetRows.size)
            assertEquals("ASP-EXISTING", remoteSheetRows[0].optString("ID"))
            assertEquals("Selesai", remoteSheetRows[0].optString("Status"))
            val addedRow = remoteSheetRows[1]
            assertEquals("ASP-NEW-01", addedRow.optString("ID"))
            assertEquals("2026-11-24", addedRow.optString("Tanggal"))
            assertEquals("Ibu Rina", addedRow.optString("Nama"))
            assertEquals("Masukan/Saran", addedRow.optString("Kategori"))
            assertEquals("Mohon perbaikan tutup selokan blok D", addedRow.optString("Pesan"))
            assertEquals("=IMAGE(\"https://drive.google.com/uc?id=m1\")", addedRow.optString("Foto_Masalah_1"))
            assertEquals("=IMAGE(\"https://drive.google.com/uc?id=m2\")", addedRow.optString("Foto_Masalah_2"))
            assertEquals("Menunggu", addedRow.optString("Status"))
            assertEquals("", addedRow.optString("Nama_Pengurus"))
            assertEquals("", addedRow.optString("Tanggapan_pengurus"))
        }
    }

    @Test
    fun respondAspirasi_savesToCentralSheetAndAppearsOnNewlyInstalledCitizenDevice() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("aplikasi_warga_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        com.example.data.local.AppDatabase.getDatabase(context).aspirasiDao().clearAllAspirasi()

        val remoteSheetRows = CopyOnWriteArrayList<JSONObject>()
        val initialRow = JSONObject().apply {
            put("ID", "ASP-1001")
            put("Tanggal", "2026-11-24")
            put("Nama", "Warga Blok C")
            put("Kategori", "Masukan/Saran")
            put("Pesan", "Mohon tambah lampu penerangan jalan")
            put("Foto_Masalah_1", "=IMAGE(\"https://drive.google.com/uc?id=masalah1\")")
            put("Status", "Menunggu")
            put("Nama_Pengurus", "")
            put("Tanggapan_pengurus", "")
        }
        remoteSheetRows.add(initialRow)

        withLocalHttpServer({ _, uri, body ->
            fun parseQueryParam(rawQuery: String, key: String): String {
                val queryPart = rawQuery.substringAfter("?", "")
                return queryPart.split("&")
                    .mapNotNull {
                        val idx = it.indexOf("=")
                        if (idx <= 0) null
                        else URLDecoder.decode(it.substring(0, idx), "UTF-8") to URLDecoder.decode(it.substring(idx + 1), "UTF-8")
                    }
                    .firstOrNull { it.first == key }?.second.orEmpty()
            }

            fun parseFormParam(formBody: String, key: String): String {
                return formBody.split("&")
                    .mapNotNull {
                        val idx = it.indexOf("=")
                        if (idx <= 0) null
                        else URLDecoder.decode(it.substring(0, idx), "UTF-8") to URLDecoder.decode(it.substring(idx + 1), "UTF-8")
                    }
                    .firstOrNull { it.first == key }?.second.orEmpty()
            }

            val action = parseQueryParam(uri, "action").ifBlank { parseFormParam(body, "action") }
            val dataStr = parseQueryParam(uri, "data").ifBlank { parseFormParam(body, "data") }
            val targetId = parseQueryParam(uri, "targetId").ifBlank { parseFormParam(body, "targetId") }

            when (action) {
                "test_connection" -> {
                    200 to """{"status":"success","version":9,"maxGaleriPhotos":10,"spreadsheetName":"Aplikasi_Warga","supportsImageFormula":true,"supportsDriveUpload":true,"driveAuthorized":true,"supportsAspirasiMeta":true}"""
                }
                "respond_aspirasi" -> {
                    val dataJson = JSONObject(dataStr)
                    val existing = remoteSheetRows.firstOrNull { it.optString("ID") == targetId }
                    if (existing != null) {
                        // Hanya memperbarui Kolom K..R tanpa menduplikasi baris atau mengubah A..J
                        existing.put("Status", dataJson.optString("status"))
                        existing.put("Nama_Pengurus", dataJson.optString("namaPengurus").ifBlank { dataJson.optString("penanggapNama") })
                        existing.put("Tanggapan_pengurus", dataJson.optString("tanggapanPengurus"))
                        existing.put("Foto_pengerjaan_1", dataJson.optString("fotoPengerjaan1"))
                        existing.put("Foto_pengerjaan_2", dataJson.optString("fotoPengerjaan2"))
                        existing.put("Foto_pengerjaan_3", dataJson.optString("fotoPengerjaan3"))
                        existing.put("Foto_pengerjaan_4", dataJson.optString("fotoPengerjaan4"))
                        existing.put("Foto_pengerjaan_5", dataJson.optString("fotoPengerjaan5"))
                    }
                    200 to """{"status":"success","message":"Berhasil","aspirasiMetaSaved":true}"""
                }
                else -> {
                    val arr = JSONArray()
                    remoteSheetRows.forEach { arr.put(it) }
                    val resp = JSONObject().apply {
                        put("status", "success")
                        put("version", 9)
                        put("supportsImageFormula", true)
                        put("supportsDriveUpload", true)
                        put("driveAuthorized", true)
                        put("supportsAspirasiMeta", true)
                        put("kas", JSONArray())
                        put("pengurus", JSONArray())
                        put("undangan", JSONArray())
                        put("galeri", JSONArray())
                        put("aspirasi", arr)
                    }
                    200 to resp.toString()
                }
            }
        }) { mockUrl ->
            val repo = WargaRepository(context)
            repo.updateSheetUrlOnly(mockUrl)
            // Seed active Pengurus account and log in as Pengurus
            repo.setRole(UserRole.ADMIN_UTAMA)
            val pengurusAcc = PengurusAccount(
                id = "ACC-TEST",
                namaAsli = "Bapak Rahman",
                username = "rahman05",
                password = "secret",
                aktif = true
            )
            repo.savePengurusAccount(pengurusAcc)
            val login = repo.verifyAuthority("rahman05", "secret")
            assertTrue(login is WargaRepository.AuthorityLoginResult.Success)

            // Fetch initial citizen item from mock sheet
            val initialSync = repo.syncWithSheet(force = true)
            assertTrue(initialSync.isSuccess)
            assertEquals(1, repo.aspirasiList.value.size)

            // Pengurus replies to citizen's aspirasi
            val replyResult = repo.respondAspirasi(
                id = "ASP-1001",
                status = "Diproses",
                tanggapan = "Tim teknisi RT segera memasang lampu baru besok pagi.",
                fotoPengerjaan = "",
                namaPenanggap = "Koordinator Keamanan"
            )
            assertTrue(replyResult.localSaved)
            assertTrue(replyResult.sheetSynced)
            assertTrue(replyResult.message.contains("Spreadsheet"))
            // Pastikan tidak ada baris duplikat dan Kolom A-J tetap utuh
            assertEquals(1, remoteSheetRows.size)
            assertEquals("Warga Blok C", remoteSheetRows[0].optString("Nama"))
            assertEquals("=IMAGE(\"https://drive.google.com/uc?id=masalah1\")", remoteSheetRows[0].optString("Foto_Masalah_1"))
            assertFalse(remoteSheetRows[0].optString("Tanggapan_pengurus").contains("META_BALASAN"))

            // Simulate a brand new citizen phone installing the app and fetching from central server
            val freshCitizenService = GoogleSheetsService()
            val freshCitizenData = freshCitizenService.fetchAllData(mockUrl)
            val citizenSeen = freshCitizenData.aspirasi?.firstOrNull { it.id == "ASP-1001" }
            assertNotNull(citizenSeen)
            assertEquals("Diproses", citizenSeen!!.status)
            assertEquals("Koordinator Keamanan", citizenSeen.penanggapNama)
            assertEquals("Tim teknisi RT segera memasang lampu baru besok pagi.", citizenSeen.tanggapanPengurus)
            assertEquals("https://drive.google.com/uc?id=masalah1", citizenSeen.fotoMasalah)
        }
    }

    @Test
    fun respondAspirasi_surfacesServerErrorsExplicitlyWithoutSilentFailure() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("aplikasi_warga_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        com.example.data.local.AppDatabase.getDatabase(context).aspirasiDao().clearAllAspirasi()

        withLocalHttpServer({ _, uri, body ->
            if (uri.contains("action=respond_aspirasi") || body.contains("action=respond_aspirasi")) {
                200 to """{"status":"error","message":"Password otoritas salah pada Spreadsheet!"}"""
            } else {
                200 to """{"status":"success","version":9,"supportsAspirasiMeta":true,"driveAuthorized":true,"kas":[],"pengurus":[],"undangan":[],"galeri":[],"aspirasi":[{"ID":"ASP-ERR","Tanggal":"2026-11-24","Nama":"Warga 1","Kategori":"Kritik","Pesan":"Jalan berlubang","Status":"Menunggu"}]}"""
            }
        }) { mockUrl ->
            val repo = WargaRepository(context)
            repo.updateSheetUrlOnly(mockUrl)
            repo.setRole(UserRole.ADMIN_UTAMA)
            assertTrue(repo.syncWithSheet(force = true).isSuccess)

            val result = repo.respondAspirasi(
                id = "ASP-ERR",
                status = "Diproses",
                tanggapan = "Sedang ditindaklanjuti",
                fotoPengerjaan = "",
                namaPenanggap = "Ketua RT"
            )
            assertFalse(result.sheetSynced)
            assertTrue(result.message.contains("gagal", ignoreCase = true))
            assertTrue("Actual message: ${result.message}", result.message.contains("Password otoritas salah", ignoreCase = true))
        }

        // Also check HTTP 500 & HTML login response on GoogleSheetsService directly
        withLocalHttpServer({ _, _, _ ->
            200 to "<html><body>accounts.google.com ServiceLogin</body></html>"
        }) { htmlUrl ->
            val service = GoogleSheetsService()
            val ok = service.sendAction(htmlUrl, "respond_aspirasi", "Aspirasi", "005011", JSONObject(), "ASP-1")
            assertFalse(ok)
            assertNotNull(service.lastActionError)
            assertTrue(service.lastActionError!!.contains("login Google", ignoreCase = true))
        }
    }

    @Test
    fun aspirasiEntity_roundTripsResponderRoleAndTimestamp() {
        val item = AspirasiItem(
            id = "ASP-ROOM",
            tanggal = "2026-11-24",
            nama = "Pak Joko",
            noRumah = "12",
            sembunyikanNama = true,
            kategori = "Masukan/Saran",
            pesan = "Perlu tempat sampah tambahan",
            status = "Selesai",
            tanggapanPengurus = "Sudah ditempatkan 2 unit",
            penanggapNama = "Admin RT",
            penanggapUsername = "Admin",
            penanggapNamaAsli = "Admin Induk",
            penanggapPeran = "Admin",
            waktuTanggapan = "2026-11-24T11:20:00+07:00"
        )
        val entity = AspirasiEntity.fromAspirasiItem(item, AspirasiEntity.SYNC_STATUS_SYNCED)
        val restored = entity.toAspirasiItem()
        assertEquals(item.penanggapPeran, restored.penanggapPeran)
        assertEquals(item.waktuTanggapan, restored.waktuTanggapan)
        assertEquals(item.penanggapNama, restored.penanggapNama)
        assertEquals(item.tanggapanPengurus, restored.tanggapanPengurus)
        assertNull(AspirasiReplyCodec.parseAndStrip("Balasan polos").penanggapPeran.takeIf { it.isNotBlank() })
    }

    @Test
    fun xlsxExtractor_extractsExactColumnsFtoJAndNtoRInAspirasi() {
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
                      <c r="N1" t="s"><v>3</v></c>
                      <c r="R1" t="s"><v>4</v></c>
                    </row>
                    <row r="2">
                      <c r="A2" t="s"><v>5</v></c>
                      <c r="F2" t="str"><f>IMAGE(&quot;https://drive.google.com/uc?id=fm1&quot;)</f><v></v></c>
                      <c r="J2" t="str"><f>IMAGE(&quot;https://drive.google.com/uc?id=fm5&quot;)</f><v></v></c>
                      <c r="N2" t="str"><f>IMAGE(&quot;https://drive.google.com/uc?id=fp1&quot;)</f><v></v></c>
                      <c r="R2" t="str"><f>IMAGE(&quot;https://drive.google.com/uc?id=fp5&quot;)</f><v></v></c>
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

    @Test
    fun aspirasiSheetColumns_andItemSlots_matchExactAtoRMappingAndIsolate5PhotoSlots() {
        val expectedHeaders = listOf(
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
        assertEquals(18, AspirasiSheetColumns.HEADERS.size)
        assertEquals(expectedHeaders, AspirasiSheetColumns.HEADERS)
        assertEquals(0, AspirasiSheetColumns.COL_ID)
        assertEquals(5, AspirasiSheetColumns.COL_FOTO_MASALAH_1)
        assertEquals(9, AspirasiSheetColumns.COL_FOTO_MASALAH_5)
        assertEquals(10, AspirasiSheetColumns.COL_STATUS)
        assertEquals(11, AspirasiSheetColumns.COL_NAMA_PENGURUS)
        assertEquals(12, AspirasiSheetColumns.COL_TANGGAPAN_PENGURUS)
        assertEquals(13, AspirasiSheetColumns.COL_FOTO_PENGERJAAN_1)
        assertEquals(17, AspirasiSheetColumns.COL_FOTO_PENGERJAAN_5)

        val item = AspirasiItem(
            id = "ASP-SLOTS",
            fotoMasalah = "https://ex.com/m1.jpg, https://ex.com/m1.jpg, https://ex.com/m2.jpg, https://ex.com/m3.jpg, https://ex.com/m4.jpg, https://ex.com/m5.jpg, https://ex.com/m6.jpg",
            fotoPengerjaan = "https://ex.com/p1.jpg, https://ex.com/p2.jpg, https://ex.com/p3.jpg, https://ex.com/p4.jpg, https://ex.com/p5.jpg, https://ex.com/p6.jpg"
        )
        assertEquals(5, item.fotoMasalahSlots.size)
        assertEquals("https://ex.com/m1.jpg", item.fotoMasalah1)
        assertEquals("https://ex.com/m2.jpg", item.fotoMasalah2)
        assertEquals("https://ex.com/m5.jpg", item.fotoMasalah5)
        assertEquals(5, item.fotoPengerjaanSlots.size)
        assertEquals("https://ex.com/p1.jpg", item.fotoPengerjaan1)
        assertEquals("https://ex.com/p5.jpg", item.fotoPengerjaan5)
        assertTrue(item.fotoMasalahSlots.none { it.contains("/p") })
        assertTrue(item.fotoPengerjaanSlots.none { it.contains("/m") })
    }

    @Test
    fun googleSheetsService_safelyRecoversShiftedLegacyRowWithoutMixingPhotoColumns() {
        val shiftedRow = JSONObject().apply {
            put("ID", "ASP-SHIFT")
            put("Tanggal", "2026-11-22")
            put("Nama", "Pak Budi")
            put("Kategori", "Kritik")
            put("Pesan", "Saluran air tersumbat")
            put("Foto_Masalah_1", "=IMAGE(\"https://drive.google.com/uc?id=mas1\")")
            // Legacy shifted bug: formula foto masuk ke Status, Status masuk ke Nama_Pengurus, nama pengurus di META_BALASAN
            put("Status", "=IMAGE(\"https://drive.google.com/uc?id=mas2\")")
            put("Nama_Pengurus", "Selesai")
            put(
                "Tanggapan_pengurus",
                "Sudah dikeruk bersih\n[[META_BALASAN:n=Ketua+RT&r=Admin&t=2026-11-22T14:00:00+07:00]]"
            )
            put("Foto_pengerjaan_1", "=IMAGE(\"https://drive.google.com/uc?id=work1\")")
            put("Foto_pengerjaan_2", "=IMAGE(\"https://drive.google.com/uc?id=work2\")")
        }
        val payload = JSONObject().apply {
            put("status", "success")
            put("version", 9)
            put("aspirasi", JSONArray().apply { put(shiftedRow) })
        }

        val item = GoogleSheetsService().parseSheetResponse(payload.toString()).aspirasi?.singleOrNull()
        assertNotNull(item)
        assertEquals("Selesai", item!!.status)
        assertEquals("Ketua RT", item.penanggapNama)
        assertEquals("Sudah dikeruk bersih", item.tanggapanPengurus)
        assertEquals(listOf("https://drive.google.com/uc?id=mas1"), item.fotoMasalahSlots)
        assertEquals(
            listOf("https://drive.google.com/uc?id=work1", "https://drive.google.com/uc?id=work2"),
            item.fotoPengerjaanSlots
        )
    }

    @Test
    fun respondAspirasi_with5WorkPhotos_preservesMasalahFtoJAndSyncsToCitizenAndRoomDao() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("aplikasi_warga_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val dao = com.example.data.local.AppDatabase.getDatabase(context).aspirasiDao()
        dao.clearAllAspirasi()

        val remoteSheetRows = CopyOnWriteArrayList<JSONObject>()
        remoteSheetRows.add(
            JSONObject().apply {
                put("ID", "ASP-WORK5")
                put("Tanggal", "2026-11-24")
                put("Nama", "Ibu Sari")
                put("Kategori", "Masukan/Saran")
                put("Pesan", "Perbaikan taman bermain")
                put("Foto_Masalah_1", "=IMAGE(\"https://drive.google.com/uc?id=issue1\")")
                put("Foto_Masalah_2", "=IMAGE(\"https://drive.google.com/uc?id=issue2\")")
                put("Foto_Masalah_3", "=IMAGE(\"https://drive.google.com/uc?id=issue3\")")
                put("Status", "Menunggu")
                put("Nama_Pengurus", "")
                put("Tanggapan_pengurus", "")
            }
        )

        withLocalHttpServer({ _, uri, body ->
            fun parseParam(source: String, key: String, isUri: Boolean): String {
                val raw = if (isUri) source.substringAfter("?", "") else source
                return raw.split("&")
                    .mapNotNull {
                        val idx = it.indexOf("=")
                        if (idx <= 0) null
                        else URLDecoder.decode(it.substring(0, idx), "UTF-8") to URLDecoder.decode(it.substring(idx + 1), "UTF-8")
                    }
                    .firstOrNull { it.first == key }?.second.orEmpty()
            }
            val action = parseParam(uri, "action", true).ifBlank { parseParam(body, "action", false) }
            val dataStr = parseParam(uri, "data", true).ifBlank { parseParam(body, "data", false) }
            val targetId = parseParam(uri, "targetId", true).ifBlank { parseParam(body, "targetId", false) }

            when (action) {
                "test_connection" -> {
                    200 to """{"status":"success","version":9,"supportsImageFormula":true,"supportsDriveUpload":true,"driveAuthorized":true,"supportsAspirasiMeta":true}"""
                }
                "respond_aspirasi" -> {
                    val dataJson = JSONObject(dataStr)
                    val existing = remoteSheetRows.firstOrNull { it.optString("ID") == targetId }
                    if (existing != null) {
                        existing.put("Status", dataJson.optString("status"))
                        existing.put("Nama_Pengurus", dataJson.optString("namaPengurus"))
                        existing.put("Tanggapan_pengurus", dataJson.optString("tanggapanPengurus"))
                        existing.put("Foto_pengerjaan_1", dataJson.optString("fotoPengerjaan1"))
                        existing.put("Foto_pengerjaan_2", dataJson.optString("fotoPengerjaan2"))
                        existing.put("Foto_pengerjaan_3", dataJson.optString("fotoPengerjaan3"))
                        existing.put("Foto_pengerjaan_4", dataJson.optString("fotoPengerjaan4"))
                        existing.put("Foto_pengerjaan_5", dataJson.optString("fotoPengerjaan5"))
                    }
                    200 to """{"status":"success","message":"Berhasil","aspirasiMetaSaved":true}"""
                }
                else -> {
                    val arr = JSONArray()
                    remoteSheetRows.forEach { arr.put(it) }
                    200 to JSONObject().apply {
                        put("status", "success")
                        put("version", 9)
                        put("driveAuthorized", true)
                        put("supportsAspirasiMeta", true)
                        put("kas", JSONArray())
                        put("pengurus", JSONArray())
                        put("undangan", JSONArray())
                        put("galeri", JSONArray())
                        put("aspirasi", arr)
                    }.toString()
                }
            }
        }) { mockUrl ->
            val repo = WargaRepository(context)
            repo.updateSheetUrlOnly(mockUrl)
            repo.setRole(UserRole.ADMIN_UTAMA)
            assertTrue(repo.syncWithSheet(force = true).isSuccess)

            val fiveWorkUrls = (1..5).joinToString(",") { "https://drive.google.com/uc?id=done$it" }
            val result = repo.respondAspirasi(
                id = "ASP-WORK5",
                status = "Selesai",
                tanggapan = "Pengecatan dan perbaikan ayunan sudah tuntas.",
                fotoPengerjaan = fiveWorkUrls,
                namaPenanggap = "Koordinator Sarpras"
            )
            assertTrue(result.sheetSynced)

            val row = remoteSheetRows.single()
            assertEquals("=IMAGE(\"https://drive.google.com/uc?id=issue1\")", row.optString("Foto_Masalah_1"))
            assertEquals("=IMAGE(\"https://drive.google.com/uc?id=issue3\")", row.optString("Foto_Masalah_3"))
            assertEquals("Selesai", row.optString("Status"))
            assertEquals("Koordinator Sarpras", row.optString("Nama_Pengurus"))
            assertEquals("Pengecatan dan perbaikan ayunan sudah tuntas.", row.optString("Tanggapan_pengurus"))
            for (slot in 1..5) {
                assertEquals("=IMAGE(\"https://drive.google.com/uc?id=done$slot\")", row.optString("Foto_pengerjaan_$slot"))
            }

            // Verify fresh citizen device reads exact separated Masalah (F-J) & Pengerjaan (N-R)
            val citizenFetched = GoogleSheetsService().fetchAllData(mockUrl).aspirasi?.singleOrNull()
            assertNotNull(citizenFetched)
            assertEquals(3, citizenFetched!!.fotoMasalahSlots.size)
            assertEquals(5, citizenFetched.fotoPengerjaanSlots.size)
            assertEquals("https://drive.google.com/uc?id=issue1", citizenFetched.fotoMasalah1)
            assertEquals("https://drive.google.com/uc?id=done5", citizenFetched.fotoPengerjaan5)

            // Verify Room atomic replaceAllAspirasi round-trip
            dao.replaceAllAspirasi(listOf(AspirasiEntity.fromAspirasiItem(citizenFetched, AspirasiEntity.SYNC_STATUS_SYNCED)))
            val roomRestored = dao.getAllAspirasiList().single().toAspirasiItem()
            assertEquals("Selesai", roomRestored.status)
            assertEquals("Koordinator Sarpras", roomRestored.penanggapNama)
            assertEquals(3, roomRestored.fotoMasalahSlots.size)
            assertEquals(5, roomRestored.fotoPengerjaanSlots.size)
        }
    }
}
