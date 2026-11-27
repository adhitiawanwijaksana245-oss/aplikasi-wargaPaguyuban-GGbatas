package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.ReminderMode
import com.example.data.model.UndanganItem
import com.example.data.model.UserRole
import com.example.ui.theme.*
import com.example.util.IndonesianDateHelper
import com.example.util.ReminderCalculator
import kotlinx.coroutines.delay

/**
 * Daftar kartu pengingat aktif yang tampil pada layar Warga dan Pengurus.
 * Menampilkan hitung mundur secara langsung dan status pengiriman notifikasi.
 */
@Composable
fun ActiveRemindersSection(
    undanganList: List<UndanganItem>,
    userRole: UserRole,
    onOpenUndanganTab: () -> Unit = {},
    onConfigureReminder: (UndanganItem) -> Unit = {},
    onCancelReminder: (UndanganItem) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val activeReminders = remember(undanganList, nowMillis) {
        val sixHoursMs = 6L * 60L * 60L * 1000L
        undanganList.filter { item ->
            item.isReminderActive && (nowMillis - item.effectiveReminderTargetMillis) <= sixHoursMs
        }.sortedBy { it.effectiveReminderTargetMillis }
    }

    LaunchedEffect(activeReminders.isNotEmpty()) {
        while (activeReminders.isNotEmpty()) {
            nowMillis = System.currentTimeMillis()
            delay(1000L)
        }
    }

    if (activeReminders.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        activeReminders.forEach { item ->
            val targetMillis = item.effectiveReminderTargetMillis
            val isDue = nowMillis >= targetMillis
            val remainingText = ReminderCalculator.formatRemainingTime(targetMillis, nowMillis)
            val targetClock = ReminderCalculator.formatClockTime(targetMillis)

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDue) Color(0xFFFFF7ED) else Color(0xFFEFF6FF)
                ),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isDue) Color(0xFFFB923C) else Color(0xFF93C5FD)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("active_reminder_card_${item.id}")
                    .clickable { onOpenUndanganTab() }
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isDue) Color(0xFFEA580C) else NavyPrimary
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = if (isDue) Icons.Filled.NotificationsActive else Icons.Filled.AlarmOn,
                                    contentDescription = "Status pengingat acara",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isDue) "PENGINGAT AKTIF" else "PENGINGAT TERJADWAL",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isDue) Color(0xFFFED7AA) else Color(0xFFDBEAFE)
                        ) {
                            Text(
                                text = remainingText,
                                color = if (isDue) Color(0xFF9A3412) else NavyDark,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = item.cleanJudul,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    val formattedTanggal = remember(item.tanggal) { IndonesianDateHelper.formatFullDate(item.tanggal) }
                    val locationSuffix = if (item.lokasi.isNotBlank() && item.lokasi != "-") " • ${item.lokasi}" else ""
                    Text(
                        text = "Jadwal: $formattedTanggal (${item.waktu})$locationSuffix",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isDue) {
                            "Notifikasi pengingat (${item.effectiveReminderMenit} menit) sudah dikirim ke aplikasi warga ($targetClock)."
                        } else {
                            "Notifikasi warga (${item.effectiveReminderMenit} menit) aktif pada $targetClock."
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isDue) Color(0xFFC2410C) else AccentBlue
                    )

                    if (userRole != UserRole.WARGA) {
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = DividerBorder)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = { onConfigureReminder(item) },
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("edit_reminder_button_${item.id}")
                            ) {
                                Icon(Icons.Outlined.EditNotifications, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Ubah Menit", fontSize = 12.sp)
                            }
                            TextButton(
                                onClick = { onCancelReminder(item) },
                                colors = ButtonDefaults.textButtonColors(contentColor = RedExpense),
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("cancel_reminder_button_${item.id}")
                            ) {
                                Icon(Icons.Outlined.AlarmOff, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Nonaktifkan", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Dialog Mode Pengingat Warga (Hanya dapat diakses oleh Pengurus RT).
 * Pengurus dapat memilih acara Rapat / Gotong Royong yang telah dijadwalkan
 * serta menentukan berapa menit pengingat diaktifkan & dikirim ke aplikasi warga.
 */
@Composable
fun EventReminderDialog(
    undanganList: List<UndanganItem>,
    initialSelectedItem: UndanganItem?,
    onDismiss: () -> Unit,
    onCreateNewEvent: () -> Unit,
    onActivateReminder: (item: UndanganItem, minutes: Int, mode: ReminderMode) -> Unit,
    onCancelReminder: (item: UndanganItem) -> Unit
) {
    val scheduledEvents = remember(undanganList) {
        undanganList.filter { !it.isPengumuman }.sortedWith(
            compareByDescending<UndanganItem> { ReminderCalculator.isRapatOrGotongRoyong(it) }
                .thenBy { it.tanggal }
        )
    }

    var selectedEventId by remember(initialSelectedItem, scheduledEvents) {
        mutableStateOf(
            initialSelectedItem?.takeIf { !it.isPengumuman }?.id
                ?: scheduledEvents.firstOrNull()?.id
                ?: ""
        )
    }
    val selectedEvent = remember(scheduledEvents, selectedEventId) {
        scheduledEvents.firstOrNull { it.id == selectedEventId } ?: scheduledEvents.firstOrNull()
    }

    var reminderMode by remember(selectedEvent?.id) {
        val hasParsedStart = selectedEvent?.let {
            ReminderCalculator.parseEventStartMillis(it.tanggal, it.waktu) != null
        } ?: false
        val startMillis = selectedEvent?.let {
            ReminderCalculator.parseEventStartMillis(it.tanggal, it.waktu)
        }
        val canUseBefore = hasParsedStart && startMillis != null && startMillis > System.currentTimeMillis() + 60_000L
        mutableStateOf(if (canUseBefore) ReminderMode.BEFORE_EVENT else ReminderMode.FROM_NOW)
    }

    var minutesInput by remember(selectedEvent?.id) {
        val defaultMins = selectedEvent?.effectiveReminderMenit?.takeIf { it > 0 } ?: 15
        mutableStateOf(defaultMins.toString())
    }

    val parsedMinutes = minutesInput.toIntOrNull()?.coerceIn(1, 1440)
    val eventStartMillis = remember(selectedEvent) {
        selectedEvent?.let { ReminderCalculator.parseEventStartMillis(it.tanggal, it.waktu) }
    }
    val nowMillis = System.currentTimeMillis()
    val previewTargetMillis = remember(reminderMode, parsedMinutes, eventStartMillis, nowMillis) {
        val mins = parsedMinutes ?: return@remember null
        ReminderCalculator.calculateTargetMillis(
            mode = reminderMode,
            minutes = mins,
            eventStartMillis = eventStartMillis,
            nowMillis = nowMillis
        )
    }
    val isBeforeEventValid = reminderMode != ReminderMode.BEFORE_EVENT ||
        (eventStartMillis != null && previewTargetMillis != null && previewTargetMillis > nowMillis)

    val presetMinutes = listOf(1, 5, 10, 15, 30, 60)

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
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
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(AmberBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AlarmAdd,
                            contentDescription = null,
                            tint = AmberWarning,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Mode Pengingat Warga",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = NavyPrimary
                        )
                        Text(
                            text = "Khusus Pengurus • Kirim ke aplikasi HP warga",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (scheduledEvents.isEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SlateBackground),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(16.dp).fillMaxWidth()
                        ) {
                            Icon(Icons.Outlined.EventBusy, contentDescription = null, tint = TextMuted, modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Belum ada acara Rapat / Gotong Royong terjadwal",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Buat jadwal undangan rapat atau gotong royong terlebih dahulu sebelum mengaktifkan pengingat warga.",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    onDismiss()
                                    onCreateNewEvent()
                                },
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("create_event_from_reminder_button")
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Buat Jadwal Acara")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                        ) {
                            Text("Tutup")
                        }
                    }
                } else {
                    Text(
                        text = "1. Pilih Acara Terjadwal (Rapat / Gotong Royong):",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        scheduledEvents.forEach { event ->
                            val isSelected = event.id == selectedEvent?.id
                            val isPrioritized = ReminderCalculator.isRapatOrGotongRoyong(event)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) SkySoft else SlateBackground,
                                border = BorderStroke(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) AccentBlue else DividerBorder
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("reminder_event_option_${event.id}")
                                    .clickable { selectedEventId = event.id }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { selectedEventId = event.id }
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = event.cleanJudul,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = TextPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            if (isPrioritized) {
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = AmberBg
                                                ) {
                                                    Text(
                                                        text = "Agenda RT",
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF92400E),
                                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        val formattedEventDate = remember(event.tanggal) {
                                            IndonesianDateHelper.formatFullDate(event.tanggal)
                                        }
                                        Text(
                                            text = "$formattedEventDate • ${event.waktu} • ${event.lokasi.ifBlank { "Lingkungan RT 005" }}",
                                            fontSize = 11.sp,
                                            color = TextSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (event.isReminderActive) {
                                            Spacer(modifier = Modifier.height(3.dp))
                                            Text(
                                                text = "🔔 Pengingat aktif (${event.effectiveReminderMenit} menit)",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = GreenIncome
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "2. Metode Waktu Pengaktifan:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = reminderMode == ReminderMode.FROM_NOW,
                            onClick = { reminderMode = ReminderMode.FROM_NOW },
                            label = { Text("Dalam X Menit", fontSize = 11.sp) },
                            leadingIcon = {
                                Icon(Icons.Filled.Timer, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("reminder_mode_from_now")
                        )
                        FilterChip(
                            selected = reminderMode == ReminderMode.BEFORE_EVENT,
                            onClick = { reminderMode = ReminderMode.BEFORE_EVENT },
                            label = { Text("Sebelum Acara", fontSize = 11.sp) },
                            leadingIcon = {
                                Icon(Icons.Filled.EventAvailable, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("reminder_mode_before_event")
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (reminderMode == ReminderMode.BEFORE_EVENT) {
                            "3. Pilih Berapa Menit Sebelum Acara Dimulai:"
                        } else {
                            "3. Pilih Berapa Menit Pengingat Diaktifkan Sekarang:"
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    // Two clean rows of preset minute chips
                    presetMinutes.chunked(3).forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowItems.forEach { mins ->
                                val selected = parsedMinutes == mins
                                FilterChip(
                                    selected = selected,
                                    onClick = { minutesInput = mins.toString() },
                                    label = { Text("$mins Menit", fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .defaultMinSize(minHeight = 48.dp)
                                        .testTag("reminder_preset_${mins}m")
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = minutesInput,
                        onValueChange = { raw ->
                            minutesInput = raw.filter { it.isDigit() }.take(4)
                        },
                        label = { Text("Atau masukkan jumlah menit (1 - 1440)") },
                        placeholder = { Text("Contoh: 15") },
                        suffix = { Text("menit") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("reminder_custom_minutes_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Preview / Validation box
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isBeforeEventValid && previewTargetMillis != null) Color(0xFFF0FDF4) else AmberBg,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            if (reminderMode == ReminderMode.BEFORE_EVENT && eventStartMillis == null) {
                                Text(
                                    text = "⚠️ Jadwal waktu acara '${selectedEvent?.waktu}' tidak memakai format jam (mis. 19:30). Pilih mode 'Dalam X Menit' atau edit jam acara.",
                                    fontSize = 11.sp,
                                    color = Color(0xFF92400E),
                                    lineHeight = 15.sp
                                )
                            } else if (reminderMode == ReminderMode.BEFORE_EVENT && !isBeforeEventValid) {
                                Text(
                                    text = "⚠️ Waktu ${parsedMinutes ?: 0} menit sebelum jadwal acara sudah lewat. Pilih jumlah menit lebih kecil atau pilih mode 'Dalam X Menit'.",
                                    fontSize = 11.sp,
                                    color = Color(0xFF92400E),
                                    lineHeight = 15.sp
                                )
                            } else if (previewTargetMillis != null && parsedMinutes != null) {
                                val targetLabel = ReminderCalculator.formatClockTime(previewTargetMillis)
                                Text(
                                    text = "🔔 Pengingat aktif pada: $targetLabel",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF166534)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Akan otomatis disinkronkan dan memicu notifikasi pengingat di seluruh HP warga.",
                                    fontSize = 11.sp,
                                    color = Color(0xFF15803D)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (selectedEvent != null && selectedEvent.isReminderActive) {
                        OutlinedButton(
                            onClick = { onCancelReminder(selectedEvent) },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = RedExpense),
                            border = BorderStroke(1.dp, RedExpense.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("deactivate_reminder_dialog_button")
                        ) {
                            Icon(Icons.Outlined.AlarmOff, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Nonaktifkan Pengingat Acara Ini", fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                        ) {
                            Text("Batal")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val targetEvent = selectedEvent
                                val mins = parsedMinutes
                                if (targetEvent != null && mins != null && isBeforeEventValid) {
                                    onActivateReminder(targetEvent, mins, reminderMode)
                                }
                            },
                            enabled = selectedEvent != null && parsedMinutes != null && isBeforeEventValid && previewTargetMillis != null,
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("activate_reminder_button")
                        ) {
                            Icon(Icons.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Aktifkan & Kirim", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
