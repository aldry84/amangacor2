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
import com.lagradost.cloudstream3.network.WebViewResolver // Wajib Import Ini
import java.net.URI

// ============================================================================
// 1. EMTURBOVID EXTRACTOR (Server: TurboVip & Emturbovid) - [FIXED]
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
// 2. P2P EXTRACTOR (Server: P2P / Hownetwork) - [FIXED]
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
// 3. F16 EXTRACTOR (Server: CAST / f16px.com) - [UPGRADE ALA FILEMOON]
// ============================================================================
open class F16Extractor : ExtractorApi() {
    override var name = "F16"
    override var mainUrl = "https://f16px.com"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val sources = mutableListOf<ExtractorLink>()
        
        // Header ala Filemoon (Penting buat menipu server)
        val f16Headers = mapOf(
            "Referer" to "https://playeriframe.sbs/",
            "Sec-Fetch-Dest" to "iframe",
            "Sec-Fetch-Mode" to "navigate",
            "Sec-Fetch-Site" to "cross-site",
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        )

        try {
            // 1. Ambil Source HTML
            val response = app.get(url, headers = f16Headers).text
            
            // 2. Cek apakah script dipacking, kalau iya unpack dulu
            val unpacked = if (response.contains("eval(function(p,a,c,k,e,d)")) {
                getAndUnpack(response)
            } else {
                response
            }

            // 3. CARA PERTAMA: Regex Standar ala FileMoon (Mencari 'sources:[{file:"..."}]')
            val regexFilemoon = Regex("""sources:\s*\[\s*\{\s*file:\s*"(.*?)"""")
            var m3u8Url = regexFilemoon.find(unpacked)?.groupValues?.get(1)

            // 4. CARA KEDUA: Regex Cadangan (Mencari 'file:"..."')
            if (m3u8Url.isNullOrEmpty()) {
                val regexSimple = Regex("""file:\s*"(.*?m3u8.*?)" """)
                m3u8Url = regexSimple.find(unpacked)?.groupValues?.get(1)
            }

            if (!m3u8Url.isNullOrEmpty()) {
                // Sukses dapet link via Regex (Cara Cepat)
                sources.add(
                    newExtractorLink(
                        source = "CAST", // Nama yang muncul di app
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
                // 5. CARA KETIGA: WebView Fallback (Jurus Pamungkas)
                // Kalau Regex gagal, kita suruh WebView buka halamannya dan cegat link m3u8
                Log.d("F16Extractor", "Regex gagal, mencoba WebView Fallback...")
                
                val resolver = WebViewResolver(
                    interceptUrl = Regex("""(m3u8|master\.txt)"""), // Tangkap apa pun yang berbau m3u8
                    additionalUrls = listOf(Regex("""(m3u8|master\.txt)""")),
                    useOkhttp = false,
                    timeout = 15_000L // Tunggu 15 detik
                )

                val interceptedUrl = app.get(
                    url,
                    headers = f16Headers,
                    interceptor = resolver
                ).url

                if (interceptedUrl.isNotEmpty() && interceptedUrl != url) {
                    sources.add(
                        newExtractorLink(
                            source = "CAST (WebView)", 
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
