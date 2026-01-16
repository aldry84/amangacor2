@file:Suppress("DEPRECATION") // <- INI KUNCINYA. Membungkam error deprecated satu file penuh.

package com.layarKacaProvider

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.extractors.Filesim

class Co4nxtrl : Filesim() {
    override val mainUrl = "https://co4nxtrl.com"
    override val name = "Co4nxtrl"
    override val requiresReferer = true
}

class Furher : Filesim() {
    override val name = "Furher"
    override var mainUrl = "https://furher.in"
}

class Turbovidhls : ExtractorApi() {
    override val name = "Turbovid"
    override val mainUrl = "https://turbovidhls.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val headers = mapOf(
            "Origin" to "https://turbovidhls.com",
            "Referer" to "https://turbovidhls.com/",
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Accept" to "*/*"
        )

        try {
            val response = app.get(url, headers = headers)
            val m3uData = response.text
            val baseUrl = url.substringBeforeLast("/")

            // Cek apakah ini Master Playlist (Nested M3U8)
            if (m3uData.contains(".m3u8") && !m3uData.contains("#EXTINF")) {
                val variantPath = m3uData.lines().firstOrNull { 
                    it.trim().endsWith(".m3u8") && !it.startsWith("#") 
                }?.trim()
                
                if (variantPath != null) {
                    val finalVariantUrl = if (variantPath.startsWith("http")) variantPath else "$baseUrl/$variantPath"
                    
                    // Kita pakai Constructor manual karena helper bawaan tidak support isM3u8
                    callback(
                        ExtractorLink(
                            source = this.name,
                            name = this.name,
                            url = finalVariantUrl,
                            referer = "https://turbovidhls.com/",
                            quality = Qualities.Unknown.value,
                            isM3u8 = true, 
                            headers = headers,
                            extractorData = null
                        )
                    )
                    return
                }
            }

            // Link Final
            callback(
                ExtractorLink(
                    source = this.name,
                    name = this.name,
                    url = url,
                    referer = "https://turbovidhls.com/",
                    quality = Qualities.Unknown.value,
                    isM3u8 = true, 
                    headers = headers,
                    extractorData = null
                )
            )

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
