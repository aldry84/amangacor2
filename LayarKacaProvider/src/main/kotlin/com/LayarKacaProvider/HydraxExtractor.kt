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

open class HydraxExtractor : ExtractorApi() {
    override var name = "Hydrax"
    // URL ini harus cocok dengan yang ada di iframe LayarKacaProvider
    override var mainUrl = "https://playeriframe.sbs"
    override val requiresReferer = true

    data class AbyssData(
        @JsonProperty("slug") val slug: String? = null,
        @JsonProperty("md5_id") val md5Id: Any? = null,
        @JsonProperty("user_id") val userId: Any? = null,
        @JsonProperty("media") val media: String? = null
    )

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val sources = mutableListOf<ExtractorLink>()

        try {
            // Gunakan referer dari LK21 agar tidak kena blokir
            val response = app.get(url, referer = referer ?: "https://tv8.lk21official.cc/")
            val html = response.text
            val finalUrl = response.url

            val encodedData = Regex("""const datas\s*=\s*"([^"]+)"""").find(html)?.groupValues?.get(1) ?: return null
            
            // Bersihkan Base64 dari sampah unicode
            val cleanB64 = encodedData.replace(Regex("""\\u[0-9a-fA-F]{4}"""), "")
            val decodedJson = String(Base64.decode(cleanB64, Base64.DEFAULT), Charsets.ISO_8859_1)
            
            val data = tryParseJson<AbyssData>(decodedJson) ?: return null
            val mediaData = data.media ?: return null
            
            // Generate Kunci Final Boss: user_id:slug:md5_id
            val keyString = "${data.userId}:${data.slug}:${data.md5Id}"
            val md5Hash = MessageDigest.getInstance("MD5")
                .digest(keyString.toByteArray())
                .joinToString("") { "%02x".format(it) }
            
            val keyBytes = md5Hash.toByteArray(Charsets.UTF_8)
            val ivBytes = keyBytes.sliceArray(0 until 16)

            val encryptedBytes = unescapeMedia(mediaData)
            val decryptedUrl = decryptAesCtr(encryptedBytes, keyBytes, ivBytes)

            if (decryptedUrl.contains("http")) {
                // WAJIB: Gunakan newExtractorLink agar plugin tidak dianggap "Restricted"
                sources.add(
                    newExtractorLink(
                        source = name,
                        name = "$name VIP",
                        url = decryptedUrl,
                        type = ExtractorLinkType.VIDEO
                    ) {
                        this.referer = finalUrl
                        // Gunakan angka 400 (Unknown) atau 1080 secara langsung agar aman di build stable
                        this.quality = 400 
                    }
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return sources
    }

    private fun decryptAesCtr(encrypted: ByteArray, key: ByteArray, iv: ByteArray): String {
        return try {
            val secretKey = SecretKeySpec(key, "AES")
            val ivSpec = IvParameterSpec(iv)
            val cipher = Cipher.getInstance("AES/CTR/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)
            String(cipher.doFinal(encrypted)).filter { it.toInt() in 32..126 }
        } catch (e: Exception) { "" }
    }

    private fun unescapeMedia(media: String): ByteArray {
        val bytes = mutableListOf<Byte>()
        var i = 0
        while (i < media.length) {
            if (media[i] == '\\' && i + 1 < media.length && media[i+1] == 'u') {
                val hex = media.substring(i + 2, i + 6)
                bytes.add(hex.toInt(16).toByte())
                i += 6
            } else {
                bytes.add(media[i].toByte())
                i++
            }
        }
        return bytes.toByteArray()
    }
}
