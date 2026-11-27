package com.example

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.PengurusAccount
import com.example.data.model.UserRole
import com.example.data.repository.WargaRepository
import com.example.ui.components.AppHeader
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AuthoritySessionAndHeaderTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("aplikasi_warga_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun adminSession_persistsAcrossAppRestartAndClearsOnlyOnExplicitWargaLogout() {
        val repo1 = WargaRepository(context)
        assertEquals(UserRole.WARGA, repo1.userRole.value)
        assertEquals("Warga", repo1.activeRoleDisplayName.value)

        // Login sebagai Admin dengan username "Adhit" dan password "080309"
        val login = repo1.verifyAuthority("Adhit", "080309")
        assertTrue(login is WargaRepository.AuthorityLoginResult.Success)
        assertEquals(UserRole.ADMIN_UTAMA, repo1.userRole.value)
        assertEquals("Adithiawan Wijaksana", repo1.activeRoleDisplayName.value)

        // Simulasi aplikasi ditutup langsung (swipe / force close) lalu dibuka kembali
        val repoAfterRestart = WargaRepository(context)
        assertEquals(UserRole.ADMIN_UTAMA, repoAfterRestart.userRole.value)
        assertEquals("Adithiawan Wijaksana", repoAfterRestart.activeRoleDisplayName.value)

        // Sengaja beralih kembali ke mode Warga -> sesi dibersihkan
        repoAfterRestart.lockToWargaMode()
        assertEquals(UserRole.WARGA, repoAfterRestart.userRole.value)
        assertEquals("Warga", repoAfterRestart.activeRoleDisplayName.value)

        // Buka ulang aplikasi -> tetap di mode Warga, wajib login lagi
        val repoAfterLogoutRestart = WargaRepository(context)
        assertEquals(UserRole.WARGA, repoAfterLogoutRestart.userRole.value)
        assertEquals("Warga", repoAfterLogoutRestart.activeRoleDisplayName.value)
    }

    @Test
    fun pengurusSession_persistsAcrossRestartEnforcesActiveAccountAndClearsOnWargaSwitch() {
        val seedRepo = WargaRepository(context)
        assertTrue(seedRepo.verifyAuthority("Adhit", "080309") is WargaRepository.AuthorityLoginResult.Success)
        val saveRes = seedRepo.savePengurusAccount(
            PengurusAccount(
                id = "ACC-BUDI",
                namaAsli = "Bapak Budi Santoso",
                username = "budi05",
                password = "passwordBudi",
                aktif = true
            )
        )
        assertTrue(saveRes.first)
        // Admin cannot be shadowed by creating an account named Adhit
        val reservedRes = seedRepo.savePengurusAccount(
            PengurusAccount(
                id = "ACC-ADHIT",
                namaAsli = "Fake",
                username = "Adhit",
                password = "123",
                aktif = true
            )
        )
        assertFalse(reservedRes.first)

        seedRepo.lockToWargaMode()

        // Login sebagai Pengurus
        val pengurusLogin = seedRepo.verifyAuthority("budi05", "passwordBudi")
        assertTrue(pengurusLogin is WargaRepository.AuthorityLoginResult.Success)
        assertEquals(UserRole.PENGURUS, seedRepo.userRole.value)
        assertEquals("Bapak Budi Santoso", seedRepo.activeRoleDisplayName.value)

        // Simulasi aplikasi langsung ditutup lalu dibuka lagi -> tetap mode Pengurus dengan nama pengurus
        val repoAfterRestart = WargaRepository(context)
        assertEquals(UserRole.PENGURUS, repoAfterRestart.userRole.value)
        assertEquals("Bapak Budi Santoso", repoAfterRestart.activeRoleDisplayName.value)
        assertEquals("budi05", repoAfterRestart.currentPengurusAccount.value?.username)

        // Sengaja keluar ke mode Warga -> sesi login berakhir
        repoAfterRestart.lockToWargaMode()
        assertEquals(UserRole.WARGA, repoAfterRestart.userRole.value)

        val repoAfterLogoutRestart = WargaRepository(context)
        assertEquals(UserRole.WARGA, repoAfterLogoutRestart.userRole.value)
        assertEquals("Warga", repoAfterLogoutRestart.activeRoleDisplayName.value)
    }

    @Test
    fun appHeader_displaysLoggedInPengurusAndAdminNamesWithoutOverlappingRightIcons() {
        var currentRole by mutableStateOf(UserRole.WARGA)
        var currentDisplayName by mutableStateOf("Warga")

        composeRule.setContent {
            MyApplicationTheme {
                Box(modifier = Modifier.width(360.dp)) {
                    AppHeader(
                        userRole = currentRole,
                        isSyncing = false,
                        roleDisplayName = currentDisplayName,
                        onRoleClick = {},
                        onRefreshClick = {},
                        onOpenSettings = {},
                        onOpenChangePassword = {}
                    )
                }
            }
        }

        // Mode Warga
        composeRule.onNodeWithTag("role_badge_label", useUnmergedTree = true)
            .assertIsDisplayed()
            .assertTextEquals("Warga")

        // Mode Pengurus (nama akun pengurus)
        composeRule.runOnUiThread {
            currentRole = UserRole.PENGURUS
            currentDisplayName = "Bapak Budi Santoso"
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("role_badge_label", useUnmergedTree = true)
            .assertIsDisplayed()
            .assertTextEquals("Bapak Budi Santoso")

        // Pastikan badge tidak menabrak ikon notifikasi, bintang, dan titik tiga pada lebar 360dp
        val badgeBoundsPengurus = composeRule.onNodeWithTag("role_toggle_button").getUnclippedBoundsInRoot()
        val bellBoundsPengurus = composeRule.onNodeWithTag("notification_bell_button").getUnclippedBoundsInRoot()
        val starBoundsPengurus = composeRule.onNodeWithTag("refresh_sheet_button").getUnclippedBoundsInRoot()
        val menuBoundsPengurus = composeRule.onNodeWithTag("more_menu_button").getUnclippedBoundsInRoot()
        assertTrue(badgeBoundsPengurus.right <= bellBoundsPengurus.left)
        assertTrue(bellBoundsPengurus.right <= starBoundsPengurus.left)
        assertTrue(starBoundsPengurus.right <= menuBoundsPengurus.left)

        // Mode Admin (nama profil Admin)
        composeRule.runOnUiThread {
            currentRole = UserRole.ADMIN_UTAMA
            currentDisplayName = "Adithiawan Wijaksana"
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("role_badge_label", useUnmergedTree = true)
            .assertIsDisplayed()
            .assertTextEquals("Adithiawan Wijaksana")

        val badgeBoundsAdmin = composeRule.onNodeWithTag("role_toggle_button").getUnclippedBoundsInRoot()
        val bellBoundsAdmin = composeRule.onNodeWithTag("notification_bell_button").getUnclippedBoundsInRoot()
        assertTrue(badgeBoundsAdmin.right <= bellBoundsAdmin.left)
        composeRule.onNodeWithTag("notification_bell_button").assertIsDisplayed()
        composeRule.onNodeWithTag("refresh_sheet_button").assertIsDisplayed()
        composeRule.onNodeWithTag("more_menu_button").assertIsDisplayed()
    }
}
