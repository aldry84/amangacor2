package com.NgeFilm

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class RpmLive : ExtractorApi() {
    override val name = "RpmLive"
    override val mainUrl = "https://playerngefilm21.rpmlive.online"
    
    // PERBAIKAN: Typo 'Referrer' -> 'Referer' (satu r di tengah)
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val videoId = url.substringAfterLast("/").substringBefore("?")
        val apiUrl = "$mainUrl/api/v1/video?id=$videoId"
        val headers = mapOf(
            "Referer" to "$mainUrl/",
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Accept" to "*/*",
            "Origin" to mainUrl
        )

        try {
            val response = app.get(apiUrl, headers = headers).text
            if (response.isBlank()) return

            val keyBytes = byteArrayOf(107, 105, 101, 109, 116, 105, 101, 110, 109, 117, 97, 57, 49, 49, 99, 97)
            val ivBytes = byteArrayOf(49, 50, 51, 52, 53, 54, 55, 56, 57, 48, 111, 105, 117, 121, 116, 114)

            val encryptedBytes = hexToBytes(response)
            val decryptedJson = decryptAes(encryptedBytes, keyBytes, ivBytes)

            val mapper = jacksonObjectMapper()
            val jsonNode = mapper.readTree(decryptedJson)
            
            jsonNode["source"]?.forEach { source ->
                val m3u8Url = source["file"]?.asText()
                val label = source["label"]?.asText() ?: "Auto"
                
                if (m3u8Url != null && m3u8Url.contains(".m3u8")) {
                    callback.invoke(
                        ExtractorLink(
                            source = name,
                            name = "$name $label",
                            url = m3u8Url,
                            referer = mainUrl,
                            quality = Qualities.Unknown.value,
                            type = INFER_TYPE
                        )
                    )
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun hexToBytes(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(hex[i], 16) shl 4) + Character.digit(hex[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    private fun decryptAes(data: ByteArray, key: ByteArray, iv: ByteArray): String {
        val secretKey = SecretKeySpec(key, "AES")
        val ivSpec = IvParameterSpec(iv)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)
        return String(cipher.doFinal(data))
    }
}
