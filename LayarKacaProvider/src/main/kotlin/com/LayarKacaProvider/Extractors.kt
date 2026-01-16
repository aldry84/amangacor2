package com.layarKacaProvider

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.extractors.Filesim

// --- CLASS EMTURBOVID ---
class EmturboCustom : Turbovidhls() {
    override val name = "Emturbovid"
    override val mainUrl = "https://emturbovid.com"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Referer" to "https://emturbovid.com/"
        )

        try {
            val response = app.get(url, headers = headers).text
            
            var m3u8Url: String? = null
            
            // Regex 1: Mencari di sources: [{ file: "..." }]
            val regex1 = """file:\s*["']([^"']+\.m3u8[^"']*)["']""".toRegex()
            m3u8Url = regex1.find(response)?.groupValues?.get(1)

            // Regex 2: Fallback mencari string m3u8 apapun
            if (m3u8Url == null) {
                val regex2 = """["'](https?://[^"']+\.m3u8[^"']*)["']""".toRegex()
                m3u8Url = regex2.find(response)?.groupValues?.get(1)
            }

            if (m3u8Url != null) {
                // PENTING: Kita kirim 'url' (Link Embed) sebagai 'referer' ke parent class
                // Agar server tidak menolak koneksi (Fix Error 3001)
                super.getUrl(m3u8Url, url, subtitleCallback, callback)
            } 
            // Jika tidak ketemu, jangan lakukan apa-apa agar tidak error 3002

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

class Co4nxtrl : Filesim() {
    override val mainUrl = "https://co4nxtrl.com"
    override val name = "Co4nxtrl"
    override val requiresReferer = true
}

class Furher : Filesim() {
    override val name = "Furher"
    override var mainUrl = "https://furher.in"
}

// --- CLASS BASE TURBOVID ---
open class Turbovidhls : ExtractorApi() {
    override val name = "Turbovid"
    override val mainUrl = "https://turbovidhls.com"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        // PERBAIKAN HEADER:
        // Gunakan referer yang dikirim dari EmturboCustom (URL Embed) jika ada.
        // Jika tidak ada, gunakan domain default.
        val safeReferer = referer ?: if (url.contains("emturbovid")) "https://emturbovid.com/" else "https://turbovidthis.com/"
        
        // Origin biasanya hanya domain dasarnya
        val originUrl = if (url.contains("emturbovid")) "https://emturbovid.com" else "https://turbovidthis.com"

        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Accept" to "*/*",
            "Origin" to originUrl,
            "Referer" to safeReferer // Ini yang krusial untuk mencegah 403 Forbidden
        )

        try {
            val response = app.get(url, headers = headers)
            val responseText = response.text

            // 1. Logika Nested M3U8 (Playlist di dalam Playlist)
            if (responseText.contains("#EXT-X-STREAM-INF") && responseText.contains("http")) {
                val nextUrl = responseText.split('\n').firstOrNull { 
                    it.trim().startsWith("http") && it.contains(".m3u8") 
                }?.trim()

                if (!nextUrl.isNullOrEmpty()) {
                    callback(
                        createSafeExtractorLink(
                            source = this.name,
                            name = this.name,
                            url = nextUrl,
                            referer = safeReferer, // Teruskan referer yang benar
                            isM3u8 = true, 
                            headers = headers
                        )
                    )
                    return
                }
            }

            // 2. Fallback: Link Langsung
            callback(
                createSafeExtractorLink(
                    source = this.name,
                    name = this.name,
                    url = url,
                    referer = safeReferer, // Teruskan referer yang benar
                    isM3u8 = true,
                    headers = headers
                )
            )

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // --- FUNGSI JEMBATAN (REFLECTION) ---
    private fun createSafeExtractorLink(
        source: String,
        name: String,
        url: String,
        referer: String,
        isM3u8: Boolean,
        headers: Map<String, String>
    ): ExtractorLink {
        val clazz = ExtractorLink::class.java
        
        val constructor = clazz.constructors.find { it.parameterCount >= 6 } 
            ?: throw RuntimeException("Constructor ExtractorLink tidak ditemukan!")

        val type = if (isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO

        return constructor.newInstance(
            source,
            name,
            url,
            referer,
            Qualities.Unknown.value,
            type,
            headers,
            null
        ) as ExtractorLink
    }
}
