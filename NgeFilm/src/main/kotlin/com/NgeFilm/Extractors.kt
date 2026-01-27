package com.NgeFilm

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.app
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class RpmLive : ExtractorApi() {
    override val name = "RpmLive"
    override val mainUrl = "https://playerngefilm21.rpmlive.online"
    override val requiresReferer = true

    private val key = byteArrayOf(107, 105, 101, 109, 116, 105, 101, 110, 109, 117, 97, 57, 49, 49, 99, 97)
    private val iv = byteArrayOf(49, 50, 51, 52, 53, 54, 55, 56, 57, 48, 111, 105, 117, 121, 116, 114)

    // Header Persis CURL yang Berhasil
    private val apiHeaders = mapOf(
        "Authority" to "playerngefilm21.rpmlive.online",
        "Accept" to "*/*",
        "Accept-Language" to "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7",
        "Origin" to "https://playerngefilm21.rpmlive.online",
        "Referer" to "https://playerngefilm21.rpmlive.online/",
        "Sec-Fetch-Dest" to "empty",
        "Sec-Fetch-Mode" to "cors",
        "Sec-Fetch-Site" to "same-origin",
        "User-Agent" to "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36"
    )

    private fun String.decodeHex(): ByteArray {
        check(length % 2 == 0) { "Must have an even length" }
        return chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }

    suspend fun getStreamUrl(url: String, callback: (ExtractorLink) -> Unit) {
        try {
            var id = Regex("id=([^&]+)").find(url)?.groupValues?.get(1)
            if (id == null) {
                id = url.trimEnd('/').substringAfterLast('/')
            }

            if (id.isNullOrBlank()) return

            // API Video (Sumber M3U8)
            val apiUrl = "$mainUrl/api/v1/video?id=$id&w=100%25&h=100%25&r=new31.ngefilm.site"
            
            // Request ke API untuk dapat HEX
            val response = app.get(apiUrl, headers = apiHeaders).text.trim()

            if (response.isNotEmpty()) {
                val decrypted = decryptAes(response)
                
                // Ambil link M3U8 dari JSON hasil dekripsi
                val videoUrl = Regex("""file"\s*:\s*"([^"]+)""").find(decrypted)?.groupValues?.get(1)
                    ?: Regex("""file":"([^"]+)""").find(decrypted)?.groupValues?.get(1)

                videoUrl?.let { link ->
                    val cleanedLink = link.replace("\\", "")
                    
                    callback.invoke(
                        newExtractorLink(this.name, this.name, cleanedLink, INFER_TYPE) {
                            // Referer ini penting untuk request m3u8 pertama kali
                            this.referer = "https://playerngefilm21.rpmlive.online/"
                            this.headers = apiHeaders
                            this.quality = Qualities.P720.value
                        }
                    )
                }
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
            
            // Bersihkan spasi/newline (Jaga-jaga)
            val cleanHex = encryptedHex.replace(Regex("\\s"), "")
            
            val encryptedBytes = cleanHex.decodeHex()
            val decryptedBytes = cipher.doFinal(encryptedBytes)
            
            String(decryptedBytes)
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }
}
