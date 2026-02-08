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
// HYDRAX / ABYSSCDN EXTRACTOR (UPDATED STANDARD)
// ============================================================================
open class HydraxExtractor : ExtractorApi() {
    override var name = "Hydrax"
    override var mainUrl = "https://playeriframe.sbs"
    override val requiresReferer = true

    // Model data untuk parsing JSON internal
    data class AbyssData(
        @JsonProperty("slug") val slug: String? = null,
        @JsonProperty("md5_id") val md5Id: Any? = null, // Bisa berupa Int atau String
        @JsonProperty("user_id") val userId: Any? = null,
        @JsonProperty("media") val media: String? = null
    )

    // Menggunakan override versi baru dengan Callback (lebih cepat & responsif)
    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            // 1. Request Halaman Player
            // Default referer ke main URL jika null, untuk menghindari blokir
            val safeReferer = referer ?: "https://tv8.lk21official.cc/"
            val response = app.get(url, referer = safeReferer)
            val html = response.text
            val finalUrl = response.url

            // 2. Ekstrak variabel 'datas' (Base64 JSON)
            // Regex mencari: const datas = "..."
            val encodedData = Regex("""const datas\s*=\s*"([^"]+)"""").find(html)?.groupValues?.get(1) 
            
            if (encodedData == null) {
                // Log jika gagal menemukan data (berguna untuk debugging)
                System.err.println("HydraxExtractor: Gagal menemukan variabel 'datas' di HTML.")
                return
            }
            
            // 3. Bersihkan & Decode Base64
            // Menghapus unicode escape sequence yang merusak decoding
            val cleanB64 = encodedData.replace(Regex("""\\u[0-9a-fA-F]{4}"""), "")
            val decodedJson = try {
                String(Base64.decode(cleanB64, Base64.DEFAULT), Charsets.ISO_8859_1)
            } catch (e: Exception) {
                System.err.println("HydraxExtractor: Gagal decode Base64.")
                return
            }
            
            // 4. Parsing JSON ke Object
            val data = tryParseJson<AbyssData>(decodedJson)
            if (data?.media == null) return
            
            // Konversi ID ke string aman
            val sSlug = data.slug.toString()
            val sMd5Id = data.md5Id.toString()
            val sUserId = data.userId.toString()

            // 5. GENERATE KEY (Logic Reverse Engineering)
            // Rumus: user_id + ":" + slug + ":" + md5_id
            val keyString = "$sUserId:$sSlug:$sMd5Id"
            val md5HashStr = md5(keyString) // 32 chars Hex String
            
            // Key dan IV diambil dari representasi byte string hex tersebut
            val keyBytes = md5HashStr.toByteArray(Charsets.UTF_8)
            val ivBytes = keyBytes.sliceArray(0 until 16) // 16 byte pertama untuk IV

            // 6. DEKRIPSI (AES-CTR)
            val encryptedBytes = unescapeMediaToBytes(data.media)
            val decryptedUrl = decryptAesCtr(encryptedBytes, keyBytes, ivBytes)

            // 7. Kirim Hasil ke Cloudstream (Callback)
            if (decryptedUrl.startsWith("http")) {
                callback.invoke(
                    newExtractorLink(
                        source = name,
                        name = "$name VIP",
                        url = decryptedUrl,
                        type = ExtractorLinkType.VIDEO // Asumsikan video biasa (mp4), auto-detect jika m3u8
                    ) {
                        this.referer = finalUrl
                        this.quality = Qualities.Unknown.value // Biarkan player menentukan/menampilkan
                    }
                )
            }

        } catch (e: Exception) {
            // Log error agar tidak crash diam-diam
            e.printStackTrace()
        }
    }

    // --- CRYPTO HELPERS ---

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
            // Filter hasil dekripsi agar hanya karakter URL valid yang tersisa
            String(decrypted).filter { it.code in 32..126 }
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    private fun unescapeMediaToBytes(media: String): ByteArray {
        // Mengubah string custom escape (misal \u00ff) menjadi ByteArray asli
        val bytes = mutableListOf<Byte>()
        var i = 0
        while (i < media.length) {
            if (media[i] == '\\' && i + 1 < media.length && media[i+1] == 'u') {
                try {
                    val hex = media.substring(i + 2, i + 6)
                    bytes.add(hex.toInt(16).toByte())
                    i += 6
                } catch (e: Exception) {
                    // Fallback jika format unicode salah
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
