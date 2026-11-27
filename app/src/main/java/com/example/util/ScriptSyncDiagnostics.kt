package com.example.util

import com.example.data.model.UserRole
import org.json.JSONObject

data class ScriptCapabilities(
    val rawVersion: Int,
    val effectiveVersion: Int,
    val hasExplicitVersion: Boolean,
    val driveAuthorized: Boolean,
    val hasExplicitDriveAuth: Boolean,
    val supportsImageFormula: Boolean,
    val supportsDriveUpload: Boolean,
    val maxGaleriPhotos: Int
)

data class SyncHealthState(
    val isSyncing: Boolean = false,
    val hasVerifiedScriptStatus: Boolean = false,
    val lastSyncSucceeded: Boolean = false,
    val scriptVersion: Int = 0,
    val driveAuthorized: Boolean = false,
    val hasUnreadableImageFormula: Boolean = false,
    val lastErrorMessage: String? = null
)

data class SyncDiagnosticEvaluation(
    val needsScriptUpdate: Boolean,
    val statusMessage: String,
    val isWarning: Boolean
)

object ScriptSyncDiagnostics {

    fun parseVersionNumber(raw: Any?): Int {
        return when (raw) {
            is Number -> raw.toInt().takeIf { it > 0 } ?: -1
            is String -> {
                val match = Regex("""(\d+)""").find(raw.trim())
                match?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it > 0 } ?: -1
            }
            else -> -1
        }
    }

    fun resolveScriptCapabilities(json: JSONObject): ScriptCapabilities {
        val rawVersionArg: Any? = if (json.has("version") && !json.isNull("version")) json.opt("version") else null
        val maxGaleriPhotos = json.optInt("maxGaleriPhotos", 0)
        val supportsImageFormula = json.optBoolean("supportsImageFormula", false)
        val supportsDriveUpload = json.optBoolean("supportsDriveUpload", false)
        val hasExplicitDriveAuth = json.has("driveAuthorized") && !json.isNull("driveAuthorized")
        val driveAuthorizedRaw = if (hasExplicitDriveAuth) json.optBoolean("driveAuthorized", false) else null
        val supportsModernRecap = json.optBoolean("supportsRolling12", false) ||
            json.optBoolean("supportsRekapBulananSheet", false)

        return resolveScriptCapabilitiesFromValues(
            rawVersionValue = rawVersionArg,
            maxGaleriPhotos = maxGaleriPhotos,
            supportsImageFormula = supportsImageFormula,
            supportsDriveUpload = supportsDriveUpload,
            explicitDriveAuthorized = driveAuthorizedRaw,
            supportsModernRecap = supportsModernRecap
        )
    }

    fun resolveScriptCapabilitiesFromValues(
        rawVersionValue: Any?,
        maxGaleriPhotos: Int = 0,
        supportsImageFormula: Boolean = false,
        supportsDriveUpload: Boolean = false,
        explicitDriveAuthorized: Boolean? = null,
        supportsModernRecap: Boolean = false
    ): ScriptCapabilities {
        val rawVersion = parseVersionNumber(rawVersionValue)
        val hasExplicitVersion = rawVersion > 0
        val hasExplicitDriveAuth = explicitDriveAuthorized != null
        val driveAuthorized = explicitDriveAuthorized ?: supportsDriveUpload

        val effectiveVersion = when {
            hasExplicitVersion && rawVersion >= 8 -> rawVersion
            driveAuthorized && supportsDriveUpload -> maxOf(rawVersion.coerceAtLeast(1), 9)
            !hasExplicitVersion && maxGaleriPhotos >= 10 && supportsImageFormula && supportsModernRecap -> 9
            hasExplicitVersion -> rawVersion
            else -> 1
        }

        return ScriptCapabilities(
            rawVersion = if (hasExplicitVersion) rawVersion else 1,
            effectiveVersion = effectiveVersion,
            hasExplicitVersion = hasExplicitVersion,
            driveAuthorized = driveAuthorized,
            hasExplicitDriveAuth = hasExplicitDriveAuth,
            supportsImageFormula = supportsImageFormula || effectiveVersion >= 2,
            supportsDriveUpload = supportsDriveUpload,
            maxGaleriPhotos = maxGaleriPhotos
        )
    }

    fun formatConnectionTestResult(
        spreadsheetName: String,
        capabilities: ScriptCapabilities,
        driveError: String = ""
    ): Pair<Boolean, String> {
        val nameStr = if (spreadsheetName.isNotBlank()) " ($spreadsheetName)" else " (Aplikasi_Warga)"
        val version = capabilities.effectiveVersion
        val driveAuthorized = capabilities.driveAuthorized
        val supportsFormula = capabilities.supportsImageFormula
        val supportsDrive = capabilities.supportsDriveUpload && driveAuthorized

        return when {
            version < 8 -> {
                Pair(
                    false,
                    "Terhubung ke Spreadsheet$nameStr, TETAPI script yang aktif di URL ini masih versi lama (v$version). Di Apps Script, klik Simpan (Ctrl+S), jalankan fungsi 'otorisasiDanCekSetup', lalu klik Deploy -> Manage deployments -> Edit -> Version: New version -> Deploy."
                )
            }
            capabilities.hasExplicitDriveAuth && !driveAuthorized -> {
                val errDetail = if (driveError.isNotBlank()) " ($driveError)" else ""
                Pair(
                    false,
                    "Terhubung ke Spreadsheet$nameStr (v$version), TETAPI izin Google Drive belum aktif$errDetail! Di editor Apps Script, pilih fungsi 'otorisasiDanCekSetup' lalu klik Run -> Authorize Access -> Allow."
                )
            }
            supportsFormula && (supportsDrive || driveAuthorized) -> {
                Pair(
                    true,
                    "Koneksi Berhasil! Terhubung ke Spreadsheet$nameStr (Script v$version, mendukung upload Google Drive & rumus =IMAGE)."
                )
            }
            else -> {
                Pair(
                    false,
                    "Terhubung ke Spreadsheet$nameStr, namun fitur foto Drive belum aktif. Jalankan 'otorisasiDanCekSetup' di Apps Script lalu Deploy -> Manage deployments -> Edit -> New version."
                )
            }
        }
    }

    fun evaluateSyncHealth(
        health: SyncHealthState,
        userRole: UserRole
    ): SyncDiagnosticEvaluation {
        // 1. Before any remote verification has succeeded (fresh install / startup / pre-check):
        // NEVER emit false "v2" or "Apps Script Spreadsheet Masih Versi Lama" warnings.
        if (!health.hasVerifiedScriptStatus) {
            if (health.isSyncing) {
                return SyncDiagnosticEvaluation(
                    needsScriptUpdate = false,
                    statusMessage = "🔄 Menghubungkan ke Spreadsheet: Aplikasi_Warga...",
                    isWarning = false
                )
            }
            val err = health.lastErrorMessage?.trim().orEmpty()
            if (err.isNotBlank()) {
                val msg = when {
                    userRole == UserRole.WARGA ->
                        "⚠️ Menunggu koneksi internet untuk sinkronisasi otomatis"
                    err.contains("ReferenceError", ignoreCase = true) ->
                        "⚠️ Gagal Sync ($err). Kode Apps Script terpotong/belum lengkap. Ketuk di sini -> Salin Script v9, hapus SEMUA kode lama di Apps Script, tempel ulang, Simpan, jalankan 'otorisasiDanCekSetup', lalu Deploy New version."
                    else ->
                        "⚠️ Gagal Sync: $err"
                }
                return SyncDiagnosticEvaluation(
                    needsScriptUpdate = false,
                    statusMessage = msg,
                    isWarning = true
                )
            }
            return SyncDiagnosticEvaluation(
                needsScriptUpdate = false,
                statusMessage = "Menghubungkan ke Spreadsheet: Aplikasi_Warga...",
                isWarning = false
            )
        }

        // 2. Previously verified, but the latest sync attempt failed (e.g. temporary offline):
        // Keep genuine verified needsScriptUpdate only if verified script was genuinely < 8 or un-authorized,
        // and surface network error truthfully without fabricating a v2 version warning.
        val isGenuineOutdatedScript = health.scriptVersion in 1..7
        val isGenuineMissingDrive = !health.driveAuthorized
        val needsUpdate = isGenuineOutdatedScript || isGenuineMissingDrive

        if (!health.lastSyncSucceeded) {
            val err = health.lastErrorMessage?.trim().orEmpty().ifBlank { "Cek koneksi internet/URL" }
            val msg = when {
                userRole == UserRole.WARGA ->
                    "⚠️ Menunggu koneksi internet untuk sinkronisasi otomatis"
                err.contains("ReferenceError", ignoreCase = true) ->
                    "⚠️ Gagal Sync ($err). Kode Apps Script terpotong/belum lengkap. Ketuk di sini -> Salin Script v9, hapus SEMUA kode lama di Apps Script, tempel ulang, Simpan, jalankan 'otorisasiDanCekSetup', lalu Deploy New version."
                else ->
                    "⚠️ Gagal Sync: $err"
            }
            return SyncDiagnosticEvaluation(
                needsScriptUpdate = needsUpdate,
                statusMessage = msg,
                isWarning = true
            )
        }

        // 3. Verified & latest sync succeeded:
        // Never mark needsScriptUpdate = true solely from hasUnreadableImageFormula when script >= 8 & Drive authorized.
        if (needsUpdate && userRole != UserRole.WARGA) {
            val msg = when {
                isGenuineOutdatedScript ->
                    "⚠️ Apps Script di URL ini masih v${health.scriptVersion} — foto belum bisa tersimpan ke Spreadsheet. Ketuk untuk update ke v9."
                else ->
                    "⚠️ Script v${health.scriptVersion} aktif, tapi izin Google Drive belum dijalankan ('otorisasiDanCekSetup'). Ketuk untuk panduan."
            }
            return SyncDiagnosticEvaluation(
                needsScriptUpdate = true,
                statusMessage = msg,
                isWarning = true
            )
        }

        return SyncDiagnosticEvaluation(
            needsScriptUpdate = needsUpdate,
            statusMessage = "🟢 Tersinkron Realtime — Aplikasi_Warga",
            isWarning = false
        )
    }
}
