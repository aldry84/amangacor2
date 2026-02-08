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
// HYDRAX / ABYSSCDN EXTRACTOR (DECRYPTOR TUNTAS)
// ============================================================================
open class HydraxExtractor : ExtractorApi() {
    override var name = "Hydrax"
    override var mainUrl = "https://playeriframe.sbs"
    override val requiresReferer = true

    // Model untuk membedah JSON 'datas' yang ada di HTML
    data class AbyssData(
        @JsonProperty("slug") val slug: String? = null,
        @JsonProperty("md5_id") val md5Id: Any? = null, // md5_id kadang int kadang string
        @JsonProperty("user_id") val userId: Any? = null,
        @JsonProperty("media") val media: String? = null
    )

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val sources = mutableListOf<ExtractorLink>()

        try {
            // 1. Ambil HTML dari Player (playeriframe -> short.icu -> abysscdn)
            val response = app.get(url, referer = referer ?: "https://tv8.lk21official.cc/")
            val html = response.text
            val finalUrl = response.url

            // 2. Ekstrak variabel 'datas' (Base64 JSON)
            val encodedData = Regex("""const datas\s*=\s*"([^"]+)"""").find(html)?.groupValues?.get(1) ?: return null
            
            // Bersihkan Base64 dari unicode escape \u00xx
            val cleanB64 = encodedData.replace(Regex("""\\u[0-9a-fA-F]{4}"""), "")
            val decodedJson = String(Base64.decode(cleanB64, Base64.DEFAULT), Charsets.ISO_8859_1)
            
            // 3. Parsing Metadata Film
            val data = tryParseJson<AbyssData>(decodedJson) ?: return null
            val media = data.media ?: return null
            
            // Konversi ID ke string agar aman digabung
            val sSlug = data.slug.toString()
            val sMd5Id = data.md5Id.toString()
            val sUserId = data.userId.toString()

            // 4. GENERATE KUNCI (Rumus: user_id:slug:md5_id)
            // Sesuai temuan di Class _0x5e685c tadi
            val keyString = "$sUserId:$sSlug:$sMd5Id"
            val md5HashStr = md5(keyString) // Hasil MD5 dalam format Hex String (32 char)
            
            // Kunci AES & Counter diambil dari byte string MD5 tersebut
            val keyBytes = md5HashStr.toByteArray(Charsets.UTF_8)
            val ivBytes = keyBytes.sliceArray(0 until 16) // Ambil 16 byte pertama (slice 0, 0x10)

            // 5. DEKRIPSI (AES-CTR)
            val encryptedBytes = unescapeMediaToBytes(media)
            val decryptedUrl = decryptAesCtr(encryptedBytes, keyBytes, ivBytes)

            if (decryptedUrl.contains("http")) {
                sources.add(
                    ExtractorLink(
                        source = name,
                        name = "$name VIP",
                        url = decryptedUrl,
                        referer = finalUrl,
                        quality = Qualities.Unknown.value,
                        type = ExtractorLinkType.VIDEO
                    )
                )
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }

        return sources
    }

    // --- HELPER DECRYPTOR ---

    private fun md5(input: String): String {
        return MessageDigest.getInstance("MD5")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    private fun decryptAesCtr(encrypted: ByteArray, key: ByteArray, iv: ByteArray): String {
        return try {
            // Web Crypto AES-CTR biasanya menggunakan 128-bit atau 256-bit key
            // Karena md5HashStr ada 32 byte (256-bit), kita pakai 32 byte tersebut sebagai key
            val secretKey = SecretKeySpec(key, "AES") 
            val ivSpec = IvParameterSpec(iv)
            val cipher = Cipher.getInstance("AES/CTR/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)
            
            val decrypted = cipher.doFinal(encrypted)
            // Filter hanya karakter yang valid untuk URL
            String(decrypted).filter { it.toInt() in 32..126 }
        } catch (e: Exception) {
            ""
        }
    }

    private fun unescapeMediaToBytes(media: String): ByteArray {
        // Mengubah string yang mengandung \u00xx menjadi Byte Array asli
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
