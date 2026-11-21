package com.example.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.util.PhotoStorageHelper
import java.io.File
import java.io.FileOutputStream

@Composable
fun PhotoPickerSection(
    label: String = "Lampiran Foto / Bukti",
    currentPhotoUri: String,
    onPhotoSelected: (String) -> Unit,
    onPhotoRemoved: () -> Unit,
    isDarkTheme: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Launcher for Gallery Photo Picker
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val persistentUri = PhotoStorageHelper.saveUriToInternalStorage(context, uri)
            onPhotoSelected(persistentUri)
        }
    }

    // Launcher for Camera capture (TakePicturePreview returns Bitmap directly)
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            val persistentUri = PhotoStorageHelper.saveBitmapToInternalStorage(context, bitmap)
            if (persistentUri.isNotBlank()) {
                onPhotoSelected(persistentUri)
            }
        }
    }

    // Camera permission requester
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            cameraLauncher.launch(null)
        } else {
            Toast.makeText(context, "Izin kamera diperlukan untuk mengambil foto langsung", Toast.LENGTH_SHORT).show()
        }
    }

    val textColor = if (isDarkTheme) Color(0xFFCBD5E1) else MaterialTheme.colorScheme.onSurface
    val containerColor = if (isDarkTheme) Color(0xFF1E293B) else Color(0xFFF8FAFC)
    val borderColor = if (isDarkTheme) Color(0xFF475569) else Color(0xFFCBD5E1)

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = textColor
        )
        Spacer(modifier = Modifier.height(6.dp))

        if (currentPhotoUri.isNotBlank()) {
            // Preview card with delete and change actions
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = containerColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        val previewModel = remember(currentPhotoUri) {
                            PhotoStorageHelper.getDisplayModel(currentPhotoUri)
                        }
                        AsyncImage(
                            model = previewModel,
                            contentDescription = "Preview Foto",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Retake with Camera
                            OutlinedButton(
                                onClick = {
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                        cameraLauncher.launch(null)
                                    } else {
                                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Icon(Icons.Filled.PhotoCamera, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Kamera", fontSize = 11.sp)
                            }

                            // Choose another from Gallery
                            OutlinedButton(
                                onClick = {
                                    galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Galeri", fontSize = 11.sp)
                            }
                        }

                        // Remove Photo Button
                        IconButton(
                            onClick = onPhotoRemoved,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = "Hapus Foto",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        } else {
            // Choice buttons: Camera or Gallery
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = containerColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Option 1: Camera
                        Button(
                            onClick = {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                    cameraLauncher.launch(null)
                                } else {
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("camera_button")
                        ) {
                            Icon(Icons.Filled.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Buka Kamera", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }

                        // Option 2: Gallery
                        OutlinedButton(
                            onClick = {
                                galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("gallery_button")
                        ) {
                            Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Dari Galeri", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Ambil langsung foto kondisi/nota tanpa perlu link",
                        fontSize = 11.sp,
                        color = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF64748B)
                    )
                }
            }
        }
    }
}

@Composable
fun MultiPhotoPickerSection(
    label: String = "Foto Kegiatan (Maksimal 5 Foto)",
    photoUris: List<String>,
    onPhotosChanged: (List<String>) -> Unit,
    maxPhotos: Int = 5,
    isDarkTheme: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // Launcher for Multi Gallery Photo Picker
    val multiGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = maxPhotos.coerceAtLeast(2))
    ) { uris: List<Uri> ->
        val slotsLeft = (maxPhotos - photoUris.size).coerceAtLeast(0)
        if (uris.isNotEmpty() && slotsLeft > 0) {
            if (uris.size > slotsLeft) {
                Toast.makeText(
                    context,
                    "Maksimal $maxPhotos foto sekali pengiriman ($slotsLeft slot tersisa ditambahkan)",
                    Toast.LENGTH_SHORT
                ).show()
            }
            val newUris = uris.take(slotsLeft)
                .map { PhotoStorageHelper.saveUriToInternalStorage(context, it) }
                .filter { it.isNotBlank() }
            val combined = (photoUris + newUris).distinct().take(maxPhotos)
            onPhotosChanged(combined)
        } else if (uris.isNotEmpty()) {
            Toast.makeText(context, "Batas maksimal $maxPhotos foto sudah tercapai", Toast.LENGTH_SHORT).show()
        }
    }

    // Single gallery launcher fallback
    val singleGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null && photoUris.size < maxPhotos) {
            val persistentUri = PhotoStorageHelper.saveUriToInternalStorage(context, uri)
            val combined = (photoUris + persistentUri).distinct().take(maxPhotos)
            onPhotosChanged(combined)
        }
    }

    // Launcher for Camera capture
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            val persistentUri = PhotoStorageHelper.saveBitmapToInternalStorage(context, bitmap)
            if (persistentUri.isNotBlank() && photoUris.size < maxPhotos) {
                onPhotosChanged(photoUris + persistentUri)
            }
        }
    }

    // Camera permission requester
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            cameraLauncher.launch(null)
        } else {
            Toast.makeText(context, "Izin kamera diperlukan untuk mengambil foto langsung", Toast.LENGTH_SHORT).show()
        }
    }

    val textColor = if (isDarkTheme) Color(0xFFCBD5E1) else MaterialTheme.colorScheme.onSurface
    val containerColor = if (isDarkTheme) Color(0xFF1E293B) else Color(0xFFF8FAFC)
    val borderColor = if (isDarkTheme) Color(0xFF475569) else Color(0xFFCBD5E1)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = textColor
            )
            Text(
                text = "${photoUris.size}/$maxPhotos Foto",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (photoUris.size >= maxPhotos) Color(0xFF16A34A) else Color(0xFF0284C7)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Selected photos thumbnails
        if (photoUris.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(photoUris) { index, uri ->
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
                            .background(Color.Black.copy(alpha = 0.05f)),
                        contentAlignment = Alignment.Center
                    ) {
                        val thumbModel = remember(uri) {
                            PhotoStorageHelper.getDisplayModel(uri)
                        }
                        AsyncImage(
                            model = thumbModel,
                            contentDescription = "Foto ${index + 1}",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )

                        // Delete button badge on top right
                        IconButton(
                            onClick = {
                                val updated = photoUris.toMutableList()
                                updated.removeAt(index)
                                onPhotosChanged(updated)
                            },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(24.dp)
                                .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Hapus Foto",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        // Order badge on bottom left
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color.Black.copy(alpha = 0.65f),
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(4.dp)
                        ) {
                            Text(
                                text = "${index + 1}",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }

        // Add action buttons
        if (photoUris.size < maxPhotos) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = containerColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Option 1: Camera
                    Button(
                        onClick = {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                cameraLauncher.launch(null)
                            } else {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Buka Kamera", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    // Option 2: Gallery
                    OutlinedButton(
                        onClick = {
                            try {
                                multiGalleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            } catch (e: Exception) {
                                singleGalleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Pilih Galeri", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        } else {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFDCFCE7),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Maksimal $maxPhotos foto sudah terpilih.",
                        fontSize = 11.sp,
                        color = Color(0xFF166534),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

