package com.NgeFilm

import com.lagradost.cloudstream3.* // Wajib untuk akses newExtractorLink dan INFER_TYPE
import com.lagradost.cloudstream3.utils.*
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class RpmLive : ExtractorApi() {
    override val name = "RpmLive"
    override val mainUrl = "https://rpmlive.online"
    override val requiresReferer = true

    // Byte Array
    private val key = byteArrayOf(107, 105, 101, 109, 116, 105, 101, 110, 109, 117, 97, 57, 49, 49, 99, 97)
    private val iv = byteArrayOf(49, 50, 51, 52, 53, 54, 55, 56, 57, 48, 111, 105, 117, 121, 116, 114)

    suspend fun getStreamUrl(url: String, callback: (ExtractorLink) -> Unit) {
        val response = app.get(url, referer = "https://ngefilm21.pw/").text
        
        // Ambil data terenkripsi
        val encryptedData = Regex("""makePlayer\("([^"]+)""").find(response)?.groupValues?.get(1)

        encryptedData?.let {
            val decrypted = decryptAes(it)
            
            // Ambil URL dari JSON
            val videoUrl = Regex("""file":"([^"]+)""").find(decrypted)?.groupValues?.get(1)
            
            videoUrl?.let { link ->
                val cleanedLink = link.replace("\\", "")
                
                // Gunakan newExtractorLink dengan benar
                // INFER_TYPE akan otomatis mendeteksi jika link adalah .m3u8
                callback.invoke(
                    newExtractorLink(this.name, this.name, cleanedLink, INFER_TYPE) {
                        this.referer = url
                        this.quality = Qualities.P720.value
                    }
                )
            }
        }
    }

    private fun decryptAes(encryptedB64: String): String {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val keySpec = SecretKeySpec(key, "AES")
        val ivSpec = IvParameterSpec(iv)
        
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
        
        // base64DecodeArray berasal dari com.lagradost.cloudstream3.*
        val decryptedBytes = cipher.doFinal(base64DecodeArray(encryptedB64))
        return String(decryptedBytes)
    }
}
