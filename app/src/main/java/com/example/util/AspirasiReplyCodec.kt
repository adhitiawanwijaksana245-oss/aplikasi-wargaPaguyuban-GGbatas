package com.example.util

import java.net.URLDecoder
import java.net.URLEncoder

data class ParsedAspirasiReplyMeta(
    val cleanTanggapan: String,
    val penanggapNama: String = "",
    val penanggapPeran: String = "",
    val waktuTanggapan: String = ""
)

/**
 * Codec backwards-compatible untuk menyisipkan metadata balasan (nama penanggap, peran, waktu)
 * ke kolom "Tanggapan pengurus" hanya ketika Google Apps Script Web App di server masih versi lama
 * (belum memiliki kolom Penanggap_Nama, Penanggap_Peran, Waktu_Tanggapan).
 */
object AspirasiReplyCodec {
    private val MARKER_REGEX = Regex("\\s*\\[\\[META_BALASAN:([^\\]]+)\\]\\]\\s*")

    fun encodeIntoTanggapan(
        rawTanggapan: String,
        penanggapNama: String,
        penanggapPeran: String,
        waktuTanggapan: String
    ): String {
        val clean = stripMarker(rawTanggapan)
        if (clean.isBlank()) return ""
        val cleanNama = penanggapNama.trim()
        val cleanPeran = penanggapPeran.trim()
        val cleanWaktu = waktuTanggapan.trim()
        if (cleanNama.isBlank() && cleanPeran.isBlank() && cleanWaktu.isBlank()) {
            return clean
        }
        val params = listOfNotNull(
            cleanNama.takeIf { it.isNotBlank() }?.let { "n=${URLEncoder.encode(it, "UTF-8")}" },
            cleanPeran.takeIf { it.isNotBlank() }?.let { "r=${URLEncoder.encode(it, "UTF-8")}" },
            cleanWaktu.takeIf { it.isNotBlank() }?.let { "t=${URLEncoder.encode(it, "UTF-8")}" }
        ).joinToString("&")
        if (params.isBlank()) return clean
        return "$clean\n[[META_BALASAN:$params]]"
    }

    fun stripMarker(rawTanggapan: String): String {
        if (rawTanggapan.isBlank()) return ""
        return MARKER_REGEX.replace(rawTanggapan, "\n").trim()
    }

    fun parseAndStrip(rawTanggapan: String): ParsedAspirasiReplyMeta {
        if (rawTanggapan.isBlank()) {
            return ParsedAspirasiReplyMeta(cleanTanggapan = "")
        }
        val match = MARKER_REGEX.find(rawTanggapan)
        val clean = stripMarker(rawTanggapan)
        if (match == null) {
            return ParsedAspirasiReplyMeta(cleanTanggapan = clean)
        }
        val payload = match.groupValues[1]
        var nama = ""
        var peran = ""
        var waktu = ""
        payload.split("&").forEach { pair ->
            val idx = pair.indexOf('=')
            if (idx > 0) {
                val key = pair.substring(0, idx)
                val value = try {
                    URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
                } catch (_: Exception) {
                    pair.substring(idx + 1)
                }
                when (key) {
                    "n" -> nama = value
                    "r" -> peran = value
                    "t" -> waktu = value
                }
            }
        }
        return ParsedAspirasiReplyMeta(
            cleanTanggapan = clean,
            penanggapNama = nama,
            penanggapPeran = peran,
            waktuTanggapan = waktu
        )
    }
}
