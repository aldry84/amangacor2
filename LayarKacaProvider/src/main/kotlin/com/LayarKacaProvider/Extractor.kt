package com.LayarKacaProvider

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import java.net.URI

// --- EXTRACTOR 1: EMTURBOVID (Untuk TurboVip & P2P Lama) ---
open class EmturbovidExtractor : ExtractorApi() {
    override var name = "Emturbovid"
    override var mainUrl = "https://emturbovid.com"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val finalReferer = referer ?: "$mainUrl/"
        
        // 1. Ambil halaman player
        val response = app.get(url, referer = finalReferer)
        val playerScript = response.document.selectXpath("//script[contains(text(),'var urlPlay')]").html()

        val sources = mutableListOf<ExtractorLink>()
        
        if (playerScript.isNotBlank()) {
            val m3u8Url = playerScript.substringAfter("var urlPlay = '").substringBefore("'")
            
            // Tentukan Origin secara dinamis dari Referer
            val originUrl = try {
                val uri = URI(finalReferer)
                "${uri.scheme}://${uri.host}"
            } catch (e: Exception) {
                "$mainUrl"
            }

            // HEADER SAKTI (Anti Error 3001)
            val headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                "Referer" to finalReferer,
                "Origin" to originUrl
            )

            sources.add(
                newExtractorLink(
                    source = name,
                    name = name,
                    url = m3u8Url,
                    type = ExtractorLinkType.M3U8
                ) {
                    this.referer = finalReferer
                    this.quality = Qualities.Unknown.value
                    this.headers = headers
                }
            )
        }
        return sources
    }
}

// --- EXTRACTOR 2: P2P (Hownetwork) ---
open class P2PExtractor : ExtractorApi() {
    override var name = "P2P"
    override var mainUrl = "https://cloud.hownetwork.xyz"
    override val requiresReferer = false

    data class HownetworkResponse(
        val file: String?,
        val link: String?,
        val label: String?
    )

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        // url input: https://cloud.hownetwork.xyz/video.php?id=...
        val id = url.substringAfter("id=").substringBefore("&")
        val apiUrl = "$mainUrl/api2.php?id=$id"

        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Mobile Safari/537.36",
            "Referer" to url,
            "Origin" to mainUrl,
            "X-Requested-With" to "XMLHttpRequest"
        )

        val formBody = mapOf(
            "r" to "https://playeriframe.sbs/",
            "d" to "cloud.hownetwork.xyz"
        )

        val sources = mutableListOf<ExtractorLink>()
        
        try {
            // POST Request sesuai cURL
            val response = app.post(apiUrl, headers = headers, data = formBody).text
            val json = tryParseJson<HownetworkResponse>(response)
            val videoUrl = json?.file ?: json?.link
            
            if (!videoUrl.isNullOrBlank()) {
                sources.add(
                    newExtractorLink(
                        source = name,
                        name = name,
                        url = videoUrl,
                        type = ExtractorLinkType.M3U8
                    ) {
                        this.referer = mainUrl
                        this.quality = Qualities.Unknown.value
                    }
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return sources
    }
}
