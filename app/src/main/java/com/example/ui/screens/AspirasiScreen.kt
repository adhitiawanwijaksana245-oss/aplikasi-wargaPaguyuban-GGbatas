package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.example.R
import com.example.data.model.AspirasiItem
import com.example.data.model.PengurusAccount
import com.example.data.model.UserRole
import com.example.data.repository.WargaRepository
import com.example.ui.components.FullScreenPhotoState
import com.example.ui.components.FullScreenPhotoViewerDialog
import com.example.ui.theme.*
import com.example.util.IndonesianDateHelper

@Composable
fun AspirasiScreen(
    aspirasiList: List<AspirasiItem>,
    userRole: UserRole,
    pengurusAccounts: List<PengurusAccount> = emptyList(),
    sheetUrl: String = "",
    onOpenConfigDialog: () -> Unit = {},
    onOpenSubmitAspirasi: () -> Unit,
    onRespondAspirasi: (AspirasiItem) -> Unit,
    onDeleteAspirasi: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedFilter by remember { mutableStateOf("Semua") } // "Semua", "Menunggu", "Diproses", "Selesai"
    var fullScreenState by remember { mutableStateOf<FullScreenPhotoState?>(null) }

    val filteredList = remember(aspirasiList, selectedFilter) {
        if (selectedFilter == "Semua") aspirasiList else aspirasiList.filter { it.status == selectedFilter }
    }

    Box(modifier = modifier.fillMaxSize().background(SlateBackground)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            item {
                Column {
                    Text(
                        text = "Aspirasi, Saran & Kritik Warga",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = NavyPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Wadah penyampaian aspirasi warga RT 005 secara transparan",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }

            // Spreadsheet Status Card (Hanya tampil saat mode Admin)
            if (userRole == UserRole.ADMIN_UTAMA) {
                item {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFDCFCE7)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenConfigDialog() }
                            .testTag("spreadsheet_status_card")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF16A34A),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "🟢 Terkoneksi ke Spreadsheet: Aplikasi_Warga",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = Color(0xFF14532D)
                                )
                                Text(
                                    text = "Mode Admin: Database Aktif. Klik untuk kelola koneksi Apps Script / backup manual.",
                                    fontSize = 11.sp,
                                    color = Color(0xFF15803D),
                                    lineHeight = 15.sp
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                contentDescription = null,
                                tint = Color(0xFF16A34A),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            // Big Action Button to Submit (Accessible to ALL warga!)
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = NavyPrimary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("submit_aspirasi_banner_button")
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Punya Masukan atau Kritik?",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Tersimpan di Room Database & tersinkron ke Cloud Firestore realtime.",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 11.sp
                            )
                        }
                        Button(
                            onClick = onOpenSubmitAspirasi,
                            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Tulis Usulan", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Filter status chips
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Semua", "Menunggu", "Diproses", "Selesai").forEach { filter ->
                        FilterChip(
                            selected = selectedFilter == filter,
                            onClick = { selectedFilter = filter },
                            label = { Text(filter, fontSize = 11.sp) }
                        )
                    }
                }
            }

            // List of submissions
            if (filteredList.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(32.dp).fillMaxWidth()
                        ) {
                            Icon(Icons.Outlined.Forum, contentDescription = null, tint = TextMuted, modifier = Modifier.size(40.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Belum ada aspirasi warga pada kategori ini", color = TextSecondary, fontSize = 13.sp)
                        }
                    }
                }
            } else {
                items(filteredList, key = { it.id }) { item ->
                    val canRespond = (userRole != UserRole.WARGA)
                    val isAdmin = (userRole == UserRole.ADMIN_UTAMA)
                    val itemIndex = aspirasiList.indexOfFirst { it.id == item.id }
                    val itemOrder = if (itemIndex != -1) itemIndex + 1 else 1

                    // Privacy rule: Only Admin sees real sender name when sembunyikanNama == true.
                    // Both Warga & Pengurus ONLY see alias "Warga #$itemOrder".
                    val displayName = when {
                        isAdmin -> {
                            if (item.noRumah.isNotBlank()) "${item.nama} • Rumah: ${item.noRumah}" else item.nama
                        }
                        item.sembunyikanNama -> "Warga #$itemOrder"
                        else -> {
                            if (item.noRumah.isNotBlank()) "${item.nama} (No. ${item.noRumah})" else item.nama
                        }
                    }

                    // Format date cleanly in WIB Indonesian format
                    val formattedDate = remember(item.tanggal) {
                        IndonesianDateHelper.formatFullDate(item.tanggal)
                    }

                    // Strip any old bracketed [topic] prefix so message is pure and clean
                    val cleanPesan = remember(item.pesan) {
                        item.pesan.replace(Regex("^\\[.*?\\]\\s*"), "")
                    }

                    val (statusBg, statusFg) = when {
                        item.status.trim().equals("Selesai", ignoreCase = true) -> Pair(Color(0xFFDCFCE7), Color(0xFF166534))
                        item.status.trim().equals("Diproses", ignoreCase = true) -> Pair(Color(0xFFDBEAFE), Color(0xFF1E40AF))
                        else -> Pair(Color(0xFFFEF3C7), Color(0xFF92400E))
                    }

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier.fillMaxWidth().testTag("aspirasi_item_${item.id}")
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Top Row: Category pill on the left, Date on the right
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (item.kategori == "Kritik") Color(0xFFFEE2E2) else Color(0xFFE0F2FE)
                                ) {
                                    Text(
                                        text = item.kategori,
                                        color = if (item.kategori == "Kritik") Color(0xFFDC2626) else Color(0xFF0284C7),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }

                                Text(
                                    text = formattedDate,
                                    fontSize = 11.sp,
                                    color = TextMuted
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Horizontal Status Badge placed directly UNDER "Masukan/Saran" (as requested!)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = statusBg
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(statusFg)
                                        )
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text(
                                            text = item.status,
                                            color = statusFg,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                // Only Admin sees identity-hidden tag with alias; Pengurus sees only generic notice
                                if (canRespond && item.sembunyikanNama) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = Color(0xFFF1F5F9)
                                    ) {
                                        Text(
                                            text = if (isAdmin) {
                                                "Disamarkan (Warga #$itemOrder)"
                                            } else {
                                                "Identitas Disamarkan"
                                            },
                                            fontSize = 10.sp,
                                            color = Color(0xFF64748B),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Author Name
                            Text(
                                text = displayName,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            // Message detail
                            Text(
                                text = cleanPesan,
                                fontSize = 13.sp,
                                color = TextSecondary,
                                lineHeight = 18.sp
                            )

                            // Issue Photo Attachment (Kolom F-J: Foto_Masalah_1..5, max 5 photos, fit without cropping & clickable fullscreen)
                            val masalahPhotos = remember(item.fotoMasalah) {
                                item.fotoMasalah.split(",")
                                    .map { it.trim() }
                                    .filter { it.isNotBlank() && com.example.util.PhotoStorageHelper.getDisplayModel(it) != "" }
                                    .distinct()
                                    .take(5)
                            }
                            if (masalahPhotos.size > 1) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    LazyRow(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(180.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFFE2E8F0)),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        itemsIndexed(masalahPhotos) { idx, photoUrl ->
                                            val modelMasalah = remember(photoUrl) {
                                                com.example.util.PhotoStorageHelper.getDisplayModel(photoUrl)
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxHeight()
                                                    .width(240.dp)
                                                    .background(Color(0xFFF1F5F9))
                                                    .testTag("aspirasi_foto_masalah_${item.id}_$idx")
                                                    .clickable {
                                                        fullScreenState = FullScreenPhotoState(
                                                            title = "Foto Masalah • $displayName",
                                                            tanggal = formattedDate,
                                                            photos = masalahPhotos,
                                                            initialIndex = idx
                                                        )
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                SubcomposeAsyncImage(
                                                    model = modelMasalah,
                                                    contentDescription = "Foto Masalah ${idx + 1}",
                                                    contentScale = ContentScale.Fit,
                                                    modifier = Modifier.fillMaxSize(),
                                                    loading = {
                                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = NavyPrimary)
                                                        }
                                                    },
                                                    error = {
                                                        Box(modifier = Modifier.fillMaxSize().background(Color(0xFFF1F5F9)), contentAlignment = Alignment.Center) {
                                                            Text("Gagal memuat foto masalah", color = Color(0xFF64748B), fontSize = 11.sp)
                                                        }
                                                    }
                                                )
                                                Surface(
                                                    shape = CircleShape,
                                                    color = Color.Black.copy(alpha = 0.55f),
                                                    modifier = Modifier
                                                        .align(Alignment.TopEnd)
                                                        .padding(8.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Fullscreen,
                                                        contentDescription = "Perbesar Foto",
                                                        tint = Color.White,
                                                        modifier = Modifier.padding(5.dp).size(16.dp)
                                                    )
                                                }
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = Color.Black.copy(alpha = 0.65f),
                                                    modifier = Modifier
                                                        .align(Alignment.BottomEnd)
                                                        .padding(8.dp)
                                                ) {
                                                    Text(
                                                        text = "${idx + 1}/${masalahPhotos.size}",
                                                        color = Color.White,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color.Black.copy(alpha = 0.7f),
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .padding(8.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Icon(Icons.Filled.PhotoLibrary, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "${masalahPhotos.size} Foto",
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            } else if (masalahPhotos.size == 1) {
                                val modelMasalah = remember(masalahPhotos[0]) {
                                    com.example.util.PhotoStorageHelper.getDisplayModel(masalahPhotos[0])
                                }
                                if (modelMasalah != "") {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(180.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFFF1F5F9))
                                            .testTag("aspirasi_foto_masalah_${item.id}")
                                            .clickable {
                                                fullScreenState = FullScreenPhotoState(
                                                    title = "Foto Masalah • $displayName",
                                                    tanggal = formattedDate,
                                                    photos = masalahPhotos,
                                                    initialIndex = 0
                                                )
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        SubcomposeAsyncImage(
                                            model = modelMasalah,
                                            contentDescription = "Foto Masalah / Usulan",
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier.fillMaxSize(),
                                            loading = {
                                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = NavyPrimary)
                                                }
                                            },
                                            error = {
                                                Box(modifier = Modifier.fillMaxSize().background(Color(0xFFF1F5F9)), contentAlignment = Alignment.Center) {
                                                    Text("Gagal memuat foto masalah", color = Color(0xFF64748B), fontSize = 11.sp)
                                                }
                                            }
                                        )
                                        Surface(
                                            shape = CircleShape,
                                            color = Color.Black.copy(alpha = 0.55f),
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Fullscreen,
                                                contentDescription = "Perbesar Foto",
                                                tint = Color.White,
                                                modifier = Modifier.padding(5.dp).size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // Pengurus / Admin Response Box (Kolom L-R: Nama_Pengurus, Tanggapan_pengurus, Foto_pengerjaan_1..5)
                            val cleanTanggapanText = remember(item.tanggapanPengurus) {
                                com.example.util.AspirasiReplyCodec.stripMarker(item.tanggapanPengurus)
                            }
                            if (cleanTanggapanText.isNotBlank() || item.fotoPengerjaan.isNotBlank() || item.penanggapNama.isNotBlank()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                val formattedReplyTime = remember(item.waktuTanggapan) {
                                    IndonesianDateHelper.formatDateTimeWib(item.waktuTanggapan)
                                }
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFFF0FDF4),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("aspirasi_response_box_${item.id}")
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Filled.CheckCircle,
                                                    contentDescription = null,
                                                    tint = Color(0xFF16A34A),
                                                    modifier = Modifier.size(15.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "Tanggapan Pengurus RT:",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF166534)
                                                )
                                            }
                                            if (item.penanggapPeran.isNotBlank()) {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = Color(0xFFDCFCE7)
                                                ) {
                                                    Text(
                                                        text = item.penanggapPeran,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF15803D),
                                                        modifier = Modifier
                                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                                            .testTag("aspirasi_peran_penanggap_${item.id}")
                                                    )
                                                }
                                            }
                                        }
                                        if (cleanTanggapanText.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = cleanTanggapanText,
                                                fontSize = 12.sp,
                                                color = Color(0xFF14532D),
                                                lineHeight = 16.sp,
                                                modifier = Modifier.testTag("aspirasi_tanggapan_text_${item.id}")
                                            )
                                        }

                                        val resolvedRealResponder = remember(
                                            item.penanggapUsername,
                                            item.penanggapNamaAsli,
                                            pengurusAccounts
                                        ) {
                                            WargaRepository.resolveRealResponderName(item, pengurusAccounts)
                                        }

                                        val responderRoleSuffix = item.penanggapPeran.trim()
                                            .takeIf { it.isNotBlank() }
                                            ?.let { " ($it)" }
                                            .orEmpty()
                                        val responderLine = when {
                                            item.penanggapNama.isNotBlank() -> "Oleh: ${item.penanggapNama}$responderRoleSuffix"
                                            item.penanggapPeran.isNotBlank() -> "Oleh: ${item.penanggapPeran} RT"
                                            else -> ""
                                        }
                                        if (responderLine.isNotBlank() || formattedReplyTime.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(6.dp))
                                            if (responderLine.isNotBlank()) {
                                                Text(
                                                    text = responderLine,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = Color(0xFF15803D),
                                                    modifier = Modifier.testTag("aspirasi_penanggap_nama_${item.id}")
                                                )
                                            }
                                            if (formattedReplyTime.isNotBlank()) {
                                                Text(
                                                    text = "Dijawab: $formattedReplyTime",
                                                    fontSize = 11.sp,
                                                    color = Color(0xFF15803D),
                                                    modifier = Modifier.testTag("aspirasi_waktu_tanggapan_${item.id}")
                                                )
                                            }
                                        }

                                        if (isAdmin && (item.penanggapNama.isNotBlank() || resolvedRealResponder.isNotBlank() || item.penanggapUsername.isNotBlank())) {
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = Color(0xFFDCFCE7),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .testTag("admin_responder_audit_${item.id}")
                                            ) {
                                                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                                                    Text(
                                                        text = "Nama Tampil / Samaran: ${item.penanggapNama.ifBlank { "-" }}",
                                                        fontSize = 10.5.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = Color(0xFF14532D)
                                                    )
                                                    val realLabel = resolvedRealResponder.ifBlank { "-" }
                                                    val usernameSuffix = if (item.penanggapUsername.isNotBlank()) {
                                                        " (@${item.penanggapUsername})"
                                                    } else ""
                                                    Text(
                                                        text = "Nama Asli Akun: $realLabel$usernameSuffix",
                                                        fontSize = 10.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF166534)
                                                    )
                                                }
                                            }
                                        }

                                        // Work/Fix photos in response (Kolom N-R: Foto_pengerjaan_1..5, max 5 photos)
                                        val pengerjaanPhotos = remember(item.fotoPengerjaan) {
                                            item.fotoPengerjaan.split(",")
                                                .map { it.trim() }
                                                .filter { it.isNotBlank() && com.example.util.PhotoStorageHelper.getDisplayModel(it) != "" }
                                                .distinct()
                                                .take(5)
                                        }
                                        if (pengerjaanPhotos.size > 1) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Box(modifier = Modifier.fillMaxWidth()) {
                                                LazyRow(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(180.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(Color(0xFFE2E8F0).copy(alpha = 0.45f)),
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    itemsIndexed(pengerjaanPhotos) { idx, photoUrl ->
                                                        val modelSolusi = remember(photoUrl) {
                                                            com.example.util.PhotoStorageHelper.getDisplayModel(photoUrl)
                                                        }
                                                        Box(
                                                            modifier = Modifier
                                                                .fillMaxHeight()
                                                                .width(240.dp)
                                                                .background(Color(0xFFF1F5F9))
                                                                .testTag("aspirasi_foto_pengerjaan_${item.id}_$idx")
                                                                .clickable {
                                                                    fullScreenState = FullScreenPhotoState(
                                                                        title = "Bukti Pengerjaan Pengurus RT",
                                                                        tanggal = formattedDate,
                                                                        photos = pengerjaanPhotos,
                                                                        initialIndex = idx
                                                                    )
                                                                },
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            SubcomposeAsyncImage(
                                                                model = modelSolusi,
                                                                contentDescription = "Foto Pengerjaan ${idx + 1}",
                                                                contentScale = ContentScale.Fit,
                                                                modifier = Modifier.fillMaxSize(),
                                                                loading = {
                                                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = NavyPrimary)
                                                                    }
                                                                },
                                                                error = {
                                                                    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFF1F5F9)), contentAlignment = Alignment.Center) {
                                                                        Text("Gagal memuat foto bukti", color = Color(0xFF64748B), fontSize = 11.sp)
                                                                    }
                                                                }
                                                            )
                                                            Surface(
                                                                shape = CircleShape,
                                                                color = Color.Black.copy(alpha = 0.55f),
                                                                modifier = Modifier
                                                                    .align(Alignment.TopEnd)
                                                                    .padding(8.dp)
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Filled.Fullscreen,
                                                                    contentDescription = "Perbesar Foto",
                                                                    tint = Color.White,
                                                                    modifier = Modifier.padding(5.dp).size(16.dp)
                                                                )
                                                            }
                                                            Surface(
                                                                shape = RoundedCornerShape(4.dp),
                                                                color = Color.Black.copy(alpha = 0.65f),
                                                                modifier = Modifier
                                                                    .align(Alignment.BottomEnd)
                                                                    .padding(8.dp)
                                                            ) {
                                                                Text(
                                                                    text = "${idx + 1}/${pengerjaanPhotos.size}",
                                                                    color = Color.White,
                                                                    fontSize = 11.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = Color.Black.copy(alpha = 0.7f),
                                                    modifier = Modifier
                                                        .align(Alignment.TopStart)
                                                        .padding(8.dp)
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                    ) {
                                                        Icon(Icons.Filled.PhotoLibrary, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text(
                                                            text = "${pengerjaanPhotos.size} Foto",
                                                            color = Color.White,
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                            }
                                        } else if (pengerjaanPhotos.size == 1) {
                                            val modelSolusi = remember(pengerjaanPhotos[0]) {
                                                com.example.util.PhotoStorageHelper.getDisplayModel(pengerjaanPhotos[0])
                                            }
                                            if (modelSolusi != "") {
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(180.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(Color(0xFFE2E8F0).copy(alpha = 0.45f))
                                                        .testTag("aspirasi_foto_pengerjaan_${item.id}")
                                                        .clickable {
                                                            fullScreenState = FullScreenPhotoState(
                                                                title = "Bukti Pengerjaan Pengurus RT",
                                                                tanggal = formattedDate,
                                                                photos = pengerjaanPhotos,
                                                                initialIndex = 0
                                                            )
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    SubcomposeAsyncImage(
                                                        model = modelSolusi,
                                                        contentDescription = "Foto Pengerjaan / Bukti Selesai",
                                                        contentScale = ContentScale.Fit,
                                                        modifier = Modifier.fillMaxSize(),
                                                        loading = {
                                                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = NavyPrimary)
                                                            }
                                                        },
                                                        error = {
                                                            Box(modifier = Modifier.fillMaxSize().background(Color(0xFFF1F5F9)), contentAlignment = Alignment.Center) {
                                                                Text("Gagal memuat foto bukti", color = Color(0xFF64748B), fontSize = 11.sp)
                                                            }
                                                        }
                                                    )
                                                    Surface(
                                                        shape = CircleShape,
                                                        color = Color.Black.copy(alpha = 0.55f),
                                                        modifier = Modifier
                                                            .align(Alignment.TopEnd)
                                                            .padding(8.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Filled.Fullscreen,
                                                            contentDescription = "Perbesar Foto",
                                                            tint = Color.White,
                                                            modifier = Modifier.padding(5.dp).size(16.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Pengurus & Admin Action Controls
                            if (canRespond) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = { onRespondAspirasi(item) },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Filled.Reply, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            if (item.tanggapanPengurus.isBlank() && item.fotoPengerjaan.isBlank()) "Beri Tanggapan" else "Edit Tanggapan",
                                            fontSize = 12.sp
                                        )
                                    }

                                    if (isAdmin) {
                                        IconButton(onClick = { onDeleteAspirasi(item.id) }, modifier = Modifier.size(32.dp)) {
                                            Icon(Icons.Outlined.Delete, contentDescription = "Hapus", tint = RedExpense, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(60.dp))
            }
        }

        // FAB to Add Aspiration
        FloatingActionButton(
            onClick = onOpenSubmitAspirasi,
            containerColor = AccentBlue,
            contentColor = Color.White,
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("add_aspirasi_fab")
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Kirim Usulan")
        }
    }

    // Fullscreen photo viewer dengan tombol tanda X untuk menutup dan kembali
    fullScreenState?.let { state ->
        FullScreenPhotoViewerDialog(
            state = state,
            onDismiss = { fullScreenState = null }
        )
    }
}
