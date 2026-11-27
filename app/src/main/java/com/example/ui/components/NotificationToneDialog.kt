package com.example.ui.components

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.SkySoft
import com.example.util.DeviceToneItem
import com.example.util.NotificationToneManager
import com.example.util.NotificationToneOption
import com.example.util.NotificationTonePreference
import com.example.util.ReminderRingtonePlayer

@Composable
fun NotificationToneDialog(
    initialPreference: NotificationTonePreference,
    onDismiss: () -> Unit,
    onSave: (option: NotificationToneOption, customUri: String, customTitle: String) -> Unit
) {
    val context = LocalContext.current
    val isPreviewPlaying by ReminderRingtonePlayer.isPreviewPlaying.collectAsStateWithLifecycle()

    var selectedOption by remember(initialPreference) { mutableStateOf(initialPreference.option) }
    var selectedCustomUri by remember(initialPreference) { mutableStateOf(initialPreference.customUri) }
    var selectedCustomTitle by remember(initialPreference) { mutableStateOf(initialPreference.customTitle) }
    var activePreviewKey by remember { mutableStateOf<String?>(null) }

    val deviceTones = remember { NotificationToneManager.queryDeviceNotificationTones(context, limit = 25) }

    val presetOptions = remember {
        listOf(
            NotificationToneOption.SYSTEM_DEFAULT,
            NotificationToneOption.APP_REMINDER,
            NotificationToneOption.SYSTEM_ALARM,
            NotificationToneOption.SYSTEM_RINGTONE,
            NotificationToneOption.SILENT_VIBRATE
        )
    }

    LaunchedEffect(isPreviewPlaying) {
        if (!isPreviewPlaying) {
            activePreviewKey = null
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            ReminderRingtonePlayer.stopPreview()
        }
    }

    fun triggerPreview(option: NotificationToneOption, customUri: String, previewKey: String) {
        if (isPreviewPlaying && activePreviewKey == previewKey) {
            ReminderRingtonePlayer.stopPreview()
            activePreviewKey = null
        } else {
            val started = ReminderRingtonePlayer.playPreview(context, option, customUri)
            activePreviewKey = if (started) previewKey else null
        }
    }

    val ringtonePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val pickedUri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
            if (pickedUri == null) {
                selectedOption = NotificationToneOption.SILENT_VIBRATE
                selectedCustomUri = ""
                selectedCustomTitle = ""
                ReminderRingtonePlayer.stopPreview()
                NotificationToneManager.triggerShortVibrate(context)
            } else {
                val uriStr = pickedUri.toString()
                val title = NotificationToneManager.resolveToneTitle(context, pickedUri)
                selectedOption = NotificationToneOption.DEVICE_PICKED
                selectedCustomUri = uriStr
                selectedCustomTitle = title
                triggerPreview(NotificationToneOption.DEVICE_PICKED, uriStr, "device:$uriStr")
            }
        }
    }

    Dialog(onDismissRequest = {
        ReminderRingtonePlayer.stopPreview()
        onDismiss()
    }) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp)
                .fillMaxHeight(0.86f)
                .padding(vertical = 12.dp)
                .testTag("notification_tone_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SkySoft,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.NotificationsActive,
                                contentDescription = null,
                                tint = NavyPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Nada Notifikasi",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Tersimpan lokal khusus di perangkat ini",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = SkySoft,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PhoneAndroid,
                            contentDescription = null,
                            tint = NavyPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Pilihan nada hanya berlaku di HP ini dan tidak mengubah HP warga, pengurus, atau admin lain.",
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            color = NavyPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Text(
                            text = "Pilihan Utama",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = NavyPrimary,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }

                    items(presetOptions, key = { it.storageKey }) { option ->
                        val isSelected = selectedOption == option
                        val previewKey = "preset:${option.storageKey}"
                        val isThisPlaying = isPreviewPlaying && activePreviewKey == previewKey

                        ToneOptionRow(
                            title = option.label,
                            subtitle = option.subtitle,
                            isSelected = isSelected,
                            isSilentOption = option == NotificationToneOption.SILENT_VIBRATE,
                            isPlaying = isThisPlaying,
                            rowTestTag = "tone_option_${option.storageKey}",
                            previewTestTag = "tone_preview_${option.storageKey}",
                            onSelect = {
                                selectedOption = option
                                if (option != NotificationToneOption.DEVICE_PICKED) {
                                    selectedCustomUri = ""
                                    selectedCustomTitle = ""
                                }
                                triggerPreview(option, "", previewKey)
                            },
                            onPreviewClick = {
                                selectedOption = option
                                triggerPreview(option, "", previewKey)
                            }
                        )
                    }

                    if (selectedOption == NotificationToneOption.DEVICE_PICKED && selectedCustomUri.isNotBlank()) {
                        val isSelectedInDeviceList = deviceTones.any { it.uriString == selectedCustomUri }
                        if (!isSelectedInDeviceList) {
                            item {
                                val previewKey = "device:$selectedCustomUri"
                                ToneOptionRow(
                                    title = selectedCustomTitle.ifBlank { "Nada Pilihan Perangkat" },
                                    subtitle = "Dipilih dari pemilih nada sistem perangkat",
                                    isSelected = true,
                                    isSilentOption = false,
                                    isPlaying = isPreviewPlaying && activePreviewKey == previewKey,
                                    rowTestTag = "tone_option_custom_selected",
                                    previewTestTag = "tone_preview_custom_selected",
                                    onSelect = {
                                        triggerPreview(NotificationToneOption.DEVICE_PICKED, selectedCustomUri, previewKey)
                                    },
                                    onPreviewClick = {
                                        triggerPreview(NotificationToneOption.DEVICE_PICKED, selectedCustomUri, previewKey)
                                    }
                                )
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Nada Notifikasi di HP Ini",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = NavyPrimary
                            )
                            TextButton(
                                onClick = {
                                    ReminderRingtonePlayer.stopPreview()
                                    val pickerIntent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                        putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
                                        putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Pilih Nada Notifikasi HP")
                                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                                        val currentUri = NotificationToneManager.resolvePreviewSoundUri(
                                            context,
                                            selectedOption,
                                            selectedCustomUri
                                        )
                                        putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, currentUri)
                                    }
                                    ringtonePickerLauncher.launch(pickerIntent)
                                },
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("open_system_ringtone_picker_button")
                            ) {
                                Icon(Icons.Outlined.MusicNote, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Cari di Sistem HP", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    if (deviceTones.isEmpty()) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Gunakan tombol \"Cari di Sistem HP\" di atas untuk memilih daftar nada bawaan perangkat.",
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    } else {
                        items(deviceTones, key = { it.uriString }) { tone: DeviceToneItem ->
                            val isSelected = selectedOption == NotificationToneOption.DEVICE_PICKED &&
                                selectedCustomUri == tone.uriString
                            val previewKey = "device:${tone.uriString}"
                            ToneOptionRow(
                                title = tone.title,
                                subtitle = "Nada notifikasi perangkat",
                                isSelected = isSelected,
                                isSilentOption = false,
                                isPlaying = isPreviewPlaying && activePreviewKey == previewKey,
                                rowTestTag = "tone_device_item_${tone.title}",
                                previewTestTag = "tone_device_preview_${tone.title}",
                                onSelect = {
                                    selectedOption = NotificationToneOption.DEVICE_PICKED
                                    selectedCustomUri = tone.uriString
                                    selectedCustomTitle = tone.title
                                    triggerPreview(NotificationToneOption.DEVICE_PICKED, tone.uriString, previewKey)
                                },
                                onPreviewClick = {
                                    selectedOption = NotificationToneOption.DEVICE_PICKED
                                    selectedCustomUri = tone.uriString
                                    selectedCustomTitle = tone.title
                                    triggerPreview(NotificationToneOption.DEVICE_PICKED, tone.uriString, previewKey)
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Bottom actions: Preview button + Cancel + Save
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            val key = if (selectedOption == NotificationToneOption.DEVICE_PICKED) {
                                "device:$selectedCustomUri"
                            } else {
                                "preset:${selectedOption.storageKey}"
                            }
                            triggerPreview(selectedOption, selectedCustomUri, key)
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("test_notification_tone_button")
                    ) {
                        Icon(
                            imageVector = when {
                                isPreviewPlaying -> Icons.Filled.Stop
                                selectedOption == NotificationToneOption.SILENT_VIBRATE -> Icons.Filled.Vibration
                                else -> Icons.AutoMirrored.Filled.VolumeUp
                            },
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = when {
                                isPreviewPlaying -> "Hentikan"
                                selectedOption == NotificationToneOption.SILENT_VIBRATE -> "Tes Getar"
                                else -> "Tes Bunyi"
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = {
                                ReminderRingtonePlayer.stopPreview()
                                onDismiss()
                            },
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("cancel_notification_tone_button")
                        ) {
                            Text("Batal")
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Button(
                            onClick = {
                                ReminderRingtonePlayer.stopPreview()
                                onSave(selectedOption, selectedCustomUri, selectedCustomTitle)
                            },
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("save_notification_tone_button")
                        ) {
                            Text("Simpan")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToneOptionRow(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    isSilentOption: Boolean,
    isPlaying: Boolean,
    rowTestTag: String,
    previewTestTag: String,
    onSelect: () -> Unit,
    onPreviewClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) SkySoft else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = if (isSelected) BorderStroke(1.5.dp, NavyPrimary) else null,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
            .testTag(rowTestTag)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)
        ) {
            RadioButton(
                selected = isSelected,
                onClick = onSelect
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 6.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 13.5.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (isSelected) NavyPrimary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(
                onClick = onPreviewClick,
                modifier = Modifier
                    .size(48.dp)
                    .testTag(previewTestTag)
            ) {
                Icon(
                    imageVector = when {
                        isPlaying -> Icons.Filled.StopCircle
                        isSilentOption -> Icons.Filled.Vibration
                        else -> Icons.Filled.PlayCircleOutline
                    },
                    contentDescription = if (isPlaying) "Hentikan pratinjau $title" else "Putar pratinjau $title",
                    tint = if (isPlaying) Color(0xFFDC2626) else NavyPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
