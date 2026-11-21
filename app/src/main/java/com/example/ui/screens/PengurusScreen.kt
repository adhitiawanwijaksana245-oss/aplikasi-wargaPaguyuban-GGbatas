package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.SubcomposeAsyncImage
import com.example.data.model.PengurusItem
import com.example.data.model.UserRole
import com.example.ui.theme.*
import com.example.util.PhotoStorageHelper

@Composable
fun PengurusScreen(
    pengurusList: List<PengurusItem>,
    userRole: UserRole,
    onAddPengurusClick: () -> Unit,
    onEditPengurusClick: (PengurusItem) -> Unit,
    onDeletePengurusClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var previewPhotoModel by remember { mutableStateOf<Any?>(null) }
    var previewPhotoName by remember { mutableStateOf("") }

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
                            text = "Struktur & Pengurus RT 005",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = NavyPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Masa Bakti Paguyuban RT 005 / RW 011 Mulyaharja",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (userRole == UserRole.WARGA) SkySoft else Color(0xFFDCFCE7)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Icon(
                                imageVector = if (userRole == UserRole.WARGA) Icons.Outlined.Lock else Icons.Filled.VerifiedUser,
                                contentDescription = null,
                                tint = if (userRole == UserRole.WARGA) NavyPrimary else Color(0xFF166534),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (userRole == UserRole.WARGA) "Edit Terkunci" else "Akses Terbuka",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (userRole == UserRole.WARGA) NavyPrimary else Color(0xFF166534)
                            )
                        }
                    }
                }
            }

            if (pengurusList.isEmpty()) {
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
                            Icon(Icons.Outlined.People, contentDescription = null, tint = TextMuted, modifier = Modifier.size(40.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Belum ada data pengurus", color = TextSecondary, fontSize = 13.sp)
                        }
                    }
                }
            } else {
                items(pengurusList, key = { it.id }) { item ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier.fillMaxWidth().testTag("pengurus_item_${item.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Avatar circle with Google Drive / URL photo or first letter fallback
                            val fotoModel = remember(item.foto) {
                                PhotoStorageHelper.getDisplayModel(item.foto)
                            }
                            val hasPhoto = fotoModel != ""
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFE0F2FE))
                                    .then(
                                        if (hasPhoto) {
                                            Modifier.clickable {
                                                previewPhotoModel = fotoModel
                                                previewPhotoName = "${item.nama} (${item.jabatan})"
                                            }
                                        } else {
                                            Modifier
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                val fallbackInitial = item.nama.trim().take(1).uppercase().ifBlank { "P" }
                                if (hasPhoto) {
                                    SubcomposeAsyncImage(
                                        model = fotoModel,
                                        contentDescription = "Foto Profil ${item.nama}",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                        loading = {
                                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(20.dp),
                                                    strokeWidth = 2.dp,
                                                    color = AccentBlue
                                                )
                                            }
                                        },
                                        error = {
                                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = fallbackInitial,
                                                    color = Color(0xFF0369A1),
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 20.sp
                                                )
                                            }
                                        }
                                    )
                                } else {
                                    Text(
                                        text = fallbackInitial,
                                        color = Color(0xFF0369A1),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 20.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFFEFF6FF)
                                ) {
                                    Text(
                                        text = item.jabatan,
                                        color = AccentBlue,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = item.nama,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Home, contentDescription = null, tint = TextMuted, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(item.alamat, fontSize = 12.sp, color = TextSecondary)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.DateRange, contentDescription = null, tint = TextMuted, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Masa Bakti: ${item.masaBakti}", fontSize = 11.sp, color = TextMuted)
                                }
                            }

                            // Contact & Authority-Protected Edit Actions
                            Column(horizontalAlignment = Alignment.End) {
                                if (item.noHp.isNotBlank()) {
                                    FilledIconButton(
                                        onClick = {
                                            val cleanPhone = item.noHp.replace(Regex("[^0-9]"), "")
                                            val formattedPhone = if (cleanPhone.startsWith("0")) "62" + cleanPhone.substring(1) else cleanPhone
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$formattedPhone"))
                                            context.startActivity(intent)
                                        },
                                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFF22C55E)),
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(Icons.Filled.Call, contentDescription = "Hubungi WhatsApp", tint = Color.White, modifier = Modifier.size(18.dp))
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // Tombol Edit selalu tersedia & diproteksi password otoritas 005011 saat Mode Warga
                                    IconButton(
                                        onClick = { onEditPengurusClick(item) },
                                        modifier = Modifier
                                            .size(48.dp)
                                            .testTag("edit_pengurus_${item.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Edit,
                                            contentDescription = "Edit Pengurus",
                                            tint = AccentBlue,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    if (userRole != UserRole.WARGA) {
                                        IconButton(
                                            onClick = { onDeletePengurusClick(item.id) },
                                            modifier = Modifier
                                                .size(48.dp)
                                                .testTag("delete_pengurus_${item.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Delete,
                                                contentDescription = "Hapus Pengurus",
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

            item {
                Spacer(modifier = Modifier.height(60.dp))
            }
        }

        // FAB to Add Officer (Auth-protected)
        FloatingActionButton(
            onClick = onAddPengurusClick,
            containerColor = NavyPrimary,
            contentColor = Color.White,
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("add_pengurus_fab")
        ) {
            Icon(Icons.Filled.PersonAdd, contentDescription = "Tambah Pengurus")
        }

        if (previewPhotoModel != null) {
            Dialog(
                onDismissRequest = { previewPhotoModel = null },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.92f))
                        .clickable { previewPhotoModel = null }
                        .padding(20.dp)
                ) {
                    SubcomposeAsyncImage(
                        model = previewPhotoModel,
                        contentDescription = previewPhotoName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .align(Alignment.Center)
                    )
                    IconButton(
                        onClick = { previewPhotoModel = null },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .statusBarsPadding()
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.6f))
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Tutup pratinjau foto",
                            tint = Color.White
                        )
                    }
                    if (previewPhotoName.isNotBlank()) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .navigationBarsPadding()
                                .padding(bottom = 12.dp)
                        ) {
                            Text(
                                text = previewPhotoName,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
