package com.LayarKacaProvider

import android.util.Base64
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
// 1. EMTURBOVID EXTRACTOR (Server: TurboVip & Emturbovid)
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

            sources.add(
                newExtractorLink(source = name, name = name, url = m3u8Url, type = ExtractorLinkType.M3U8) {
                    this.referer = finalReferer
                    this.quality = Qualities.Unknown.value
                    this.headers = headers
                }
            )
        }
        return sources
    }
}

// ============================================================================
// 2. P2P EXTRACTOR (Server: P2P / Hownetwork)
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
                sources.add(
                    newExtractorLink(source = name, name = name, url = videoUrl, type = ExtractorLinkType.M3U8) {
                        this.referer = mainUrl
                        this.quality = Qualities.Unknown.value
                    }
                )
            }
        } catch (e: Exception) { e.printStackTrace() }
        return sources
    }
}

// ============================================================================
// 3. F16 EXTRACTOR (Server: CAST / f16px.com) - [PURE KOTLIN DECRYPTION]
// ============================================================================
open class F16Extractor : ExtractorApi() {
    override var name = "F16"
    override var mainUrl = "https://f16px.com"
    override val requiresReferer = false

    // Struktur JSON untuk parsing response API
    data class F16Playback(val playback: PlaybackData?)
    data class PlaybackData(val iv: String?, val payload: String?, val key_parts: List<String>?)
    data class DecryptedSource(val file: String?, val label: String?, val type: String?)
    data class DecryptedResponse(val sources: List<DecryptedSource>?)

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val sources = mutableListOf<ExtractorLink>()
        
        // ID Video: https://f16px.com/e/ex5eimwh97ha -> ex5eimwh97ha
        val videoId = url.substringAfter("/e/").substringBefore("?")
        val apiUrl = "$mainUrl/api/videos/$videoId/embed/playback"

        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36",
            "Referer" to "https://playeriframe.sbs/",
            "Origin" to "https://playeriframe.sbs",
            "x-embed-origin" to "playeriframe.sbs",
            "x-embed-parent" to url,
            "x-embed-referer" to "https://playeriframe.sbs/"
        )

        try {
            // 1. Tembak API Playback (Pura-pura jadi browser)
            // Kita kirim fingerprint dummy biar dikira proses 'Attest' sudah lewat
            val dummyBody = """{"fingerprint":{"token":"dummy_bypass","viewer_id":"7e847c23137449fbb73cbf6bb7f9bceb","device_id":"daeba4e7719c4d4c91e53dd03849cf37","confidence":0.6}}"""
            
            val responseText = app.post(apiUrl, headers = headers, data = mapOf("body" to dummyBody)).text
            val json = tryParseJson<F16Playback>(responseText)
            val pb = json?.playback

            if (pb != null && pb.payload != null && pb.iv != null && !pb.key_parts.isNullOrEmpty()) {
                
                // 2. RUMUS RAHASIA: Base64(Part1) + Base64(Part2)
                val part1 = Base64.decode(pb.key_parts[0], Base64.DEFAULT)
                val part2 = Base64.decode(pb.key_parts[1], Base64.DEFAULT)
                val combinedKey = part1 + part2 // Gabungkan byte array

                // 3. Lakukan Dekripsi AES-GCM
                val decryptedJson = decryptAesGcm(
                    encryptedBase64 = pb.payload,
                    keyBytes = combinedKey,
                    ivBase64 = pb.iv
                )

                // 4. Parse Hasil Dekripsi (Isinya JSON lagi)
                if (decryptedJson != null) {
                    val result = tryParseJson<DecryptedResponse>(decryptedJson)
                    result?.sources?.forEach { source ->
                        if (!source.file.isNullOrBlank()) {
                            sources.add(
                                newExtractorLink(
                                    source = "CAST",
                                    name = "CAST ${source.label ?: "Auto"}",
                                    url = source.file,
                                    type = ExtractorLinkType.M3U8
                                ) {
                                    this.referer = "$mainUrl/"
                                    this.quality = Qualities.Unknown.value
                                }
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return sources
    }

    // Fungsi Pembantu Dekripsi AES-GCM
    private fun decryptAesGcm(encryptedBase64: String, keyBytes: ByteArray, ivBase64: String): String? {
        try {
            // Decode Base64 Input
            val cipherText = Base64.decode(encryptedBase64, Base64.DEFAULT)
            val iv = Base64.decode(ivBase64, Base64.DEFAULT)

            // Setup Cipher
            val spec = GCMParameterSpec(128, iv) // 128 bit tag length
            val keySpec = SecretKeySpec(keyBytes, "AES")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            
            cipher.init(Cipher.DECRYPT_MODE, keySpec, spec)
            
            // Decrypt
            val decryptedBytes = cipher.doFinal(cipherText)
            return String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}
