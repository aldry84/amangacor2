package com.LayarKacaProvider

import android.util.Base64
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.fasterxml.jackson.annotation.JsonProperty
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

// ============================================================================
// HYDRAX / ABYSSCDN EXTRACTOR (FIXED PARSING)
// ============================================================================
open class HydraxExtractor : ExtractorApi() {
    override var name = "Hydrax"
    override var mainUrl = "https://playeriframe.sbs"
    override val requiresReferer = true

    data class AbyssData(
        @JsonProperty("slug") val slug: String? = null,
        @JsonProperty("md5_id") val md5Id: Any? = null,
        @JsonProperty("user_id") val userId: Any? = null,
        @JsonProperty("media") val media: String? = null
    )

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val safeReferer = referer ?: "https://tv8.lk21official.cc/"
            val response = app.get(url, referer = safeReferer)
            val html = response.text
            val finalUrl = response.url

            // --- PERBAIKAN: MENGGUNAKAN INDEXOF (BUKAN REGEX) ---
            // Regex sering gagal pada string base64 yang sangat panjang.
            // Kita cari manual posisi 'const datas = "'
            val marker = "const datas = \""
            val startIndex = html.indexOf(marker)
            
            if (startIndex == -1) {
                // Fallback: coba cari tanpa spasi jika minified
                val markerMin = "const datas=\""
                val startMin = html.indexOf(markerMin)
                if (startMin == -1) {
                    System.err.println("HydraxExtractor: Marker variabel 'datas' tidak ditemukan.")
                    return
                }
                parseAndDecrypt(html, startMin + markerMin.length, finalUrl, callback)
            } else {
                parseAndDecrypt(html, startIndex + marker.length, finalUrl, callback)
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun parseAndDecrypt(
        html: String, 
        startIdx: Int, 
        referer: String, 
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            // Cari ujung kutipan penutup "
            val endIdx = html.indexOf("\"", startIdx)
            if (endIdx == -1) return

            // Ambil string mentah (masih mengandung escape seperti \u00xx atau \)
            val rawString = html.substring(startIdx, endIdx)

            // Bersihkan string dari format JS Escape menjadi string normal
            val unescapedString = unescapeJsString(rawString)

            // Decode Base64
            val decodedJson = String(Base64.decode(unescapedString, Base64.DEFAULT), Charsets.ISO_8859_1)
            
            val data = tryParseJson<AbyssData>(decodedJson) ?: return
            val media = data.media ?: return
            
            val sSlug = data.slug.toString()
            val sMd5Id = data.md5Id.toString()
            val sUserId = data.userId.toString()

            // Key Generation (Logic: user_id:slug:md5_id)
            val keyString = "$sUserId:$sSlug:$sMd5Id"
            val md5HashStr = md5(keyString)
            
            val keyBytes = md5HashStr.toByteArray(Charsets.UTF_8)
            val ivBytes = keyBytes.sliceArray(0 until 16)

            // Dekripsi AES-CTR
            val encryptedBytes = unescapeMediaToBytes(media)
            val decryptedUrl = decryptAesCtr(encryptedBytes, keyBytes, ivBytes)

            if (decryptedUrl.startsWith("http")) {
                callback.invoke(
                    newExtractorLink(
                        source = name,
                        name = "$name VIP",
                        url = decryptedUrl,
                        type = ExtractorLinkType.VIDEO
                    ) {
                        this.referer = referer
                        this.quality = Qualities.Unknown.value
                    }
                )
            }
        } catch (e: Exception) {
            System.err.println("HydraxExtractor: Gagal saat parsing/decrypt.")
            e.printStackTrace()
        }
    }

    // --- HELPER UNTUK MEMBERSIHKAN JS STRING ---
    private fun unescapeJsString(input: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < input.length) {
            val c = input[i]
            if (c == '\\' && i + 1 < input.length) {
                val next = input[i+1]
                if (next == 'u' && i + 5 < input.length) {
                    // Handle unicode escape \uXXXX
                    try {
                        val hex = input.substring(i + 2, i + 6)
                        sb.append(hex.toInt(16).toChar())
                        i += 6
                    } catch(e: Exception) {
                        sb.append(c)
                        i++
                    }
                } else if (next == 'x' && i + 3 < input.length) {
                    // Handle hex escape \xXX
                    try {
                        val hex = input.substring(i + 2, i + 4)
                        sb.append(hex.toInt(16).toChar())
                        i += 4
                    } catch(e: Exception) {
                        sb.append(c)
                        i++
                    }
                } else {
                    // Escape biasa (misal \" atau \n atau sekedar backslash pemisah)
                    // Kita ambil karakter setelah backslash
                    sb.append(next)
                    i += 2
                }
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }

    private fun md5(input: String): String {
        return MessageDigest.getInstance("MD5")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    private fun decryptAesCtr(encrypted: ByteArray, key: ByteArray, iv: ByteArray): String {
        return try {
            val secretKey = SecretKeySpec(key, "AES") 
            val ivSpec = IvParameterSpec(iv)
            val cipher = Cipher.getInstance("AES/CTR/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)
            
            val decrypted = cipher.doFinal(encrypted)
            String(decrypted).filter { it.code in 32..126 }
        } catch (e: Exception) {
            ""
        }
    }

    private fun unescapeMediaToBytes(media: String): ByteArray {
        val bytes = mutableListOf<Byte>()
        var i = 0
        while (i < media.length) {
            if (media[i] == '\\' && i + 1 < media.length && media[i+1] == 'u') {
                try {
                    val hex = media.substring(i + 2, i + 6)
                    bytes.add(hex.toInt(16).toByte())
                    i += 6
                } catch (e: Exception) {
                    bytes.add(media[i].code.toByte())
                    i++
                }
            } else {
                bytes.add(media[i].code.toByte())
                i++
            }
        }
        return bytes.toByteArray()
    }
}
