@file:Suppress("DEPRECATION") 

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
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36",
            "Accept" to "*/*"
        )

        try {
            val response = app.get(url, headers = headers)
            val responseText = response.text

            if (responseText.contains("#EXT-X-STREAM-INF") && responseText.contains("http")) {
                val nextUrl = responseText.split('\n').firstOrNull { 
                    it.trim().startsWith("http") && it.contains(".m3u8") 
                }?.trim()

                if (!nextUrl.isNullOrEmpty()) {
                    callback(
                        ExtractorLink(
                            source = this.name,
                            name = this.name,
                            url = nextUrl,
                            referer = "https://turbovidthis.com/",
                            quality = Qualities.Unknown.value,
                            isM3u8 = true,
                            headers = headers,
                            extractorData = null
                        )
                    )
                    return
                }
            }

            callback(
                ExtractorLink(
                    source = this.name,
                    name = this.name,
                    url = url,
                    referer = "https://turbovidthis.com/",
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
