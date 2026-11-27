package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.example.data.model.GaleriItem
import com.example.data.model.UserRole
import com.example.ui.components.FullScreenPhotoState
import com.example.ui.components.FullScreenPhotoViewerDialog
import com.example.ui.theme.*
import com.example.util.IndonesianDateHelper

@Composable
fun GaleriScreen(
    galeriList: List<GaleriItem>,
    userRole: UserRole,
    isSyncing: Boolean = false,
    needsScriptUpdate: Boolean = false,
    onRefreshClick: () -> Unit = {},
    onOpenConfigDialog: () -> Unit = {},
    onAddGaleriClick: () -> Unit,
    onEditGaleriClick: (GaleriItem) -> Unit = {},
    onDeleteGaleriClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var fullScreenState by remember { mutableStateOf<FullScreenPhotoState?>(null) }

    Box(modifier = modifier.fillMaxSize().background(SlateBackground)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Dokumentasi & Galeri Warga",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = NavyPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Ketuk foto untuk memperbesar ke layar penuh",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                    IconButton(
                        onClick = onRefreshClick,
                        enabled = !isSyncing,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("galeri_refresh_button")
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = NavyPrimary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Segarkan Galeri",
                                tint = NavyPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }

            if (needsScriptUpdate && userRole != UserRole.WARGA) {
                item {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = AmberBg),
                        border = BorderStroke(1.dp, AmberWarning.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.Warning,
                                    contentDescription = null,
                                    tint = AmberWarning,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Apps Script Spreadsheet Masih Versi Lama",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF92400E)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Apps Script atau izin Google Drive belum aktif (script masih versi lama). Foto baru hanya tersimpan di HP dan tidak masuk Spreadsheet sampai Script v9 di-Deploy & 'otorisasiDanCekSetup' dijalankan.",
                                fontSize = 11.sp,
                                color = Color(0xFF78350F),
                                lineHeight = 15.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = onOpenConfigDialog,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB45309)),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("galeri_update_script_button")
                            ) {
                                Icon(Icons.Outlined.CloudSync, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Perbarui Script & Backup", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            if (galeriList.isEmpty()) {
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
                            Icon(Icons.Outlined.PhotoCamera, contentDescription = null, tint = TextMuted, modifier = Modifier.size(40.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Belum ada foto kegiatan", color = TextSecondary, fontSize = 13.sp)
                        }
                    }
                }
            } else {
                items(galeriList, key = { it.id }) { item ->
                    val photos = remember(item.foto) {
                        item.foto.split(",")
                            .map { it.trim() }
                            .filter { it.isNotBlank() && com.example.util.PhotoStorageHelper.getDisplayModel(it) != "" }
                    }
                    val formattedFullDate = remember(item.tanggal) {
                        IndonesianDateHelper.formatFullDate(item.tanggal)
                    }
                    val formattedShortDate = remember(item.tanggal) {
                        IndonesianDateHelper.formatShortDate(item.tanggal)
                    }

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier.fillMaxWidth().testTag("galeri_item_${item.id}")
                    ) {
                        Column {
                            // Photo Area: Multi-photo or single photo or fallback
                            if (photos.size > 1) {
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    LazyRow(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(190.dp)
                                            .background(Color(0xFFE2E8F0)),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        itemsIndexed(photos) { idx, photoUrl ->
                                            val displayModel = remember(photoUrl) {
                                                com.example.util.PhotoStorageHelper.getDisplayModel(photoUrl)
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxHeight()
                                                    .width(260.dp)
                                                    .background(Color(0xFFF1F5F9))
                                                    .testTag("galeri_photo_${item.id}_$idx")
                                                    .clickable {
                                                        fullScreenState = FullScreenPhotoState(
                                                            title = item.judul,
                                                            tanggal = formattedFullDate,
                                                            photos = photos,
                                                            initialIndex = idx
                                                        )
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                SubcomposeAsyncImage(
                                                    model = displayModel,
                                                    contentDescription = "${item.judul} Foto ${idx + 1}",
                                                    contentScale = ContentScale.Fit,
                                                    modifier = Modifier.fillMaxSize(),
                                                    loading = {
                                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = NavyPrimary)
                                                        }
                                                    },
                                                    error = {
                                                        Box(modifier = Modifier.fillMaxSize().background(Color(0xFFF1F5F9)), contentAlignment = Alignment.Center) {
                                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                                Icon(Icons.Outlined.BrokenImage, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(28.dp))
                                                                Spacer(modifier = Modifier.height(4.dp))
                                                                Text("Gagal memuat foto", color = Color(0xFF64748B), fontSize = 11.sp)
                                                            }
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
                                                        text = "${idx + 1}/${photos.size}",
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
                                            .padding(10.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Icon(Icons.Filled.PhotoLibrary, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "${photos.size} Foto",
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            } else if (photos.size == 1) {
                                val displayModel = remember(photos[0]) {
                                    com.example.util.PhotoStorageHelper.getDisplayModel(photos[0])
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp)
                                        .background(Color(0xFFF1F5F9))
                                        .testTag("galeri_photo_${item.id}_0")
                                        .clickable {
                                            fullScreenState = FullScreenPhotoState(
                                                title = item.judul,
                                                tanggal = formattedFullDate,
                                                photos = photos,
                                                initialIndex = 0
                                            )
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    SubcomposeAsyncImage(
                                        model = displayModel,
                                        contentDescription = item.judul,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize(),
                                        loading = {
                                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp, color = NavyPrimary)
                                            }
                                        },
                                        error = {
                                            Box(modifier = Modifier.fillMaxSize().background(Color(0xFFF1F5F9)), contentAlignment = Alignment.Center) {
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Icon(Icons.Outlined.BrokenImage, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(32.dp))
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text("Gagal memuat foto", color = Color(0xFF64748B), fontSize = 11.sp)
                                                }
                                            }
                                        }
                                    )
                                    Surface(
                                        shape = CircleShape,
                                        color = Color.Black.copy(alpha = 0.55f),
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Fullscreen,
                                            contentDescription = "Perbesar Foto",
                                            tint = Color.White,
                                            modifier = Modifier.padding(6.dp).size(18.dp)
                                        )
                                    }
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(140.dp)
                                        .background(Color(0xFFF1F5F9)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            imageVector = Icons.Outlined.PhotoCamera,
                                            contentDescription = null,
                                            tint = Color(0xFF94A3B8),
                                            modifier = Modifier.size(36.dp)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("Foto Kegiatan", color = Color(0xFF94A3B8), fontSize = 11.sp)
                                    }
                                }
                            }

                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = item.judul,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = formattedShortDate,
                                        fontSize = 11.sp,
                                        color = TextMuted
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = item.keterangan,
                                    fontSize = 12.sp,
                                    color = TextSecondary,
                                    lineHeight = 17.sp
                                )

                                val hasLocalOnlyPhoto = remember(photos) {
                                    photos.any { com.example.util.PhotoStorageHelper.isLocalUri(it) }
                                }
                                if (hasLocalOnlyPhoto && userRole != UserRole.WARGA) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = AmberBg,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onOpenConfigDialog() }
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.CloudOff,
                                                contentDescription = null,
                                                tint = AmberWarning,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Foto masih di HP ini (belum masuk Spreadsheet). Ketuk untuk periksa Script v9 & klik Backup Semua.",
                                                fontSize = 10.sp,
                                                lineHeight = 14.sp,
                                                color = Color(0xFF92400E),
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Filled.AccountCircle, contentDescription = null, tint = TextMuted, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Petugas: ${item.petugas.ifBlank { "Seksi Dokumentasi" }}",
                                            fontSize = 11.sp,
                                            color = TextMuted
                                        )
                                    }

                                    if (userRole != UserRole.WARGA) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(
                                                onClick = { onEditGaleriClick(item) },
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .testTag("edit_galeri_${item.id}")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.Edit,
                                                    contentDescription = "Edit Galeri",
                                                    tint = NavyPrimary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            if (userRole == UserRole.ADMIN_UTAMA) {
                                                IconButton(
                                                    onClick = { onDeleteGaleriClick(item.id) },
                                                    modifier = Modifier
                                                        .size(48.dp)
                                                        .testTag("delete_galeri_${item.id}")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Outlined.Delete,
                                                        contentDescription = "Hapus",
                                                        tint = RedExpense,
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
                }
            }

            item {
                Spacer(modifier = Modifier.height(60.dp))
            }
        }

        // FAB to Add Gallery Photo (Hidden in Warga mode)
        if (userRole != UserRole.WARGA) {
            FloatingActionButton(
                onClick = onAddGaleriClick,
                containerColor = NavyPrimary,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp)
                    .testTag("add_galeri_fab")
            ) {
                Icon(Icons.Filled.AddAPhoto, contentDescription = "Tambah Foto")
            }
        }
    }

    // Fullscreen Lightbox Viewer dengan tombol X di pojok kanan atas
    fullScreenState?.let { state ->
        FullScreenPhotoViewerDialog(
            state = state,
            onDismiss = { fullScreenState = null }
        )
    }
}
