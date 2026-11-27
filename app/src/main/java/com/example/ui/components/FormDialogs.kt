package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.*
import com.example.ui.theme.NavyPrimary
import com.example.util.IndonesianDateHelper
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun AspirasiDialog(
    onDismiss: () -> Unit,
    onSubmit: (AspirasiItem) -> Unit
) {
    val todayDate = remember {
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    var nama by remember { mutableStateOf("") }
    var noRumah by remember { mutableStateOf("") }
    var sembunyikanNama by remember { mutableStateOf(false) } // false: Terlihat, true: Disamarkan
    var kategori by remember { mutableStateOf("Masukan/Saran") } // "Masukan/Saran" or "Kritik"
    var keterangan by remember { mutableStateOf("") }
    var fotoMasalahList by remember { mutableStateOf<List<String>>(emptyList()) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF232730), // Dark card dialog matching Screenshot 00.22
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Kirim Masukan, Saran &\nKritik Warga",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 26.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Aspirasi Anda langsung diarsipkan ke Google Sheets pengurus secara transparan.",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Field 1: Nama Warga (Terpisah)
                OutlinedTextField(
                    value = nama,
                    onValueChange = { nama = it },
                    label = { Text("Nama Warga", color = Color(0xFFCBD5E1)) },
                    placeholder = { Text("Contoh: Pak Budi", color = Color(0xFF64748B)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF60A5FA),
                        unfocusedBorderColor = Color(0xFF475569)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("aspirasi_nama_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Field 2: Nomor Rumah (Terpisah dari Nama)
                OutlinedTextField(
                    value = noRumah,
                    onValueChange = { noRumah = it },
                    label = { Text("Nomor Rumah / Blok", color = Color(0xFFCBD5E1)) },
                    placeholder = { Text("Contoh: No. 12 / Blok B4", color = Color(0xFF64748B)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF60A5FA),
                        unfocusedBorderColor = Color(0xFF475569)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("aspirasi_norumah_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Pilihan: Nama Dilihat atau Tidak Terlihat (Privasi Warga)
                Text(
                    text = "Privasi Nama:",
                    color = Color(0xFFCBD5E1),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .selectable(
                                selected = !sembunyikanNama,
                                onClick = { sembunyikanNama = false },
                                role = Role.RadioButton
                            )
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(
                            selected = !sembunyikanNama,
                            onClick = { sembunyikanNama = false },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = Color(0xFF3B82F6),
                                unselectedColor = Color(0xFF94A3B8)
                            )
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Nama Terlihat", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .selectable(
                                selected = sembunyikanNama,
                                onClick = { sembunyikanNama = true },
                                role = Role.RadioButton
                            )
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(
                            selected = sembunyikanNama,
                            onClick = { sembunyikanNama = true },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = Color(0xFF3B82F6),
                                unselectedColor = Color(0xFF94A3B8)
                            )
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Nama Disamarkan", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Kategori Laporan (Masukan/Saran vs Kritik)
                Text(
                    text = "Kategori Laporan:",
                    color = Color(0xFFCBD5E1),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val categories = listOf("Masukan/Saran", "Kritik")
                    categories.forEach { cat ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .selectable(
                                    selected = (kategori == cat),
                                    onClick = { kategori = cat },
                                    role = Role.RadioButton
                                )
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = (kategori == cat),
                                onClick = { kategori = cat },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = Color(0xFF3B82F6),
                                    unselectedColor = Color(0xFF94A3B8)
                                )
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = cat,
                                color = Color.White,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Keterangan Detail Usulan (Topik/Judul sudah dihapuskan sesuai permintaan!)
                OutlinedTextField(
                    value = keterangan,
                    onValueChange = { keterangan = it },
                    label = { Text("Keterangan Detail Usulan", color = Color(0xFFCBD5E1)) },
                    placeholder = { Text("Tuliskan detail masukan atau kendala secara jelas...", color = Color(0xFF64748B)) },
                    minLines = 3,
                    maxLines = 5,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF60A5FA),
                        unfocusedBorderColor = Color(0xFF475569)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("aspirasi_pesan_input")
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Lampiran Foto: Buka Kamera atau Galeri (Maksimal 5 foto)
                MultiPhotoPickerSection(
                    label = "Lampirkan Foto Masalah / Bukti (Kamera / Galeri - Maks. 5 Foto)",
                    photoUris = fotoMasalahList,
                    onPhotosChanged = { fotoMasalahList = it },
                    maxPhotos = 5,
                    isDarkTheme = true
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Batal", color = Color(0xFF60A5FA), fontSize = 14.sp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val item = AspirasiItem(
                                tanggal = todayDate,
                                nama = nama.ifBlank { "Warga RT 005" },
                                noRumah = noRumah.trim(),
                                sembunyikanNama = sembunyikanNama,
                                kategori = kategori,
                                pesan = keterangan.trim(),
                                fotoMasalah = fotoMasalahList.joinToString(","),
                                status = "Menunggu"
                            )
                            onSubmit(item)
                        },
                        enabled = keterangan.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1D4ED8),
                            contentColor = Color.White
                        ),
                        modifier = Modifier.testTag("submit_aspirasi_button")
                    ) {
                        Text("Kirim Sekarang", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun TanggapanAspirasiDialog(
    item: AspirasiItem,
    userRole: UserRole = UserRole.WARGA,
    onDismiss: () -> Unit,
    onSubmit: (status: String, tanggapan: String, fotoPengerjaan: String, namaPenanggap: String) -> Unit
) {
    var status by remember { mutableStateOf(if (item.status.isBlank()) "Menunggu" else item.status) }
    var tanggapan by remember { mutableStateOf(item.tanggapanPengurus) }
    var namaPenanggap by remember { mutableStateOf(item.penanggapNama) }
    var showNameError by remember { mutableStateOf(false) }
    var fotoPengerjaanList by remember {
        mutableStateOf(
            item.fotoPengerjaan.split(",")
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .take(5)
        )
    }

    val statusOptions = listOf("Menunggu", "Diproses", "Selesai")
    val showRealSender = !item.sembunyikanNama || userRole == UserRole.ADMIN_UTAMA
    val senderLabel = if (showRealSender) {
        item.nama + if (item.noRumah.isNotBlank()) " (Rumah: ${item.noRumah})" else ""
    } else {
        "Warga Disamarkan"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tanggapan Pengurus RT", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Aspirasi dari: $senderLabel",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                if (item.sembunyikanNama) {
                    Text(
                        text = if (userRole == UserRole.ADMIN_UTAMA) {
                            "🔒 Pengirim memilih menyamarkan nama (terlihat khusus mode Admin)"
                        } else {
                            "🔒 Pengirim memilih menyamarkan identitas"
                        },
                        fontSize = 11.sp,
                        color = Color(0xFFD97706)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "\"${item.pesan}\"",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))
                Text("Ubah Status:", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    statusOptions.forEach { opt ->
                        FilterChip(
                            selected = (status == opt),
                            onClick = { status = opt },
                            label = { Text(opt, fontSize = 11.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = tanggapan,
                    onValueChange = { tanggapan = it },
                    label = { Text("Tanggapan Pengurus") },
                    placeholder = { Text("Contoh: Sudah diperbaiki oleh tim kebersihan RT...") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth().testTag("tanggapan_input")
                )

                Spacer(modifier = Modifier.height(12.dp))
                val roleBadgeLabel = if (userRole == UserRole.ADMIN_UTAMA) "Admin" else "Pengurus"
                OutlinedTextField(
                    value = namaPenanggap,
                    onValueChange = {
                        namaPenanggap = it
                        if (it.isNotBlank()) showNameError = false
                    },
                    label = { Text("Nama Penanggap *") },
                    placeholder = { Text("Wajib isi nama Anda / nama samaran") },
                    singleLine = true,
                    isError = showNameError || namaPenanggap.isBlank(),
                    supportingText = {
                        if (showNameError && namaPenanggap.isBlank()) {
                            Text(
                                text = "Nama penanggap wajib diisi sebelum menyimpan",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 11.sp
                            )
                        } else {
                            Text(
                                text = "Wajib diisi • Peran tercatat sebagai $roleBadgeLabel (bisa nama asli atau samaran)",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tanggapan_nama_input")
                )

                Spacer(modifier = Modifier.height(14.dp))
                MultiPhotoPickerSection(
                    label = "Foto Bukti Pengerjaan (Kamera / Galeri - Maks. 5 Foto)",
                    photoUris = fotoPengerjaanList,
                    onPhotosChanged = { fotoPengerjaanList = it.take(5) },
                    maxPhotos = 5
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (namaPenanggap.isBlank()) {
                        showNameError = true
                    } else {
                        onSubmit(status, tanggapan, fotoPengerjaanList.joinToString(","), namaPenanggap.trim())
                    }
                },
                enabled = namaPenanggap.isNotBlank() && tanggapan.isNotBlank(),
                modifier = Modifier.testTag("save_tanggapan_button")
            ) {
                Text("Simpan Tanggapan")
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
fun KasTransactionDialog(
    item: KasItem?,
    onDismiss: () -> Unit,
    onSubmit: (KasItem) -> Unit
) {
    val todayDate = remember {
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    var tanggal by remember {
        mutableStateOf(
            item?.tanggal?.takeIf { it.isNotBlank() }?.let { IndonesianDateHelper.toIsoDate(it) } ?: todayDate
        )
    }
    var kategori by remember { mutableStateOf(item?.kategori ?: "") }
    var keterangan by remember { mutableStateOf(item?.keterangan ?: "") }
    var isMasuk by remember { mutableStateOf((item?.masuk ?: 0L) > 0L || (item == null)) }
    var nominalText by remember {
        mutableStateOf(
            if (item != null) {
                if (item.masuk > 0L) item.masuk.toString() else item.keluar.toString()
            } else ""
        )
    }
    var buktiFoto by remember { mutableStateOf(item?.buktiFoto ?: "") }

    val kategoriSuggestions = remember(isMasuk) {
        if (isMasuk) {
            listOf("Iuran Warga", "Donatur", "Sumbangan", "Lainnya")
        } else {
            listOf("Operasional", "Kebersihan", "Sosial", "Perbaikan", "Lainnya")
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (item == null) "Tambah Transaksi Kas RT" else "Edit Transaksi Kas RT", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Radio Masuk vs Keluar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FilterChip(
                        selected = isMasuk,
                        onClick = { isMasuk = true },
                        label = { Text("Pemasukan (+)", fontWeight = FontWeight.SemiBold) },
                        leadingIcon = { Icon(Icons.Filled.ArrowDownward, contentDescription = null, tint = Color(0xFF16A34A)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFDCFCE7),
                            selectedLabelColor = Color(0xFF166534)
                        )
                    )
                    FilterChip(
                        selected = !isMasuk,
                        onClick = { isMasuk = false },
                        label = { Text("Pengeluaran (-)", fontWeight = FontWeight.SemiBold) },
                        leadingIcon = { Icon(Icons.Filled.ArrowUpward, contentDescription = null, tint = Color(0xFFDC2626)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFFEE2E2),
                            selectedLabelColor = Color(0xFF991B1B)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = nominalText,
                    onValueChange = { nominalText = it.filter { char -> char.isDigit() } },
                    label = { Text("Nominal (Rp)") },
                    placeholder = { Text("Contoh: 150000") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("kas_nominal_input")
                )

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = kategori,
                    onValueChange = { kategori = it },
                    label = { Text("Kategori") },
                    placeholder = { Text("Contoh: Iuran Warga / Operasional") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("kas_kategori_input")
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    kategoriSuggestions.take(3).forEach { suggestion ->
                        FilterChip(
                            selected = kategori.equals(suggestion, ignoreCase = true),
                            onClick = { kategori = suggestion },
                            label = { Text(suggestion, fontSize = 11.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = keterangan,
                    onValueChange = { keterangan = it },
                    label = { Text("Keterangan Transaksi") },
                    placeholder = { Text("Contoh: Iuran Warga Gang Melati") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("kas_keterangan_input")
                )

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = tanggal,
                    onValueChange = { tanggal = it },
                    label = { Text("Tanggal (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))
                PhotoPickerSection(
                    label = "Bukti Foto / Nota Transaksi (Kamera / Galeri)",
                    currentPhotoUri = buktiFoto,
                    onPhotoSelected = { buktiFoto = it },
                    onPhotoRemoved = { buktiFoto = "" }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val nominal = nominalText.toLongOrNull() ?: 0L
                    val derivedPeriode = com.example.util.KasRecapHelper.resolvePeriodeKey(item?.periodeBulan, tanggal)
                    val existingSaldo = item?.effectiveSaldo ?: 0L
                    val newItem = KasItem(
                        id = item?.id ?: "",
                        tanggal = tanggal,
                        periodeBulan = derivedPeriode,
                        kategori = kategori.trim(),
                        keterangan = keterangan,
                        masuk = if (isMasuk) nominal else 0L,
                        keluar = if (!isMasuk) nominal else 0L,
                        saldo = existingSaldo,
                        jumlah = existingSaldo,
                        buktiFoto = buktiFoto
                    )
                    onSubmit(newItem)
                },
                enabled = nominalText.isNotBlank() && keterangan.isNotBlank(),
                modifier = Modifier.testTag("save_kas_button")
            ) {
                Text("Simpan Transaksi")
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
fun UndanganDialog(
    item: UndanganItem?,
    onDismiss: () -> Unit,
    onSubmit: (UndanganItem, isEdit: Boolean) -> Unit
) {
    val todayDate = remember {
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    var isPengumuman by remember { mutableStateOf(item?.isPengumuman ?: false) }
    var rawJudul by remember { mutableStateOf(item?.cleanJudul ?: "") }
    var tanggal by remember {
        mutableStateOf(
            item?.tanggal?.takeIf { it.isNotBlank() }?.let { IndonesianDateHelper.toIsoDate(it) } ?: todayDate
        )
    }
    var waktu by remember { mutableStateOf(item?.waktu ?: if (isPengumuman) "-" else "19:30 WIB") }
    var lokasi by remember { mutableStateOf(item?.lokasi ?: if (isPengumuman) "Lingkungan RT 005" else "") }
    var keterangan by remember { mutableStateOf(item?.cleanKeterangan ?: "") }
    var foto by remember { mutableStateOf(item?.foto ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (item == null) {
                    if (isPengumuman) "Buat Pengumuman Warga" else "Buat Undangan / Acara RT"
                } else {
                    if (isPengumuman) "Edit Pengumuman Warga" else "Edit Undangan Warga"
                },
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Pilihan: Pengumuman atau Undangan (Sesuai Permintaan User!)
                Text(
                    text = "Pilih Jenis:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = isPengumuman,
                        onClick = {
                            isPengumuman = true
                            if (waktu.isBlank() || waktu == "19:30 WIB") waktu = "-"
                            if (lokasi.isBlank()) lokasi = "Lingkungan RT 005"
                        },
                        leadingIcon = {
                            Icon(Icons.Filled.Campaign, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = { Text("Pengumuman", fontSize = 12.sp, fontWeight = if (isPengumuman) FontWeight.Bold else FontWeight.Normal) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFF59E0B),
                            selectedLabelColor = Color.Black
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    FilterChip(
                        selected = !isPengumuman,
                        onClick = {
                            isPengumuman = false
                            if (waktu == "-") waktu = "19:30 WIB"
                            if (lokasi == "Lingkungan RT 005") lokasi = ""
                        },
                        leadingIcon = {
                            Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = { Text("Undangan", fontSize = 12.sp, fontWeight = if (!isPengumuman) FontWeight.Bold else FontWeight.Normal) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NavyPrimary,
                            selectedLabelColor = Color.White
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                if (isPengumuman) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFFEF3C7),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "📢 Pengumuman ini otomatis tampil di Kotak Pengumuman halaman depan (Beranda) menggantikan pengumuman lama.",
                            fontSize = 11.sp,
                            color = Color(0xFF92400E),
                            modifier = Modifier.padding(8.dp),
                            lineHeight = 15.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = rawJudul,
                    onValueChange = { rawJudul = it },
                    label = { Text(if (isPengumuman) "Judul Pengumuman" else "Judul Acara / Kegiatan") },
                    placeholder = { Text(if (isPengumuman) "Contoh: Kerja Bakti & Fogging Nyamuk" else "Contoh: Rapat Pleno Warga RT 005") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("undangan_judul_input")
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = tanggal,
                        onValueChange = { tanggal = it },
                        label = { Text("Tanggal") },
                        placeholder = { Text("2026-11-15") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = waktu,
                        onValueChange = { waktu = it },
                        label = { Text(if (isPengumuman) "Waktu (Opsional)" else "Waktu") },
                        placeholder = { Text(if (isPengumuman) "Contoh: Pagi / -" else "19:30 WIB") },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = lokasi,
                    onValueChange = { lokasi = it },
                    label = { Text(if (isPengumuman) "Lokasi (Opsional)" else "Lokasi Acara") },
                    placeholder = { Text(if (isPengumuman) "Lingkungan RT 005" else "Contoh: Rumah Ketua RT 005 / Pos Ronda") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("undangan_lokasi_input")
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = keterangan,
                    onValueChange = { keterangan = it },
                    label = { Text(if (isPengumuman) "Isi Lengkap Pengumuman" else "Agenda / Keterangan Acara") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth().testTag("undangan_keterangan_input")
                )
                Spacer(modifier = Modifier.height(12.dp))
                PhotoPickerSection(
                    label = if (isPengumuman) {
                        "Lampiran Foto Pengumuman (Maks. 1 Foto)"
                    } else {
                        "Lampiran Foto Undangan (Maks. 1 Foto)"
                    },
                    currentPhotoUri = foto,
                    onPhotoSelected = { foto = it },
                    onPhotoRemoved = { foto = "" }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalJudul = if (isPengumuman) {
                        if (rawJudul.startsWith("[Pengumuman]", ignoreCase = true)) rawJudul else "[Pengumuman] $rawJudul"
                    } else {
                        rawJudul.replace(Regex("^\\[(Pengumuman|Undangan)\\]\\s*", RegexOption.IGNORE_CASE), "")
                    }
                    val result = UndanganItem(
                        id = item?.id ?: "",
                        judul = finalJudul,
                        tanggal = tanggal,
                        waktu = if (isPengumuman && waktu.isBlank()) "-" else waktu,
                        lokasi = if (isPengumuman && lokasi.isBlank()) "Lingkungan RT 005" else lokasi,
                        keterangan = keterangan,
                        tipe = if (isPengumuman) "Pengumuman" else "Undangan",
                        foto = foto
                    )
                    onSubmit(result, item != null)
                },
                enabled = rawJudul.isNotBlank(),
                modifier = Modifier.testTag("save_undangan_button")
            ) {
                Text(if (isPengumuman) "Simpan Pengumuman" else "Simpan Undangan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}

@Composable
fun PengurusDialog(
    item: PengurusItem?,
    onDismiss: () -> Unit,
    onSubmit: (PengurusItem, isEdit: Boolean) -> Unit
) {
    var jabatan by remember { mutableStateOf(item?.jabatan ?: "") }
    var nama by remember { mutableStateOf(item?.nama ?: "") }
    var noHp by remember { mutableStateOf(item?.noHp ?: "") }
    var alamat by remember { mutableStateOf(item?.alamat ?: "") }
    var masaBakti by remember { mutableStateOf(item?.masaBakti ?: "2024 - 2027") }
    var foto by remember { mutableStateOf(item?.foto ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (item == null) "Tambah Pengurus RT" else "Edit Data Pengurus", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = jabatan,
                    onValueChange = { jabatan = it },
                    label = { Text("Jabatan") },
                    placeholder = { Text("Contoh: Ketua RT 005 / Sekretaris") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("pengurus_jabatan_input")
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = nama,
                    onValueChange = { nama = it },
                    label = { Text("Nama Lengkap") },
                    placeholder = { Text("Contoh: Bapak Adhitia") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("pengurus_nama_input")
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = noHp,
                    onValueChange = { noHp = it },
                    label = { Text("Nomor HP / WhatsApp") },
                    placeholder = { Text("0812xxxxxxxx") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("pengurus_nohp_input")
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = alamat,
                    onValueChange = { alamat = it },
                    label = { Text("Alamat / No. Rumah") },
                    placeholder = { Text("RT 005 / RW 011 No. ...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = masaBakti,
                    onValueChange = { masaBakti = it },
                    label = { Text("Masa Bakti") },
                    placeholder = { Text("2024 - 2027") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = if (com.example.util.PhotoStorageHelper.isLocalUri(foto)) "" else foto,
                    onValueChange = { foto = it.trim() },
                    label = { Text("URL Foto / Link Google Drive (Opsional)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pengurus_foto_url_input")
                )
                Spacer(modifier = Modifier.height(10.dp))
                PhotoPickerSection(
                    label = "Foto Profil Pengurus (Kamera / Galeri)",
                    currentPhotoUri = foto,
                    onPhotoSelected = { foto = it },
                    onPhotoRemoved = { foto = "" }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val result = PengurusItem(
                        id = item?.id ?: "",
                        jabatan = jabatan,
                        nama = nama,
                        noHp = noHp,
                        alamat = alamat,
                        masaBakti = masaBakti,
                        foto = foto
                    )
                    onSubmit(result, item != null)
                },
                enabled = jabatan.isNotBlank() && nama.isNotBlank(),
                modifier = Modifier.testTag("save_pengurus_button")
            ) {
                Text("Simpan Pengurus")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}

@Composable
fun GaleriDialog(
    item: GaleriItem? = null,
    onDismiss: () -> Unit,
    onSubmit: (GaleriItem) -> Unit
) {
    val isEdit = item != null
    val todayDate = remember {
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }
    val initialPhotos = remember(item) {
        item?.foto?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.distinct()
            ?.take(10)
            ?: emptyList()
    }

    var judul by remember(item) { mutableStateOf(item?.judul ?: "") }
    var tanggal by remember(item) {
        mutableStateOf(
            item?.tanggal?.takeIf { it.isNotBlank() }?.let { IndonesianDateHelper.toIsoDate(it) } ?: todayDate
        )
    }
    var keterangan by remember(item) { mutableStateOf(item?.keterangan ?: "") }
    var photoList by remember(item) { mutableStateOf(initialPhotos) }
    var petugas by remember(item) { mutableStateOf(item?.petugas ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isEdit) "Edit Dokumentasi & Foto Galeri" else "Tambah Foto Kegiatan Galeri",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = judul,
                    onValueChange = { judul = it },
                    label = { Text("Judul Kegiatan") },
                    placeholder = { Text("Contoh: Pembersihan Drainase RT") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("galeri_judul_input")
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = tanggal,
                    onValueChange = { tanggal = it },
                    label = { Text("Tanggal Kegiatan") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = keterangan,
                    onValueChange = { keterangan = it },
                    label = { Text("Keterangan Dokumentasi") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                MultiPhotoPickerSection(
                    label = "Foto Kegiatan (Kamera / Galeri - Maks. 10 Foto)",
                    photoUris = photoList,
                    onPhotosChanged = { photoList = it.take(10) },
                    maxPhotos = 10
                )
                if (isEdit) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Anda dapat menambah atau menghapus foto kegiatan (batas maksimal 10 foto dalam sekali kirim).",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 15.sp
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = petugas,
                    onValueChange = { petugas = it },
                    label = { Text("Petugas / Dokumentator") },
                    placeholder = { Text("Contoh: Seksi Dokumentasi / Pengurus") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val result = GaleriItem(
                        id = item?.id ?: "",
                        judul = judul.trim(),
                        tanggal = tanggal.trim(),
                        kategori = item?.kategori?.ifBlank { "Dokumentasi" } ?: "Dokumentasi",
                        keterangan = keterangan.trim(),
                        foto = photoList.take(10).joinToString(","),
                        petugas = petugas.trim()
                    )
                    onSubmit(result)
                },
                enabled = judul.isNotBlank(),
                modifier = Modifier.testTag("save_galeri_button")
            ) {
                Text(if (isEdit) "Simpan Perubahan" else "Simpan Foto")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}
