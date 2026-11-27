package com.example

import com.example.data.model.UserRole
import com.example.data.source.GoogleAppsScriptTemplate
import com.example.util.ScriptSyncDiagnostics
import com.example.util.SyncHealthState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScriptSyncDiagnosticsTest {

    @Test
    fun parseVersionNumber_supportsNumericAndPrefixedStrings() {
        assertEquals(9, ScriptSyncDiagnostics.parseVersionNumber(9))
        assertEquals(9, ScriptSyncDiagnostics.parseVersionNumber("9"))
        assertEquals(9, ScriptSyncDiagnostics.parseVersionNumber("v9"))
        assertEquals(9, ScriptSyncDiagnostics.parseVersionNumber(" V9 "))
        assertEquals(2, ScriptSyncDiagnostics.parseVersionNumber("v2"))
        assertEquals(-1, ScriptSyncDiagnostics.parseVersionNumber(0))
        assertEquals(-1, ScriptSyncDiagnostics.parseVersionNumber(""))
        assertEquals(-1, ScriptSyncDiagnostics.parseVersionNumber(null))
    }

    @Test
    fun resolveScriptCapabilities_correctlyDistinguishesV9AndLegacyV2() {
        val v9Caps = ScriptSyncDiagnostics.resolveScriptCapabilitiesFromValues(
            rawVersionValue = 9,
            maxGaleriPhotos = 10,
            supportsImageFormula = true,
            supportsDriveUpload = true,
            explicitDriveAuthorized = true,
            supportsModernRecap = true
        )
        assertEquals(9, v9Caps.effectiveVersion)
        assertTrue(v9Caps.hasExplicitVersion)
        assertTrue(v9Caps.driveAuthorized)

        val v9StringCaps = ScriptSyncDiagnostics.resolveScriptCapabilitiesFromValues(
            rawVersionValue = "v9",
            maxGaleriPhotos = 10,
            supportsImageFormula = true,
            supportsDriveUpload = true,
            explicitDriveAuthorized = true
        )
        assertEquals(9, v9StringCaps.effectiveVersion)
        assertTrue(v9StringCaps.driveAuthorized)

        val inferredV9Caps = ScriptSyncDiagnostics.resolveScriptCapabilitiesFromValues(
            rawVersionValue = null,
            maxGaleriPhotos = 10,
            supportsImageFormula = true,
            supportsDriveUpload = true,
            explicitDriveAuthorized = true,
            supportsModernRecap = true
        )
        assertEquals(9, inferredV9Caps.effectiveVersion)
        assertFalse(inferredV9Caps.hasExplicitVersion)
        assertTrue(inferredV9Caps.driveAuthorized)

        val legacyV2Caps = ScriptSyncDiagnostics.resolveScriptCapabilitiesFromValues(
            rawVersionValue = 2,
            maxGaleriPhotos = 5,
            supportsImageFormula = true,
            supportsDriveUpload = false,
            explicitDriveAuthorized = null
        )
        assertEquals(2, legacyV2Caps.effectiveVersion)
        assertFalse(legacyV2Caps.driveAuthorized)
    }

    @Test
    fun formatConnectionTestResult_reportsV9SuccessAndActionableWarnings() {
        val v9Caps = ScriptSyncDiagnostics.resolveScriptCapabilitiesFromValues(
            rawVersionValue = 9,
            maxGaleriPhotos = 10,
            supportsImageFormula = true,
            supportsDriveUpload = true,
            explicitDriveAuthorized = true
        )
        val (okV9, msgV9) = ScriptSyncDiagnostics.formatConnectionTestResult("Aplikasi_Warga", v9Caps)
        assertTrue(okV9)
        assertTrue(msgV9.contains("Koneksi Berhasil"))
        assertTrue(msgV9.contains("v9"))

        val v9NoDriveCaps = ScriptSyncDiagnostics.resolveScriptCapabilitiesFromValues(
            rawVersionValue = 9,
            maxGaleriPhotos = 10,
            supportsImageFormula = true,
            supportsDriveUpload = false,
            explicitDriveAuthorized = false
        )
        val (okNoDrive, msgNoDrive) = ScriptSyncDiagnostics.formatConnectionTestResult(
            "Aplikasi_Warga",
            v9NoDriveCaps,
            driveError = "No DriveApp permission"
        )
        assertFalse(okNoDrive)
        assertTrue(msgNoDrive.contains("otorisasiDanCekSetup"))

        val v2Caps = ScriptSyncDiagnostics.resolveScriptCapabilitiesFromValues(
            rawVersionValue = 2,
            maxGaleriPhotos = 5,
            supportsImageFormula = true,
            supportsDriveUpload = false,
            explicitDriveAuthorized = null
        )
        val (okV2, msgV2) = ScriptSyncDiagnostics.formatConnectionTestResult("Aplikasi_Warga", v2Caps)
        assertFalse(okV2)
        assertTrue(msgV2.contains("v2"))
    }

    @Test
    fun evaluateSyncHealth_neverEmitsFalseV2WarningBeforeVerificationOrDuringFreshInstall() {
        val preSyncConnecting = SyncHealthState(
            isSyncing = true,
            hasVerifiedScriptStatus = false,
            lastSyncSucceeded = false,
            scriptVersion = 0,
            driveAuthorized = false
        )
        val offlineBeforeVerify = SyncHealthState(
            isSyncing = false,
            hasVerifiedScriptStatus = false,
            lastSyncSucceeded = false,
            scriptVersion = 0,
            driveAuthorized = false,
            lastErrorMessage = "Unable to resolve host"
        )

        listOf(UserRole.WARGA, UserRole.PENGURUS, UserRole.ADMIN_UTAMA).forEach { role ->
            val evalConnecting = ScriptSyncDiagnostics.evaluateSyncHealth(preSyncConnecting, role)
            assertFalse("Role $role should not show needsScriptUpdate while connecting", evalConnecting.needsScriptUpdate)
            assertFalse(evalConnecting.statusMessage.contains("v2", ignoreCase = true))
            assertFalse(evalConnecting.statusMessage.contains("Versi Lama", ignoreCase = true))

            val evalOffline = ScriptSyncDiagnostics.evaluateSyncHealth(offlineBeforeVerify, role)
            assertFalse("Role $role should not show false script update offline", evalOffline.needsScriptUpdate)
            assertFalse(evalOffline.statusMessage.contains("v2", ignoreCase = true))
            assertFalse(evalOffline.statusMessage.contains("Versi Lama", ignoreCase = true))
        }
    }

    @Test
    fun evaluateSyncHealth_verifiedV9WithDriveAuthorizedNeverShowsOutdatedWarningEvenWithCellImageFormula() {
        val verifiedV9 = SyncHealthState(
            isSyncing = false,
            hasVerifiedScriptStatus = true,
            lastSyncSucceeded = true,
            scriptVersion = 9,
            driveAuthorized = true,
            hasUnreadableImageFormula = true
        )

        listOf(UserRole.WARGA, UserRole.PENGURUS, UserRole.ADMIN_UTAMA).forEach { role ->
            val eval = ScriptSyncDiagnostics.evaluateSyncHealth(verifiedV9, role)
            assertFalse("Role $role must not flag v9+Drive as outdated", eval.needsScriptUpdate)
            assertFalse(eval.isWarning)
            assertEquals("🟢 Tersinkron Realtime — Aplikasi_Warga", eval.statusMessage)
        }
    }

    @Test
    fun evaluateSyncHealth_temporaryOfflineAfterV9VerifiedPreservesHealthyVersionState() {
        val transientOffline = SyncHealthState(
            isSyncing = false,
            hasVerifiedScriptStatus = true,
            lastSyncSucceeded = false,
            scriptVersion = 9,
            driveAuthorized = true,
            lastErrorMessage = "timeout"
        )
        val adminEval = ScriptSyncDiagnostics.evaluateSyncHealth(transientOffline, UserRole.ADMIN_UTAMA)
        assertFalse(adminEval.needsScriptUpdate)
        assertTrue(adminEval.isWarning)
        assertTrue(adminEval.statusMessage.contains("Gagal Sync: timeout"))
        assertFalse(adminEval.statusMessage.contains("v2", ignoreCase = true))

        val wargaEval = ScriptSyncDiagnostics.evaluateSyncHealth(transientOffline, UserRole.WARGA)
        assertFalse(wargaEval.needsScriptUpdate)
        assertTrue(wargaEval.isWarning)
        assertTrue(wargaEval.statusMessage.contains("Menunggu koneksi internet"))
    }

    @Test
    fun evaluateSyncHealth_genuineV2OrUnauthorizedDriveWarnsOnlyPengurusAndAdminWithoutContradictingWarga() {
        val genuineV2 = SyncHealthState(
            isSyncing = false,
            hasVerifiedScriptStatus = true,
            lastSyncSucceeded = true,
            scriptVersion = 2,
            driveAuthorized = false
        )
        val adminV2 = ScriptSyncDiagnostics.evaluateSyncHealth(genuineV2, UserRole.ADMIN_UTAMA)
        assertTrue(adminV2.needsScriptUpdate)
        assertTrue(adminV2.statusMessage.contains("v2"))

        val pengurusV2 = ScriptSyncDiagnostics.evaluateSyncHealth(genuineV2, UserRole.PENGURUS)
        assertTrue(pengurusV2.needsScriptUpdate)
        assertTrue(pengurusV2.statusMessage.contains("v2"))

        val wargaV2 = ScriptSyncDiagnostics.evaluateSyncHealth(genuineV2, UserRole.WARGA)
        assertEquals("🟢 Tersinkron Realtime — Aplikasi_Warga", wargaV2.statusMessage)
        assertFalse(wargaV2.isWarning)

        val missingDrive = SyncHealthState(
            isSyncing = false,
            hasVerifiedScriptStatus = true,
            lastSyncSucceeded = true,
            scriptVersion = 9,
            driveAuthorized = false
        )
        val adminNoDrive = ScriptSyncDiagnostics.evaluateSyncHealth(missingDrive, UserRole.ADMIN_UTAMA)
        assertTrue(adminNoDrive.needsScriptUpdate)
        assertTrue(adminNoDrive.statusMessage.contains("otorisasiDanCekSetup"))
    }

    @Test
    fun appsScriptTemplate_exposesV9MetadataAcrossDoGetAndTestConnection() {
        val script = GoogleAppsScriptTemplate.SCRIPT_CODE
        assertTrue(script.contains("version: 9"))
        assertTrue(script.contains("action === \"test_connection\""))
        assertTrue(script.contains("function otorisasiDanCekSetup()"))
        assertTrue(script.contains("driveAuthorized: driveStatus.ok"))
    }
}
