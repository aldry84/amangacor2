package com.Movie21.extractors

import com.Movie21.VidSrcResponse // Import Data Model
import com.lagacy.Movie21.ExtractorLink
import com.lagacy.Movie21.Extractor
import com.lagacy.Movie21.SubtitleData
import com.lagacy.Movie21.app
import com.lagacy.Movie21.logError
import com.lagacy.Movie21.Qualities

// Extractor menerima URL dasar API di konstruktor
class VidSrcApiExtractor(private val apiUrl: String) : Extractor() {
    override val name = "VidSrc Vercel API"

    override fun extract(
        url: String, 
        niceUrl: String, 
        subtitleCallback: (SubtitleData) -> Unit, 
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            // Lakukan request ke URL API Vercel (contoh: https://<deployment-anda>/76479/1/1)
            val res = app.get(url, timeout = 30000)
            
            // Parse hasil JSON menggunakan Data Model
            val data = res.parsed<VidSrcResponse>()

            // Jika streamUrl ditemukan dan tidak kosong
            if (data.streamUrl.isNullOrEmpty().not()) {
                val streamLink = data.streamUrl!!
                
                callback.invoke(
                    ExtractorLink(
                        name = this.name,
                        source = "Vercel Stream",
                        url = streamLink, 
                        referer = apiUrl, 
                        // Deteksi apakah itu HLS/m3u8 untuk pemutar
                        isM3u8 = streamLink.endsWith(".m3u8", true), 
                        quality = Qualities.Unknown.value,
                    )
                )
            } else {
                // Log jika link tidak ditemukan dari JSON
                logError("VidSrcApiExtractor: Stream link not found in response for $url")
            }

        } catch (e: Exception) {
            logError(e)
        }
    }
}
