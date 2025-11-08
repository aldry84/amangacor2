// DramaDrip/src/main/kotlin/com/Phisher98/VidSrcEmbedExtractor.kt

package com.Phisher98

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.api.Log

// Extractor ini tidak benar-benar mengekstrak video,
// tetapi menghasilkan URL embed yang harus diekstrak oleh Cloudstream.
class VidSrcEmbedExtractor : ExtractorApi() {
    override val name: String = "VidSrcEmbed"
    // Domain baru berdasarkan gambar dokumentasi
    override val mainUrl: String = "https://vidsrc-embed.ru" 
    override val requiresReferer = false

    // Kita akan menggunakan 'url' sebagai data gabungan: "TYPE|IMDB_ID|TMDB_ID|SEASON|EPISODE"
    override suspend fun getUrl(
        url: String, // format: "TYPE|IMDB_ID|TMDB_ID|SEASON|EPISODE"
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val parts = url.split("|")
        if (parts.size < 5) {
            Log.e("VidSrcEmbed", "Data format invalid: $url")
            return
        }

        val type = parts[0]
        val imdbId = parts[1].takeIf { it.isNotBlank() && it != "null" }
        val tmdbId = parts[2].takeIf { it.isNotBlank() && it != "null" }
        val season = parts[3].toIntOrNull()
        val episode = parts[4].toIntOrNull()

        if (imdbId == null && tmdbId == null) {
            Log.e("VidSrcEmbed", "Missing both IMDb and TMDb ID.")
            return
        }

        val embedUrl = if (type == TvType.Movie.name) {
            // Film: https://vidsrc-embed.ru/embed/movie?imdb=tt5433140
            val idParam = imdbId?.let { "imdb=$it" } ?: tmdbId?.let { "tmdb=$it" } ?: return
            "$mainUrl/embed/movie?$idParam"
        } else { // TvSeries / AsianDrama
            if (season == null || episode == null) {
                Log.e("VidSrcEmbed", "Missing season/episode for series.")
                return
            }
            // Serial: https://vidsrc-embed.ru/embed/tv?imdb=tt0944947&season=1&episode=1
            val idParam = imdbId?.let { "imdb=$it" } ?: tmdbId?.let { "tmdb=$it" } ?: return
            "$mainUrl/embed/tv?$idParam&season=$season&episode=$episode"
        }
        
        Log.d("VidSrcEmbed", "Generated URL: $embedUrl")

        // Memberikan URL embed sebagai ExtractorLink agar Cloudstream memecahnya lebih lanjut
        callback(
            newExtractorLink(
                source = name,
                name = name,
                url = embedUrl,
                type = INFER_TYPE
            ) {
                // Menggunakan blok lambda untuk properti tambahan
                this.referer = mainUrl
                this.quality = Qualities.Unknown.value
            }
        )
    }
}
