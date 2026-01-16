package com.layarKacaProvider

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.extractors.Filesim
import java.net.URI

// ... (Class Co4nxtrl dan Furher tetap sama, tidak perlu diubah) ...

class Turbovidhls : ExtractorApi() {
    override val name = "Turbovid"
    // Update domain utama sesuai screenshot
    override val mainUrl = "https://turbovidthis.com" 
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        // Headers disesuaikan dengan screenshot network log kamu
        val headers = mapOf(
            "Origin" to mainUrl,
            "Referer" to "$mainUrl/",
            "User-Agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
            "Accept" to "*/*"
        )

        // Ambil isi M3U8
        // Kita perlu request dulu untuk melihat apakah ini master playlist atau langsung stream
        val response = app.get(url, headers = headers)
        val m3uData = response.text

        // Logika untuk menangani Nested M3U8 (Master Playlist)
        if (m3uData.contains(".m3u8") && !m3uData.contains("#EXTINF")) {
            val variantUrl = Regex("""(https?://.*\.m3u8|.*\.m3u8)""")
                .find(m3uData)?.value

            if (variantUrl != null) {
                val finalVariantUrl = if (variantUrl.startsWith("http")) {
                    variantUrl
                } else {
                    try {
                        URI(url).resolve(variantUrl).toString()
                    } catch (e: Exception) {
                        "$mainUrl/$variantUrl"
                    }
                }
                
                // Callback untuk link M3U8 final
                callback(
                    newExtractorLink(
                        source = this.name,
                        name = this.name,
                        url = finalVariantUrl
                    ).apply {
                        this.headers = headers
                        this.referer = "$mainUrl/"
                        this.quality = Qualities.Unknown.value
                        // Penting: Cloudstream akan otomatis menangani .png di dalam m3u8
                        // sebagai video segment (hls)
                        this.isM3u8 = true 
                    }
                )
                return
            }
        }

        // Jika url awal sudah merupakan link stream langsung
        callback(
            newExtractorLink(
                source = this.name,
                name = this.name,
                url = url
            ).apply {
                this.headers = headers
                this.referer = "$mainUrl/"
                this.quality = Qualities.Unknown.value
                this.isM3u8 = true
            }
        )
    }
}
