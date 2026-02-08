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
// HYDRAX / ABYSSCDN EXTRACTOR (UNIVERSAL BYPASS FIX)
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
            // 1. LOGIC BYPASS UNIVERSAL
            // Menangkap semua pola: /iframe/[APAPUN]/[ID]
            // Contoh: /iframe/hydrax/123, /iframe/p2p/456, /iframe/cast/789
            var targetUrl = url
            val idMatch = Regex("""/iframe/[^/]+/([^/?]+)""").find(url)
            
            if (idMatch != null) {
                val id = idMatch.groupValues[1]
                targetUrl = "https://short.icu/$id"
                System.out.println("HydraxExtractor: Bypass Aktif! $url -> $targetUrl")
            }

            // 2. Request ke Pintu Belakang (Short.icu -> Abysscdn)
            val headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                "Referer" to "https://tv8.lk21official.cc/",
                "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8"
            )

            // Timeout diperpanjang untuk antisipasi redirect lambat
            val response = app.get(targetUrl, headers = headers, timeout = 60L)
            val html = response.text
            val finalUrl = response.url

            // 3. Cari Data (Pola: const datas = "eyJ...")
            val regex = Regex("""const\s+\w+\s*=\s*"(eyJ[^"]+)"""")
            val match = regex.find(html)
            
            if (match == null) {
                // Jika masih gagal, cetak URL akhir untuk debug
                System.err.println("HydraxExtractor: Data tidak ditemukan di $finalUrl (Asal: $url)")
                return
            }

            val rawString = match.groupValues[1]

            // 4. Bersihkan & Decode
            val unescapedString = unescapeJsString(rawString)
            val decodedJson = String(Base64.decode(unescapedString, Base64.DEFAULT), Charsets.ISO_8859_1)
            
            val data = tryParseJson<AbyssData>(decodedJson) ?: return
            val media = data.media ?: return
            
            val sSlug = data.slug.toString()
            val sMd5Id = data.md5Id.toString()
            val sUserId = data.userId.toString()

            // 5. Generate Key & Decrypt
            val keyString = "$sUserId:$sSlug:$sMd5Id"
            val md5HashStr = md5(keyString)
            val keyBytes = md5HashStr.toByteArray(Charsets.UTF_8)
            val ivBytes = keyBytes.sliceArray(0 until 16)

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
                        this.referer = finalUrl
                        this.quality = Qualities.Unknown.value
                    }
                )
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // --- HELPER FUNCTIONS ---

    private fun unescapeJsString(input: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < input.length) {
            val c = input[i]
            if (c == '\\' && i + 1 < input.length) {
                val next = input[i+1]
                if (next == 'u' && i + 5 < input.length) {
                    try {
                        val hex = input.substring(i + 2, i + 6)
                        sb.append(hex.toInt(16).toChar())
                        i += 6
                    } catch(e: Exception) {
                        sb.append(c)
                        i++
                    }
                } else if (next == 'x' && i + 3 < input.length) {
                    try {
                        val hex = input.substring(i + 2, i + 4)
                        sb.append(hex.toInt(16).toChar())
                        i += 4
                    } catch(e: Exception) {
                        sb.append(c)
                        i++
                    }
                } else {
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
