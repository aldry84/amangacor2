package com.NgeFilm

import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.app
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import android.util.Base64

class RpmLive : ExtractorApi() {
    override val name = "RpmLive"
    override val mainUrl = "https://rpmlive.online"
    override val requiresReferer = true

    // Data Enkripsi dari inspeksi elemen kamu
    private val keyBytes = byteArrayOf(107, 105, 101, 109, 116, 105, 101, 110, 109, 117, 97, 57, 49, 49, 99, 97)
    private val ivBytes = byteArrayOf(49, 50, 51, 52, 53, 54, 55, 56, 57, 48, 111, 105, 117, 121, 116, 114)

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val response = app.get(url).text
        
        // Target Regex: makePlayer("([^"]+)
        val regex = """makePlayer\("([^"]+)""".toRegex()
        val match = regex.find(response)
        
        if (match != null) {
            val encryptedData = match.groupValues[1]
            try {
                val decryptedUrl = decrypt(encryptedData)
                
                // Biasanya hasil dekripsi adalah URL .m3u8 langsung
                callback.invoke(
                    ExtractorLink(
                        name,
                        name,
                        decryptedUrl,
                        referer ?: mainUrl,
                        Qualities.Unknown.value,
                        type = INFER_TYPE // Biarkan CS mendeteksi HLS/MP4
                    )
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun decrypt(encrypted: String): String {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val secretKeySpec = SecretKeySpec(keyBytes, "AES")
        val ivParameterSpec = IvParameterSpec(ivBytes)
        
        cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivParameterSpec)
        
        val decodedBytes = Base64.decode(encrypted, Base64.DEFAULT)
        val decryptedBytes = cipher.doFinal(decodedBytes)
        
        return String(decryptedBytes)
    }
}
