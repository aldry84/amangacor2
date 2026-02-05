package com.AdiNgeFilm

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.extractors.Gdriveplayer
import com.lagradost.cloudstream3.extractors.VidStack
import java.net.URI

// ================= DINGTEZUNI BASE =================
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
            "Origin" to mainUrl,
            "User-Agent" to USER_AGENT
        )

        val response = app.get(getEmbedUrl(url), referer = referer)
        val script = if (!getPacked(response.text).isNullOrEmpty()) {
            var result = getAndUnpack(response.text)
            if (result.contains("var links")) {
                result = result.substringAfter("var links")
            }
            result
        } else {
            response.document.selectFirst("script:containsData(sources:)")?.data()
        } ?: return

        Regex(":\\s*\"(.*?m3u8.*?)\"").findAll(script).forEach { match ->
            generateM3u8(
                name,
                fixUrl(match.groupValues[1]),
                referer = "$mainUrl/",
                headers = headers
            ).forEach(callback)
        }
    }

    private fun getEmbedUrl(url: String): String = when {
        url.contains("/d/") -> url.replace("/d/", "/v/")
        url.contains("/download/") -> url.replace("/download/", "/v/")
        url.contains("/file/") -> url.replace("/file/", "/v/")
        else -> url.replace("/f/", "/v/")
    }
}

// ================= SIMPLE EXTRACTORS =================
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

// ================= MIRROR SITES =================
class Movearnpre : Dingtezuni() {
    override var name = "Earnvids"
    override var mainUrl = "https://movearnpre.com"
}

class Dhtpre : Dingtezuni() {
    override var name = "Earnvids"
    override var mainUrl = "https://dhtpre.com"
}

class Mivalyo : Dingtezuni() {
    override var name = "Earnvids"
    override var mainUrl = "https://mivalyo.com"
}

class Bingezove : Dingtezuni() {
    override var name = "Earnvids"
    override var mainUrl = "https://bingezove.com"
}

// ================= STREAMPLAY (API COMPATIBLE) =================
open class Streamplay : ExtractorApi() {
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

        val mainServer = URI(redirectUrl).let {
            "${it.scheme}://${it.host}"
        }

        val key = redirectUrl.substringAfter("embed-").substringBefore(".html")

        val captchaKey = request.document.select("script")
            .find { it.data().contains("sitekey:") }
            ?.data()
            ?.substringAfterLast("sitekey: '")
            ?.substringBefore("',")

        val token = if (!captchaKey.isNullOrEmpty()) {
            com.lagradost.cloudstream3.APIHolder.getCaptchaToken(
                redirectUrl,
                captchaKey,
                referer = "$mainServer/"
            )
        } else null

        app.post(
            "$mainServer/player-$key-488x286.html",
            data = mapOf(
                "op" to "embed",
                "token" to (token ?: "")
            ),
            referer = redirectUrl,
            headers = mapOf(
                "User-Agent" to USER_AGENT
            )
        ).document.select("script")
            .find { it.data().contains("eval(function(p,a,c,k,e,d)") }
            ?.let {
                val unpacked = getAndUnpack(it.data())
                val data = unpacked
                    .substringAfter("sources=[")
                    .substringBefore(",desc")
                    .replace("file", "\"file\"")
                    .replace("label", "\"label\"")

                val jsonString = "[$data]"

                tryParseJson<List<Source>>(jsonString)?.forEach { res ->
                    val fileUrl = res.file ?: return@forEach

                    callback.invoke(
                        newExtractorLink(
                            source = this.name,
                            name = this.name,
                            url = fileUrl
                        ) {
                            referer = "$mainServer/"
                            isM3u8 = fileUrl.contains("m3u8")
                        }
                    )
                }
            }
    }

    data class Source(
        @com.fasterxml.jackson.annotation.JsonProperty("file")
        val file: String? = null,

        @com.fasterxml.jackson.annotation.JsonProperty("label")
        val label: String? = null
    )
}
