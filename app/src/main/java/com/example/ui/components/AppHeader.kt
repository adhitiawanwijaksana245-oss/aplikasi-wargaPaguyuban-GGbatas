package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.UserRole
import com.example.data.repository.WargaRepository
import com.example.ui.theme.NavyPrimary

@Composable
fun AppHeader(
    userRole: UserRole,
    isSyncing: Boolean,
    unreadNotificationCount: Int = 0,
    roleDisplayName: String = "",
    onRoleClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onNotificationClick: () -> Unit = {},
    onOpenSettings: () -> Unit,
    onOpenChangePassword: () -> Unit,
    onOpenReminderMode: () -> Unit = {},
    onOpenPengurusAccounts: () -> Unit = {},
    onOpenSplashSettings: () -> Unit = {},
    onOpenNotificationTone: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    val rotation = remember { Animatable(0f) }
    LaunchedEffect(isSyncing) {
        if (isSyncing) {
            rotation.animateTo(
                targetValue = rotation.value + 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(800, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                )
            )
        } else {
            rotation.snapTo(0f)
        }
    }

    Surface(
        color = NavyPrimary,
        modifier = modifier.fillMaxWidth()
    ) {
        val resolvedRoleLabel = when (userRole) {
            UserRole.WARGA -> "Warga"
            UserRole.PENGURUS -> roleDisplayName.trim().ifBlank { "Pengurus" }
            UserRole.ADMIN_UTAMA -> roleDisplayName.trim().ifBlank { WargaRepository.ADMIN_UTAMA_DISPLAY_NAME }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val badgeMaxWidth = (maxWidth * 0.31f).coerceIn(92.dp, 168.dp)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Left Logo + Title
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .padding(2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.rt_logo),
                                contentDescription = "Logo RT 005 Mulyaharja",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f, fill = false)) {
                            Text(
                                text = "Paguyuban Warga",
                                color = Color.White,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "RT 005 / RW 011 Mulyaharja",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 10.5.sp,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Right Actions: Role pill + Notification Bell + Refresh + Menu
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        // Role Toggle Button
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (userRole == UserRole.WARGA) Color.White.copy(alpha = 0.18f) else Color(0xFF22C55E),
                            modifier = Modifier
                                .widthIn(max = badgeMaxWidth)
                                .testTag("role_toggle_button")
                                .clip(RoundedCornerShape(20.dp))
                                .clickable { onRoleClick() }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            ) {
                                Icon(
                                    imageVector = if (userRole == UserRole.WARGA) Icons.Filled.Lock else Icons.Filled.Security,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = resolvedRoleLabel,
                                    color = Color.White,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.testTag("role_badge_label")
                                )
                            }
                        }

                    // Notification Bell with Badge
                    IconButton(
                        onClick = onNotificationClick,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("notification_bell_button")
                    ) {
                        Box(contentAlignment = Alignment.TopEnd) {
                            Icon(
                                imageVector = if (unreadNotificationCount > 0) Icons.Filled.NotificationsActive else Icons.Outlined.Notifications,
                                contentDescription = "Pemberitahuan Warga",
                                tint = if (unreadNotificationCount > 0) Color(0xFFFBBF24) else Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            if (unreadNotificationCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFEF4444))
                                )
                            }
                        }
                    }

                    // Sync Button (Full Red Star Icon)
                    IconButton(
                        onClick = onRefreshClick,
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("refresh_sheet_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = "Sinkronisasi Spreadsheet",
                            tint = Color(0xFFFF1E1E),
                            modifier = Modifier
                                .size(22.dp)
                                .rotate(rotation.value)
                        )
                    }

                    // More Menu
                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("more_menu_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = "Menu Pengaturan",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Pemberitahuan Warga") },
                                onClick = {
                                    showMenu = false
                                    onNotificationClick()
                                },
                                leadingIcon = {
                                    Icon(Icons.Outlined.Notifications, contentDescription = null)
                                },
                                modifier = Modifier.testTag("menu_pemberitahuan_warga")
                            )

                            DropdownMenuItem(
                                text = { Text("Nada Notifikasi") },
                                onClick = {
                                    showMenu = false
                                    onOpenNotificationTone()
                                },
                                leadingIcon = {
                                    Icon(Icons.Outlined.MusicNote, contentDescription = null)
                                },
                                modifier = Modifier.testTag("menu_nada_notifikasi")
                            )

                            if (userRole == UserRole.PENGURUS) {
                                DropdownMenuItem(
                                    text = { Text("Mode Pengingat Warga") },
                                    onClick = {
                                        showMenu = false
                                        onOpenReminderMode()
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Outlined.AlarmAdd, contentDescription = null)
                                    },
                                    modifier = Modifier.testTag("menu_pengingat_warga")
                                )
                            }

                            if (userRole == UserRole.ADMIN_UTAMA) {
                                DropdownMenuItem(
                                    text = { Text("Sinkronisasi Google Sheets") },
                                    onClick = {
                                        showMenu = false
                                        onOpenSettings()
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Outlined.CloudSync, contentDescription = null)
                                    },
                                    modifier = Modifier.testTag("menu_sinkronisasi_sheets")
                                )
                                DropdownMenuItem(
                                    text = { Text("Mode Pengingat Warga") },
                                    onClick = {
                                        showMenu = false
                                        onOpenReminderMode()
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Outlined.AlarmAdd, contentDescription = null)
                                    },
                                    modifier = Modifier.testTag("menu_pengingat_warga")
                                )
                                DropdownMenuItem(
                                    text = { Text("Pengaturan Akun Pengurus") },
                                    onClick = {
                                        showMenu = false
                                        onOpenPengurusAccounts()
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Outlined.ManageAccounts, contentDescription = null)
                                    },
                                    modifier = Modifier.testTag("menu_akun_pengurus")
                                )
                                DropdownMenuItem(
                                    text = { Text("Durasi Splash Screen") },
                                    onClick = {
                                        showMenu = false
                                        onOpenSplashSettings()
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Outlined.Timer, contentDescription = null)
                                    },
                                    modifier = Modifier.testTag("menu_splash_duration")
                                )
                                DropdownMenuItem(
                                    text = { Text("Ubah Password Otoritas") },
                                    onClick = {
                                        showMenu = false
                                        onOpenChangePassword()
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Outlined.Key, contentDescription = null)
                                    },
                                    modifier = Modifier.testTag("menu_ubah_password")
                                )
                                DropdownMenuItem(
                                    text = { Text("Kunci ke Mode Warga") },
                                    onClick = {
                                        showMenu = false
                                        onRoleClick()
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Outlined.Lock, contentDescription = null)
                                    },
                                    modifier = Modifier.testTag("menu_kunci_warga")
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
