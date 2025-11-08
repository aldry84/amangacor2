package com.AdicinemaxNew

import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.INFER_TYPE
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.URI

object AdicinemaxNewExtractor {

    /**
     * Fungsi: Primary streaming source - Vidsrc.cc
     * Menggunakan TMDB ID dengan sistem VRF encryption
     */
    suspend fun invokeVidsrcCc(
        id: Int? = null,
        imdbId: String? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit
    ) {
        if (id == null) return

        val url = if (season == null) {
            "https://vidsrc.cc/v2/embed/movie/$id"
        } else {
            "https://vidsrc.cc/v2/embed/tv/$id/$season/$episode"
        }

        try {
            val document = app.get(url).document
            val scriptContent = document.selectFirst("script:containsData(window.videoSource)")?.data()
            
            if (scriptContent != null) {
                val sourceRegex = Regex("""window\.videoSource\s*=\s*["'](.*?)["']""")
                val videoUrl = sourceRegex.find(scriptContent)?.groupValues?.get(1)
                
                videoUrl?.let { source ->
                    callback.invoke(
                        newExtractorLink(
                            "Vidsrc.cc",
                            "Vidsrc.cc [Primary]",
                            url = source,
                            type = INFER_TYPE
                        ) {
                            this.referer = "https://vidsrc.cc/"
                            this.quality = Qualities.P1080.value
                        }
                    )
                }
            }
        } catch (e: Exception) {
            // Fallback ke metode alternatif jika metode utama gagal
            invokeVidsrcCcAlternative(id, imdbId, season, episode, callback)
        }
    }

    /**
     * Fungsi: Backup method untuk Vidsrc.cc dengan API approach
     */
    private suspend fun invokeVidsrcCcAlternative(
        id: Int? = null,
        imdbId: String? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val apiUrl = if (season == null) {
                "https://vidsrc.cc/v2/$imdbId/media.json"
            } else {
                "https://vidsrc.cc/v2/$imdbId/$season/$episode/media.json"
            }

            val response = app.get(apiUrl)
            if (response.code == 200) {
                val json = JSONObject(response.text)
                val result = json.optJSONObject("result")
                val url = result?.optString("url")

                url?.let { source ->
                    callback.invoke(
                        newExtractorLink(
                            "Vidsrc.cc",
                            "Vidsrc.cc [Backup]",
                            url = source,
                            type = ExtractorLinkType.M3U8
                        ) {
                            this.referer = "https://vidsrc.cc/"
                            this.quality = Qualities.P1080.value
                        }
                    )
                }
            }
        } catch (e: Exception) {
            // Log error but don't throw - system will try next extractor
        }
    }

    /**
     * Fungsi: Alternative streaming source - Vidsrc.xyz
     * Menggunakan IMDb ID dengan sistem decrypt yang berbeda
     */
    suspend fun invokeVidsrcXyz(
        id: String? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit
    ) {
        if (id.isNullOrBlank()) return

        val url = if (season == null) {
            "https://vidsrc.xyz/embed/movie?imdb=$id"
        } else {
            "https://vidsrc.xyz/embed/tv?imdb=$id&season=$season&episode=$episode"
        }

        try {
            val document = app.get(url).document
            val iframeSrc = document.selectFirst("iframe")?.attr("src")
            
            iframeSrc?.let { src ->
                // Extract dari iframe
                val iframeDoc = app.get(src).document
                val videoSource = extractVideoSourceFromIframe(iframeDoc)
                
                videoSource?.let { source ->
                    callback.invoke(
                        newExtractorLink(
                            "Vidsrc.xyz", 
                            "Vidsrc.xyz [Alternative]",
                            url = source,
                            type = INFER_TYPE
                        ) {
                            this.referer = "https://vidsrc.xyz/"
                            this.quality = Qualities.P1080.value
                        }
                    )
                }
            }
        } catch (e: Exception) {
            // Log error - this is a backup extractor
        }
    }

    /**
     * Helper function untuk extract video source dari iframe
     */
    private fun extractVideoSourceFromIframe(document: org.jsoup.nodes.Document): String? {
        // Method 1: Cari dalam script tags
        val scripts = document.select("script")
        for (script in scripts) {
            val data = script.data()
            if (data.contains("source") && data.contains("http")) {
                val regex = Regex("""src\s*:\s*["'](https?://[^"']+)["']""")
                return regex.find(data)?.groupValues?.get(1)
            }
        }
        
        // Method 2: Cari dalam video tags
        val video = document.selectFirst("video source")
        return video?.attr("src")
    }
}
