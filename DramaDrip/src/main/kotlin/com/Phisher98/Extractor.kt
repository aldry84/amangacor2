package com.Phisher98

import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.delay
import okhttp3.FormBody
import org.json.JSONObject
import java.net.URI
import kotlin.text.Regex

class Driveseed : ExtractorApi() {
    override val name: String = "Driveseed"
    override val mainUrl: String = "https://driveseed.org"
    override val requiresReferer = false

    // Enhanced error handling with retry
    private suspend fun <T> executeWithRetry(
        operation: String,
        maxRetries: Int = 3,
        block: suspend () -> T?
    ): T? {
        repeat(maxRetries) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                Log.e("Driveseed", "$operation attempt ${attempt + 1} failed: ${e.message}")
                if (attempt == maxRetries - 1) throw e
                delay(1000L * (attempt + 1)) // Exponential backoff
            }
        }
        return null
    }

    private fun getIndexQuality(str: String?): Int {
        return try {
            Regex("(\\d{3,4})[pP]").find(str ?: "")?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: Qualities.Unknown.value
        } catch (e: Exception) {
            Log.e("Quality", "Failed to parse quality from: $str")
            Qualities.Unknown.value
        }
    }

    private suspend fun CFType1(url: String): List<String> {
        return executeWithRetry("CFType1") {
            app.get("$url?type=1").document
                .select("a.btn-success")
                .mapNotNull { it.attr("href").takeIf { href -> href.startsWith("http") } }
        } ?: emptyList()
    }

    private suspend fun resumeCloudLink(baseUrl: String, path: String): String? {
        return executeWithRetry("ResumeCloud") {
            app.get(baseUrl + path).document
                .selectFirst("a.btn-success")?.attr("href")
                ?.takeIf { it.startsWith("http") }
        }
    }

    private suspend fun resumeBot(url: String): String? {
        return executeWithRetry("ResumeBot") {
            val response = app.get(url)
            val docString = response.document.toString()
            val ssid = response.cookies["PHPSESSID"].orEmpty()
            val token = Regex("formData\\.append\\('token', '([a-f0-9]+)'\\)").find(docString)?.groupValues?.getOrNull(1).orEmpty()
            val path = Regex("fetch\\('/download\\?id=([a-zA-Z0-9/+]+)'").find(docString)?.groupValues?.getOrNull(1).orEmpty()
            val baseUrl = url.substringBefore("/download")

            if (token.isEmpty() || path.isEmpty()) {
                Log.e("ResumeBot", "Missing token or path")
                return@executeWithRetry null
            }

            val json = app.post(
                "$baseUrl/download?id=$path",
                requestBody = FormBody.Builder().addEncoded("token", token).build(),
                headers = mapOf("Accept" to "*/*", "Origin" to baseUrl, "Sec-Fetch-Site" to "same-origin"),
                cookies = mapOf("PHPSESSID" to ssid),
                referer = url
            ).text

            JSONObject(json).getString("url").takeIf { it.startsWith("http") }
        }
    }

    private suspend fun instantLink(finallink: String): String? {
        return executeWithRetry("InstantLink") {
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
            executeWithRetry("PageLoad") {
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
            }
        } catch (e: Exception) {
            Log.e("Driveseed", "getUrl page load failed after retries: ${e.message}")
            return
        }

        if (document == null) {
            Log.e("Driveseed", "Failed to load document")
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

        val links = document.select("div.text-center > a")
        if (links.isEmpty()) {
            Log.w("Driveseed", "No download links found on page")
            return
        }

        links.forEach { element ->
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
                                    url = link,
                                    INFER_TYPE
                                ) {
                                    this.quality = getIndexQuality(qualityText)
                                }
                            )
                        } ?: Log.w("Driveseed", "Instant download link extraction failed")
                    }

                    text.contains("Resume Worker Bot", ignoreCase = true) -> {
                        resumeBot(href)?.let { link ->
                            callback(
                                newExtractorLink(
                                    "$name ResumeBot $labelExtras",
                                    "$name ResumeBot $labelExtras",
                                    url = link
                                ) {
                                    this.quality = getIndexQuality(qualityText)
                                }
                            )
                        } ?: Log.w("Driveseed", "Resume bot link extraction failed")
                    }

                    text.contains("Direct Links", ignoreCase = true) -> {
                        val directLinks = CFType1(Basedomain + href)
                        if (directLinks.isNotEmpty()) {
                            directLinks.forEach { link ->
                                callback(
                                    newExtractorLink(
                                        "$name CF Type1 $labelExtras",
                                        "$name CF Type1 $labelExtras",
                                        url = link
                                    ) {
                                        this.quality = getIndexQuality(qualityText)
                                    }
                                )
                            }
                        } else {
                            Log.w("Driveseed", "No direct links found")
                        }
                    }

                    text.contains("Resume Cloud", ignoreCase = true) -> {
                        resumeCloudLink(Basedomain, href)?.let { link ->
                            callback(
                                newExtractorLink(
                                    "$name ResumeCloud $labelExtras",
                                    "$name ResumeCloud $labelExtras",
                                    url = link
                                ) {
                                    this.quality = getIndexQuality(qualityText)
                                }
                            )
                        } ?: Log.w("Driveseed", "Resume cloud link extraction failed")
                    }

                    text.contains("Cloud Download", ignoreCase = true) -> {
                        callback(
                            newExtractorLink(
                                "$name Cloud Download $labelExtras",
                                "$name Cloud Download $labelExtras",
                                url = href
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

// Enhanced title cleaning with better error handling
fun cleanTitle(title: String): String {
    return try {
        if (title.isBlank()) return ""

        val parts = title.split(".", "-", "_")

        val qualityTags = listOf(
            "WEBRip", "WEB-DL", "WEB", "BluRay", "HDRip", "DVDRip", "HDTV",
            "CAM", "TS", "R5", "DVDScr", "BRRip", "BDRip", "DVD", "PDTV", "HD"
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

        if (startIndex != -1 && endIndex != -1 && endIndex >= startIndex) {
            parts.subList(startIndex, endIndex + 1).joinToString(".")
        } else if (startIndex != -1) {
            parts.subList(startIndex, parts.size).joinToString(".")
        } else {
            parts.takeLast(3).joinToString(".")
        }
    } catch (e: Exception) {
        Log.e("TitleClean", "Failed to clean title: '$title' - ${e.message}")
        title
    }
}
