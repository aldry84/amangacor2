package com.layarKacaProvider

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.extractors.Filesim

// Extractor Kustom untuk menangani Error 3001 di Movie
class CustomEmturbovid : ExtractorApi() {
    override val name = "Emturbovid"
    override val mainUrl = "https://turboviplay.com"
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
            "User-Agent" to "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36",
            "Accept" to "*/*"
        )

        val response = app.get(url, headers = headers)
        val m3uData = response.text

        // Logika Lompat untuk Nested M3U8 (Solusi Error 3001)
        if (m3uData.contains(".m3u8") && !m3uData.contains("#EXTINF")) {
            val nextPath = m3uData.split("\n").firstOrNull { 
                it.contains(".m3u8") && !it.startsWith("#") 
            }?.trim()
            
            if (nextPath != null) {
                val baseUrl = url.substringBeforeLast("/")
                val actualUrl = if (nextPath.startsWith("http")) nextPath else "$baseUrl/$nextPath"
                
                // PERBAIKAN: Menggunakan blok lambda untuk properti
                callback(
                    newExtractorLink(this.name, this.name, actualUrl) {
                        this.headers = headers
                        this.referer = "https://turbovidhls.com/"
                        this.quality = Qualities.Unknown.value
                    }
                )
                return
            }
        }

        // Link normal (Series)
        callback(
            newExtractorLink(this.name, this.name, url) {
                this.headers = headers
                this.referer = "https://turbovidhls.com/"
                this.quality = Qualities.Unknown.value
            }
        )
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
            "User-Agent" to "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36",
            "Accept" to "*/*"
        )

        val response = app.get(url, headers = headers)
        val m3uData = response.text
        val baseUrl = url.substringBeforeLast("/")

        if (m3uData.contains(".m3u8") && !m3uData.contains("#EXTINF")) {
            val variantPath = m3uData.split("\n").firstOrNull { 
                it.contains(".m3u8") && !it.startsWith("#") 
            }?.trim()
            
            if (variantPath != null) {
                val finalVariantUrl = if (variantPath.startsWith("http")) variantPath else "$baseUrl/$variantPath"
                
                callback(
                    newExtractorLink(this.name, this.name, finalVariantUrl) {
                        this.headers = headers
                        this.referer = "https://turbovidhls.com/"
                        this.quality = Qualities.Unknown.value
                    }
                )
                return
            }
        }

        callback(
            newExtractorLink(this.name, this.name, url) {
                this.headers = headers
                this.referer = "https://turbovidhls.com/"
                this.quality = Qualities.Unknown.value
            }
        )
    }
}
