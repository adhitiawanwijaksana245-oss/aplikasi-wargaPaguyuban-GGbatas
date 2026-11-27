package com.example

import com.example.data.model.UserRole
import com.example.data.source.GoogleSheetsService
import com.example.util.ScriptSyncDiagnostics
import com.example.util.SyncHealthState
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetSocketAddress

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class GoogleSheetsFetchCrossVerifyTest {

    private suspend fun withLocalHttpServer(
        handler: (pathAndQuery: String, body: String) -> Pair<Int, String>,
        block: suspend (baseUrl: String) -> Unit
    ) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/exec") { exchange ->
            val requestBody = exchange.requestBody.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val uri = exchange.requestURI.toString()
            val (statusCode, responseBody) = handler(uri, requestBody)
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
    fun fetchAllData_crossVerifiesTestConnectionWhenDoGetOmitsVersionMetadata() = runBlocking {
        withLocalHttpServer({ uri, _ ->
            if (uri.contains("action=test_connection")) {
                200 to """{"status":"success","version":"v9","maxGaleriPhotos":10,"spreadsheetName":"Aplikasi_Warga","supportsImageFormula":true,"supportsDriveUpload":true,"driveAuthorized":true}"""
            } else {
                // Simulate a partial doGet payload missing version/driveAuthorized, plus an in-cell CellImage placeholder
                200 to """{"status":"success","kas":[{"id":"KAS-1","tanggal":"2026-11-10","keterangan":"Iuran","masuk":50000,"keluar":0,"bukti_foto":"{valueType: CellImage}"}],"pengurus":[],"undangan":[],"galeri":[],"aspirasi":[]}"""
            }
        }) { url ->
            val service = GoogleSheetsService()
            val response = service.fetchAllData(url)

            assertEquals(9, response.scriptVersion)
            assertTrue(response.driveAuthorized)
            assertTrue(response.supportsDriveUpload)
            assertNull(service.lastDriveUploadError)

            val health = SyncHealthState(
                isSyncing = false,
                hasVerifiedScriptStatus = true,
                lastSyncSucceeded = true,
                scriptVersion = response.scriptVersion,
                driveAuthorized = response.driveAuthorized,
                hasUnreadableImageFormula = response.hasUnreadableImageFormula
            )
            listOf(UserRole.WARGA, UserRole.PENGURUS, UserRole.ADMIN_UTAMA).forEach { role ->
                val eval = ScriptSyncDiagnostics.evaluateSyncHealth(health, role)
                assertFalse("Should not trigger false script update warning for $role", eval.needsScriptUpdate)
                assertEquals("🟢 Tersinkron Realtime — Aplikasi_Warga", eval.statusMessage)
            }
        }
    }

    @Test
    fun fetchAllData_detectsGenuineV2AndReportsConsistentRoleDiagnostics() = runBlocking {
        withLocalHttpServer({ uri, _ ->
            if (uri.contains("action=test_connection")) {
                200 to """{"status":"success","version":2,"maxGaleriPhotos":5,"spreadsheetName":"Aplikasi_Warga","supportsImageFormula":true,"supportsDriveUpload":false}"""
            } else {
                200 to """{"status":"success","version":2,"kas":[],"pengurus":[],"undangan":[],"galeri":[],"aspirasi":[]}"""
            }
        }) { url ->
            val service = GoogleSheetsService()
            val response = service.fetchAllData(url)

            assertEquals(2, response.scriptVersion)
            assertFalse(response.driveAuthorized)

            val health = SyncHealthState(
                isSyncing = false,
                hasVerifiedScriptStatus = true,
                lastSyncSucceeded = true,
                scriptVersion = response.scriptVersion,
                driveAuthorized = response.driveAuthorized,
                hasUnreadableImageFormula = response.hasUnreadableImageFormula
            )
            val adminEval = ScriptSyncDiagnostics.evaluateSyncHealth(health, UserRole.ADMIN_UTAMA)
            assertTrue(adminEval.needsScriptUpdate)
            assertTrue(adminEval.statusMessage.contains("v2"))

            val wargaEval = ScriptSyncDiagnostics.evaluateSyncHealth(health, UserRole.WARGA)
            assertEquals("🟢 Tersinkron Realtime — Aplikasi_Warga", wargaEval.statusMessage)
            assertFalse(wargaEval.isWarning)
        }
    }
}
