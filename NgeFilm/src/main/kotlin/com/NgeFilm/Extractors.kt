package com.NgeFilm

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.app
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class RpmLive : ExtractorApi() {
    override val name = "RpmLive"
    override val mainUrl = "https://rpmlive.online"
    override val requiresReferer = true

    private val key = byteArrayOf(107, 105, 101, 109, 116, 105, 101, 110, 109, 117, 97, 57, 49, 49, 99, 97)
    private val iv = byteArrayOf(49, 50, 51, 52, 53, 54, 55, 56, 57, 48, 111, 105, 117, 121, 116, 114)

    suspend fun getStreamUrl(url: String, callback: (ExtractorLink) -> Unit) {
        try {
            val response = app.get(url, referer = "https://ngefilm21.pw/").text
            
            // Regex diperbaiki: Menangani kemungkinan spasi atau kutip tunggal/ganda
            // Mencari: makePlayer("..." atau makePlayer('...'
            val encryptedData = Regex("""makePlayer\(\s*["']([^"']+)["']""").find(response)?.groupValues?.get(1)

            encryptedData?.let {
                val decrypted = decryptAes(it)
                val videoUrl = Regex("""file":"([^"]+)""").find(decrypted)?.groupValues?.get(1)
                
                videoUrl?.let { link ->
                    val cleanedLink = link.replace("\\", "")
                    callback.invoke(
                        newExtractorLink(this.name, this.name, cleanedLink, INFER_TYPE) {
                            this.referer = url
                            this.quality = Qualities.P720.value
                        }
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun decryptAes(encryptedB64: String): String {
        return try {
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            val keySpec = SecretKeySpec(key, "AES")
            val ivSpec = IvParameterSpec(iv)
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
            val decryptedBytes = cipher.doFinal(base64DecodeArray(encryptedB64))
            String(decryptedBytes)
        } catch (e: Exception) {
            ""
        }
    }
}
