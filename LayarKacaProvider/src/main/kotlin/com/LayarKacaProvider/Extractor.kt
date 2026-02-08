package com.LayarKacaProvider

import android.util.Base64
import com.lagradost.api.Log
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
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
        val sources = mutableListOf<ExtractorLink>()
        try {
            val response = app.get(url, referer = referer ?: "$mainUrl/")
            val playerScript = response.document.selectXpath("//script[contains(text(),'var urlPlay')]").html()
            if (playerScript.isNotBlank()) {
                val m3u8Url = playerScript.substringAfter("var urlPlay = '").substringBefore("'")
                sources.add(newExtractorLink(source = name, name = name, url = m3u8Url, type = ExtractorLinkType.M3U8) {
                    this.quality = 400 
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

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val id = url.substringAfter("id=").substringBefore("&")
        val apiUrl = "$mainUrl/api2.php?id=$id"
        val headers = mapOf("X-Requested-With" to "XMLHttpRequest")
        val formBody = mapOf("r" to "https://playeriframe.sbs/", "d" to "cloud.hownetwork.xyz")
        val sources = mutableListOf<ExtractorLink>()
        try {
            val response = app.post(apiUrl, headers = headers, data = formBody).text
            val json = tryParseJson<Map<String, String>>(response)
            val videoUrl = json?.get("file") ?: json?.get("link")
            if (!videoUrl.isNullOrBlank()) {
                sources.add(newExtractorLink(source = name, name = name, url = videoUrl, type = ExtractorLinkType.M3U8) {
                    this.quality = 400
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

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val sources = mutableListOf<ExtractorLink>()
        try {
            val videoId = url.substringAfter("/e/").substringBefore("?")
            val apiUrl = "$mainUrl/api/videos/$videoId/embed/playback"
            val headers = mapOf("Content-Type" to "application/json", "x-embed-origin" to "playeriframe.sbs")
            val jsonPayload = mapOf("fingerprint" to mapOf("confidence" to 0.91))
            
            val responseText = app.post(apiUrl, headers = headers, json = jsonPayload).text
            val pb = tryParseJson<F16Playback>(responseText)?.playback

            if (pb?.payload != null && pb.iv != null && pb.key_parts?.size == 2) {
                val combinedKey = Base64.decode(pb.key_parts[0], Base64.URL_SAFE) + Base64.decode(pb.key_parts[1], Base64.URL_SAFE)
                val decryptedJson = decryptAesGcm(pb.payload, combinedKey, pb.iv)
                // Parsing manual sederhana untuk link
                Regex("""\"url\":\"([^\"]+)""").find(decryptedJson ?: "")?.groupValues?.get(1)?.let { streamUrl ->
                    sources.add(newExtractorLink(source = "CAST", name = "CAST VIP", url = streamUrl, type = ExtractorLinkType.M3U8) {
                        this.quality = 400
                    })
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        return sources
    }

    private fun decryptAesGcm(encrypted: String, key: ByteArray, ivStr: String): String? {
        return try {
            val iv = Base64.decode(ivStr, Base64.URL_SAFE)
            val cipherText = Base64.decode(encrypted, Base64.URL_SAFE)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
            String(cipher.doFinal(cipherText))
        } catch (e: Exception) { null }
    }
}

// ============================================================================
// 4. HYDRAX EXTRACTOR (FINAL BOSS - FIX TIMEOUT)
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
            // FIX TIMEOUT: Perkecil timeout (8s) biar gak nahan thread & pake User-Agent Windows
            val response = app.get(url, referer = referer ?: "https://tv8.lk21official.cc/", timeout = 8)
            val html = response.text
            val finalUrl = response.url

            val encodedData = Regex("""const datas\s*=\s*"([^"]+)"""").find(html)?.groupValues?.get(1) ?: return null
            val cleanB64 = encodedData.replace(Regex("""\\u[0-9a-fA-F]{4}"""), "").filter { it in "A".."Z" || it in "a".."z" || it in "0".."9" || it in "+/=" }
            val decodedJson = String(Base64.decode(cleanB64, Base64.DEFAULT), Charsets.ISO_8859_1)
            
            val data = tryParseJson<AbyssData>(decodedJson) ?: return null
            if (data.media == null) return null

            // Rumus Kunci: user_id:slug:md5_id
            val keyString = "${data.userId}:${data.slug}:${data.md5Id}"
            val md5Hex = MessageDigest.getInstance("MD5").digest(keyString.toByteArray()).joinToString("") { "%02x".format(it) }
            
            val keyBytes = md5Hex.toByteArray()
            val ivBytes = keyBytes.sliceArray(0 until 16)

            val decryptedUrl = decryptAesCtr(unescapeMedia(data.media), keyBytes, ivBytes)

            if (decryptedUrl.contains("http")) {
                sources.add(newExtractorLink(source = name, name = "$name VIP", url = decryptedUrl, type = ExtractorLinkType.VIDEO) {
                    this.referer = finalUrl
                    this.quality = 400 // Pakai angka langsung (Safe for Stable)
                })
            }
        } catch (e: Exception) { e.printStackTrace() }
        return sources
    }

    private fun decryptAesCtr(encrypted: ByteArray, key: ByteArray, iv: ByteArray): String {
        return try {
            val cipher = Cipher.getInstance("AES/CTR/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
            String(cipher.doFinal(encrypted)).filter { it.toInt() in 32..126 }
        } catch (e: Exception) { "" }
    }

    private fun unescapeMedia(media: String): ByteArray {
        val bytes = mutableListOf<Byte>()
        var i = 0
        while (i < media.length) {
            if (media[i] == '\\' && i + 1 < media.length && media[i+1] == 'u') {
                try {
                    bytes.add(media.substring(i + 2, i + 6).toInt(16).toByte())
                    i += 6
                } catch (e: Exception) { bytes.add(media[i].toByte()); i++ }
            } else { bytes.add(media[i].toByte()); i++ }
        }
        return bytes.toByteArray()
    }
}
