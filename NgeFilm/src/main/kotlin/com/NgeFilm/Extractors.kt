package com.NgeFilm

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.app
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.net.URI

class RpmLive : ExtractorApi() {
    override val name = "RpmLive"
    override val mainUrl = "https://playerngefilm21.rpmlive.online"
    override val requiresReferer = true

    // Key: kiemtienmua911ca (dari byte array user)
    private val key = byteArrayOf(107, 105, 101, 109, 116, 105, 101, 110, 109, 117, 97, 57, 49, 49, 99, 97)
    // IV: 1234567890oiuytr (dari byte array user)
    private val iv = byteArrayOf(49, 50, 51, 52, 53, 54, 55, 56, 57, 48, 111, 105, 117, 121, 116, 114)

    // Fungsi Helper: Ubah string HEX (ba94..) menjadi Byte Array
    private fun String.decodeHex(): ByteArray {
        check(length % 2 == 0) { "Must have an even length" }
        return chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }

    suspend fun getStreamUrl(url: String, callback: (ExtractorLink) -> Unit) {
        try {
            // 1. Ambil ID dari URL (misal: .../embed?id=cm51is atau .../v/cm51is)
            var id = Regex("id=([^&]+)").find(url)?.groupValues?.get(1)
            if (id == null) {
                // Coba ambil dari segment terakhir URL jika tidak ada query param 'id'
                id = url.trimEnd('/').substringAfterLast('/')
            }

            if (id.isNullOrBlank()) return

            // 2. Tembak API Info
            val apiUrl = "$mainUrl/api/v1/info?id=$id"
            val jsonHeaders = mapOf(
                "Accept" to "*/*",
                "Origin" to mainUrl,
                "Referer" to "$mainUrl/",
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            )

            // Response ini adalah string HEX panjang (ba9409...)
            val encryptedHex = app.get(apiUrl, headers = jsonHeaders).text

            // 3. Dekripsi (Hex -> Bytes -> Decrypt)
            val decrypted = decryptAes(encryptedHex)
            
            // 4. Ambil Link M3U8 dari JSON hasil dekripsi
            // Pola: "file": "https://..."
            val videoUrl = Regex("""file"\s*:\s*"([^"]+)""").find(decrypted)?.groupValues?.get(1)
                ?: Regex("""file":"([^"]+)""").find(decrypted)?.groupValues?.get(1)

            videoUrl?.let { link ->
                val cleanedLink = link.replace("\\", "")
                callback.invoke(
                    newExtractorLink(this.name, this.name, cleanedLink, INFER_TYPE) {
                        this.referer = "$mainUrl/"
                        this.quality = Qualities.P720.value
                    }
                )
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun decryptAes(encryptedHex: String): String {
        return try {
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            val keySpec = SecretKeySpec(key, "AES")
            val ivSpec = IvParameterSpec(iv)
            
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
            
            // PENTING: Decode HEX dulu, baru decrypt. Jangan pakai base64DecodeArray!
            val encryptedBytes = encryptedHex.decodeHex()
            val decryptedBytes = cipher.doFinal(encryptedBytes)
            
            String(decryptedBytes)
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }
}
