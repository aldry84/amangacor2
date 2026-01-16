package com.layarKacaProvider

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.extractors.Filesim
import java.net.URI

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

        // Ambil isi M3U8
        val response = app.get(url, headers = headers)
        val m3uData = response.text

        // Jika ini Master Playlist (Nested)
        if (m3uData.contains(".m3u8") && !m3uData.contains("#EXTINF")) {
            // Gunakan Regex agar lebih aman
            val variantUrl = Regex("""(https?://.*\.m3u8|.*\.m3u8)""")
                .find(m3uData)?.value

            if (variantUrl != null) {
                // Perbaikan penggabungan URL
                val finalVariantUrl = if (variantUrl.startsWith("http")) {
                    variantUrl
                } else {
                    try {
                        URI(url).resolve(variantUrl).toString()
                    } catch (e: Exception) {
                        "$mainUrl/$variantUrl"
                    }
                }
                
                callback(
                    newExtractorLink(
                        source = this.name,
                        name = this.name,
                        url = finalVariantUrl
                    ).apply {
                        this.headers = headers
                        this.referer = "https://turbovidhls.com/"
                        this.quality = Qualities.Unknown.value
                    }
                )
                return
            }
        }

        // Link Final
        callback(
            newExtractorLink(
                source = this.name,
                name = this.name,
                url = url
            ).apply {
                this.headers = headers
                this.referer = "https://turbovidhls.com/"
                this.quality = Qualities.Unknown.value
            }
        )
    }
}
