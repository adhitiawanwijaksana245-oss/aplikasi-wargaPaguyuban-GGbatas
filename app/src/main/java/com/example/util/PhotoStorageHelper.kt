package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileOutputStream

object PhotoStorageHelper {
    private const val TAG = "PhotoStorageHelper"
    private const val PHOTOS_FOLDER = "warga_photos"
    private const val MAX_DIMENSION = 1280
    private const val COMPRESS_QUALITY = 85
    private val fileSequence = java.util.concurrent.atomic.AtomicInteger(0)

    /**
     * Menyimpan URI gambar dari Galeri / Photo Picker ke penyimpanan internal permanen (filesDir).
     * Mencegah foto hilang / blank putih ketika aplikasi ditutup dan dibuka kembali.
     */
    fun saveUriToInternalStorage(context: Context, sourceUri: Uri): String {
        val uriStr = sourceUri.toString()
        if (uriStr.isBlank()) return ""

        // Jika sudah merupakan file lokal di direktori permanen warga_photos, kembalikan langsung
        if (uriStr.startsWith("file://") && uriStr.contains(PHOTOS_FOLDER)) {
            return uriStr
        }

        // Coba pertahankan izin persistable URI jika didukung oleh provider
        try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(sourceUri, flags)
        } catch (_: Exception) {
            // Beberapa provider Photo Picker tidak mendukung persistable permission,
            // sehingga penyalinan ke internal storage di bawah adalah solusi mutlak.
        }

        return try {
            val photosDir = File(context.filesDir, PHOTOS_FOLDER).apply {
                if (!exists()) mkdirs()
            }
            val seq = fileSequence.incrementAndGet()
            val fileName = "warga_img_${System.currentTimeMillis()}_${seq}_${(1000..9999).random()}.jpg"
            val destFile = File(photosDir, fileName)

            val exifOrientation = try {
                context.contentResolver.openInputStream(sourceUri)?.use { exifInput ->
                    ExifInterface(exifInput).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                } ?: ExifInterface.ORIENTATION_NORMAL
            } catch (_: Exception) {
                ExifInterface.ORIENTATION_NORMAL
            }

            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                val originalBitmap = BitmapFactory.decodeStream(input)
                if (originalBitmap != null) {
                    val orientedBitmap = applyExifOrientation(originalBitmap, exifOrientation)
                    val scaled = scaleBitmapDown(orientedBitmap, MAX_DIMENSION)
                    FileOutputStream(destFile).use { out ->
                        scaled.compress(Bitmap.CompressFormat.JPEG, COMPRESS_QUALITY, out)
                    }
                    if (scaled != orientedBitmap) {
                        orientedBitmap.recycle()
                    }
                    if (orientedBitmap != originalBitmap) {
                        originalBitmap.recycle()
                    }
                    Uri.fromFile(destFile).toString()
                } else {
                    // Fallback direct stream copy jika BitmapFactory gagal decode
                    var copied = false
                    context.contentResolver.openInputStream(sourceUri)?.use { rawInput ->
                        FileOutputStream(destFile).use { out ->
                            rawInput.copyTo(out)
                        }
                        copied = destFile.exists() && destFile.length() > 0L
                    }
                    if (copied) Uri.fromFile(destFile).toString() else uriStr
                }
            } ?: uriStr
        } catch (e: Exception) {
            Log.e(TAG, "Gagal menyalin foto ke penyimpanan internal: ${e.message}", e)
            uriStr
        }
    }

    /**
     * Menyimpan Bitmap hasil tangkapan kamera langsung ke penyimpanan internal permanen.
     */
    fun saveBitmapToInternalStorage(context: Context, bitmap: Bitmap): String {
        return try {
            val photosDir = File(context.filesDir, PHOTOS_FOLDER).apply {
                if (!exists()) mkdirs()
            }
            val seq = fileSequence.incrementAndGet()
            val fileName = "warga_cam_${System.currentTimeMillis()}_${seq}_${(1000..9999).random()}.jpg"
            val destFile = File(photosDir, fileName)

            val scaled = scaleBitmapDown(bitmap, MAX_DIMENSION)
            FileOutputStream(destFile).use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, COMPRESS_QUALITY, out)
            }
            Uri.fromFile(destFile).toString()
        } catch (e: Exception) {
            Log.e(TAG, "Gagal menyimpan bitmap kamera ke internal storage: ${e.message}", e)
            ""
        }
    }

    /**
     * Memastikan string URI foto sudah tersimpan di internal storage.
     * Jika masih berupa content://, maka akan disalin ke internal storage.
     */
    fun ensurePersistentUri(context: Context, uriOrPath: String): String {
        if (uriOrPath.isBlank()) return ""
        if (uriOrPath.startsWith("http://") || uriOrPath.startsWith("https://")) return uriOrPath
        if (uriOrPath.startsWith("file://") && uriOrPath.contains(PHOTOS_FOLDER)) return uriOrPath

        return if (uriOrPath.startsWith("content://")) {
            try {
                val uri = Uri.parse(uriOrPath)
                saveUriToInternalStorage(context, uri)
            } catch (e: Exception) {
                Log.w(TAG, "Gagal mengonversi content URI: ${e.message}")
                uriOrPath
            }
        } else {
            uriOrPath
        }
    }

    /**
     * Memproses beberapa URI yang dipisahkan koma (format foto Galeri)
     */
    fun ensurePersistentCommaSeparated(context: Context, commaUris: String): String {
        if (commaUris.isBlank()) return ""
        val list = commaUris.split(",").map { it.trim() }.filter { it.isNotBlank() }
        val persistentList = list.map { ensurePersistentUri(context, it) }
        return persistentList.joinToString(",")
    }

    private fun openImageInputStream(context: Context, uriOrPath: String): java.io.InputStream? {
        val clean = uriOrPath.trim()
        if (clean.startsWith("file://")) {
            val path = try {
                java.net.URI(clean).path ?: clean.removePrefix("file://")
            } catch (_: Exception) {
                clean.removePrefix("file://")
            }
            val file = File(path)
            if (file.exists() && file.length() > 0L) {
                return java.io.FileInputStream(file)
            }
        }
        val directFile = File(clean)
        if (!clean.startsWith("content://") && directFile.exists() && directFile.length() > 0L) {
            return java.io.FileInputStream(directFile)
        }
        val uri = if (clean.startsWith("content://") || clean.startsWith("file://")) {
            Uri.parse(clean)
        } else {
            Uri.fromFile(directFile)
        }
        return context.contentResolver.openInputStream(uri)
    }

    /**
     * Mengonversi URI foto lokal (file:// atau content://) menjadi string Base64 yang ringkas.
     * Gambar diperkecil secara proporsional agar hemat kuota dan muat disimpan di cloud/database.
     */
    fun encodeUriToBase64(context: Context, uriOrPath: String, maxDimension: Int = 480, quality: Int = 45): String {
        if (uriOrPath.isBlank()) return ""
        if (uriOrPath.startsWith("data:image/") || uriOrPath.startsWith("base64,")) return uriOrPath

        return try {
            openImageInputStream(context, uriOrPath)?.use { input ->
                val bitmap = BitmapFactory.decodeStream(input)
                if (bitmap != null) {
                    val scaled = scaleBitmapDown(bitmap, maxDimension)
                    val baos = java.io.ByteArrayOutputStream()
                    scaled.compress(Bitmap.CompressFormat.JPEG, quality, baos)
                    val bytes = baos.toByteArray()
                    if (scaled != bitmap) {
                        bitmap.recycle()
                    }
                    val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                    "data:image/jpeg;base64,$base64"
                } else ""
            } ?: ""
        } catch (e: Exception) {
            Log.e(TAG, "Gagal encodeUriToBase64: ${e.message}", e)
            ""
        }
    }

    /**
     * Mengekstrak URL murni dari rumus =IMAGE("...") jika ada.
     * Contoh: =IMAGE("https://drive.google.com/uc?id=123") -> https://drive.google.com/uc?id=123
     */
    fun extractUrlFromFormula(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank() ||
            trimmed == "{}" ||
            trimmed.equals("null", ignoreCase = true) ||
            trimmed.contains("CellImage", ignoreCase = true) ||
            trimmed.contains("valueType", ignoreCase = true) ||
            (trimmed.startsWith("{") && trimmed.endsWith("}"))
        ) {
            return ""
        }
        if (trimmed.startsWith("=IMAGE(\"", ignoreCase = true) && trimmed.endsWith("\")")) {
            return trimmed.substring(8, trimmed.length - 2).trim()
        }
        val regex = Regex("""=IMAGE\(\s*["']([^"']+)["']\s*[,)]""", RegexOption.IGNORE_CASE)
        val match = regex.find(trimmed)
        if (match != null) {
            return match.groupValues[1].trim()
        }
        return trimmed
    }

    fun isPublicHttpUrl(raw: String): Boolean {
        val clean = extractUrlFromFormula(raw)
        return clean.startsWith("http://") || clean.startsWith("https://")
    }

    fun isLocalUri(raw: String): Boolean {
        val clean = raw.trim()
        return clean.startsWith("file://") || clean.startsWith("content://")
    }

    /**
     * Membungkus URL publik ke dalam format rumus Google Sheets =IMAGE("URL").
     * Tidak pernah membungkus path lokal (file:// atau content://).
     */
    fun formatAsImageFormula(url: String): String {
        val cleanUrl = extractUrlFromFormula(url)
        if (cleanUrl.isBlank()) return ""
        return if (cleanUrl.startsWith("http://") || cleanUrl.startsWith("https://")) {
            "=IMAGE(\"$cleanUrl\")"
        } else if (isLocalUri(cleanUrl)) {
            "" // Jangan pernah kirim path lokal HP ke Google Sheets!
        } else {
            cleanUrl
        }
    }

    /**
     * Mengambil byte array gambar terkompresi dari URI atau path lokal untuk diunggah ke cloud.
     */
    fun compressAndGetImageBytes(context: Context, uriOrPath: String, maxDimension: Int = 800, quality: Int = 65): ByteArray? {
        if (uriOrPath.isBlank()) return null
        return try {
            openImageInputStream(context, uriOrPath)?.use { input ->
                val bitmap = BitmapFactory.decodeStream(input) ?: return null
                val scaled = scaleBitmapDown(bitmap, maxDimension)
                val baos = java.io.ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, baos)
                if (scaled != bitmap) {
                    bitmap.recycle()
                }
                baos.toByteArray()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gagal compressAndGetImageBytes: ${e.message}", e)
            null
        }
    }

    fun normalizeGoogleDriveUrl(url: String): String {
        val clean = url.trim()
        if (clean.isBlank()) return ""
        val fileRegex = Regex("""https?://drive\.google\.com/file/d/([a-zA-Z0-9_-]+)(?:/.*)?""")
        fileRegex.find(clean)?.let { match ->
            val fileId = match.groupValues[1]
            return "https://drive.google.com/uc?export=view&id=$fileId"
        }
        val openRegex = Regex("""https?://drive\.google\.com/open\?(?:.*&)?id=([a-zA-Z0-9_-]+)(?:&.*)?""")
        openRegex.find(clean)?.let { match ->
            val fileId = match.groupValues[1]
            return "https://drive.google.com/uc?export=view&id=$fileId"
        }
        return clean
    }

    /**
     * Mengonversi string URI/URL/Base64/=IMAGE() menjadi model yang valid untuk Coil AsyncImage.
     * Mendukung format =IMAGE("..."), URL online (HTTP/HTTPS & Google Drive), Base64, dan file lokal yang ada.
     */
    fun getDisplayModel(photoUrl: String): Any {
        val rawClean = photoUrl.trim()
        if (rawClean.isBlank()) return ""

        // 1. Ekstrak URL jika berupa rumus =IMAGE("...")
        val clean = extractUrlFromFormula(rawClean)
        if (clean.isBlank()) return ""

        // 2. Base64
        if (clean.startsWith("data:image/") || clean.startsWith("base64,")) {
            val base64Part = if (clean.contains("base64,")) clean.substringAfter("base64,") else clean
            return try {
                android.util.Base64.decode(base64Part, android.util.Base64.DEFAULT)
            } catch (e: Exception) {
                Log.w(TAG, "Gagal decode base64: ${e.message}")
                ""
            }
        }

        // 3. Online URL (Google Drive, Cloud Storage, dsb.)
        if (clean.startsWith("http://") || clean.startsWith("https://")) {
            return normalizeGoogleDriveUrl(clean)
        }

        // 4. File lokal di perangkat ini
        if (clean.startsWith("file://")) {
            val path = try {
                java.net.URI(clean).path ?: clean.removePrefix("file://")
            } catch (_: Exception) {
                clean.removePrefix("file://")
            }
            return if (path.isNotBlank() && File(path).exists()) {
                clean
            } else {
                "" // File lokal dari HP lain yang tidak ada di perangkat ini
            }
        }

        // 5. Content URI lokal
        if (clean.startsWith("content://")) {
            return clean
        }

        return ""
    }

    private fun scaleBitmapDown(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDimension && height <= maxDimension) return bitmap

        val ratio = width.toFloat() / height.toFloat()
        val targetWidth: Int
        val targetHeight: Int
        if (width > height) {
            targetWidth = maxDimension
            targetHeight = (maxDimension / ratio).toInt().coerceAtLeast(1)
        } else {
            targetHeight = maxDimension
            targetWidth = (maxDimension * ratio).toInt().coerceAtLeast(1)
        }
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }

    private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.preScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.preScale(-1f, 1f)
            }
            else -> return bitmap
        }
        return try {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (_: Exception) {
            bitmap
        }
    }
}
