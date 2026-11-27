package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.PengurusAccount
import com.example.data.model.UserRole
import com.example.data.source.GoogleAppsScriptTemplate
import com.example.ui.theme.GreenIncome
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.SkySoft
import com.example.util.AppNotification
import com.example.util.NotificationHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AuthorityPasswordDialog(
    errorMessage: String?,
    onDismiss: () -> Unit,
    onSubmit: (username: String, password: String) -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                tint = NavyPrimary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "Password Otoritas",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("authority_username_input")
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                contentDescription = if (passwordVisible) "Sembunyikan password" else "Tampilkan password"
                            )
                        }
                    },
                    isError = errorMessage != null,
                    supportingText = if (errorMessage != null) {
                        { Text(errorMessage, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold) }
                    } else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("authority_password_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(username, password) },
                enabled = password.isNotBlank(),
                modifier = Modifier.testTag("authority_submit_button")
            ) {
                Text("Konfirmasi")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("authority_cancel_button")
            ) {
                Text("Batal")
            }
        }
    )
}

@Composable
fun LogoutAuthorityDialog(
    currentRole: UserRole,
    activeDisplayName: String = "",
    onDismiss: () -> Unit,
    onConfirmLogout: () -> Unit
) {
    val roleLabel = when (currentRole) {
        UserRole.ADMIN_UTAMA -> "Admin"
        UserRole.PENGURUS -> "Pengurus"
        UserRole.WARGA -> "Warga"
    }
    val cleanDisplayName = activeDisplayName.trim()
    val bodyText = if (cleanDisplayName.isNotBlank() && !cleanDisplayName.equals(roleLabel, ignoreCase = true)) {
        "Apakah Anda ingin keluar dari sesi $cleanDisplayName ($roleLabel) dan kembali ke mode Warga?"
    } else {
        "Apakah Anda ingin keluar dari mode $roleLabel dan kembali ke mode Warga?"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Filled.Logout,
                contentDescription = null,
                tint = NavyPrimary,
                modifier = Modifier.size(30.dp)
            )
        },
        title = {
            Text(
                text = "Keluar dari Mode $roleLabel",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Text(
                text = bodyText,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirmLogout,
                modifier = Modifier.testTag("logout_authority_confirm_button")
            ) {
                Text("Keluar ke Mode Warga")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("logout_authority_cancel_button")
            ) {
                Text("Batal")
            }
        }
    )
}

@Composable
fun SheetConfigDialog(
    currentUrl: String,
    onDismiss: () -> Unit,
    onSaveUrl: (String) -> Unit,
    onTestConnection: ((urlToTest: String, onResult: (Boolean, String) -> Unit) -> Unit)? = null,
    onBackupAll: ((urlToUse: String) -> Unit)? = null
) {
    val context = LocalContext.current
    var urlInput by remember { mutableStateOf(currentUrl) }
    var showScriptCode by remember { mutableStateOf(false) }
    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CloudSync,
                        contentDescription = null,
                        tint = NavyPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Sinkronisasi Google Sheets",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Aplikasi ini terhubung dua arah dengan spreadsheet 'Aplikasi_Warga' untuk kas RT, pengurus, undangan, galeri, dan aspirasi warga.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Target Spreadsheet ID Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Description, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Spreadsheet Target Anda:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF166534))
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Aplikasi_Warga",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Color(0xFF14532D)
                        )
                        Text(
                            text = "ID: ${GoogleAppsScriptTemplate.TARGET_SPREADSHEET_ID}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = Color(0xFF15803D)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(GoogleAppsScriptTemplate.TARGET_SPREADSHEET_URL))
                                context.startActivity(intent)
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Buka Spreadsheet di Browser", fontSize = 11.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = urlInput,
                    onValueChange = {
                        urlInput = it
                        testResult = null
                    },
                    label = { Text("URL Web App Google Apps Script (/exec)") },
                    placeholder = { Text("https://script.google.com/macros/s/.../exec") },
                    singleLine = false,
                    maxLines = 3,
                    leadingIcon = { Icon(Icons.Outlined.Link, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sheet_url_input")
                )

                if (urlInput.trim() != GoogleAppsScriptTemplate.EMBEDDED_WEB_APP_URL) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = {
                                urlInput = GoogleAppsScriptTemplate.EMBEDDED_WEB_APP_URL
                                testResult = null
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Filled.Restore, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Gunakan URL Bawaan Aplikasi", fontSize = 11.sp)
                        }
                    }
                }

                // Warning if user pasted docs.google.com
                if (urlInput.contains("docs.google.com/spreadsheets")) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFFEF3C7),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Warning, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Tautan Spreadsheet Browser Terdeteksi", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF92400E))
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Aplikasi HP tidak bisa menulis langsung ke URL docs.google.com karena Google mewajibkan login akun. Silakan tempel kode script di bawah ke Apps Script spreadsheet Anda dan gunakan URL Web App (/exec) yang dihasilkan.",
                                fontSize = 11.sp,
                                color = Color(0xFF78350F),
                                lineHeight = 16.sp
                            )
                        }
                    }
                } else if (urlInput.contains("script.google.com/macros/s/")) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "✅ Format URL Web App Valid",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF16A34A)
                        )
                        TextButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("URL Web App", urlInput.trim()))
                                Toast.makeText(context, "URL Web App disalin! Kirimkan ke chat AI Studio agar ditanamkan ke file APK warga.", Toast.LENGTH_LONG).show()
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Salin URL", fontSize = 11.sp)
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFEFF6FF),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(8.dp)
                        ) {
                            Icon(Icons.Outlined.Info, contentDescription = null, tint = Color(0xFF2563EB), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Kirimkan URL ini di chat AI Studio untuk ditanamkan ke dalam file APK. Setelah ditanamkan, setiap HP warga yang baru install akan otomatis terhubung ke saldo & pengumuman yang sama!",
                                fontSize = 10.sp,
                                color = Color(0xFF1E40AF),
                                lineHeight = 14.sp
                            )
                        }
                    }
                }

                // Quick Action Buttons: Uji Koneksi & Backup Semua Data
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val trimmed = urlInput.trim()
                            if (trimmed.isBlank()) {
                                Toast.makeText(context, "Masukkan URL Web App terlebih dahulu", Toast.LENGTH_SHORT).show()
                                return@OutlinedButton
                            }
                            isTesting = true
                            testResult = null
                            onTestConnection?.invoke(trimmed) { ok, msg ->
                                isTesting = false
                                testResult = Pair(ok, msg)
                            }
                        },
                        enabled = !isTesting,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("test_sheet_connection_button")
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Menguji...", fontSize = 11.sp)
                        } else {
                            Icon(Icons.Filled.NetworkCheck, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Uji Koneksi", fontSize = 11.sp)
                        }
                    }

                    Button(
                        onClick = {
                            val trimmed = urlInput.trim()
                            if (trimmed.isBlank() || trimmed.contains("docs.google.com/spreadsheets")) {
                                Toast.makeText(context, "Masukkan URL Web App (/exec) yang valid terlebih dahulu", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            onBackupAll?.invoke(trimmed)
                            Toast.makeText(context, "Memulai backup seluruh data ke Spreadsheet...", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("backup_all_to_sheet_button")
                    ) {
                        Icon(Icons.Filled.Backup, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Backup Semua", fontSize = 11.sp)
                    }
                }

                // Test Connection Feedback Result Banner
                testResult?.let { (success, message) ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (success) Color(0xFFDCFCE7) else Color(0xFFFEE2E2),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(10.dp)
                        ) {
                            Icon(
                                imageVector = if (success) Icons.Filled.CheckCircle else Icons.Filled.Error,
                                contentDescription = null,
                                tint = if (success) Color(0xFF16A34A) else Color(0xFFDC2626),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            SelectionContainer {
                                Text(
                                    text = message,
                                    color = if (success) Color(0xFF14532D) else Color(0xFF7F1D1D),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Guide card
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Cara Menghubungkan ke Spreadsheet Anda:",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "1. Buka spreadsheet Anda (tekan tombol hijau di atas) lalu klik menu Ekstensi -> Apps Script.\n" +
                                   "2. Hapus SEMUA kode lama di Code.gs, tekan 'Salin Script' / 'Bagikan' di bawah, tempel kode v9 dari baris MULAI sampai AKHIR, lalu klik Simpan (Ctrl+S).\n" +
                                   "3. WAJIB (1x saja): Di toolbar atas editor Apps Script, pilih fungsi 'otorisasiDanCekSetup' lalu klik Run -> Review permissions -> Allow agar izin Google Drive (simpan foto) aktif.\n" +
                                   "4. PENTING agar URL lama (/exec) ikut berubah: klik Deploy -> Manage deployments -> ikon Pensil (Edit) -> pada dropdown 'Version' WAJIB pilih 'New version' (jangan biarkan angka lama!) -> klik Deploy.\n" +
                                   "5. Jika pernah klik 'New deployment', salin URL /exec baru tersebut ke kolom di atas lalu tekan 'Uji Koneksi' (dan kirim ke chat agar ditanam ke APK warga).",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("AppsScript_AplikasiWarga", GoogleAppsScriptTemplate.SCRIPT_CODE)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "Kode Script v9 disalin (${GoogleAppsScriptTemplate.SCRIPT_CODE.length} karakter)!", Toast.LENGTH_SHORT).show()
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Salin Script", fontSize = 11.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, "Apps Script v9 - Aplikasi Warga RT 005")
                                        putExtra(Intent.EXTRA_TEXT, GoogleAppsScriptTemplate.SCRIPT_CODE)
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "Kirim Kode Apps Script v9"))
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Bagikan", fontSize = 11.sp)
                            }

                            TextButton(
                                onClick = { showScriptCode = !showScriptCode },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                            ) {
                                Text(if (showScriptCode) "Tutup" else "Lihat", fontSize = 11.sp)
                            }
                        }

                        if (showScriptCode) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                color = Color(0xFF1E293B),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                SelectionContainer {
                                    Text(
                                        text = GoogleAppsScriptTemplate.SCRIPT_CODE,
                                        color = Color(0xFFE2E8F0),
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Batal")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onSaveUrl(urlInput) },
                        modifier = Modifier.testTag("save_sheet_url_button")
                    ) {
                        Text("Simpan & Hubungkan")
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationCenterDialog(
    notifications: List<AppNotification>,
    onDismiss: () -> Unit,
    onClearAll: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM, HH:mm", Locale("in", "ID")) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Dialog Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Notifications,
                            contentDescription = null,
                            tint = NavyPrimary,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Pemberitahuan Warga",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }

                    if (notifications.isNotEmpty()) {
                        TextButton(onClick = onClearAll) {
                            Text("Hapus Semua", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                Text(
                    text = "Update real-time berita desa, aspirasi baru, dan kas RT",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                if (notifications.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Outlined.NotificationsNone,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(54.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Belum ada pemberitahuan baru",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Semua berita, aspirasi, dan kas akan muncul di sini",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(notifications, key = { it.id }) { notif ->
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (!notif.isRead) SkySoft else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = when (notif.type) {
                                                "ASPIRASI" -> Color(0xFFF59E0B)
                                                "KAS" -> Color(0xFF10B981)
                                                "UNDANGAN" -> Color(0xFF6366F1)
                                                "PENGINGAT" -> Color(0xFFEA580C)
                                                else -> NavyPrimary
                                            }
                                        ) {
                                            Text(
                                                text = notif.type,
                                                color = Color.White,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }

                                        Text(
                                            text = dateFormat.format(Date(notif.timestamp)),
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Text(
                                        text = notif.title,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    Spacer(modifier = Modifier.height(3.dp))

                                    Text(
                                        text = notif.message,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Tutup")
                }
            }
        }
    }
}

@Composable
fun ChangePasswordDialog(
    errorMessage: String? = null,
    onVerifyMasterPassword: (String) -> Boolean,
    onClearError: () -> Unit = {},
    onDismiss: () -> Unit,
    onSubmit: (masterPass: String, newPass: String) -> Unit
) {
    var isMasterVerified by remember { mutableStateOf(false) }
    var masterPass by remember { mutableStateOf("") }
    var masterPassVisible by remember { mutableStateOf(false) }

    var newPass by remember { mutableStateOf("") }
    var confirmPass by remember { mutableStateOf("") }
    var newPassVisible by remember { mutableStateOf(false) }
    var confirmPassVisible by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }

    val activeError = localError ?: errorMessage

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Filled.Key,
                contentDescription = null,
                tint = NavyPrimary,
                modifier = Modifier.size(30.dp)
            )
        },
        title = {
            Text(
                text = if (!isMasterVerified) "Verifikasi Ubah Password" else "Ubah Password Otoritas RT",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (!isMasterVerified) {
                    Text(
                        text = "Untuk mengubah password otoritas, wajib memasukkan password khusus terlebih dahulu.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Password Khusus",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = masterPass,
                        onValueChange = {
                            masterPass = it
                            localError = null
                            onClearError()
                        },
                        singleLine = true,
                        visualTransformation = if (masterPassVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { masterPassVisible = !masterPassVisible }) {
                                Icon(
                                    imageVector = if (masterPassVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                    contentDescription = if (masterPassVisible) "Sembunyikan password" else "Tampilkan password"
                                )
                            }
                        },
                        isError = activeError != null,
                        supportingText = if (activeError != null) {
                            {
                                Text(
                                    text = activeError,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("change_password_master_input")
                    )
                } else {
                    Text(
                        text = "Verifikasi berhasil. Silakan masukkan password otoritas baru untuk pengurus.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Password Baru",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = newPass,
                        onValueChange = {
                            newPass = it
                            localError = null
                            onClearError()
                        },
                        singleLine = true,
                        visualTransformation = if (newPassVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { newPassVisible = !newPassVisible }) {
                                Icon(
                                    imageVector = if (newPassVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                    contentDescription = if (newPassVisible) "Sembunyikan password" else "Tampilkan password"
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("change_password_new_input")
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Ulangi Password Baru",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = confirmPass,
                        onValueChange = {
                            confirmPass = it
                            localError = null
                            onClearError()
                        },
                        singleLine = true,
                        visualTransformation = if (confirmPassVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { confirmPassVisible = !confirmPassVisible }) {
                                Icon(
                                    imageVector = if (confirmPassVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                    contentDescription = if (confirmPassVisible) "Sembunyikan password" else "Tampilkan password"
                                )
                            }
                        },
                        isError = activeError != null,
                        supportingText = if (activeError != null) {
                            {
                                Text(
                                    text = activeError,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("change_password_confirm_input")
                    )
                }
            }
        },
        confirmButton = {
            if (!isMasterVerified) {
                Button(
                    onClick = {
                        if (masterPass.isBlank()) {
                            localError = "Wajib memasukkan password terlebih dahulu"
                        } else if (onVerifyMasterPassword(masterPass)) {
                            localError = null
                            isMasterVerified = true
                        } else {
                            localError = "HUB ADHITIAWAN"
                        }
                    },
                    modifier = Modifier.testTag("change_password_verify_button")
                ) {
                    Text("Verifikasi")
                }
            } else {
                Button(
                    onClick = {
                        if (newPass.isBlank()) {
                            localError = "Password baru tidak boleh kosong"
                        } else if (newPass != confirmPass) {
                            localError = "Konfirmasi password baru tidak cocok"
                        } else {
                            onSubmit(masterPass, newPass)
                        }
                    },
                    modifier = Modifier.testTag("change_password_save_button")
                ) {
                    Text("Simpan Password")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}

@Composable
fun PengurusAccountsDialog(
    accounts: List<PengurusAccount>,
    onDismiss: () -> Unit,
    onSaveAccount: (PengurusAccount) -> Pair<Boolean, String>,
    onToggleActive: (accountId: String, aktif: Boolean) -> Unit,
    onDeleteAccount: (accountId: String) -> Unit
) {
    var editingAccount by remember { mutableStateOf<PengurusAccount?>(null) }
    var showForm by remember { mutableStateOf(false) }
    var revealedIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.84f)
                .padding(vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = Icons.Outlined.ManageAccounts,
                            contentDescription = null,
                            tint = NavyPrimary,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Pengaturan Akun Pengurus",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "Khusus Admin Induk • Buat, aktifkan & nonaktifkan akses pengurus",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        editingAccount = null
                        showForm = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_pengurus_account_button")
                ) {
                    Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Tambah Akun Pengurus")
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (accounts.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.SupervisorAccount,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Belum ada akun pengurus",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Buat username & password unik untuk setiap pengurus agar akses & nama aslinya tercatat.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(accounts, key = { it.id }) { acc ->
                            val isPasswordShown = acc.id in revealedIds
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (acc.aktif) {
                                        Color(0xFFF0FDF4)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                                    }
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("pengurus_account_card_${acc.id}")
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = acc.namaAsli,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "Username: ${acc.username}",
                                                fontSize = 12.sp,
                                                color = NavyPrimary,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = if (acc.aktif) Color(0xFFDCFCE7) else Color(0xFFFEE2E2)
                                        ) {
                                            Text(
                                                text = if (acc.aktif) "Aktif" else "Nonaktif",
                                                color = if (acc.aktif) Color(0xFF166534) else Color(0xFF991B1B),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "Password: ${if (isPasswordShown) acc.password else "••••••••"}",
                                                fontSize = 12.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            IconButton(
                                                onClick = {
                                                    revealedIds = if (isPasswordShown) {
                                                        revealedIds - acc.id
                                                    } else {
                                                        revealedIds + acc.id
                                                    }
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (isPasswordShown) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                                    contentDescription = if (isPasswordShown) "Sembunyikan password" else "Lihat password",
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Switch(
                                                checked = acc.aktif,
                                                onCheckedChange = { checked ->
                                                    onToggleActive(acc.id, checked)
                                                },
                                                modifier = Modifier.testTag("pengurus_account_switch_${acc.id}")
                                            )
                                            IconButton(
                                                onClick = {
                                                    editingAccount = acc
                                                    showForm = true
                                                },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.Edit,
                                                    contentDescription = "Edit Akun",
                                                    tint = NavyPrimary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            IconButton(
                                                onClick = { onDeleteAccount(acc.id) },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.Delete,
                                                    contentDescription = "Hapus Akun",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Tutup")
                }
            }
        }
    }

    if (showForm) {
        PengurusAccountFormDialog(
            initial = editingAccount,
            onDismiss = { showForm = false },
            onSave = { account ->
                val result = onSaveAccount(account)
                if (result.first) {
                    showForm = false
                }
                result
            }
        )
    }
}

@Composable
private fun PengurusAccountFormDialog(
    initial: PengurusAccount?,
    onDismiss: () -> Unit,
    onSave: (PengurusAccount) -> Pair<Boolean, String>
) {
    var namaAsli by remember(initial) { mutableStateOf(initial?.namaAsli ?: "") }
    var username by remember(initial) { mutableStateOf(initial?.username ?: "") }
    var password by remember(initial) { mutableStateOf(initial?.password ?: "") }
    var aktif by remember(initial) { mutableStateOf(initial?.aktif ?: true) }
    var passwordVisible by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initial == null) "Buat Akun Pengurus" else "Edit Akun Pengurus",
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = namaAsli,
                    onValueChange = {
                        namaAsli = it
                        errorMsg = null
                    },
                    label = { Text("Nama Asli Pengurus *") },
                    placeholder = { Text("Contoh: Bapak Budi Santoso") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("account_nama_asli_input")
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = {
                        username = it
                        errorMsg = null
                    },
                    label = { Text("Username *") },
                    placeholder = { Text("Contoh: budi_rt05") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("account_username_input")
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        errorMsg = null
                    },
                    label = { Text("Password *") },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                contentDescription = if (passwordVisible) "Sembunyikan password" else "Tampilkan password"
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("account_password_input")
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (aktif) "Status Akun: Aktif" else "Status Akun: Nonaktif",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Switch(
                        checked = aktif,
                        onCheckedChange = { aktif = it },
                        modifier = Modifier.testTag("account_aktif_switch")
                    )
                }
                if (errorMsg != null) {
                    Text(
                        text = errorMsg.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val acc = PengurusAccount(
                        id = initial?.id ?: "",
                        namaAsli = namaAsli.trim(),
                        username = username.trim(),
                        password = password.trim(),
                        aktif = aktif,
                        createdAt = initial?.createdAt ?: System.currentTimeMillis()
                    )
                    val (ok, msg) = onSave(acc)
                    if (!ok) {
                        errorMsg = msg
                    }
                },
                enabled = namaAsli.isNotBlank() && username.isNotBlank() && password.isNotBlank(),
                modifier = Modifier.testTag("save_pengurus_account_button")
            ) {
                Text("Simpan Akun")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}

@Composable
fun SplashDurationDialog(
    currentDurationMs: Long,
    onDismiss: () -> Unit,
    onSaveSeconds: (Int) -> Unit
) {
    val initialSeconds = remember(currentDurationMs) {
        (currentDurationMs / 1000L).toInt().coerceIn(1, 15)
    }
    var secondsText by remember(initialSeconds) { mutableStateOf(initialSeconds.toString()) }
    val presetSeconds = listOf(2, 3, 4, 5, 7, 10)
    val parsedSeconds = secondsText.toIntOrNull()
    val isValid = parsedSeconds != null && parsedSeconds in 1..15

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Outlined.Timer,
                contentDescription = null,
                tint = NavyPrimary,
                modifier = Modifier.size(30.dp)
            )
        },
        title = {
            Text(
                text = "Durasi Layar Pembuka (Splash)",
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Atur berapa detik gambar pembuka tampil saat aplikasi pertama kali dibuka (1 - 15 detik).",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presetSeconds.take(3).forEach { sec ->
                        FilterChip(
                            selected = parsedSeconds == sec,
                            onClick = { secondsText = sec.toString() },
                            label = { Text("$sec dtk", fontSize = 12.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presetSeconds.drop(3).forEach { sec ->
                        FilterChip(
                            selected = parsedSeconds == sec,
                            onClick = { secondsText = sec.toString() },
                            label = { Text("$sec dtk", fontSize = 12.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                OutlinedTextField(
                    value = secondsText,
                    onValueChange = { input ->
                        secondsText = input.filter { it.isDigit() }.take(2)
                    },
                    label = { Text("Durasi Custom (Detik)") },
                    suffix = { Text("detik") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = !isValid,
                    supportingText = if (!isValid) {
                        { Text("Masukkan angka antara 1 sampai 15 detik", color = MaterialTheme.colorScheme.error) }
                    } else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("splash_duration_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (parsedSeconds != null) {
                        onSaveSeconds(parsedSeconds.coerceIn(1, 15))
                    }
                },
                enabled = isValid,
                modifier = Modifier.testTag("save_splash_duration_button")
            ) {
                Text("Simpan Durasi")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}
