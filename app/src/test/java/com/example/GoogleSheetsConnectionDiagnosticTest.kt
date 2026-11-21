package com.example

import com.example.data.source.GoogleAppsScriptTemplate
import com.example.data.source.GoogleSheetsService
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetSocketAddress

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GoogleSheetsConnectionDiagnosticTest {

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
    fun appsScriptTemplate_containsV9DriveAuthorizationAndPasswordSync() {
        val script = GoogleAppsScriptTemplate.SCRIPT_CODE
        assertTrue(script.contains("version: 9"))
        assertTrue(script.contains("function otorisasiDanCekSetup()"))
        assertTrue(script.contains("function checkDrivePermission()"))
        assertTrue(script.contains("function getConfiguredAuthPassword()"))
        assertTrue(script.contains("action === \"update_auth_password\""))
        assertTrue(script.contains("driveAuthorized: driveStatus.ok"))
    }

    @Test
    fun parseSheetResponse_rejectsHtmlAndErrorStatus() {
        val service = GoogleSheetsService()
        var htmlThrew = false
        try {
            service.parseSheetResponse("<!DOCTYPE html><html><body>Login</body></html>")
        } catch (e: Exception) {
            htmlThrew = e.message?.contains("JSON", ignoreCase = true) == true
        }
        assertTrue("Expected non-JSON HTML response to throw", htmlThrew)

        var errThrew = false
        try {
            service.parseSheetResponse("""{"status":"error","message":"Spreadsheet tidak ditemukan"}""")
        } catch (e: Exception) {
            errThrew = e.message?.contains("Spreadsheet tidak ditemukan") == true
        }
        assertTrue("Expected error status JSON to throw", errThrew)
    }

    @Test
    fun testConnection_reportsOldV1ScriptAsActionableFailure() = runBlocking {
        withLocalHttpServer({ _, _ ->
            200 to """{"status":"success","spreadsheetName":"Aplikasi_Warga","message":"Terhubung ke Aplikasi_Warga"}"""
        }) { url ->
            val service = GoogleSheetsService()
            val (ok, msg) = service.testConnection(url)
            assertFalse(ok)
            assertTrue(msg.contains("versi lama", ignoreCase = true))
            assertTrue(msg.contains("Manage deployments", ignoreCase = true))
        }
    }

    @Test
    fun testConnection_reportsMissingDriveAuthorization() = runBlocking {
        withLocalHttpServer({ _, _ ->
            200 to """{"status":"success","version":9,"spreadsheetName":"Aplikasi_Warga","supportsImageFormula":true,"supportsDriveUpload":false,"driveAuthorized":false,"driveError":"You do not have permission to call DriveApp"}"""
        }) { url ->
            val service = GoogleSheetsService()
            val (ok, msg) = service.testConnection(url)
            assertFalse(ok)
            assertTrue(msg.contains("otorisasiDanCekSetup"))
        }
    }

    @Test
    fun testConnection_succeedsWhenV9AndDriveAuthorized() = runBlocking {
        withLocalHttpServer({ _, _ ->
            200 to """{"status":"success","version":9,"maxGaleriPhotos":10,"spreadsheetName":"Aplikasi_Warga","supportsImageFormula":true,"supportsDriveUpload":true,"driveAuthorized":true}"""
        }) { url ->
            val service = GoogleSheetsService()
            val (ok, msg) = service.testConnection(url)
            assertTrue(ok)
            assertTrue(msg.contains("Koneksi Berhasil"))
            assertTrue(msg.contains("v9"))
        }
    }

    @Test
    fun uploadPhotoToDrive_recordsClearErrorWhenScriptIsOldAndClearsOnSuccess() = runBlocking {
        val service = GoogleSheetsService()
        withLocalHttpServer({ _, _ ->
            200 to """{"status":"error","message":"Nama sheet harus diisi"}"""
        }) { url ->
            val uploaded = service.uploadPhotoToDrive(url, "005011", "AAAA", "foto.jpg")
            assertNull(uploaded)
            assertNotNull(service.lastDriveUploadError)
            assertTrue(service.lastDriveUploadError!!.contains("versi lama", ignoreCase = true))
        }

        service.resetCapabilityCache()
        withLocalHttpServer({ _, _ ->
            200 to """{"status":"success","fileId":"abc123XYZ","url":"https://drive.google.com/uc?export=view&id=abc123XYZ"}"""
        }) { url ->
            val uploaded = service.uploadPhotoToDrive(url, "005011", "AAAA", "foto.jpg")
            assertNotNull(uploaded)
            assertEquals("https://drive.google.com/uc?export=view&id=abc123XYZ", uploaded)
            assertNull(service.lastDriveUploadError)
        }
    }

    @Test
    fun testConnection_reportsReferenceErrorWithCopyPasteHint() = runBlocking {
        withLocalHttpServer({ _, _ ->
            200 to """{"status":"error","message":"ReferenceError: checkDrivePermission is not defined"}"""
        }) { url ->
            val service = GoogleSheetsService()
            val (ok, msg) = service.testConnection(url)
            assertFalse(ok)
            assertTrue(msg.contains("ReferenceError: checkDrivePermission is not defined"))
            assertTrue(msg.contains("terpotong", ignoreCase = true))
        }
    }

    @Test
    fun parseSheetResponse_tracksScriptVersionAndDriveAuthorization() {
        val service = GoogleSheetsService()
        val v1Parsed = service.parseSheetResponse("""{"status":"success","kas":[]}""")
        assertEquals(1, v1Parsed.scriptVersion)
        assertFalse(v1Parsed.driveAuthorized)

        val v9NoDrive = service.parseSheetResponse("""{"status":"success","version":9,"driveAuthorized":false,"supportsDriveUpload":false,"kas":[]}""")
        assertEquals(9, v9NoDrive.scriptVersion)
        assertFalse(v9NoDrive.driveAuthorized)

        val v9Authorized = service.parseSheetResponse("""{"status":"success","version":9,"driveAuthorized":true,"supportsDriveUpload":true,"kas":[]}""")
        assertEquals(9, v9Authorized.scriptVersion)
        assertTrue(v9Authorized.driveAuthorized)
    }
}
