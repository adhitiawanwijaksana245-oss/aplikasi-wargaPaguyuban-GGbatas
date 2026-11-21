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
import com.example.data.model.UserRole
import com.example.ui.components.FullScreenPhotoState
import com.example.ui.components.FullScreenPhotoViewerDialog
import com.example.ui.theme.*

@Composable
fun AspirasiScreen(
    aspirasiList: List<AspirasiItem>,
    userRole: UserRole,
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

            // Spreadsheet Status Card
            item {
                val isPengurus = userRole == UserRole.PENGURUS
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFDCFCE7)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isPengurus) Modifier.clickable { onOpenConfigDialog() }
                            else Modifier
                        )
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
                                text = if (isPengurus) {
                                    "Mode Pengurus: Database Aktif. Klik untuk kelola koneksi Apps Script / backup manual."
                                } else {
                                    "Database terhubung. Setiap masukan & aspirasi warga otomatis tersinkron dan tercatat ke database RT."
                                },
                                fontSize = 11.sp,
                                color = Color(0xFF15803D),
                                lineHeight = 15.sp
                            )
                        }
                        if (isPengurus) {
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
                    val isPengurus = (userRole != UserRole.WARGA)
                    val itemIndex = aspirasiList.indexOfFirst { it.id == item.id }
                    val itemOrder = if (itemIndex != -1) itemIndex + 1 else 1

                    // Display Name calculation based on User Role and Privacy toggle
                    val displayName = if (isPengurus) {
                        // Pengurus sees real name and house number!
                        if (item.noRumah.isNotBlank()) "${item.nama} • Rumah: ${item.noRumah}" else item.nama
                    } else {
                        // Warga Mode
                        if (item.sembunyikanNama) {
                            // Sesuai urutan yang tersimpan di sheet
                            "Warga #$itemOrder"
                        } else {
                            if (item.noRumah.isNotBlank()) "${item.nama} (No. ${item.noRumah})" else item.nama
                        }
                    }

                    // Format date cleanly
                    val formattedDate = remember(item.tanggal) {
                        formatCleanDate(item.tanggal)
                    }

                    // Strip any old bracketed [topic] prefix so message is pure and clean
                    val cleanPesan = remember(item.pesan) {
                        item.pesan.replace(Regex("^\\[.*?\\]\\s*"), "")
                    }

                    val (statusBg, statusFg) = when (item.status) {
                        "Selesai" -> Pair(Color(0xFFDCFCE7), Color(0xFF166534))
                        "Diproses" -> Pair(Color(0xFFDBEAFE), Color(0xFF1E40AF))
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

                                // If Pengurus and citizen hid their name, show indicator for pengurus
                                if (isPengurus && item.sembunyikanNama) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = Color(0xFFF1F5F9)
                                    ) {
                                        Text(
                                            text = "Disamarkan dari warga (Warga #$itemOrder)",
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

                            // Issue Photo Attachment (up to 5 photos, fit without cropping & clickable fullscreen)
                            val masalahPhotos = remember(item.fotoMasalah) {
                                item.fotoMasalah.split(",")
                                    .map { it.trim() }
                                    .filter { it.isNotBlank() && com.example.util.PhotoStorageHelper.getDisplayModel(it) != "" }
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

                            // Pengurus Response Box
                            if (item.tanggapanPengurus.isNotBlank()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFFF0FDF4),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
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
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = item.tanggapanPengurus,
                                            fontSize = 12.sp,
                                            color = Color(0xFF14532D),
                                            lineHeight = 16.sp
                                        )

                                        // Work/Fix photos in response (up to 5 photos)
                                        val pengerjaanPhotos = remember(item.fotoPengerjaan) {
                                            item.fotoPengerjaan.split(",")
                                                .map { it.trim() }
                                                .filter { it.isNotBlank() && com.example.util.PhotoStorageHelper.getDisplayModel(it) != "" }
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

                            // Pengurus Action Controls
                            if (isPengurus) {
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
                                        Text(if (item.tanggapanPengurus.isBlank()) "Beri Tanggapan" else "Edit Tanggapan", fontSize = 12.sp)
                                    }

                                    IconButton(onClick = { onDeleteAspirasi(item.id) }, modifier = Modifier.size(32.dp)) {
                                        Icon(Icons.Outlined.Delete, contentDescription = "Hapus", tint = RedExpense, modifier = Modifier.size(16.dp))
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

        // FAB to Write Aspiration
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
            Icon(Icons.Filled.Edit, contentDescription = "Kirim Usulan")
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

private fun formatCleanDate(rawDate: String): String {
    if (rawDate.isBlank()) return ""
    return try {
        if (rawDate.contains("T")) {
            val datePart = rawDate.substringBefore("T")
            val timePart = rawDate.substringAfter("T").take(5)
            "$datePart, $timePart"
        } else {
            rawDate
        }
    } catch (_: Exception) {
        rawDate
    }
}
