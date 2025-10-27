package com.adimoviemaze

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.ExtractorLink
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.getQualityFromName
import org.jsoup.nodes.Element
import java.util.Base64

object Extractors {
    suspend fun loadLinksFromUrl(
        data: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document

        // Find server links or embeds
        document.select("div.server-list a, iframe[src]").apmap { element ->
            val link = fixUrl(element.attr("href") ?: element.attr("src"))
            if (link.contains("embed") || link.contains("player")) {
                // Load extractor for embedded players
                loadExtractor(link, data, subtitleCallback, callback)
            } else {
                // If direct server, fetch and decode if needed
                val res = app.get(link).text
                // Look for obfuscated JS
                val jsCode = Regex("""eval\('(.*?)'\)""").find(res)?.groupValues?.get(1)
                if (jsCode != null) {
                    val decoded = String(Base64.getDecoder().decode(jsCode))
                    val videoUrl = Regex("""file:\s*"([^"]+)"""").find(decoded)?.groupValues?.get(1)
                    if (videoUrl != null) {
                        callback.invoke(
                            ExtractorLink(
                                "AdiMovieMaze",
                                "AdiMovieMaze",
                                videoUrl,
                                "",
                                getQualityFromName("HD"),
                                isM3u8 = videoUrl.contains(".m3u8")
                            )
                        )
                    }
                }
            }
        }

        return true
    }
}
