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
    onSubmit: (String) -> Unit
) {
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
                text = "Otoritas Pengurus RT",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Akses edit, tambah, dan hapus hanya untuk Pengurus dan Admin. Masukkan password otoritas:",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Password Otoritas",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
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
                onClick = { onSubmit(password) },
                enabled = password.isNotBlank(),
                modifier = Modifier.testTag("authority_submit_button")
            ) {
                Text("Buka Akses")
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
