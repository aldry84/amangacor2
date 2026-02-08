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
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.fasterxml.jackson.annotation.JsonProperty
import java.net.URI
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

// ============================================================================
// 1. EMTURBOVID EXTRACTOR
// ============================================================================
open class EmturbovidExtractor : ExtractorApi() {
    override var name = "Emturbovid"
    override var mainUrl = "https://emturbovid.com"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val finalReferer = referer ?: "$mainUrl/"
        val sources = mutableListOf<ExtractorLink>()
        
        try {
            val response = app.get(url, referer = finalReferer)
            val playerScript = response.document.selectXpath("//script[contains(text(),'var urlPlay')]").html()
            
            if (playerScript.isNotBlank()) {
                val m3u8Url = playerScript.substringAfter("var urlPlay = '").substringBefore("'")
                val originUrl = try { URI(finalReferer).let { "${it.scheme}://${it.host}" } } catch (e: Exception) { mainUrl }
                
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
        } catch (e: Exception) { e.printStackTrace() }
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
// 3. F16 EXTRACTOR
// ============================================================================
open class F16Extractor : ExtractorApi() {
    override var name = "F16"
    override var mainUrl = "https://f16px.com"
    override val requiresReferer = false

    data class F16Playback(val playback: PlaybackData?)
    data class PlaybackData(val iv: String?, val payload: String?, val key_parts: List<String>?)
    data class DecryptedSource(val url: String?, val label: String?)
    data class DecryptedResponse(val sources: List<DecryptedSource>?)

    private fun String.fixBase64(): String {
        var s = this
        while (s.length % 4 != 0) s += "="
        return s
    }

    private fun randomHex(length: Int): String {
        val chars = "0123456789abcdef"
        return (1..length).map { chars[Random.nextInt(chars.length)] }.joinToString("")
    }

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val sources = mutableListOf<ExtractorLink>()
        try {
            val videoId = url.substringAfter("/e/").substringBefore("?")
            val apiUrl = "$mainUrl/api/videos/$videoId/embed/playback"
            val pageUrl = "$mainUrl/e/$videoId"
            
            val viewerId = randomHex(32) 
            val deviceId = randomHex(32)
            val jwtHeader = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9" 
            val timestamp = System.currentTimeMillis() / 1000
            val jwtPayload = """{"viewer_id":"$viewerId","device_id":"$deviceId","confidence":0.91,"iat":$timestamp,"exp":${timestamp + 600}}"""
            val jwtPayloadEncoded = Base64.encodeToString(jwtPayload.toByteArray(), Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
            val jwtSignature = randomHex(43)
            val token = "$jwtHeader.$jwtPayloadEncoded.$jwtSignature"

            val headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Mobile Safari/537.36",
                "Referer" to pageUrl,
                "Origin" to mainUrl,
                "Content-Type" to "application/json",
                "x-embed-origin" to "playeriframe.sbs",
                "x-embed-parent" to pageUrl,
                "x-embed-referer" to "https://playeriframe.sbs/"
            )

            val jsonPayload = mapOf("fingerprint" to mapOf("token" to token, "viewer_id" to viewerId, "device_id" to deviceId, "confidence" to 0.91))
            val responseText = app.post(apiUrl, headers = headers, json = jsonPayload).text
            val json = tryParseJson<F16Playback>(responseText)
            val pb = json?.playback

            if (pb != null && pb.payload != null && pb.iv != null && !pb.key_parts.isNullOrEmpty()) {
                val part1 = Base64.decode(pb.key_parts[0].fixBase64(), Base64.URL_SAFE)
                val part2 = Base64.decode(pb.key_parts[1].fixBase64(), Base64.URL_SAFE)
                val combinedKey = part1 + part2 

                val decryptedJson = decryptAesGcm(pb.payload, combinedKey, pb.iv)
                if (decryptedJson != null) {
                    val result = tryParseJson<DecryptedResponse>(decryptedJson)
                    result?.sources?.forEach { source ->
                        if (!source.url.isNullOrBlank()) {
                            sources.add(newExtractorLink(source = "CAST", name = "CAST ${source.label ?: "Auto"}", url = source.url, type = ExtractorLinkType.M3U8) {
                                this.referer = "$mainUrl/"
                                this.quality = getQualityFromName(source.label)
                            })
                        }
                    }
                }
            }
        } catch (e: Exception) { Log.e("F16Extractor", "Error: ${e.message}") }
        return sources
    }

    private fun decryptAesGcm(encryptedBase64: String, keyBytes: ByteArray, ivBase64: String): String? {
        return try {
            val iv = Base64.decode(ivBase64.fixBase64(), Base64.URL_SAFE)
            val cipherText = Base64.decode(encryptedBase64.fixBase64(), Base64.URL_SAFE)
            val spec = GCMParameterSpec(128, iv)
            val keySpec = SecretKeySpec(keyBytes, "AES")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, keySpec, spec)
            String(cipher.doFinal(cipherText), Charsets.UTF_8)
        } catch (e: Exception) { null }
    }
}

// ============================================================================
// 4. HYDRAX EXTRACTOR (FINAL BOSS DECRYPTOR)
// ============================================================================
open class HydraxExtractor : ExtractorApi() {
    override var name = "Hydrax"
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
            // Gunakan timeout agar tidak SocketTimeoutException
            val response = app.get(url, referer = referer ?: "https://tv8.lk21official.cc/", timeout = 15)
            val html = response.text
            val finalUrl = response.url

            val encodedData = Regex("""const datas\s*=\s*"([^"]+)"""").find(html)?.groupValues?.get(1) ?: return null
            val cleanB64 = encodedData.replace(Regex("""\\u[0-9a-fA-F]{4}"""), "")
                .filter { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '+' || it == '/' || it == '=' }
            
            val decodedJson = String(Base64.decode(cleanB64, Base64.DEFAULT), Charsets.ISO_8859_1)
            val data = tryParseJson<AbyssData>(decodedJson) ?: return null
            val mediaData = data.media ?: return null
            
            // Rumus Kunci Master: user_id:slug:md5_id
            val keyString = "${data.userId}:${data.slug}:${data.md5Id}"
            val md5Hash = MessageDigest.getInstance("MD5").digest(keyString.toByteArray()).joinToString("") { "%02x".format(it) }
            val keyBytes = md5Hash.toByteArray(Charsets.UTF_8)
            val ivBytes = keyBytes.sliceArray(0 until 16)

            val encryptedBytes = unescapeMedia(mediaData)
            val decryptedUrl = decryptAesCtr(encryptedBytes, keyBytes, ivBytes)

            if (decryptedUrl.contains("http")) {
                sources.add(newExtractorLink(source = name, name = "$name VIP", url = decryptedUrl, type = ExtractorLinkType.VIDEO) {
                    this.referer = finalUrl
                    this.quality = Qualities.Unknown.value // Menggunakan context dari ExtractorApi.kt
                })
            }
        } catch (e: Exception) { e.printStackTrace() }
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
                try {
                    val hex = media.substring(i + 2, i + 6)
                    bytes.add(hex.toInt(16).toByte())
                    i += 6
                } catch (e: Exception) { bytes.add(media[i].toByte()); i++ }
            } else {
                bytes.add(media[i].toByte()); i++
            }
        }
        return bytes.toByteArray()
    }
}
