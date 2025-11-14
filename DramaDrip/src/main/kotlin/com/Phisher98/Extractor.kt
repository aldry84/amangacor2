package com.Phisher98

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.M3u8Helper
import okhttp3.FormBody
import org.json.JSONObject
import java.net.URI
import kotlin.text.Regex

class Driveseed : ExtractorApi() {
    override val name: String = "Driveseed"
    override val mainUrl: String = "https://driveseed.org"
    override val requiresReferer = false

    private fun getIndexQuality(str: String?): Int {
        return Regex("(\\d{3,4})[pP]").find(str ?: "")?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: Qualities.Unknown.value
    }

    private suspend fun CFType1(url: String): List<String> {
        return runCatching {
            app.get("$url?type=1").document
                .select("a.btn-success")
                .mapNotNull { it.attr("href").takeIf { href -> href.startsWith("http") } }
        }.getOrElse {
            Log.e("Driveseed", "CFType1 error: ${it.message}")
            emptyList()
        }
    }

    private suspend fun resumeCloudLink(baseUrl: String, path: String): String? {
        return runCatching {
            app.get(baseUrl + path).document
                .selectFirst("a.btn-success")?.attr("href")
                ?.takeIf { it.startsWith("http") }
        }.getOrElse {
            Log.e("Driveseed", "ResumeCloud error: ${it.message}")
            null
        }
    }

    private suspend fun resumeBot(url: String): String? {
        return runCatching {
            val response = app.get(url)
            val docString = response.document.toString()
            val ssid = response.cookies["PHPSESSID"].orEmpty()
            val token = Regex("formData\\.append\\('token', '([a-f0-9]+)'\\)").find(docString)?.groupValues?.getOrNull(1).orEmpty()
            val path = Regex("fetch\\('/download\\?id=([a-zA-Z0-9/+]+)'").find(docString)?.groupValues?.getOrNull(1).orEmpty()
            val baseUrl = url.substringBefore("/download")

            if (token.isEmpty() || path.isEmpty()) return@runCatching null

            val json = app.post(
                "$baseUrl/download?id=$path",
                requestBody = FormBody.Builder().addEncoded("token", token).build(),
                headers = mapOf("Accept" to "*/*", "Origin" to baseUrl, "Sec-Fetch-Site" to "same-origin"),
                cookies = mapOf("PHPSESSID" to ssid),
                referer = url
            ).text

            JSONObject(json).getString("url").takeIf { it.startsWith("http") }
        }.getOrElse {
            Log.e("Driveseed", "ResumeBot error: ${it.message}")
            null
        }
    }

    private suspend fun instantLink(finallink: String): String? {
        return runCatching {
            val uri = URI(finallink)
            val host = uri.host ?: if (finallink.contains("video-leech")) "video-leech.pro" else "video-seed.pro"

            val token = finallink.substringAfter("url=")
            val response = app.post(
                "https://$host/api",
                data = mapOf("keys" to token),
                referer = finallink,
                headers = mapOf("x-token" to host)
            ).text

            response.substringAfter("url\":\"")
                .substringBefore("\",\"name")
                .replace("\\/", "/")
                .takeIf { it.startsWith("http") }
        }.getOrElse {
            Log.e("Driveseed", "InstantLink error: ${it.message}")
            null
        }
    }

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val Basedomain = getBaseUrl(url)

        val document = try {
            if (url.contains("r?key=")) {
                val temp = app.get(url).document.selectFirst("script")
                    ?.data()
                    ?.substringAfter("replace(\"")
                    ?.substringBefore("\")")
                    .orEmpty()
                app.get(mainUrl + temp).document
            } else {
                app.get(url).document
            }
        } catch (e: Exception) {
            Log.e("Driveseed", "getUrl page load error: ${e.message}")
            return
        }

        val qualityText = document.selectFirst("li.list-group-item")?.text().orEmpty()
        val rawFileName = qualityText.replace("Name : ", "").trim()
        val fileName = cleanTitle(rawFileName)
        val size = document.selectFirst("li:nth-child(3)")?.text().orEmpty().replace("Size : ", "").trim()

        val labelExtras = buildString {
            if (fileName.isNotEmpty()) append("[$fileName]")
            if (size.isNotEmpty()) append("[$size]")
        }

        document.select("div.text-center > a").forEach { element ->
            val text = element.text()
            val href = element.attr("href")

            if (href.isNotBlank()) {
                when {
                    text.contains("Instant Download", ignoreCase = true) -> {
                        instantLink(href)?.let { link ->
                            callback(
                                newExtractorLink(
                                    "$name Instant(Download)(VLC) $labelExtras",
                                    "$name Instant(Download)(VLC) $labelExtras",
                                    link,
                                    INFER_TYPE
                                ) {
                                    this.quality = getIndexQuality(qualityText)
                                }
                            )
                        }
                    }

                    text.contains("Resume Worker Bot", ignoreCase = true) -> {
                        resumeBot(href)?.let { link ->
                            callback(
                                newExtractorLink(
                                    "$name ResumeBot $labelExtras",
                                    "$name ResumeBot $labelExtras",
                                    link,
                                    INFER_TYPE
                                ) {
                                    this.quality = getIndexQuality(qualityText)
                                }
                            )
                        }
                    }

                    text.contains("Direct Links", ignoreCase = true) -> {
                        CFType1(Basedomain + href).forEach { link ->
                            callback(
                                newExtractorLink(
                                    "$name CF Type1 $labelExtras",
                                    "$name CF Type1 $labelExtras",
                                    link,
                                    INFER_TYPE
                                ) {
                                    this.quality = getIndexQuality(qualityText)
                                }
                            )
                        }
                    }

                    text.contains("Resume Cloud", ignoreCase = true) -> {
                        resumeCloudLink(Basedomain, href)?.let { link ->
                            callback(
                                newExtractorLink(
                                    "$name ResumeCloud $labelExtras",
                                    "$name ResumeCloud $labelExtras",
                                    link,
                                    INFER_TYPE
                                ) {
                                    this.quality = getIndexQuality(qualityText)
                                }
                            )
                        }
                    }

                    text.contains("Cloud Download", ignoreCase = true) -> {
                        callback(
                            newExtractorLink(
                                "$name Cloud Download $labelExtras",
                                "$name Cloud Download $labelExtras",
                                href,
                                INFER_TYPE
                            ) {
                                this.quality = getIndexQuality(qualityText)
                            }
                        )
                    }
                }
            }
        }
    }
}

// ========== JENIUSPLAY EXTRACTOR ==========
class Jeniusplay : ExtractorApi() {
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

            // Get video source
            val response = app.post(
                url = "$mainUrl/player/index.php?data=$hash&do=getVideo",
                data = mapOf("hash" to hash, "r" to "$referer"),
                referer = url,
                headers = mapOf("X-Requested-With" to "XMLHttpRequest")
            )
            val jsonResponse = response.parsed<JeniusplayResponse>()
            val m3uLink = jsonResponse.videoSource

            // Callback untuk video stream
            callback(
                newExtractorLink(
                    this.name,
                    this.name,
                    m3uLink,
                    ExtractorLinkType.M3U8
                ) {
                    this.referer = url
                }
            )

            // Extract subtitles
            document.select("script").map { script ->
                if (script.data().contains("eval(function(p,a,c,k,e,d)")) {
                    val subData = getAndUnpack(script.data())
                        .substringAfter("\"tracks\":[")
                        .substringBefore("],")
                    
                    tryParseJson<List<JeniusplaySubtitle>>("[$subData]")?.map { subtitle ->
                        subtitleCallback.invoke(
                            SubtitleFile(
                                getLanguage(subtitle.label ?: ""),
                                subtitle.file
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("Jeniusplay", "Failed to extract from Jeniusplay: ${e.message}")
        }
    }

    private fun getLanguage(str: String): String {
        return when {
            str.contains("indonesia", true) || str.contains("bahasa", true) -> "Indonesian"
            str.contains("english", true) || str.contains("inggris", true) -> "English"
            else -> str
        }
    }
}

// Data classes untuk Jeniusplay
data class JeniusplayResponse(
    @JsonProperty("hls") val hls: Boolean,
    @JsonProperty("videoSource") val videoSource: String,
    @JsonProperty("securedLink") val securedLink: String?,
)

data class JeniusplaySubtitle(
    @JsonProperty("kind") val kind: String?,
    @JsonProperty("file") val file: String,
    @JsonProperty("label") val label: String?,
)

// ========== ADDITIONAL EXTRACTORS ==========
class StreamTake : ExtractorApi() {
    override val name = "StreamTake"
    override val mainUrl = "https://streamtake.xyz"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val document = app.get(url, referer = referer).document
        
        // Extract m3u8 from script tags
        val script = document.select("script").find { it.data().contains("sources:") }?.data()
        val m3u8Url = Regex("""file:\s*["'](.*?\.m3u8)["']""").find(script ?: "")?.groupValues?.get(1)
        
        if (!m3u8Url.isNullOrEmpty()) {
            val quality = Regex("""(\d+)p""").find(script ?: "")?.groupValues?.get(1)?.toIntOrNull() 
                ?: Qualities.Unknown.value
            
            callback(
                newExtractorLink(
                    name,
                    name,
                    m3u8Url,
                    ExtractorLinkType.M3U8
                ) {
                    this.referer = url
                    this.quality = quality
                }
            )
        }
    }
}

class VidMoly : ExtractorApi() {
    override val name = "VidMoly"
    override val mainUrl = "https://vidmoly.to"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val document = app.get(url, referer = referer).document
        
        // Multiple extraction methods for VidMoly
        val sources = listOf(
            // Method 1: Direct m3u8 in source tag
            document.select("source").attr("src"),
            // Method 2: In script tags
            extractFromScript(document, """file:\s*["'](.*?)["']"""),
            // Method 3: In iframe
            document.select("iframe").attr("src")
        ).filter { it.isNotBlank() && it.contains("m3u8") }
        
        sources.forEach { source ->
            val finalUrl = if (source.startsWith("//")) "https:$source" else source
            callback(
                newExtractorLink(
                    name,
                    name,
                    finalUrl,
                    ExtractorLinkType.M3U8
                ) {
                    this.referer = url
                }
            )
        }
    }
    
    private fun extractFromScript(document: org.jsoup.nodes.Document, regex: String): String {
        val script = document.select("script").find { it.data().contains(regex) }?.data()
        return Regex(regex).find(script ?: "")?.groupValues?.get(1) ?: ""
    }
}

class FileMoon : ExtractorApi() {
    override val name = "FileMoon"
    override val mainUrl = "https://filemoon.sx"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val response = app.get(url, referer = referer)
        val document = response.document
        
        // Extract from player configuration
        val script = document.select("script").find { it.data().contains("sources") }?.data()
        
        // Method 1: Direct m3u8 in sources array
        val m3u8Url = Regex("""sources:\s*\[{\s*file:\s*["'](.*?\.m3u8)["']""").find(script ?: "")?.groupValues?.get(1)
        
        // Method 2: From eval script
        val evalScript = document.select("script").find { it.data().contains("eval") }?.data()
        val decodedScript = if (!evalScript.isNullOrEmpty()) {
            decodeEvalScript(evalScript)
        } else ""
        
        val finalUrl = m3u8Url ?: extractM3u8FromDecodedScript(decodedScript)
        
        if (!finalUrl.isNullOrEmpty()) {
            callback(
                newExtractorLink(
                    name,
                    name,
                    finalUrl,
                    ExtractorLinkType.M3U8
                ) {
                    this.referer = url
                }
            )
        }
    }
    
    private fun decodeEvalScript(script: String): String {
        return try {
            // Simple eval decoding
            script.substringAfter("eval(\"").substringBefore("\")")
        } catch (e: Exception) {
            ""
        }
    }
    
    private fun extractM3u8FromDecodedScript(script: String): String? {
        return Regex("""(https?://[^\s"'<>]*?\.m3u8[^\s"'<>]*)""").find(script)?.groupValues?.get(1)
    }
}

class DUpload : ExtractorApi() {
    override val name = "DUpload"
    override val mainUrl = "https://dupload.org"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val document = app.get(url).document
        
        // DUpload usually has direct video links
        val videoElement = document.select("video source").first()
        val videoUrl = videoElement?.attr("src")
        
        if (!videoUrl.isNullOrEmpty()) {
            val quality = extractQualityFromUrl(videoUrl)
            val finalUrl = fixUrl(videoUrl, mainUrl)
            
            callback(
                newExtractorLink(
                    name,
                    name,
                    finalUrl,
                    INFER_TYPE
                ) {
                    this.referer = url
                    this.quality = quality
                }
            )
        }
    }
    
    private fun extractQualityFromUrl(url: String): Int {
        return when {
            "1080" in url -> Qualities.FullHd.value
            "720" in url -> Qualities.HD.value
            "480" in url -> Qualities.SD.value
            "360" in url -> Qualities.Low.value
            else -> Qualities.Unknown.value
        }
    }
    
    private fun fixUrl(url: String, domain: String): String {
        return if (url.startsWith("http")) url else "$domain$url"
    }
}

class MultiQualityM3u8 : ExtractorApi() {
    override val name = "MultiQualityM3u8"
    override val mainUrl = ""
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        // For m3u8 files with multiple quality levels
        M3u8Helper.generateM3u8(
            name,
            url,
            referer = referer ?: ""
        ).forEach(callback)
    }
}

// ========== HELPER FUNCTIONS ==========
fun cleanTitle(title: String): String {
    val parts = title.split(".", "-", "_")

    val qualityTags = listOf(
        "WEBRip", "WEB-DL", "WEB", "BluRay", "HDRip", "DVDRip", "HDTV",
        "CAM", "TS", "R5", "DVDScr", "BRRip", "BDRip", "DVD", "PDTV",
        "HD"
    )

    val audioTags = listOf(
        "AAC", "AC3", "DTS", "MP3", "FLAC", "DD5", "EAC3", "Atmos"
    )

    val subTags = listOf(
        "ESub", "ESubs", "Subs", "MultiSub", "NoSub", "EnglishSub", "HindiSub"
    )

    val codecTags = listOf(
        "x264", "x265", "H264", "HEVC", "AVC"
    )

    val startIndex = parts.indexOfFirst { part ->
        qualityTags.any { tag -> part.contains(tag, ignoreCase = true) }
    }

    val endIndex = parts.indexOfLast { part ->
        subTags.any { tag -> part.contains(tag, ignoreCase = true) } ||
                audioTags.any { tag -> part.contains(tag, ignoreCase = true) } ||
                codecTags.any { tag -> part.contains(tag, ignoreCase = true) }
    }

    return if (startIndex != -1 && endIndex != -1 && endIndex >= startIndex) {
        parts.subList(startIndex, endIndex + 1).joinToString(".")
    } else if (startIndex != -1) {
        parts.subList(startIndex, parts.size).joinToString(".")
    } else {
        parts.takeLast(3).joinToString(".")
    }
}
