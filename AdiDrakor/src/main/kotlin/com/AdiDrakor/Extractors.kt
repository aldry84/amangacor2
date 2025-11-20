package com.AdiDrakor

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import okhttp3.FormBody
import java.net.URI
import java.net.URL

// ==============================
// EXISTING EXTRACTOR
// ==============================

open class Jeniusplay2 : ExtractorApi() {
    override val name = "Jeniusplay"
    override val mainUrl = "https://jeniusplay.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val document = app.get(url, referer = "$mainUrl/").document
            val hash = url.split("/").last().substringAfter("data=")

            val m3uLink = app.post(
                url = "$mainUrl/player/index.php?data=$hash&do=getVideo",
                data = mapOf("hash" to hash, "r" to "$referer"),
                referer = url,
                headers = mapOf("X-Requested-With" to "XMLHttpRequest")
            ).parsedSafe<ResponseSource>()?.videoSource

            if (m3uLink != null) {
                callback.invoke(
                    newExtractorLink(
                        this.name,
                        this.name,
                        m3uLink,
                        ExtractorLinkType.M3U8
                    ) {
                        this.referer = url
                    }
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    data class ResponseSource(
        @JsonProperty("hls") val hls: Boolean? = null,
        @JsonProperty("videoSource") val videoSource: String? = null,
    )
}

// ==============================
// REQUIRED FOR XDMOVIES
// ==============================

class HubCloud : ExtractorApi() {
    override val name = "Hub-Cloud"
    override val mainUrl = "https://hubcloud.club" // Default base, dynamic handling below
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val realUrl = url.takeIf {
            try { URL(it); true } catch (e: Exception) { false }
        } ?: return

        val baseUrl = getBaseUrl(realUrl)

        val href = try {
            if ("hubcloud.php" in realUrl) {
                realUrl
            } else {
                val rawHref = app.get(realUrl).document.select("#download").attr("href")
                if (rawHref.startsWith("http", ignoreCase = true)) {
                    rawHref
                } else {
                    baseUrl.trimEnd('/') + "/" + rawHref.trimStart('/')
                }
            }
        } catch (e: Exception) {
            ""
        }
        if (href.isBlank()) return

        val document = app.get(href).document
        val header = document.selectFirst("div.card-header")?.text().orEmpty()
        val quality = Regex("(\\d{3,4})[pP]").find(header)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: Qualities.Unknown.value

        document.select("div.card-body h2 a.btn").amap { element ->
            val link = element.attr("href")
            val text = element.text()

            when {
                text.contains("pixeldra", ignoreCase = true) || text.contains("pixel", ignoreCase = true) -> {
                    PixelDrain().getUrl(link, referer, subtitleCallback, callback)
                }
                text.contains("gdlink", ignoreCase = true) || text.contains("gdflix", ignoreCase = true) -> {
                    GDFlix().getUrl(link, referer, subtitleCallback, callback)
                }
                else -> {
                    loadExtractor(link, "", subtitleCallback, callback)
                }
            }
        }
    }

    private fun getBaseUrl(url: String): String {
        return try {
            URI(url).let { "${it.scheme}://${it.host}" }
        } catch (_: Exception) {
            ""
        }
    }
}

open class PixelDrain : ExtractorApi() {
    override val name = "PixelDrain"
    override val mainUrl = "https://pixeldrain.com"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val mId = Regex("/u/(.*)").find(url)?.groupValues?.get(1)
        val finalUrl = if (mId.isNullOrEmpty()) url else "$mainUrl/api/file/${mId}?download"
        
        callback.invoke(
            newExtractorLink(
                this.name,
                this.name,
                url = finalUrl
            ) {
                this.referer = url
                this.quality = Qualities.Unknown.value
            }
        )
    }
}

open class GDFlix : ExtractorApi() {
    override val name = "GDFlix"
    override val mainUrl = "https://new6.gdflix.dad"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val newUrl = try {
            app.get(url).document.selectFirst("meta[http-equiv=refresh]")
                ?.attr("content")?.substringAfter("url=")
        } catch (e: Exception) {
            null
        } ?: url

        val document = app.get(newUrl).document
        
        document.select("div.text-center a").amap { anchor ->
            val text = anchor.text()
            if (text.contains("Instant DL", ignoreCase = true) || text.contains("DIRECT DL", ignoreCase = true)) {
                 val link = anchor.attr("href")
                 callback.invoke(
                    newExtractorLink(name, "$name [Direct]", link)
                 )
            } else if (text.contains("PixelDrain", ignoreCase = true)) {
                 PixelDrain().getUrl(anchor.attr("href"), referer, subtitleCallback, callback)
            }
        }
    }
}
