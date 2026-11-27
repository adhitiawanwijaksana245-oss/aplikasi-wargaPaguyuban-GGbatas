package com.example.data.source

import android.util.Log
import com.example.data.model.*
import com.example.util.ScriptCapabilities
import com.example.util.ScriptSyncDiagnostics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class ConnectionCheckResult(
    val ok: Boolean,
    val message: String,
    val capabilities: ScriptCapabilities? = null,
    val reachable: Boolean = false
)

class GoogleSheetsService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    companion object {
        private const val TAG = "GoogleSheetsService"
    }

    @Volatile
    private var unsupportedDriveScriptUrl: String? = null

    @Volatile
    private var multiPhotoSupportedUrl: String? = null

    @Volatile
    private var galeri10ColsSupportedUrl: String? = null

    @Volatile
    private var multiPhotoCheckedUrl: String? = null

    @Volatile
    private var aspirasiMetaSupportedUrl: String? = null

    @Volatile
    var lastDriveUploadError: String? = null
        private set

    @Volatile
    var lastActionError: String? = null
        private set

    fun isAspirasiMetaSupported(url: String): Boolean {
        val cleanUrl = url.trim()
        return cleanUrl.isNotBlank() && aspirasiMetaSupportedUrl == cleanUrl
    }

    fun resetCapabilityCache() {
        unsupportedDriveScriptUrl = null
        multiPhotoSupportedUrl = null
        galeri10ColsSupportedUrl = null
        multiPhotoCheckedUrl = null
        aspirasiMetaSupportedUrl = null
        lastDriveUploadError = null
        lastActionError = null
    }

    suspend fun supportsMultiPhotoColumns(url: String): Boolean = withContext(Dispatchers.IO) {
        val cleanUrl = url.trim()
        if (cleanUrl.isBlank() || cleanUrl.contains("docs.google.com/spreadsheets")) return@withContext false
        if (multiPhotoCheckedUrl == cleanUrl) {
            return@withContext multiPhotoSupportedUrl == cleanUrl
        }
        testConnection(cleanUrl)
        multiPhotoSupportedUrl == cleanUrl
    }

    suspend fun supportsGaleri10Columns(url: String): Boolean = withContext(Dispatchers.IO) {
        val cleanUrl = url.trim()
        if (cleanUrl.isBlank() || cleanUrl.contains("docs.google.com/spreadsheets")) return@withContext false
        if (multiPhotoCheckedUrl == cleanUrl) {
            return@withContext galeri10ColsSupportedUrl == cleanUrl
        }
        testConnection(cleanUrl)
        galeri10ColsSupportedUrl == cleanUrl
    }

    suspend fun fetchAllData(url: String): SheetDataResponse = withContext(Dispatchers.IO) {
        val cleanUrl = url.trim()
        if (cleanUrl.contains("docs.google.com/spreadsheets")) {
            throw Exception("URL adalah link spreadsheet docs.google.com. Gunakan URL Web App Google Apps Script (/exec).")
        }

        val request = Request.Builder()
            .url(cleanUrl)
            .get()
            .build()

        val (body, parsed) = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Gagal terhubung ke Google Sheets: HTTP ${response.code}")
            }
            val rawBody = response.body?.string() ?: throw Exception("Data kosong dari Spreadsheet")
            rawBody to parseSheetResponse(rawBody, cleanUrl)
        }

        val hydrated = if (parsed.hasUnreadableImageFormula) {
            val spreadsheetId = try {
                JSONObject(body).optString("spreadsheetId", "").trim()
            } catch (_: Exception) {
                ""
            }.ifBlank { GoogleAppsScriptTemplate.TARGET_SPREADSHEET_ID }
            hydrateFromPublicXlsx(parsed, spreadsheetId)
        } else {
            parsed
        }

        // Cross-verify capabilities via ?action=test_connection only if doGet omitted version/Drive metadata
        // or returned an older/incomplete script signature so we never show false v1/v2 warnings.
        if (!hydrated.hasExplicitVersion || hydrated.scriptVersion < 8 || !hydrated.hasExplicitDriveAuth || !hydrated.driveAuthorized) {
            val check = try {
                testConnectionDetailed(cleanUrl)
            } catch (_: Exception) {
                null
            }
            val verifiedCaps = check?.capabilities
            if (check != null && check.reachable && verifiedCaps != null) {
                val mergedVersion = maxOf(hydrated.scriptVersion, verifiedCaps.effectiveVersion)
                val mergedDrive = if (verifiedCaps.hasExplicitDriveAuth) {
                    verifiedCaps.driveAuthorized
                } else {
                    hydrated.driveAuthorized || verifiedCaps.driveAuthorized
                }
                if (mergedVersion >= 8 && mergedDrive) {
                    unsupportedDriveScriptUrl = null
                    lastDriveUploadError = null
                }
                return@withContext hydrated.copy(
                    scriptVersion = mergedVersion,
                    driveAuthorized = mergedDrive,
                    hasExplicitVersion = hydrated.hasExplicitVersion || verifiedCaps.hasExplicitVersion,
                    hasExplicitDriveAuth = hydrated.hasExplicitDriveAuth || verifiedCaps.hasExplicitDriveAuth,
                    supportsDriveUpload = hydrated.supportsDriveUpload || verifiedCaps.supportsDriveUpload,
                    maxGaleriPhotos = maxOf(hydrated.maxGaleriPhotos, verifiedCaps.maxGaleriPhotos)
                )
            }
        }
        hydrated
    }

    private fun hydrateFromPublicXlsx(
        parsed: SheetDataResponse,
        spreadsheetId: String
    ): SheetDataResponse {
        if (spreadsheetId.isBlank()) return parsed
        return try {
            val exportUrl = "https://docs.google.com/spreadsheets/d/$spreadsheetId/export?format=xlsx"
            val request = Request.Builder().url(exportUrl).get().build()
            val sheetImages = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return parsed
                val stream = response.body?.byteStream() ?: return parsed
                com.example.util.XlsxImageFormulaExtractor.extract(stream)
            }
            if (sheetImages.isEmpty()) return parsed

            val kasSheet = sheetImages["Kas_RT"]
            val hydratedKas = parsed.kas?.mapIndexed { idx, item ->
                if (item.buktiFoto.isNotBlank()) item
                else {
                    val cols = kasSheet?.byRowId?.get(item.id) ?: kasSheet?.byRowIndex?.get(idx)
                    val url = cols?.get("bukti_foto")
                        ?: cols?.get("Bukti_Foto")
                        ?: cols?.get("buktiFoto")
                        ?: ""
                    if (url.isNotBlank()) item.copy(buktiFoto = url) else item
                }
            }

            val pgrSheet = sheetImages["Pengurus"]
            val hydratedPgr = parsed.pengurus?.mapIndexed { idx, item ->
                if (item.foto.isNotBlank()) item
                else {
                    val cols = pgrSheet?.byRowId?.get(item.id) ?: pgrSheet?.byRowIndex?.get(idx)
                    val url = cols?.get("Foto") ?: cols?.get("foto") ?: ""
                    if (url.isNotBlank()) item.copy(foto = url) else item
                }
            }

            val galSheet = sheetImages["Galeri"]
            val hydratedGal = parsed.galeri?.mapIndexed { idx, item ->
                val cols = galSheet?.byRowId?.get(item.id) ?: galSheet?.byRowIndex?.get(idx)
                if (cols == null) {
                    item
                } else {
                    fun findGalCol(vararg candidates: String): String? {
                        for (c in candidates) {
                            cols[c]?.let { return it }
                            cols.entries.firstOrNull { it.key.equals(c, ignoreCase = true) }?.let { return it.value }
                        }
                        return null
                    }
                    val existingParts = item.foto.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    val extractedOrdered = listOfNotNull(
                        findGalCol("Foto_1", "Foto", "foto1", "foto"),
                        findGalCol("Foto_2", "foto2"),
                        findGalCol("Foto_3", "foto3"),
                        findGalCol("Foto_4", "foto4"),
                        findGalCol("Foto_5", "foto5"),
                        findGalCol("Foto_6", "foto6"),
                        findGalCol("Foto_7", "foto7"),
                        findGalCol("Foto_8", "foto8"),
                        findGalCol("Foto_9", "foto9"),
                        findGalCol("Foto_10", "foto10")
                    ).map { it.trim() }.filter { it.isNotBlank() }
                    val merged = (existingParts + extractedOrdered).distinct()
                    if (merged.isNotEmpty()) item.copy(foto = merged.joinToString(",")) else item
                }
            }

            val aspSheet = sheetImages["Aspirasi"]
            val hydratedAsp = parsed.aspirasi?.mapIndexed { idx, item ->
                val cols = aspSheet?.byRowId?.get(item.id) ?: aspSheet?.byRowIndex?.get(idx)
                if (cols == null) {
                    item
                } else {
                    fun findAspCol(vararg candidates: String): String? {
                        for (c in candidates) {
                            cols[c]?.let { return it }
                            cols.entries.firstOrNull { it.key.equals(c, ignoreCase = true) }?.let { return it.value }
                        }
                        return null
                    }
                    val existingMasalah = item.fotoMasalah.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    val extractedMasalah = listOfNotNull(
                        findAspCol("Foto_Masalah_1", "foto_masalah_1", "fotoMasalah1", "Foto_Masalah", "fotoMasalah"),
                        findAspCol("Foto_Masalah_2", "foto_masalah_2", "fotoMasalah2"),
                        findAspCol("Foto_Masalah_3", "foto_masalah_3", "fotoMasalah3"),
                        findAspCol("Foto_Masalah_4", "foto_masalah_4", "fotoMasalah4"),
                        findAspCol("Foto_Masalah_5", "foto_masalah_5", "fotoMasalah5")
                    ).flatMap { it.split(",") }.map { it.trim() }.filter { it.isNotBlank() }
                    val masalah = (existingMasalah + extractedMasalah).distinct().take(5).joinToString(",")

                    val existingPengerjaan = item.fotoPengerjaan.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    val extractedPengerjaan = listOfNotNull(
                        findAspCol("Foto_pengerjaan_1", "Foto_Pengerjaan_1", "foto_pengerjaan_1", "fotoPengerjaan1", "Foto_pengerjaan", "Foto_Pengerjaan", "fotoPengerjaan"),
                        findAspCol("Foto_pengerjaan_2", "Foto_Pengerjaan_2", "foto_pengerjaan_2", "fotoPengerjaan2"),
                        findAspCol("Foto_pengerjaan_3", "Foto_Pengerjaan_3", "foto_pengerjaan_3", "fotoPengerjaan3"),
                        findAspCol("Foto_pengerjaan_4", "Foto_Pengerjaan_4", "foto_pengerjaan_4", "fotoPengerjaan4"),
                        findAspCol("Foto_pengerjaan_5", "Foto_Pengerjaan_5", "foto_pengerjaan_5", "fotoPengerjaan5")
                    ).flatMap { it.split(",") }.map { it.trim() }.filter { it.isNotBlank() }
                    val pengerjaan = (existingPengerjaan + extractedPengerjaan).distinct().take(5).joinToString(",")
                    item.copy(fotoMasalah = masalah, fotoPengerjaan = pengerjaan)
                }
            }

            parsed.copy(
                kas = hydratedKas,
                pengurus = hydratedPgr,
                galeri = hydratedGal,
                aspirasi = hydratedAsp
            )
        } catch (e: Exception) {
            Log.w(TAG, "Gagal ekstrak formula gambar dari XLSX publik: ${e.message}")
            parsed
        }
    }

    suspend fun testConnection(url: String): Pair<Boolean, String> {
        val result = testConnectionDetailed(url)
        return Pair(result.ok, result.message)
    }

    suspend fun testConnectionDetailed(url: String): ConnectionCheckResult = withContext(Dispatchers.IO) {
        try {
            val cleanUrl = url.trim()
            if (cleanUrl.contains("docs.google.com/spreadsheets")) {
                return@withContext ConnectionCheckResult(
                    ok = false,
                    message = "Tautan 'docs.google.com' adalah halaman spreadsheet Anda di browser. Anda memerlukan URL Web App (berakhiran /exec dari Apps Script) agar Google mengizinkan aplikasi menulis & membaca data tanpa login akun Google."
                )
            }
            if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
                return@withContext ConnectionCheckResult(
                    ok = false,
                    message = "Format URL harus diawali dengan https://"
                )
            }

            val pingUrl = if (cleanUrl.contains("?")) {
                "$cleanUrl&action=test_connection"
            } else {
                "$cleanUrl?action=test_connection"
            }
            val request = Request.Builder().url(pingUrl).get().build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                val trimmed = body.trim()
                if (!response.isSuccessful) {
                    return@withContext ConnectionCheckResult(
                        ok = false,
                        message = "Gagal terhubung ke Web App (HTTP ${response.code}). Periksa URL /exec dan status deployment Apps Script."
                    )
                }
                if (!trimmed.startsWith("{")) {
                    val isHtmlLogin = trimmed.contains("ServiceLogin", ignoreCase = true) ||
                        trimmed.contains("accounts.google.com", ignoreCase = true)
                    val isDrivePerm = trimmed.contains("DriveApp", ignoreCase = true) ||
                        trimmed.contains("permission", ignoreCase = true)
                    val hint = when {
                        isDrivePerm -> "Izin Google Drive pada Apps Script belum diaktifkan. Di editor Apps Script, jalankan fungsi 'otorisasiDanCekSetup' lalu klik Allow."
                        isHtmlLogin -> "Apps Script meminta login Google. Pastikan 'Who has access' diatur ke 'Anyone' (Siapa saja), lalu Deploy ulang."
                        else -> "Server tidak mengembalikan JSON (HTTP ${response.code}). Pastikan URL berakhiran '/exec' dan akses Web App diset ke 'Anyone'."
                    }
                    return@withContext ConnectionCheckResult(ok = false, message = hint)
                }

                val json = try { JSONObject(trimmed) } catch (_: Exception) { null }
                if (json == null) {
                    return@withContext ConnectionCheckResult(
                        ok = false,
                        message = "Respons Apps Script tidak valid: ${trimmed.take(120)}"
                    )
                }
                if (json.optString("status", "").equals("error", ignoreCase = true)) {
                    val errMsg = json.optString("message", "Terjadi kesalahan di Apps Script.")
                    val friendly = if (errMsg.contains("ReferenceError", ignoreCase = true)) {
                        "$errMsg — Ada fungsi/baris kode Apps Script yang hilang atau terpotong saat copy-paste. Hapus SEMUA kode di Code.gs, tempel ulang seluruh Script v9 dari awal sampai akhir, Simpan, jalankan 'otorisasiDanCekSetup', lalu Deploy -> Manage deployments -> Edit -> New version."
                    } else {
                        errMsg
                    }
                    return@withContext ConnectionCheckResult(ok = false, message = friendly)
                }

                if (trimmed.contains("success")) {
                    val ssName = json.optString("spreadsheetName", "")
                    val driveError = json.optString("driveError", "")
                    val caps = ScriptSyncDiagnostics.resolveScriptCapabilities(json)
                    val supports10Cols = caps.effectiveVersion >= 3 || caps.maxGaleriPhotos >= 10
                    multiPhotoCheckedUrl = cleanUrl
                    multiPhotoSupportedUrl = if (caps.supportsImageFormula) cleanUrl else null
                    galeri10ColsSupportedUrl = if (supports10Cols) cleanUrl else null
                    if (json.optBoolean("supportsAspirasiMeta", false)) {
                        aspirasiMetaSupportedUrl = cleanUrl
                    }

                    val (ok, msg) = ScriptSyncDiagnostics.formatConnectionTestResult(
                        spreadsheetName = ssName,
                        capabilities = caps,
                        driveError = driveError
                    )
                    if (ok || (caps.effectiveVersion >= 8 && caps.driveAuthorized)) {
                        unsupportedDriveScriptUrl = null
                        lastDriveUploadError = null
                    }
                    ConnectionCheckResult(
                        ok = ok,
                        message = msg,
                        capabilities = caps,
                        reachable = true
                    )
                } else {
                    ConnectionCheckResult(
                        ok = false,
                        message = "Respons HTTP ${response.code}: ${trimmed.take(120)}"
                    )
                }
            }
        } catch (e: Exception) {
            ConnectionCheckResult(
                ok = false,
                message = "Koneksi gagal: ${e.localizedMessage ?: "Cek koneksi internet"}"
            )
        }
    }

    /**
     * Mengirim aksi ke Google Apps Script Web App secara fail-safe.
     * Mengirim query parameter pada URL agar tidak hilang saat redirect HTTP 302 ke googleusercontent.com.
     */
    suspend fun sendAction(
        url: String,
        action: String,
        sheet: String,
        password: String,
        dataJson: JSONObject,
        targetId: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val cleanUrl = url.trim()
        if (cleanUrl.isBlank() || cleanUrl.contains("docs.google.com/spreadsheets")) {
            lastActionError = "URL Web App Apps Script (/exec) belum valid."
            return@withContext false
        }
        lastActionError = null

        try {
            val dataStr = dataJson.toString()
            val encodedData = URLEncoder.encode(dataStr, "UTF-8")
            val targetParam = if (targetId != null) "&targetId=" + URLEncoder.encode(targetId, "UTF-8") else ""
            val fullUrl = StringBuilder(cleanUrl)
                .append(if (cleanUrl.contains("?")) "&" else "?")
                .append("action=").append(action)
                .append("&sheet=").append(URLEncoder.encode(sheet, "UTF-8"))
                .append("&password=").append(URLEncoder.encode(password, "UTF-8"))
                .append("&data=").append(encodedData)
                .append(targetParam)
                .toString()

            if (fullUrl.length <= 1800) {
                val getRequest = Request.Builder()
                    .url(fullUrl)
                    .get()
                    .build()

                client.newCall(getRequest).execute().use { response ->
                    val resBody = response.body?.string() ?: ""
                    Log.d(TAG, "Spreadsheet GET ($action on $sheet): HTTP ${response.code} - $resBody")
                    if (evaluateActionResponse(response.isSuccessful, response.code, resBody, cleanUrl)) {
                        return@withContext true
                    }
                }
            }

            // Fallback POST FormBody untuk payload besar (mis. 5 foto galeri / deskripsi panjang)
            val formBuilder = FormBody.Builder()
                .add("action", action)
                .add("sheet", sheet)
                .add("password", password)
                .add("data", dataStr)
            if (targetId != null) {
                formBuilder.add("targetId", targetId)
            }
            val postRequest = Request.Builder()
                .url(cleanUrl)
                .post(formBuilder.build())
                .build()

            client.newCall(postRequest).execute().use { response ->
                val resBody = response.body?.string() ?: ""
                Log.d(TAG, "Spreadsheet POST ($action on $sheet): HTTP ${response.code} - $resBody")
                evaluateActionResponse(response.isSuccessful, response.code, resBody, cleanUrl)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gagal mengirim aksi ke spreadsheet: ${e.message}", e)
            lastActionError = e.localizedMessage ?: "Gagal terhubung ke server Spreadsheet"
            false
        }
    }

    private fun evaluateActionResponse(
        httpOk: Boolean,
        httpCode: Int,
        body: String,
        sourceUrl: String = ""
    ): Boolean {
        if (!httpOk) {
            lastActionError = "Server mengembalikan HTTP $httpCode"
            return false
        }
        val trimmed = body.trim()
        if (!trimmed.startsWith("{")) {
            lastActionError = if (trimmed.contains("ServiceLogin", ignoreCase = true) ||
                trimmed.contains("accounts.google.com", ignoreCase = true)
            ) {
                "Apps Script meminta login Google ('Who has access' belum diset ke 'Anyone')"
            } else {
                "Respons Apps Script bukan JSON (HTTP $httpCode)"
            }
            return false
        }
        return try {
            val json = JSONObject(trimmed)
            val ok = json.optString("status", "").equals("success", ignoreCase = true)
            if (ok) {
                lastActionError = null
                if (sourceUrl.isNotBlank() && (json.optBoolean("aspirasiMetaSaved", false) || json.optBoolean("supportsAspirasiMeta", false))) {
                    aspirasiMetaSupportedUrl = sourceUrl
                }
            } else {
                lastActionError = json.optString("message", "").trim()
                    .ifBlank { "Apps Script mengembalikan status gagal" }
            }
            ok
        } catch (_: Exception) {
            lastActionError = "Format respons Apps Script tidak valid"
            false
        }
    }

    private fun isJsonSuccess(httpOk: Boolean, body: String): Boolean {
        if (!httpOk) return false
        val trimmed = body.trim()
        if (!trimmed.startsWith("{")) return false
        return try {
            val json = JSONObject(trimmed)
            json.optString("status", "").equals("success", ignoreCase = true)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Backup seluruh data ke spreadsheet melalui aksi backup_all.
     */
    suspend fun backupAll(
        url: String,
        password: String,
        allDataJson: JSONObject
    ): Boolean = withContext(Dispatchers.IO) {
        val cleanUrl = url.trim()
        if (cleanUrl.isBlank() || cleanUrl.contains("docs.google.com/spreadsheets")) {
            lastActionError = "URL Web App Apps Script (/exec) belum valid."
            return@withContext false
        }
        lastActionError = null

        try {
            val formBody = FormBody.Builder()
                .add("action", "backup_all")
                .add("password", password)
                .add("data", allDataJson.toString())
                .build()

            val request = Request.Builder()
                .url(cleanUrl)
                .post(formBody)
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                Log.d(TAG, "Backup all response: HTTP ${response.code} - $body")
                evaluateActionResponse(response.isSuccessful, response.code, body, cleanUrl)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gagal backup all data ke spreadsheet: ${e.message}", e)
            lastActionError = e.localizedMessage ?: "Gagal terhubung ke server Spreadsheet"
            false
        }
    }

    /**
     * Mengunggah foto ke Google Drive melalui Google Apps Script Web App.
     * Mengembalikan URL publik Google Drive (https://drive.google.com/uc?id=... atau https://lh3.googleusercontent.com/d/...) jika berhasil.
     */
    suspend fun uploadPhotoToDrive(
        url: String,
        password: String,
        base64Data: String,
        filename: String = "warga_foto.jpg"
    ): String? = withContext(Dispatchers.IO) {
        val cleanUrl = url.trim()
        if (cleanUrl.isBlank() || cleanUrl.contains("docs.google.com/spreadsheets")) {
            lastDriveUploadError = "URL Web App Apps Script tidak valid"
            return@withContext null
        }
        if (unsupportedDriveScriptUrl == cleanUrl) {
            lastDriveUploadError = "Apps Script di URL ini masih versi lama (belum mendukung upload_photo)"
            return@withContext null
        }

        try {
            val payload = JSONObject().apply {
                put("imageBase64", base64Data)
                put("filename", filename)
            }
            val formBody = FormBody.Builder()
                .add("action", "upload_photo")
                .add("password", password)
                .add("data", payload.toString())
                .build()

            val request = Request.Builder()
                .url(cleanUrl)
                .post(formBody)
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                val trimmed = body.trim()
                Log.d(TAG, "uploadPhotoToDrive response: HTTP ${response.code} - ${trimmed.take(200)}")
                if (isJsonSuccess(response.isSuccessful, trimmed)) {
                    val json = JSONObject(trimmed)
                    val photoUrl = json.optString("url", "")
                    if (photoUrl.isNotBlank()) {
                        lastDriveUploadError = null
                        com.example.util.PhotoStorageHelper.normalizeGoogleDriveUrl(photoUrl)
                    } else {
                        lastDriveUploadError = "Server tidak mengembalikan URL foto Drive"
                        null
                    }
                } else {
                    if (trimmed.contains("Nama sheet harus diisi", ignoreCase = true)) {
                        unsupportedDriveScriptUrl = cleanUrl
                        lastDriveUploadError = "Apps Script masih versi lama (belum mendukung upload foto Drive)"
                    } else if (trimmed.startsWith("{")) {
                        val msg = try {
                            JSONObject(trimmed).optString("message", "")
                        } catch (_: Exception) {
                            ""
                        }
                        lastDriveUploadError = msg.ifBlank { "Drive error HTTP ${response.code}" }
                    } else if (!trimmed.startsWith("{")) {
                        lastDriveUploadError = "Akses Web App Apps Script belum 'Anyone' atau belum otorisasi Drive"
                    }
                    null
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "uploadPhotoToDrive exception: ${e.message}")
            lastDriveUploadError = e.localizedMessage ?: "Gagal terhubung ke Google Apps Script"
            null
        }
    }

    /**
     * Mengunggah foto ke Cloud Storage publik (FreeImage CDN + fallback x0.at) tanpa perlu setup tambahan.
     * Mengembalikan URL publik permanen (https://...) yang dapat diakses siapa saja dan
     * kompatibel dengan rumus Google Sheets =IMAGE("URL").
     */
    suspend fun uploadPhotoToPublicCloud(
        imageBytes: ByteArray,
        filename: String = "warga_foto.jpg"
    ): String? = withContext(Dispatchers.IO) {
        if (imageBytes.isEmpty()) return@withContext null

        for (attempt in 1..2) {
            // Provider 1: FreeImage CDN (https://iili.io/...)
            try {
                val mediaType = "image/jpeg".toMediaTypeOrNull()
                val fileBody = imageBytes.toRequestBody(mediaType)

                val multipart = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("key", "6d207e02198a847aa98d0a2a901485a5")
                    .addFormDataPart("action", "upload")
                    .addFormDataPart("format", "json")
                    .addFormDataPart("source", filename, fileBody)
                    .build()

                val request = Request.Builder()
                    .url("https://freeimage.host/api/1/upload")
                    .header("Accept", "application/json")
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36")
                    .post(multipart)
                    .build()

                client.newCall(request).execute().use { response ->
                    val body = response.body?.string() ?: ""
                    Log.d(TAG, "uploadPhotoToPublicCloud(freeimage #$attempt) response: HTTP ${response.code} - ${body.take(150)}")
                    if (response.isSuccessful && body.trim().startsWith("{")) {
                        val json = JSONObject(body)
                        if (json.optInt("status_code", 0) == 200) {
                            val imageObj = json.optJSONObject("image")
                            val directUrl = imageObj?.optString("url", "") ?: ""
                            if (directUrl.startsWith("http://") || directUrl.startsWith("https://")) {
                                return@withContext directUrl
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "freeimage.host upload exception (#$attempt): ${e.message}")
            }

            // Provider 2 (Fallback): x0.at
            try {
                val mediaType = "image/jpeg".toMediaTypeOrNull()
                val fileBody = imageBytes.toRequestBody(mediaType)
                val multipart = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", filename, fileBody)
                    .build()

                val request = Request.Builder()
                    .url("https://x0.at/")
                    .header("User-Agent", "AplikasiWarga/1.0")
                    .post(multipart)
                    .build()

                client.newCall(request).execute().use { response ->
                    val body = response.body?.string()?.trim() ?: ""
                    Log.d(TAG, "uploadPhotoToPublicCloud(x0.at #$attempt) response: HTTP ${response.code} - $body")
                    if (response.isSuccessful && (body.startsWith("https://") || body.startsWith("http://"))) {
                        return@withContext body.lines().first().trim()
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "x0.at fallback upload exception (#$attempt): ${e.message}")
            }

            if (attempt < 2) {
                delay(300)
            }
        }

        null
    }

    private fun isUnreadableFormulaCell(raw: String): Boolean {
        val trimmed = raw.trim()
        return trimmed.contains("valueType", ignoreCase = true) ||
            trimmed.contains("CellImage", ignoreCase = true)
    }

    private fun cleanPhotoField(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank() ||
            trimmed == "{}" ||
            trimmed.equals("null", ignoreCase = true) ||
            isUnreadableFormulaCell(trimmed) ||
            (trimmed.startsWith("{") && trimmed.endsWith("}"))
        ) {
            return ""
        }
        return com.example.util.PhotoStorageHelper.extractUrlFromFormula(trimmed)
    }

    internal fun parseSheetResponse(jsonString: String, sourceUrl: String = ""): SheetDataResponse {
        val trimmedJson = jsonString.trim()
        if (!trimmedJson.startsWith("{")) {
            throw Exception("Respons Web App bukan JSON (pastikan 'Who has access: Anyone' pada deployment Apps Script).")
        }
        val json = JSONObject(trimmedJson)
        val status = json.optString("status", "success")
        if (status.equals("error", ignoreCase = true)) {
            throw Exception(json.optString("message", "Terjadi kesalahan di Google Apps Script."))
        }
        val caps = ScriptSyncDiagnostics.resolveScriptCapabilities(json)
        val scriptVersion = caps.effectiveVersion
        val hasExplicitV2 = caps.supportsImageFormula
        var hasUnreadableImageFormula = false

        fun readAndCleanPhoto(raw: String): String {
            if (isUnreadableFormulaCell(raw)) hasUnreadableImageFormula = true
            return cleanPhotoField(raw)
        }

        fun readStringAny(obj: JSONObject, vararg keys: String): String {
            for (k in keys) {
                if (obj.has(k) && !obj.isNull(k)) {
                    val v = obj.optString(k, "").trim()
                    if (v.isNotBlank() && !v.equals("null", ignoreCase = true)) return v
                }
            }
            return ""
        }

        fun readLongAny(obj: JSONObject, vararg keys: String): Long {
            for (k in keys) {
                if (obj.has(k) && !obj.isNull(k)) {
                    val raw = obj.opt(k)
                    when (raw) {
                        is Number -> return raw.toLong()
                        is String -> {
                            val clean = raw.trim().replace("Rp", "", ignoreCase = true).replace(".", "").replace(",", "").trim()
                            clean.toLongOrNull()?.let { return it }
                            raw.trim().toDoubleOrNull()?.let { return it.toLong() }
                        }
                    }
                }
            }
            return 0L
        }

        fun readIntAny(obj: JSONObject, vararg keys: String): Int {
            return readLongAny(obj, *keys).toInt()
        }

        fun parseKasRecapArray(arr: org.json.JSONArray?): List<KasMonthlyRecap>? {
            if (arr == null) return null
            val list = mutableListOf<KasMonthlyRecap>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val periode = readStringAny(obj, "periode_bulan", "periodeBulan", "periode", "bulanKey", "bulan_key", "key")
                val label = readStringAny(obj, "label_bulan", "labelBulan", "Bulan", "label", "bulan", "namaBulan", "nama_bulan")
                val masuk = readLongAny(obj, "Total_Masuk", "totalMasuk", "total_masuk", "masuk", "Masuk")
                val keluar = readLongAny(obj, "Total_Keluar", "totalKeluar", "total_keluar", "keluar", "Keluar")
                val arus = if (obj.has("arusKas") || obj.has("arus_kas") || obj.has("selisih")) {
                    readLongAny(obj, "arusKas", "arus_kas", "selisih")
                } else {
                    masuk - keluar
                }
                val saldo = readLongAny(obj, "saldoAkhir", "saldo_akhir", "saldo", "Saldo", "jumlah")
                val count = readIntAny(obj, "jumlahTransaksi", "jumlah_transaksi", "transaksi", "count")
                val normalizedKey = com.example.util.KasRecapHelper.parseMonthKey(periode.ifBlank { label })
                if (normalizedKey.isNotBlank() || label.isNotBlank()) {
                    list.add(
                        KasMonthlyRecap(
                            periodeBulan = normalizedKey.ifBlank { periode },
                            labelBulan = label.ifBlank { com.example.util.KasRecapHelper.formatMonthYearLabel(normalizedKey) },
                            totalMasuk = masuk,
                            totalKeluar = keluar,
                            arusKas = arus,
                            saldoAkhir = saldo,
                            jumlahTransaksi = count
                        )
                    )
                }
            }
            return list
        }

        val kasList = mutableListOf<KasItem>()
        val kasArray = json.optJSONArray("kas")
        if (kasArray != null) {
            for (i in 0 until kasArray.length()) {
                val obj = kasArray.optJSONObject(i) ?: continue
                val rawTanggal = readStringAny(obj, "tanggal", "Tanggal")
                val rawPeriode = readStringAny(obj, "periode_bulan", "Periode_Bulan", "periodeBulan")
                val resolvedSaldo = readLongAny(obj, "saldo", "Saldo", "jumlah", "Jumlah")
                val rawFoto = readStringAny(obj, "bukti_foto", "Bukti_Foto", "buktiFoto")
                kasList.add(
                    KasItem(
                        id = readStringAny(obj, "id", "ID").ifEmpty { "KAS-$i" },
                        tanggal = rawTanggal,
                        periodeBulan = rawPeriode.ifBlank { com.example.util.KasRecapHelper.resolvePeriodeKey("", rawTanggal) },
                        kategori = readStringAny(obj, "kategori", "Kategori"),
                        keterangan = readStringAny(obj, "keterangan", "Keterangan"),
                        masuk = readLongAny(obj, "masuk", "Masuk"),
                        keluar = readLongAny(obj, "keluar", "Keluar"),
                        saldo = resolvedSaldo,
                        jumlah = resolvedSaldo,
                        buktiFoto = readAndCleanPhoto(rawFoto)
                    )
                )
            }
        }

        val kasRekapObj = json.optJSONObject("kasRekap") ?: json.optJSONObject("kas_rekap")
        val rolling12List = parseKasRecapArray(
            json.optJSONArray("rolling12")
                ?: json.optJSONArray("rolling_12")
                ?: kasRekapObj?.optJSONArray("rolling12")
        )
        val rekapBulananList = parseKasRecapArray(
            json.optJSONArray("rekapBulanan")
                ?: json.optJSONArray("rekap_bulanan")
                ?: json.optJSONArray("rekap")
                ?: kasRekapObj?.optJSONArray("rekapBulanan")
        )
        val summaryObj = json.optJSONObject("kasSummary")
            ?: json.optJSONObject("kas_summary")
            ?: kasRekapObj?.optJSONObject("summary")
        val parsedKasSummary = summaryObj?.let { sObj ->
            KasSummaryTotals(
                saldoKas = readLongAny(sObj, "saldoKas", "saldo_kas", "saldo", "saldoTerkini"),
                totalMasuk = readLongAny(sObj, "totalMasuk", "total_masuk", "masuk"),
                totalKeluar = readLongAny(sObj, "totalKeluar", "total_keluar", "keluar")
            )
        }

        val pengurusList = mutableListOf<PengurusItem>()
        val pengurusArray = json.optJSONArray("pengurus")
        if (pengurusArray != null) {
            for (i in 0 until pengurusArray.length()) {
                val obj = pengurusArray.optJSONObject(i) ?: continue
                val rawFoto = readStringAny(obj, "Foto", "foto", "Foto_Profil", "foto_profil", "fotoProfil", "fotoUrl", "url_foto")
                pengurusList.add(
                    PengurusItem(
                        id = obj.optString("ID").ifEmpty { obj.optString("id", "PGR-$i") },
                        jabatan = obj.optString("Jabatan").ifEmpty { obj.optString("jabatan", "") },
                        nama = obj.optString("Nama").ifEmpty { obj.optString("nama", "") },
                        noHp = obj.optString("No HP").ifEmpty { obj.optString("noHp", "") },
                        alamat = obj.optString("Alamat").ifEmpty { obj.optString("alamat", "") },
                        masaBakti = obj.optString("Masa_Bakti").ifEmpty { obj.optString("masaBakti", "") },
                        foto = com.example.util.PhotoStorageHelper.normalizeGoogleDriveUrl(readAndCleanPhoto(rawFoto))
                    )
                )
            }
        }

        val undanganList = mutableListOf<UndanganItem>()
        val undanganArray = json.optJSONArray("undangan")
        if (undanganArray != null) {
            for (i in 0 until undanganArray.length()) {
                val obj = undanganArray.optJSONObject(i) ?: continue
                val rawKeterangan = obj.optString("Keterangan").ifEmpty { obj.optString("keterangan", "") }
                val marker = com.example.util.ReminderCodec.parseMarker(rawKeterangan)
                val parsedMenit = obj.optInt("Pengingat_Menit", obj.optInt("pengingatMenit", marker?.minutes ?: 0))
                val parsedTarget = obj.optLong("Pengingat_Target_Millis", obj.optLong("pengingatTargetMillis", marker?.targetMillis ?: 0L))
                val parsedActivated = obj.optLong("Pengingat_Aktif_Pada", obj.optLong("pengingatActivatedMillis", marker?.activatedMillis ?: 0L))
                val parsedAktifRaw = obj.opt("Pengingat_Aktif") ?: obj.opt("pengingatAktif")
                val parsedAktif = when (parsedAktifRaw) {
                    is Boolean -> parsedAktifRaw
                    is String -> parsedAktifRaw.equals("true", ignoreCase = true) || parsedAktifRaw == "1"
                    is Number -> parsedAktifRaw.toInt() == 1
                    else -> (parsedMenit > 0 && parsedTarget > 0L)
                }

                val rawFoto = readStringAny(obj, "Foto", "foto", "Foto_Lampiran", "foto_lampiran", "url_foto")
                undanganList.add(
                    UndanganItem(
                        id = obj.optString("ID").ifEmpty { obj.optString("id", "UND-$i") },
                        judul = obj.optString("Judul").ifEmpty { obj.optString("judul", "") },
                        tanggal = obj.optString("Tanggal").ifEmpty { obj.optString("tanggal", "") },
                        waktu = obj.optString("Waktu").ifEmpty { obj.optString("waktu", "") },
                        lokasi = obj.optString("Lokasi").ifEmpty { obj.optString("lokasi", "") },
                        keterangan = rawKeterangan,
                        tipe = obj.optString("Tipe").ifEmpty { obj.optString("tipe", if (obj.optString("Judul").startsWith("[Pengumuman]", ignoreCase = true)) "Pengumuman" else "Undangan") },
                        pengingatMenit = parsedMenit,
                        pengingatAktif = parsedAktif,
                        pengingatTargetMillis = parsedTarget,
                        pengingatActivatedMillis = parsedActivated,
                        foto = com.example.util.PhotoStorageHelper.normalizeGoogleDriveUrl(readAndCleanPhoto(rawFoto))
                    )
                )
            }
        }

        val galeriList = mutableListOf<GaleriItem>()
        val galeriArray = json.optJSONArray("galeri")
        if (galeriArray != null) {
            for (i in 0 until galeriArray.length()) {
                val obj = galeriArray.optJSONObject(i) ?: continue
                val fotoParts = listOf(
                    readStringAny(obj, "Foto_1", "Foto", "foto1", "foto"),
                    readStringAny(obj, "Foto_2", "foto2"),
                    readStringAny(obj, "Foto_3", "foto3"),
                    readStringAny(obj, "Foto_4", "foto4"),
                    readStringAny(obj, "Foto_5", "foto5"),
                    readStringAny(obj, "Foto_6", "foto6"),
                    readStringAny(obj, "Foto_7", "foto7"),
                    readStringAny(obj, "Foto_8", "foto8"),
                    readStringAny(obj, "Foto_9", "foto9"),
                    readStringAny(obj, "Foto_10", "foto10")
                ).flatMap { raw ->
                    raw.split(",").map { readAndCleanPhoto(it) }
                }.filter { it.isNotBlank() }.distinct()

                galeriList.add(
                    GaleriItem(
                        id = readStringAny(obj, "ID", "id").ifEmpty { "GAL-$i" },
                        tanggal = readStringAny(obj, "Tanggal", "tanggal"),
                        judul = readStringAny(obj, "Judul", "judul"),
                        kategori = readStringAny(obj, "Kategori", "kategori"),
                        keterangan = readStringAny(obj, "Keterangan", "keterangan"),
                        foto = fotoParts.joinToString(","),
                        petugas = readStringAny(obj, "Petugas", "petugas")
                    )
                )
            }
        }

        val aspirasiList = mutableListOf<AspirasiItem>()
        val aspirasiArray = json.optJSONArray("aspirasi")
        if (aspirasiArray != null) {
            fun looksLikePhotoOrFormula(raw: String): Boolean {
                val t = raw.trim()
                return t.startsWith("=IMAGE(", ignoreCase = true) ||
                    t.startsWith("http://", ignoreCase = true) ||
                    t.startsWith("https://", ignoreCase = true) ||
                    isUnreadableFormulaCell(t)
            }
            fun isValidStatusValue(raw: String): Boolean {
                return raw.equals("Menunggu", ignoreCase = true) ||
                    raw.equals("Diproses", ignoreCase = true) ||
                    raw.equals("Selesai", ignoreCase = true)
            }

            for (i in 0 until aspirasiArray.length()) {
                val obj = aspirasiArray.optJSONObject(i) ?: continue
                // Kolom F..J: Foto_Masalah_1..5 (hanya foto masalah, maks 5)
                val masalahParts = listOf(
                    readStringAny(obj, "Foto_Masalah_1", "foto_masalah_1", "fotoMasalah1", "Foto_Masalah", "foto_masalah", "fotoMasalah"),
                    readStringAny(obj, "Foto_Masalah_2", "foto_masalah_2", "fotoMasalah2"),
                    readStringAny(obj, "Foto_Masalah_3", "foto_masalah_3", "fotoMasalah3"),
                    readStringAny(obj, "Foto_Masalah_4", "foto_masalah_4", "fotoMasalah4"),
                    readStringAny(obj, "Foto_Masalah_5", "foto_masalah_5", "fotoMasalah5")
                ).flatMap { raw ->
                    raw.split(",").map { readAndCleanPhoto(it) }
                }.filter { it.isNotBlank() }.distinct().take(5)

                // Kolom N..R: Foto_pengerjaan_1..5 (hanya foto pengerjaan, maks 5)
                val pengerjaanParts = listOf(
                    readStringAny(obj, "Foto_pengerjaan_1", "Foto_Pengerjaan_1", "foto_pengerjaan_1", "fotoPengerjaan1", "Foto_pengerjaan", "Foto_Pengerjaan", "foto_pengerjaan", "fotoPengerjaan"),
                    readStringAny(obj, "Foto_pengerjaan_2", "Foto_Pengerjaan_2", "foto_pengerjaan_2", "fotoPengerjaan2"),
                    readStringAny(obj, "Foto_pengerjaan_3", "Foto_Pengerjaan_3", "foto_pengerjaan_3", "fotoPengerjaan3"),
                    readStringAny(obj, "Foto_pengerjaan_4", "Foto_Pengerjaan_4", "foto_pengerjaan_4", "fotoPengerjaan4"),
                    readStringAny(obj, "Foto_pengerjaan_5", "Foto_Pengerjaan_5", "foto_pengerjaan_5", "fotoPengerjaan5")
                ).flatMap { raw ->
                    raw.split(",").map { readAndCleanPhoto(it) }
                }.filter { it.isNotBlank() }.distinct().take(5)

                // Kolom M: Tanggapan_pengurus
                val rawTanggapan = readStringAny(
                    obj,
                    "Tanggapan_pengurus",
                    "tanggapan_pengurus",
                    "tanggapanPengurus",
                    "Tanggapan_Pengurus",
                    "Tanggapan pengurus",
                    "Tanggapan Pengurus",
                    "Tanggapan",
                    "tanggapan"
                )
                val parsedReplyMeta = com.example.util.AspirasiReplyCodec.parseAndStrip(rawTanggapan)

                // Kolom L: Nama_Pengurus
                var resolvedNamaPengurus = readStringAny(
                    obj,
                    "Nama_Pengurus",
                    "nama_pengurus",
                    "namaPengurus",
                    "Nama Pengurus",
                    "Penanggap_Nama",
                    "Penanggap Nama",
                    "penanggapNama",
                    "penanggap_nama"
                ).ifBlank { parsedReplyMeta.penanggapNama }

                // Kolom K: Status (aman dari pergeseran kolom lama yang berisi URL/rumus foto)
                val rawStatus = readStringAny(obj, "Status", "status")
                val resolvedStatus = when {
                    rawStatus.isNotBlank() && !looksLikePhotoOrFormula(rawStatus) -> rawStatus
                    isValidStatusValue(resolvedNamaPengurus) -> {
                        val rescued = resolvedNamaPengurus
                        resolvedNamaPengurus = parsedReplyMeta.penanggapNama
                        rescued
                    }
                    parsedReplyMeta.cleanTanggapan.isNotBlank() || pengerjaanParts.isNotEmpty() -> "Diproses"
                    else -> "Menunggu"
                }

                val rawSembunyi = obj.opt("Sembunyikan_Nama") ?: obj.opt("sembunyikanNama") ?: obj.opt("sembunyikan_nama")
                val sembunyikanNamaVal = when (rawSembunyi) {
                    is Boolean -> rawSembunyi
                    is Number -> rawSembunyi.toInt() == 1
                    is String -> rawSembunyi.trim().equals("true", ignoreCase = true) ||
                        rawSembunyi.trim() == "1" ||
                        rawSembunyi.trim().equals("ya", ignoreCase = true)
                    else -> false
                }

                aspirasiList.add(
                    AspirasiItem(
                        id = readStringAny(obj, "ID", "id").ifEmpty { "ASP-$i" },
                        tanggal = readStringAny(obj, "Tanggal", "tanggal"),
                        nama = readStringAny(obj, "Nama", "nama"),
                        kategori = readStringAny(obj, "Kategori", "kategori").ifEmpty { "Masukan/Saran" },
                        pesan = readStringAny(obj, "Pesan", "pesan"),
                        fotoMasalah = masalahParts.joinToString(","),
                        status = resolvedStatus,
                        penanggapNama = resolvedNamaPengurus,
                        tanggapanPengurus = parsedReplyMeta.cleanTanggapan,
                        fotoPengerjaan = pengerjaanParts.joinToString(","),
                        noRumah = readStringAny(obj, "No_Rumah", "No Rumah", "noRumah", "no_rumah"),
                        sembunyikanNama = sembunyikanNamaVal,
                        penanggapUsername = readStringAny(obj, "Penanggap_Username", "Penanggap Username", "penanggapUsername", "penanggap_username"),
                        penanggapNamaAsli = readStringAny(obj, "Penanggap_Nama_Asli", "Penanggap Nama Asli", "penanggapNamaAsli", "penanggap_nama_asli"),
                        penanggapPeran = readStringAny(obj, "Penanggap_Peran", "Penanggap Peran", "penanggapPeran", "penanggap_peran")
                            .ifBlank { parsedReplyMeta.penanggapPeran },
                        waktuTanggapan = readStringAny(obj, "Waktu_Tanggapan", "Waktu Tanggapan", "waktuTanggapan", "waktu_tanggapan")
                            .ifBlank { parsedReplyMeta.waktuTanggapan }
                    )
                )
            }
        }

        val maxGaleriPhotos = caps.maxGaleriPhotos
        val hasExplicitV3 = scriptVersion >= 3 || maxGaleriPhotos >= 10
        val driveAuthorized = caps.driveAuthorized
        if (sourceUrl.isNotBlank()) {
            if (json.optBoolean("supportsAspirasiMeta", false)) {
                aspirasiMetaSupportedUrl = sourceUrl
            }
            if (scriptVersion >= 8 && driveAuthorized) {
                unsupportedDriveScriptUrl = null
                lastDriveUploadError = null
            }
            if (hasExplicitV3) {
                multiPhotoSupportedUrl = sourceUrl
                galeri10ColsSupportedUrl = sourceUrl
                multiPhotoCheckedUrl = sourceUrl
            } else if (hasExplicitV2) {
                multiPhotoSupportedUrl = sourceUrl
                galeri10ColsSupportedUrl = null
                multiPhotoCheckedUrl = sourceUrl
            } else if (hasUnreadableImageFormula) {
                multiPhotoSupportedUrl = null
                galeri10ColsSupportedUrl = null
                multiPhotoCheckedUrl = sourceUrl
            }
        }

        return SheetDataResponse(
            kas = kasList,
            rolling12 = rolling12List,
            rekapBulanan = rekapBulananList,
            kasSummary = parsedKasSummary,
            pengurus = pengurusList,
            undangan = undanganList,
            galeri = galeriList,
            aspirasi = aspirasiList,
            status = status,
            hasUnreadableImageFormula = hasUnreadableImageFormula,
            scriptVersion = scriptVersion,
            driveAuthorized = driveAuthorized,
            hasExplicitVersion = caps.hasExplicitVersion,
            hasExplicitDriveAuth = caps.hasExplicitDriveAuth,
            supportsDriveUpload = caps.supportsDriveUpload,
            maxGaleriPhotos = maxGaleriPhotos
        )
    }
}
