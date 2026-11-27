package com.example

import com.example.data.model.AspirasiItem
import com.example.data.model.PengurusAccount
import com.example.data.model.UserRole
import com.example.data.repository.WargaRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthorityTest {

    private val activeAccount = PengurusAccount(
        id = "ACC-1",
        namaAsli = "Bapak Budi Santoso",
        username = "budi05",
        password = "pAssBudi!",
        aktif = true
    )

    private val disabledAccount = PengurusAccount(
        id = "ACC-2",
        namaAsli = "Ibu Sari",
        username = "sari05",
        password = "passSari!",
        aktif = false
    )

    @Test
    fun resolveAuthorityLogin_removesLegacy005011AndRequiresAdminManagedPengurusAccounts() {
        val accounts = listOf(activeAccount, disabledAccount)

        // Legacy 005011 MUST no longer work for Pengurus login
        val legacyBlankUser = WargaRepository.resolveAuthorityLogin("", "005011", accounts)
        assertTrue(legacyBlankUser is WargaRepository.AuthorityLoginResult.Failure)

        val legacyWithUser = WargaRepository.resolveAuthorityLogin("pengurus", "005011", accounts)
        assertTrue(legacyWithUser is WargaRepository.AuthorityLoginResult.Failure)

        // Admin Induk primary credential: username "Adhit" + password "080309"
        val adminAdhit = WargaRepository.resolveAuthorityLogin("Adhit", "080309", accounts)
        assertTrue(adminAdhit is WargaRepository.AuthorityLoginResult.Success)
        assertEquals(UserRole.ADMIN_UTAMA, (adminAdhit as WargaRepository.AuthorityLoginResult.Success).role)

        val adminAdhitCaseInsensitive = WargaRepository.resolveAuthorityLogin(" adhit ", " 080309 ", accounts)
        assertTrue(adminAdhitCaseInsensitive is WargaRepository.AuthorityLoginResult.Success)
        assertEquals(UserRole.ADMIN_UTAMA, (adminAdhitCaseInsensitive as WargaRepository.AuthorityLoginResult.Success).role)

        // Admin Induk password 080309 also preserves backward compatibility with blank or "admin" username
        val adminBlankUser = WargaRepository.resolveAuthorityLogin("", "080309", accounts)
        assertTrue(adminBlankUser is WargaRepository.AuthorityLoginResult.Success)
        assertEquals(UserRole.ADMIN_UTAMA, (adminBlankUser as WargaRepository.AuthorityLoginResult.Success).role)

        val adminWithUser = WargaRepository.resolveAuthorityLogin("Admin", " 080309 ", accounts)
        assertTrue(adminWithUser is WargaRepository.AuthorityLoginResult.Success)
        assertEquals(UserRole.ADMIN_UTAMA, (adminWithUser as WargaRepository.AuthorityLoginResult.Success).role)

        // Unrelated username with Admin password must be rejected
        val adminWrongUser = WargaRepository.resolveAuthorityLogin("budi05", "080309", accounts)
        assertTrue(adminWrongUser is WargaRepository.AuthorityLoginResult.Failure)

        // Active Admin-created Pengurus account logs in with username + password
        val pengurusOk = WargaRepository.resolveAuthorityLogin("BUDI05", "pAssBudi!", accounts)
        assertTrue(pengurusOk is WargaRepository.AuthorityLoginResult.Success)
        val okSuccess = pengurusOk as WargaRepository.AuthorityLoginResult.Success
        assertEquals(UserRole.PENGURUS, okSuccess.role)
        assertEquals("Bapak Budi Santoso", okSuccess.pengurusAccount?.namaAsli)

        // Deactivated Pengurus account is blocked
        val pengurusDisabled = WargaRepository.resolveAuthorityLogin("sari05", "passSari!", accounts)
        assertTrue(pengurusDisabled is WargaRepository.AuthorityLoginResult.Failure)
        assertTrue((pengurusDisabled as WargaRepository.AuthorityLoginResult.Failure).isDisabledAccount)

        // Wrong password returns expected warning text
        val wrongPass = WargaRepository.resolveAuthorityLogin("budi05", "salah", accounts)
        assertTrue(wrongPass is WargaRepository.AuthorityLoginResult.Failure)
        assertFalse((wrongPass as WargaRepository.AuthorityLoginResult.Failure).isDisabledAccount)
        assertEquals("Lupa ya Broooo.....!", wrongPass.message)
    }

    @Test
    fun resolveRoleDisplayName_showsPengurusNameAndAdminProfileName() {
        val pengurusList = listOf(
            com.example.data.model.PengurusItem(
                id = "PGR-01",
                jabatan = "Sekertaris",
                nama = "Adithiawan Wijaksana"
            )
        )
        assertEquals(
            "Warga",
            WargaRepository.resolveRoleDisplayName(UserRole.WARGA, null, listOf(activeAccount), pengurusList)
        )
        assertEquals(
            "Bapak Budi Santoso",
            WargaRepository.resolveRoleDisplayName(UserRole.PENGURUS, activeAccount, listOf(activeAccount), pengurusList)
        )
        assertEquals(
            "Adithiawan Wijaksana",
            WargaRepository.resolveRoleDisplayName(UserRole.ADMIN_UTAMA, null, listOf(activeAccount), pengurusList)
        )
        assertEquals(
            WargaRepository.ADMIN_UTAMA_DISPLAY_NAME,
            WargaRepository.resolveRoleDisplayName(UserRole.ADMIN_UTAMA, null, emptyList(), emptyList())
        )
    }

    @Test
    fun resolveRealResponderName_exposesRealAccountNameEvenWithPseudonym() {
        val accounts = listOf(activeAccount)
        val item = AspirasiItem(
            id = "ASP-1",
            tanggapanPengurus = "Sudah dibersihkan",
            penanggapNama = "Tim Kebersihan",
            penanggapUsername = "budi05",
            penanggapNamaAsli = "Budi Lama"
        )
        assertEquals("Bapak Budi Santoso", WargaRepository.resolveRealResponderName(item, accounts))

        val adminReply = AspirasiItem(
            id = "ASP-2",
            tanggapanPengurus = "Sudah ditindaklanjuti",
            penanggapNama = "Pengurus Lingkungan",
            penanggapUsername = "Admin",
            penanggapNamaAsli = "Admin Induk"
        )
        assertEquals("Admin Induk", WargaRepository.resolveRealResponderName(adminReply, accounts))

        val adhitReply = AspirasiItem(
            id = "ASP-3",
            tanggapanPengurus = "Sudah selesai",
            penanggapNama = "Sekretaris",
            penanggapUsername = "Adhit",
            penanggapNamaAsli = ""
        )
        assertEquals("Adithiawan Wijaksana", WargaRepository.resolveRealResponderName(adhitReply, accounts))
    }

    @Test
    fun clampSplashDurationMs_boundsBetween1And15Seconds() {
        assertEquals(2000L, WargaRepository.DEFAULT_SPLASH_DURATION_MS)
        assertEquals(1000L, WargaRepository.clampSplashDurationMs(0L))
        assertEquals(1000L, WargaRepository.clampSplashDurationMs(500L))
        assertEquals(7000L, WargaRepository.clampSplashDurationMs(7000L))
        assertEquals(15000L, WargaRepository.clampSplashDurationMs(30000L))
    }
}
