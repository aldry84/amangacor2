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
        // Header wajib berdasarkan data curl agar file .png terbaca sebagai video
        val headers = mapOf(
            "Origin" to "https://turbovidhls.com",
            "Referer" to "https://turbovidhls.com/",
            "User-Agent" to "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36",
            "Accept" to "*/*"
        )

        // Mengambil isi file m3u8 untuk pengecekan
        val response = app.get(url, headers = headers)
        val m3uData = response.text

        // Logika Jump: Jika link adalah Master Playlist (tidak ada tag #EXTINF)
        if (m3uData.contains(".m3u8") && !m3uData.contains("#EXTINF")) {
            val actualUrl = m3uData.split("\n").firstOrNull { 
                it.contains(".m3u8") && !it.startsWith("#") 
            }?.trim()
            
            if (actualUrl != null) {
                callback(
                    newExtractorLink(
                        source = this.name,
                        name = this.name,
                        url = actualUrl, // Link yang berisi segmen video asli
                        referer = "https://turbovidhls.com/",
                        quality = Qualities.Unknown.value,
                        isM3u8 = true,
                        headers = headers
                    )
                )
                return
            }
        }

        // Jika link sudah berupa playlist langsung (seperti kategori Series)
        callback(
            newExtractorLink(this.name, this.name, url, "https://turbovidhls.com/", Qualities.Unknown.value, true, headers)
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
                    newExtractorLink(this.name, this.name, finalVariantUrl, "https://turbovidhls.com/", Qualities.Unknown.value, true, headers)
                )
                return
            }
        }

        callback(
            newExtractorLink(this.name, this.name, url, "https://turbovidhls.com/", Qualities.Unknown.value, true, headers)
        )
    }
}
