package com.LayarKacaProvider

import android.util.Base64
import com.lagradost.api.Log
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import java.net.URI
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

// ============================================================================
// 1. EMTURBOVID EXTRACTOR
// ============================================================================
open class EmturbovidExtractor : ExtractorApi() {
    override var name = "Emturbovid"
    override var mainUrl = "https://emturbovid.com"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val finalReferer = referer ?: "$mainUrl/"
        val response = app.get(url, referer = finalReferer)
        val playerScript = response.document.selectXpath("//script[contains(text(),'var urlPlay')]").html()
        val sources = mutableListOf<ExtractorLink>()
        
        if (playerScript.isNotBlank()) {
            val m3u8Url = playerScript.substringAfter("var urlPlay = '").substringBefore("'")
            val originUrl = try { URI(finalReferer).let { "${it.scheme}://${it.host}" } } catch (e: Exception) { "$mainUrl" }
            val headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                "Referer" to finalReferer,
                "Origin" to originUrl
            )
            sources.add(newExtractorLink(source = name, name = name, url = m3u8Url, type = ExtractorLinkType.M3U8) {
                this.referer = finalReferer
                this.quality = Qualities.Unknown.value
                this.headers = headers
            })
        }
        return sources
    }
}

// ============================================================================
// 2. P2P EXTRACTOR
// ============================================================================
open class P2PExtractor : ExtractorApi() {
    override var name = "P2P"
    override var mainUrl = "https://cloud.hownetwork.xyz"
    override val requiresReferer = false
    data class HownetworkResponse(val file: String?, val link: String?, val label: String?)

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val id = url.substringAfter("id=").substringBefore("&")
        val apiUrl = "$mainUrl/api2.php?id=$id"
        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Mobile Safari/537.36",
            "Referer" to url,
            "Origin" to mainUrl,
            "X-Requested-With" to "XMLHttpRequest"
        )
        val formBody = mapOf("r" to "https://playeriframe.sbs/", "d" to "cloud.hownetwork.xyz")
        val sources = mutableListOf<ExtractorLink>()
        try {
            val response = app.post(apiUrl, headers = headers, data = formBody).text
            val json = tryParseJson<HownetworkResponse>(response)
            val videoUrl = json?.file ?: json?.link
            if (!videoUrl.isNullOrBlank()) {
                sources.add(newExtractorLink(source = name, name = name, url = videoUrl, type = ExtractorLinkType.M3U8) {
                    this.referer = mainUrl
                    this.quality = Qualities.Unknown.value
                })
            }
        } catch (e: Exception) { e.printStackTrace() }
        return sources
    }
}

// ============================================================================
// 3. F16 EXTRACTOR (FULL DEBUG VERSION)
// ============================================================================
open class F16Extractor : ExtractorApi() {
    override var name = "F16"
    override var mainUrl = "https://f16px.com"
    override val requiresReferer = false

    data class F16Playback(val playback: PlaybackData?)
    data class PlaybackData(val iv: String?, val payload: String?, val key_parts: List<String>?)
    data class DecryptedSource(val file: String?, val label: String?, val type: String?)
    data class DecryptedResponse(val sources: List<DecryptedSource>?)

    private fun String.fixBase64(): String {
        var s = this
        while (s.length % 4 != 0) s += "="
        return s
    }

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        Log.d("F16-DEBUG", "Memulai Ekstraksi: $url") // LOG 1
        val sources = mutableListOf<ExtractorLink>()
        
        try {
            val videoId = url.substringAfter("/e/").substringBefore("?")
            val apiUrl = "$mainUrl/api/videos/$videoId/embed/playback"
            Log.d("F16-DEBUG", "API URL: $apiUrl") // LOG 2

            val headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36",
                "Referer" to "https://playeriframe.sbs/",
                "Origin" to "https://playeriframe.sbs",
                "Content-Type" to "application/json"
            )

            // Body Dummy
            val jsonPayload = mapOf(
                "fingerprint" to mapOf(
                    "token" to "dummy_bypass",
                    "viewer_id" to "7e847c23137449fbb73cbf6bb7f9bceb",
                    "device_id" to "daeba4e7719c4d4c91e53dd03849cf37",
                    "confidence" to 0.6
                )
            )
            
            Log.d("F16-DEBUG", "Mengirim Request API...") // LOG 3
            val responseText = app.post(apiUrl, headers = headers, json = jsonPayload).text
            Log.d("F16-DEBUG", "Respon API: ${responseText.take(100)}...") // LOG 4

            val json = tryParseJson<F16Playback>(responseText)
            val pb = json?.playback

            if (pb != null && pb.payload != null && pb.iv != null && !pb.key_parts.isNullOrEmpty()) {
                Log.d("F16-DEBUG", "Data Enkripsi Ditemukan. KeyParts: ${pb.key_parts.size}") // LOG 5
                
                // Decode Keys
                val part1 = Base64.decode(pb.key_parts[0].fixBase64(), Base64.URL_SAFE)
                val part2 = Base64.decode(pb.key_parts[1].fixBase64(), Base64.URL_SAFE)
                val combinedKey = part1 + part2 
                Log.d("F16-DEBUG", "Kunci Berhasil Digabung (Panjang: ${combinedKey.size})") // LOG 6

                // Decrypt
                val decryptedJson = decryptAesGcm(pb.payload, combinedKey, pb.iv)
                Log.d("F16-DEBUG", "Hasil Dekripsi: ${decryptedJson?.take(100)}...") // LOG 7

                if (decryptedJson != null) {
                    val result = tryParseJson<DecryptedResponse>(decryptedJson)
                    result?.sources?.forEach { source ->
                        if (!source.file.isNullOrBlank()) {
                            Log.d("F16-DEBUG", "Link Ditemukan: ${source.file}") // LOG 8
                            sources.add(newExtractorLink(
                                source = "CAST",
                                name = "CAST ${source.label ?: "Auto"}",
                                url = source.file,
                                type = ExtractorLinkType.M3U8
                            ) {
                                this.referer = "$mainUrl/"
                                this.quality = Qualities.Unknown.value
                            })
                        }
                    }
                } else {
                    Log.e("F16-ERROR", "Gagal mendekripsi payload!") // ERROR LOG
                }
            } else {
                Log.e("F16-ERROR", "Format JSON API tidak sesuai atau kosong.") // ERROR LOG
            }
        } catch (e: Exception) {
            Log.e("F16-ERROR", "Crash Utama: ${e.message}") // ERROR LOG
            e.printStackTrace()
        }
        
        Log.d("F16-DEBUG", "Selesai. Total Sources: ${sources.size}") // LOG 9
        return sources
    }

    private fun decryptAesGcm(encryptedBase64: String, keyBytes: ByteArray, ivBase64: String): String? {
        try {
            val cipherText = Base64.decode(encryptedBase64.fixBase64(), Base64.URL_SAFE)
            val iv = try {
                Base64.decode(ivBase64.fixBase64(), Base64.URL_SAFE)
            } catch (e: Exception) {
                ivBase64.toByteArray()
            }

            val spec = GCMParameterSpec(128, iv)
            val keySpec = SecretKeySpec(keyBytes, "AES")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, keySpec, spec)
            
            val decryptedBytes = cipher.doFinal(cipherText)
            return String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e("F16-ERROR", "Decryption Error: ${e.message}") // ERROR LOG
            return null
        }
    }
}
