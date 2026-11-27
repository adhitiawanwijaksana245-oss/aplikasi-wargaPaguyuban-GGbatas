package com.example

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.AspirasiEntity
import com.example.data.model.AspirasiItem
import com.example.data.model.UserRole
import com.example.data.repository.WargaRepository
import com.example.ui.screens.AspirasiScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.WargaViewModel
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AspirasiScreenSyncTest {

    @get:Rule
    val composeRule = createComposeRule()

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
    fun aspirasiScreen_hidesSpreadsheetBannerInWargaAndPengurusModesAndShowsInAdminMode() {
        var currentRole by mutableStateOf(UserRole.WARGA)

        composeRule.setContent {
            MyApplicationTheme {
                AspirasiScreen(
                    aspirasiList = emptyList(),
                    userRole = currentRole,
                    onOpenSubmitAspirasi = {},
                    onRespondAspirasi = {},
                    onDeleteAspirasi = {}
                )
            }
        }

        // Mode Warga: banner disembunyikan sepenuhnya
        composeRule.onNodeWithTag("spreadsheet_status_card").assertDoesNotExist()
        composeRule.onNodeWithText("Terkoneksi ke Spreadsheet", substring = true).assertDoesNotExist()

        // Mode Pengurus: banner tetap disembunyikan sepenuhnya
        composeRule.runOnUiThread {
            currentRole = UserRole.PENGURUS
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("spreadsheet_status_card").assertDoesNotExist()
        composeRule.onNodeWithText("Terkoneksi ke Spreadsheet", substring = true).assertDoesNotExist()

        // Mode Admin: banner tampil dan aktif
        composeRule.runOnUiThread {
            currentRole = UserRole.ADMIN_UTAMA
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("spreadsheet_status_card").assertIsDisplayed()
        composeRule.onNodeWithText("Mode Admin: Database Aktif", substring = true).assertIsDisplayed()
    }

    @Test
    fun submitRespondAndSync_automaticallyCallsDoGetAndUpdatesStatusFromSpreadsheet() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("aplikasi_warga_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        val dao = AppDatabase.getDatabase(context).aspirasiDao()
        dao.clearAllAspirasi()

        val doGetCount = AtomicInteger(0)
        val remoteRows = CopyOnWriteArrayList<JSONObject>()

        withLocalHttpServer({ _, uri, body ->
            fun parseParam(raw: String, key: String): String {
                val query = raw.substringAfter("?", raw)
                return query.split("&")
                    .mapNotNull {
                        val idx = it.indexOf("=")
                        if (idx <= 0) null
                        else URLDecoder.decode(it.substring(0, idx), "UTF-8") to URLDecoder.decode(it.substring(idx + 1), "UTF-8")
                    }
                    .firstOrNull { it.first == key }?.second.orEmpty()
            }
            val action = parseParam(uri, "action").ifBlank { parseParam(body, "action") }
            val dataStr = parseParam(uri, "data").ifBlank { parseParam(body, "data") }
            val targetId = parseParam(uri, "targetId").ifBlank { parseParam(body, "targetId") }

            when (action) {
                "add" -> {
                    val dataJson = JSONObject(dataStr)
                    remoteRows.add(
                        JSONObject().apply {
                            put("ID", dataJson.optString("id"))
                            put("Tanggal", dataJson.optString("tanggal"))
                            put("Nama", dataJson.optString("nama"))
                            put("Kategori", dataJson.optString("kategori"))
                            put("Pesan", dataJson.optString("pesan"))
                            put("Status", dataJson.optString("status").ifBlank { "Menunggu" })
                            put("Nama_Pengurus", dataJson.optString("namaPengurus"))
                            put("Tanggapan_pengurus", dataJson.optString("tanggapanPengurus"))
                        }
                    )
                    200 to """{"status":"success","message":"Berhasil"}"""
                }
                "respond_aspirasi" -> {
                    val dataJson = JSONObject(dataStr)
                    val existing = remoteRows.firstOrNull { it.optString("ID") == targetId }
                    if (existing != null) {
                        existing.put("Status", dataJson.optString("status"))
                        existing.put("Nama_Pengurus", dataJson.optString("namaPengurus"))
                        existing.put("Tanggapan_pengurus", dataJson.optString("tanggapanPengurus"))
                    }
                    200 to """{"status":"success","message":"Berhasil"}"""
                }
                else -> {
                    doGetCount.incrementAndGet()
                    val arr = JSONArray()
                    remoteRows.forEach { arr.put(it) }
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
            repo.syncWithSheet(force = true)
            val baselineGets = doGetCount.get()

            // 1. Warga submits aspirasi -> auto doGet runs right after sendAction("add")
            val submitResult = repo.submitAspirasiWarga(
                AspirasiItem(
                    id = "ASP-AUTO-1",
                    tanggal = "2026-11-24",
                    nama = "Pak Hendra",
                    kategori = "Masukan/Saran",
                    pesan = "Mohon cek saluran air depan mushola",
                    status = "Menunggu"
                )
            )
            assertTrue(submitResult.sheetSynced)
            assertTrue(
                "Expected automatic doGet after submitAspirasiWarga",
                doGetCount.get() > baselineGets
            )

            // 2. Simulate stale local PENDING_UPLOAD while Spreadsheet status changed to Selesai directly
            dao.insertAspirasi(
                AspirasiEntity.fromAspirasiItem(
                    AspirasiItem(
                        id = "ASP-AUTO-1",
                        tanggal = "2026-11-24",
                        nama = "Pak Hendra",
                        kategori = "Masukan/Saran",
                        pesan = "Mohon cek saluran air depan mushola",
                        status = "Menunggu"
                    ),
                    AspirasiEntity.SYNC_STATUS_PENDING_UPLOAD
                )
            )
            remoteRows.first { it.optString("ID") == "ASP-AUTO-1" }.apply {
                put("Status", "Selesai")
                put("Nama_Pengurus", "Ketua RT")
                put("Tanggapan_pengurus", "Sudah dibersihkan")
            }

            val syncResult = repo.syncWithSheet(force = true)
            assertTrue(syncResult.isSuccess)
            val syncedItem = repo.aspirasiList.value.firstOrNull { it.id == "ASP-AUTO-1" }
            assertEquals("Selesai", syncedItem?.status)
            assertEquals("Sudah dibersihkan", syncedItem?.tanggapanPengurus)

            // 3. Admin responds -> auto doGet runs right after sendAction("respond_aspirasi")
            val getsBeforeReply = doGetCount.get()
            repo.setRole(UserRole.ADMIN_UTAMA)
            val replyResult = repo.respondAspirasi(
                id = "ASP-AUTO-1",
                status = "Selesai",
                tanggapan = "Sudah selesai dikerjakan bersama warga",
                fotoPengerjaan = "",
                namaPenanggap = "Ketua RT 005"
            )
            assertTrue(replyResult.sheetSynced)
            assertTrue(
                "Expected automatic doGet after respondAspirasi",
                doGetCount.get() > getsBeforeReply
            )
            val afterReplyItem = repo.aspirasiList.value.firstOrNull { it.id == "ASP-AUTO-1" }
            assertEquals("Selesai", afterReplyItem?.status)
            assertEquals("Ketua RT 005", afterReplyItem?.penanggapNama)
            assertEquals("Sudah selesai dikerjakan bersama warga", afterReplyItem?.tanggapanPengurus)
        }
    }

    @Test
    fun viewModel_openingAspirasiTab_immediatelyRefreshesStatusFromSpreadsheet() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences("aplikasi_warga_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        AppDatabase.getDatabase(app).aspirasiDao().clearAllAspirasi()

        val doGetCount = AtomicInteger(0)
        val rowStatus = CopyOnWriteArrayList<String>().apply { add("Menunggu") }

        withLocalHttpServer({ _, _, _ ->
            doGetCount.incrementAndGet()
            val statusVal = rowStatus.firstOrNull() ?: "Menunggu"
            val row = JSONObject().apply {
                put("ID", "ASP-TAB-1")
                put("Tanggal", "2026-11-24")
                put("Nama", "Ibu Sari")
                put("Kategori", "Kritik")
                put("Pesan", "Gerbang perlu pelumas")
                put("Status", statusVal)
                put("Nama_Pengurus", if (statusVal == "Selesai") "Pengurus Piket" else "")
                put("Tanggapan_pengurus", if (statusVal == "Selesai") "Sudah dilumasi" else "")
            }
            200 to JSONObject().apply {
                put("status", "success")
                put("version", 9)
                put("driveAuthorized", true)
                put("kas", JSONArray())
                put("pengurus", JSONArray())
                put("undangan", JSONArray())
                put("galeri", JSONArray())
                put("aspirasi", JSONArray().apply { put(row) })
            }.toString()
        }) { mockUrl ->
            val vm = WargaViewModel(app)
            vm.repository.updateSheetUrlOnly(mockUrl)
            vm.repository.syncWithSheet(force = true)
            assertEquals("Menunggu", vm.aspirasiList.value.firstOrNull { it.id == "ASP-TAB-1" }?.status)

            // Update status on Spreadsheet and open Aspirasi tab immediately (< 4s window)
            val beforeTabGets = doGetCount.get()
            rowStatus[0] = "Selesai"
            vm.selectTab(4)

            var updatedStatus: String? = null
            repeat(30) {
                updatedStatus = vm.aspirasiList.value.firstOrNull { it.id == "ASP-TAB-1" }?.status
                if (updatedStatus == "Selesai" && doGetCount.get() > beforeTabGets) {
                    return@repeat
                }
                delay(100)
            }

            assertTrue("Expected doGet when opening Aspirasi tab", doGetCount.get() > beforeTabGets)
            assertEquals("Selesai", updatedStatus)
        }
    }
}
