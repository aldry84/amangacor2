package com.AdiNgeFilm

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.extractors.Gdriveplayer
import com.lagradost.cloudstream3.extractors.VidStack
import java.net.URI

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
            "Sec-Fetch-Dest" to "empty",
            "Sec-Fetch-Mode" to "cors",
            "Sec-Fetch-Site" to "cross-site",
            "Origin" to mainUrl,
            "User-Agent" to USER_AGENT,
        )
        
        val response = app.get(getEmbedUrl(url), referer = referer)
        val script = if (!getPacked(response.text).isNullOrEmpty()) {
            var result = getAndUnpack(response.text)
            if(result.contains("var links")){
                result = result.substringAfter("var links")
            }
            result
        } else {
            response.document.selectFirst("script:containsData(sources:)")?.data()
        } ?: return

        Regex(":\\s*\"(.*?m3u8.*?)\"").findAll(script).forEach { m3u8Match ->
            generateM3u8(
                name,
                fixUrl(m3u8Match.groupValues[1]),
                referer = "$mainUrl/",
                headers = headers
            ).forEach(callback)
        }
    }

    private fun getEmbedUrl(url: String): String {
        return when {
            url.contains("/d/") -> url.replace("/d/", "/v/")
            url.contains("/download/") -> url.replace("/download/", "/v/")
            url.contains("/file/") -> url.replace("/file/", "/v/")
            else -> url.replace("/f/", "/v/")
        }
    }
}

class Hglink : StreamWishExtractor() {
    override val name = "Hglink"
    override val mainUrl = "https://hglink.to"
}

class Gdriveplayerto : Gdriveplayer() {
    override val mainUrl: String = "https://gdriveplayer.to"
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
    override val name: String = "Shorticu"
    override val mainUrl: String = "https://short.icu"
}

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

// --- STREAMPLAY CORE (Support Reflection & Anti-Judol) ---
open class Streamplay : ExtractorApi() {
    override val name = "Streamplay"
    override val mainUrl = "https://stre4mplay.one" 
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        // Trik: Jika URL masuk masih pakai domain lama/bermasalah, paksa ganti ke domain baru
        val fixedUrl = url.replace("streamplay.to", "stre4mplay.one")
                          .replace("streamplay.cc", "stre4mplay.one")
                          .replace("streamplay.me", "stre4mplay.one")
                          .replace("streamplay.live", "stre4mplay.one")

        val request = app.get(fixedUrl, referer = referer)
        val redirectUrl = request.url
        val mainServer = URI(redirectUrl).let {
            "${it.scheme}://${it.host}"
        }
        val key = redirectUrl.substringAfter("embed-").substringBefore(".html")
        
        val captchaKey = request.document.select("script")
            .find { it.data().contains("sitekey:") }?.data()
            ?.substringAfterLast("sitekey: '")?.substringBefore("',")
            
        val token = if (!captchaKey.isNullOrEmpty()) {
             com.lagradost.cloudstream3.APIHolder.getCaptchaToken(
                redirectUrl,
                captchaKey,
                referer = "$mainServer/"
            )
        } else {
            null 
        }

        app.post(
            "$mainServer/player-$key-488x286.html", 
            data = mapOf(
                "op" to "embed",
                "token" to (token ?: "")
            ),
            referer = redirectUrl,
            headers = mapOf(
                "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
                "Content-Type" to "application/x-www-form-urlencoded",
                "User-Agent" to USER_AGENT
            )
        ).document.select("script").find { script ->
            script.data().contains("eval(function(p,a,c,k,e,d)")
        }?.let {
            val unpacked = getAndUnpack(it.data())
            val data = unpacked.substringAfter("sources=[").substringBefore(",desc")
                .replace("file", "\"file\"")
                .replace("label", "\"label\"")
            
            val jsonString = "[$data]"
            
            tryParseJson<List<Source>>(jsonString)?.forEach { res ->
                val fileUrl = res.file ?: return@forEach
                val quality = when (res.label) {
                    "HD" -> Qualities.P720.value
                    "SD" -> Qualities.P480.value
                    else -> Qualities.Unknown.value
                }

                // --- REFLECTION BYPASS ---
                // Menggunakan newExtractorLink + Reflection untuk menghindari error 'val cannot be reassigned'
                val link = newExtractorLink(this.name, this.name, fileUrl)
                try {
                    val refField = ExtractorLink::class.java.getDeclaredField("referer")
                    refField.isAccessible = true
                    refField.set(link, "$mainServer/")

                    val qualField = ExtractorLink::class.java.getDeclaredField("quality")
                    qualField.isAccessible = true
                    qualField.setInt(link, quality)

                    if (fileUrl.contains("m3u8")) {
                         val typeField = ExtractorLink::class.java.getDeclaredField("type")
                         typeField.isAccessible = true
                         typeField.set(link, ExtractorLinkType.M3U8)
                    }

                    val headersField = ExtractorLink::class.java.getDeclaredField("headers")
                    headersField.isAccessible = true
                    headersField.set(link, mapOf("User-Agent" to USER_AGENT))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                
                callback.invoke(link)
            }
        }
    }

    data class Source(
        @com.fasterxml.jackson.annotation.JsonProperty("file") val file: String? = null,
        @com.fasterxml.jackson.annotation.JsonProperty("label") val label: String? = null,
    )
}

// --- ALIAS UNTUK LINK XSHOTCOK ---
class Xshotcok : Streamplay() {
    override val name = "Streamplay"
    override val mainUrl = "https://xshotcok.com"
}
