package com.layarKacaProvider

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
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
            "User-Agent" to "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36",
            "Accept" to "*/*"
        )

        // 1. Ambil isi M3U8 Master
        val masterResponse = app.get(url, headers = headers).text
        
        // 2. Jika isi M3U8 mengarah ke file .m3u8 lain (variant)
        if (masterResponse.contains(".m3u8")) {
            val lines = masterResponse.split("\n")
            val nextPath = lines.firstOrNull { it.contains(".m3u8") }?.trim()
            
            if (nextPath != null) {
                // Buat URL absolut untuk variant playlist
                val variantUrl = if (nextPath.startsWith("http")) nextPath else {
                    val base = url.substringBeforeLast("/")
                    "$base/$nextPath"
                }
                
                // Kirim variant URL dengan header ketat
                callback(
                    newExtractorLink(
                        source = this.name,
                        name = this.name,
                        url = variantUrl,
                        type = INFER_TYPE,
                    ).apply {
                        this.headers = headers
                        this.referer = "https://turbovidhls.com/"
                        this.quality = Qualities.Unknown.value
                    }
                )
            }
        } else {
            // Jika ini sudah merupakan playlist segmen (.png)
            callback(
                newExtractorLink(
                    source = this.name,
                    name = this.name,
                    url = url,
                    type = INFER_TYPE,
                ).apply {
                    this.headers = headers
                    this.referer = "https://turbovidhls.com/"
                    this.quality = Qualities.Unknown.value
                }
            )
        }
    }
}
