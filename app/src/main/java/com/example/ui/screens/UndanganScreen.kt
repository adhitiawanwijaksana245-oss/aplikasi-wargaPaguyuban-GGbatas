package com.example.ui.screens

import android.content.Intent
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
import coil.compose.SubcomposeAsyncImage
import com.example.data.model.UndanganItem
import com.example.data.model.UserRole
import com.example.ui.components.ActiveRemindersSection
import com.example.ui.components.FullScreenPhotoState
import com.example.ui.components.FullScreenPhotoViewerDialog
import com.example.ui.theme.*
import com.example.util.PhotoStorageHelper

@Composable
fun UndanganScreen(
    undanganList: List<UndanganItem>,
    userRole: UserRole,
    onAddUndanganClick: () -> Unit,
    onEditUndanganClick: (UndanganItem) -> Unit,
    onDeleteUndanganClick: (String) -> Unit,
    onOpenReminderMode: (UndanganItem?) -> Unit = {},
    onCancelReminder: (UndanganItem) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var filterType by remember { mutableStateOf("Semua") } // "Semua", "Pengumuman", "Undangan"
    var fullScreenPhotoState by remember { mutableStateOf<FullScreenPhotoState?>(null) }

    val filteredList = remember(undanganList, filterType) {
        when (filterType) {
            "Pengumuman" -> undanganList.filter { it.isPengumuman }
            "Undangan" -> undanganList.filter { !it.isPengumuman }
            else -> undanganList
        }
    }

    Box(modifier = modifier.fillMaxSize().background(SlateBackground)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Title: Diganti menjadi "Pengumuman & Undangan"
            item {
                Column {
                    Text(
                        text = "Pengumuman & Undangan",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = NavyPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Informasi resmi, pengumuman warga, dan agenda kegiatan RT 005",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }

            // Pengurus-only Citizen Reminder Mode Control Card
            if (userRole != UserRole.WARGA) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = NavyPrimary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color.White.copy(alpha = 0.16f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.AlarmAdd,
                                        contentDescription = null,
                                        tint = Color(0xFFFBBF24),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Mode Pengingat Warga",
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Pilih acara rapat/gotong royong & menit pengaktifan ke HP warga.",
                                        color = Color.White.copy(alpha = 0.85f),
                                        fontSize = 11.sp,
                                        lineHeight = 15.sp
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Button(
                                onClick = { onOpenReminderMode(null) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFF59E0B),
                                    contentColor = Color.Black
                                ),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("open_reminder_mode_button")
                            ) {
                                Text("Atur", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Active Reminders visible to both Warga and Pengurus
            item {
                ActiveRemindersSection(
                    undanganList = undanganList,
                    userRole = userRole,
                    onConfigureReminder = { onOpenReminderMode(it) },
                    onCancelReminder = onCancelReminder
                )
            }

            // Filter Chips: Semua, Pengumuman, Undangan
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Semua", "Pengumuman", "Undangan").forEach { type ->
                        FilterChip(
                            selected = filterType == type,
                            onClick = { filterType = type },
                            leadingIcon = {
                                if (type == "Pengumuman") {
                                    Icon(Icons.Filled.Campaign, contentDescription = null, modifier = Modifier.size(15.dp))
                                } else if (type == "Undangan") {
                                    Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(15.dp))
                                }
                            },
                            label = {
                                Text(
                                    text = type,
                                    fontSize = 12.sp,
                                    fontWeight = if (filterType == type) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = if (type == "Pengumuman") Color(0xFFF59E0B) else NavyPrimary,
                                selectedLabelColor = if (type == "Pengumuman") Color.Black else Color.White
                            )
                        )
                    }
                }
            }

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
                            Icon(Icons.Outlined.Campaign, contentDescription = null, tint = TextMuted, modifier = Modifier.size(40.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (filterType == "Pengumuman") "Belum ada pengumuman pengurus" else "Belum ada data pengumuman / undangan",
                                color = TextSecondary,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            } else {
                items(filteredList) { item ->
                    val isPengumuman = item.isPengumuman

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier.fillMaxWidth().testTag("undangan_item_${item.id}")
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Header Row: Type Badge + Date + Share
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Type Badge (Pengumuman vs Undangan)
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (isPengumuman) Color(0xFFFEF3C7) else SkySoft
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isPengumuman) Icons.Filled.Campaign else Icons.Filled.CalendarToday,
                                                contentDescription = null,
                                                tint = if (isPengumuman) Color(0xFFD97706) else AccentBlue,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = if (isPengumuman) "Pengumuman" else "Undangan",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isPengumuman) Color(0xFF92400E) else NavyPrimary
                                            )
                                        }
                                    }

                                    // Date
                                    Text(
                                        text = if (isPengumuman || item.waktu == "-" || item.waktu.isBlank()) {
                                            item.tanggal
                                        } else {
                                            "${item.tanggal} • ${item.waktu}"
                                        },
                                        fontSize = 11.sp,
                                        color = TextSecondary
                                    )
                                }

                                // Share to WA Button
                                TextButton(
                                    onClick = {
                                        val shareText = if (isPengumuman) {
                                            """
📢 *PENGUMUMAN RESMI PAGUYUBAN WARGA RT 005 / RW 011 MULYAHARJA*
Judul: *${item.cleanJudul}*
📅 Tanggal: ${item.tanggal}

Isi Pengumuman:
${item.cleanKeterangan}

_Salam Pengurus RT 005 Mulyaharja._
                                            """.trimIndent()
                                        } else {
                                            """
🗓️ *UNDANGAN KEGIATAN WARGA RT 005 / RW 011 MULYAHARJA*
Acara: *${item.cleanJudul}*
📅 Tanggal: ${item.tanggal}
⏰ Waktu: ${item.waktu}
📍 Lokasi: ${item.lokasi}

Keterangan:
${item.cleanKeterangan}

_Mohon kehadirannya tepat waktu. Salam Warga RT 005._
                                            """.trimIndent()
                                        }
                                        val sendIntent = Intent().apply {
                                            action = Intent.ACTION_SEND
                                            putExtra(Intent.EXTRA_TEXT, shareText)
                                            type = "text/plain"
                                        }
                                        context.startActivity(Intent.createChooser(sendIntent, "Bagikan ke WhatsApp Warga"))
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(14.dp), tint = AccentBlue)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Bagikan", fontSize = 11.sp, color = AccentBlue)
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = item.cleanJudul,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )

                            if (!isPengumuman && item.lokasi.isNotBlank() && item.lokasi != "-") {
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.LocationOn, contentDescription = null, tint = RedExpense, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = item.lokasi,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = TextSecondary
                                    )
                                }
                            }

                            val itemFotoModel = remember(item.foto) {
                                PhotoStorageHelper.getDisplayModel(item.foto)
                            }
                            if (itemFotoModel != "") {
                                Spacer(modifier = Modifier.height(10.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(190.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFFF1F5F9))
                                        .testTag("undangan_foto_${item.id}")
                                        .clickable {
                                            fullScreenPhotoState = FullScreenPhotoState(
                                                title = item.cleanJudul,
                                                tanggal = if (isPengumuman || item.waktu == "-" || item.waktu.isBlank()) item.tanggal else "${item.tanggal} • ${item.waktu}",
                                                photos = listOf(item.foto),
                                                initialIndex = 0
                                            )
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    SubcomposeAsyncImage(
                                        model = itemFotoModel,
                                        contentDescription = "Foto ${item.cleanJudul}",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize(),
                                        loading = {
                                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(22.dp),
                                                    strokeWidth = 2.dp,
                                                    color = NavyPrimary
                                                )
                                            }
                                        },
                                        error = {
                                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                Text("Gagal memuat foto", color = TextSecondary, fontSize = 11.sp)
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

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = item.cleanKeterangan,
                                fontSize = 13.sp,
                                color = TextSecondary,
                                lineHeight = 18.sp
                            )

                            if (item.isReminderActive) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFFDCFCE7)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.NotificationsActive,
                                            contentDescription = null,
                                            tint = GreenIncome,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Pengingat Warga Aktif (${item.effectiveReminderMenit} menit)",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF166534)
                                        )
                                    }
                                }
                            }

                            if (userRole != UserRole.WARGA) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (!isPengumuman) {
                                        IconButton(
                                            onClick = { onOpenReminderMode(item) },
                                            modifier = Modifier
                                                .size(48.dp)
                                                .testTag("card_reminder_button_${item.id}")
                                        ) {
                                            Icon(
                                                imageVector = if (item.isReminderActive) Icons.Filled.AlarmOn else Icons.Outlined.AlarmAdd,
                                                contentDescription = "Atur Pengingat Warga",
                                                tint = if (item.isReminderActive) GreenIncome else AmberWarning,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    IconButton(onClick = { onEditUndanganClick(item) }, modifier = Modifier.size(48.dp)) {
                                        Icon(Icons.Outlined.Edit, contentDescription = "Edit", tint = AccentBlue, modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(onClick = { onDeleteUndanganClick(item.id) }, modifier = Modifier.size(48.dp)) {
                                        Icon(Icons.Outlined.Delete, contentDescription = "Hapus", tint = RedExpense, modifier = Modifier.size(18.dp))
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

        // FAB to Add Announcement or Invitation
        FloatingActionButton(
            onClick = onAddUndanganClick,
            containerColor = NavyPrimary,
            contentColor = Color.White,
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("add_undangan_fab")
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Tambah Pengumuman atau Undangan")
        }

        fullScreenPhotoState?.let { state ->
            FullScreenPhotoViewerDialog(
                state = state,
                onDismiss = { fullScreenPhotoState = null }
            )
        }
    }
}
