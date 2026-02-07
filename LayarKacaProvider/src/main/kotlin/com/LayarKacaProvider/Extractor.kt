package com.LayarKacaProvider

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.getAndUnpack
import java.net.URI

// ============================================================================
// EXTRACTOR 1: EMTURBOVID (Untuk TurboVip & P2P Lama)
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
// EXTRACTOR 2: P2P (Hownetwork)
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
// EXTRACTOR 3: F16 / CAST (f16px.com)
// ============================================================================
open class F16Extractor : ExtractorApi() {
    override var name = "F16" // Nanti akan muncul sebagai "CAST" di plugin karena kita mapping di provider
    override var mainUrl = "https://f16px.com"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val sources = mutableListOf<ExtractorLink>()
        try {
            // 1. Request halaman Embed
            val response = app.get(url, referer = "https://playeriframe.sbs/").text
            
            // 2. Cek apakah scriptnya dipacking (packed function)
            val unpacked = if (response.contains("eval(function(p,a,c,k,e,d)")) {
                getAndUnpack(response) // Unpack otomatis
            } else {
                response
            }

            // 3. Cari link file m3u8 menggunakan Regex
            // Pola umum: file:"https://..." atau sources:[{file:"..."}]
            val m3u8Regex = Regex("file:\\s*\"(.*?m3u8.*?)\"")
            val match = m3u8Regex.find(unpacked)

            if (match != null) {
                val m3u8Url = match.groupValues[1]
                sources.add(
                    newExtractorLink(
                        source = "CAST", // Nama yang muncul di UI
                        name = "CAST",
                        url = m3u8Url,
                        type = ExtractorLinkType.M3U8
                    ) {
                        this.referer = "$mainUrl/"
                        this.quality = Qualities.Unknown.value
                        // Header tambahan sesuai cURL agar lebih 'sopan' ke server
                        this.headers = mapOf(
                            "Origin" to mainUrl,
                            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                        )
                    }
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return sources
    }
}
