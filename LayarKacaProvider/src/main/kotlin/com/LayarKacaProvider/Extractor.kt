package com.LayarKacaProvider

import com.lagradost.api.Log
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.cloudstream3.network.WebViewResolver
import java.net.URI

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
// 3. F16 EXTRACTOR (Server: CAST / f16px.com) - [FINAL UPDATE]
// ============================================================================
open class F16Extractor : ExtractorApi() {
    override var name = "F16"
    override var mainUrl = "https://f16px.com"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val sources = mutableListOf<ExtractorLink>()
        
        // Header Lengkap sesuai Analisa cURL kamu
        val f16Headers = mapOf(
            "Referer" to "https://playeriframe.sbs/",
            "Origin" to "https://playeriframe.sbs",
            "x-embed-referer" to "https://playeriframe.sbs/", // Header Pancingan
            "Sec-Fetch-Dest" to "iframe",
            "Sec-Fetch-Mode" to "navigate",
            "Sec-Fetch-Site" to "cross-site",
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        )

        try {
            // 1. Coba Regex (Kali aja ada yang bocor/tidak terenkripsi)
            val response = app.get(url, headers = f16Headers).text
            val unpacked = if (response.contains("eval(function(p,a,c,k,e,d)")) {
                getAndUnpack(response)
            } else {
                response
            }

            val regexPatterns = listOf(
                Regex("""sources:\s*\[\s*\{\s*file:\s*"(http[^"]+)""""),
                Regex("""file:\s*"(http[^"]+)"""")
            )

            var m3u8Url: String? = null
            for (regex in regexPatterns) {
                m3u8Url = regex.find(unpacked)?.groupValues?.get(1)
                if (!m3u8Url.isNullOrEmpty()) break
            }

            if (!m3u8Url.isNullOrEmpty()) {
                sources.add(
                    newExtractorLink(
                        source = "CAST",
                        name = "CAST",
                        url = m3u8Url,
                        type = ExtractorLinkType.M3U8
                    ) {
                        this.referer = "$mainUrl/"
                        this.quality = Qualities.Unknown.value
                        this.headers = f16Headers
                    }
                )
            } else {
                // 2. JURUS UTAMA: WebView Fallback (Anti Enkripsi AES)
                Log.d("F16Extractor", "Mencoba WebView Fallback dengan Timeout 60 Detik...")
                
                val resolver = WebViewResolver(
                    interceptUrl = Regex("""(m3u8|master\.txt)"""), 
                    additionalUrls = listOf(Regex("""(m3u8|master\.txt)""")),
                    useOkhttp = false,
                    timeout = 60_000L // 60 Detik (Wajib untuk decrypt AES)
                )

                // Kita pakai URL asli, header lengkap akan di-handle WebView
                val interceptedUrl = app.get(
                    url,
                    headers = f16Headers,
                    interceptor = resolver
                ).url

                if (interceptedUrl.isNotEmpty() && interceptedUrl != url) {
                    sources.add(
                        newExtractorLink(
                            source = "CAST", 
                            name = "CAST",
                            url = interceptedUrl,
                            type = ExtractorLinkType.M3U8
                        ) {
                            this.referer = "$mainUrl/"
                            this.quality = Qualities.Unknown.value
                        }
                    )
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
        return sources
    }
}
