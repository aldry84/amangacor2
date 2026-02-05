package com.AdiNgeFilm

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.extractors.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8
import java.net.URI

/* =========================
   BASE : EARNVIDS / DINGTEZUNI
   ========================= */

open class Dingtezuni : ExtractorApi() {
    override val name = "Earnvids"
    override val mainUrl = "https://dingtezuni.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val headers = mapOf(
            "User-Agent" to USER_AGENT,
            "Origin" to mainUrl
        )

        val response = app.get(getEmbedUrl(url), referer = referer)

        val script = getAndUnpack(response.text)
            ?: response.document.selectFirst("script:containsData(sources)")?.data()
            ?: return

        Regex("\"(https?://[^\"]+\\.m3u8[^\"]*)\"")
            .findAll(script)
            .forEach { match ->
                generateM3u8(
                    source = name,
                    streamUrl = fixUrl(match.groupValues[1]),
                    referer = "$mainUrl/",
                    headers = headers
                ).forEach { link ->
                    callback(link)
                }
            }
    }

    private fun getEmbedUrl(url: String): String {
        return when {
            "/d/" in url -> url.replace("/d/", "/v/")
            "/download/" in url -> url.replace("/download/", "/v/")
            "/file/" in url -> url.replace("/file/", "/v/")
            else -> url.replace("/f/", "/v/")
        }
    }
}

/* =========================
   SIMPLE EXTRACTORS
   ========================= */

class Hglink : StreamWishExtractor() {
    override val name = "Hglink"
    override val mainUrl = "https://hglink.to"
}

class Gdriveplayerto : Gdriveplayer() {
    override val mainUrl = "https://gdriveplayer.to"
}

class Playerngefilm21 : VidStack() {
    override var name = "Playerngefilm21"
    override var mainUrl = "https://playerngefilm21.rpmlive.online"
    override var requiresReferer = true
}

class P2pplay : VidStack() {
    override var name = "P2pplay"
    override var mainUrl = "https://nf21.p2pplay.pro"
    override var requiresReferer = true
}

class Shorticu : StreamWishExtractor() {
    override val name = "Shorticu"
    override val mainUrl = "https://short.icu"
}

/* =========================
   EARNVIDS MIRRORS
   ========================= */

class Movearnpre : Dingtezuni() {
    override val mainUrl = "https://movearnpre.com"
}

class Dhtpre : Dingtezuni() {
    override val mainUrl = "https://dhtpre.com"
}

class Mivalyo : Dingtezuni() {
    override val mainUrl = "https://mivalyo.com"
}

class Bingezove : Dingtezuni() {
    override val mainUrl = "https://bingezove.com"
}

/* =========================
   STREAMPLAY (FINAL FIX)
   ========================= */

class Streamplay : ExtractorApi() {
    override val name = "Streamplay"
    override val mainUrl = "https://streamplay.to"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val request = app.get(url, referer = referer)
        val redirectUrl = request.url

        val server = URI(redirectUrl).let {
            "${it.scheme}://${it.host}"
        }

        val script = request.document
            .select("script")
            .firstOrNull { it.data().contains("eval(function") }
            ?: return

        val unpacked = getAndUnpack(script.data()) ?: return

        val jsonRaw = unpacked
            .substringAfter("sources=[")
            .substringBefore("]")
            .replace("file", "\"file\"")
            .replace("label", "\"label\"")

        val sources = tryParseJson<List<Source>>("[$jsonRaw]") ?: return

        for (res in sources) {
            val fileUrl = res.file ?: continue

            val qualityValue = when (res.label) {
                "HD" -> Qualities.P720.value
                "SD" -> Qualities.P480.value
                else -> Qualities.Unknown.value
            }

            callback(
                newExtractorLink(
                    source = name,
                    name = name,
                    url = fileUrl,
                    quality = qualityValue, // ✅ DI PARAMETER
                    type = if (fileUrl.contains(".m3u8"))
                        ExtractorLinkType.M3U8
                    else
                        ExtractorLinkType.VIDEO
                ) {
                    referer = "$server/"
                }
            )
        }
    }

    data class Source(
        @com.fasterxml.jackson.annotation.JsonProperty("file")
        val file: String? = null,
        @com.fasterxml.jackson.annotation.JsonProperty("label")
        val label: String? = null
    )
}
