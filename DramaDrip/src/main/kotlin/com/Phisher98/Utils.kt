package com.Phisher98

import android.os.Build
import androidx.annotation.RequiresApi
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.api.Log
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.SubtitleFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.nodes.Document
import java.net.URI
import java.net.URLEncoder
import java.util.Base64

data class DomainsParser(
    @JsonProperty("dramadrip")
    val dramadrip: String,
)

data class Meta(
    val id: String?,
    val imdb_id: String?,
    val type: String?,
    val poster: String?,
    val logo: String?,
    val background: String?,
    val moviedb_id: Int?,
    val name: String?,
    val description: String?,
    val genre: List<String>?,
    val releaseInfo: String?,
    val status: String?,
    val runtime: String?,
    val cast: List<String>?,
    val language: String?,
    val country: String?,
    val imdbRating: String?,
    val slug: String?,
    val year: String?,
    val videos: List<EpisodeDetails>?
)

data class EpisodeDetails(
    val id: String?,
    val name: String?,
    val title: String?,
    val season: Int?,
    val episode: Int?,
    val released: String?,
    val overview: String?,
    val thumbnail: String?,
    val moviedb_id: Int?
)

data class ResponseData(
    val meta: Meta?
)

suspend fun bypassHrefli(url: String): String? {
    fun Document.getFormUrl(): String {
        return this.select("form#landing").attr("action")
    }

    fun Document.getFormData(): Map<String, String> {
        return this.select("form#landing input").associate { it.attr("name") to it.attr("value") }
    }

    val host = getBaseUrl(url)
    var res = app.get(url).document
    var formUrl = res.getFormUrl()
    var formData = res.getFormData()

    res = app.post(formUrl, data = formData).document
    formUrl = res.getFormUrl()
    formData = res.getFormData()

    res = app.post(formUrl, data = formData).document
    val skToken = res.selectFirst("script:containsData(?go=)")?.data()?.substringAfter("?go=")
        ?.substringBefore("\"") ?: return null
    val driveUrl = app.get(
        "$host?go=$skToken", cookies = mapOf(
            skToken to "${formData["_wp_http2"]}"
        )
    ).document.selectFirst("meta[http-equiv=refresh]")?.attr("content")?.substringAfter("url=")
    val path = app.get(driveUrl ?: return null).text.substringAfter("replace(\"")
        .substringBefore("\")")
    if (path == "/404") return null
    return fixUrl(path, getBaseUrl(driveUrl))
}

fun getBaseUrl(url: String): String {
    return URI(url).let {
        "${it.scheme}://${it.host}"
    }
}

fun fixUrl(url: String, domain: String): String {
    if (url.startsWith("http")) {
        return url
    }
    if (url.isEmpty()) {
        return ""
    }

    val startsWithNoHttp = url.startsWith("//")
    if (startsWithNoHttp) {
        return "https:$url"
    } else {
        if (url.startsWith('/')) {
            return domain + url
        }
        return "$domain/$url"
    }
}

@RequiresApi(Build.VERSION_CODES.O)
suspend fun cinematickitBypass(url: String): String? {
    return try {
        val cleanedUrl = url.replace("&#038;", "&")
        val encodedLink = cleanedUrl.substringAfter("safelink=").substringBefore("-")
        if (encodedLink.isEmpty()) return null
        val decodedUrl = base64Decode(encodedLink)
        val doc = app.get(decodedUrl).document
        val goValue = doc.select("form#landing input[name=go]").attr("value")
        if (goValue.isBlank()) return null
        val decodedGoUrl = base64Decode(goValue).replace("&#038;", "&")
        val responseDoc = app.get(decodedGoUrl).document
        val script = responseDoc.select("script").firstOrNull { it.data().contains("window.location.replace") }?.data() ?: return null
        val regex = Regex("""window\.location\.replace\s*\(\s*["'](.+?)["']\s*\)\s*;?""")
        val match = regex.find(script) ?: return null
        val redirectPath = match.groupValues[1]
        return if (redirectPath.startsWith("http")) redirectPath else URI(decodedGoUrl).let { "${it.scheme}://${it.host}$redirectPath" }
    } catch (e: Exception) {
        Log.e("CinematickitBypass", "Error: ${e.message}")
        null
    }
}

@RequiresApi(Build.VERSION_CODES.O)
suspend fun cinematickitloadBypass(url: String): String? {
    return try {
        val cleanedUrl = url.replace("&#038;", "&")
        val encodedLink = cleanedUrl.substringAfter("safelink=").substringBefore("-")
        if (encodedLink.isEmpty()) return null
        val decodedUrl = base64Decode(encodedLink)
        val doc = app.get(decodedUrl).document
        val goValue = doc.select("form#landing input[name=go]").attr("value")
        Log.d("Phisher",goValue)
        return base64Decode(goValue)
    } catch (e: Exception) {
        Log.e("CinematickitLoadBypass", "Error: ${e.message}")
        null
    }
}

@RequiresApi(Build.VERSION_CODES.O)
fun base64Decode(string: String): String {
    val clean = string.trim().replace("\n", "").replace("\r", "")
    val padded = clean.padEnd((clean.length + 3) / 4 * 4, '=')
    return try {
        val decodedBytes = Base64.getDecoder().decode(padded)
        String(decodedBytes, Charsets.UTF_8)
    } catch (e: Exception) {
        Log.e("Base64Decode", "Error: ${e.message}")
        ""
    }
}

// === FUNGSI BARU UNTUK SUBTITLE ===

/**
 * Extract subtitles from embedded video players
 */
suspend fun extractEmbeddedSubtitles(embedUrl: String): List<SubtitleFile> {
    val subtitles = mutableListOf<SubtitleFile>()
    
    try {
        val doc = app.get(embedUrl).document
        
        // Cari track elements di video players
        doc.select("track").forEach { track ->
            val kind = track.attr("kind")
            if (kind == "subtitles" || kind == "captions") {
                val src = track.attr("src")
                val srclang = track.attr("srclang")
                val label = track.attr("label").lowercase()
                
                if (src.isNotBlank() && (
                    srclang == "id" || 
                    label.contains("indonesia") || 
                    label.contains("indo")
                )) {
                    val fullUrl = fixUrl(src, getBaseUrl(embedUrl))
                    // Gunakan factory function yang benar
                    subtitles.add(createSubtitleFile("Indonesian", fullUrl))
                }
            }
        }
        
        // Cari subtitle links di JavaScript data
        val scriptContents = doc.select("script").mapNotNull { it.data() }
        scriptContents.forEach { script ->
            // Pattern untuk mencari URL subtitle dalam JavaScript
            val subtitlePattern = Regex("""(https?://[^"\']*\.(?:srt|vtt|ass)[^"\']*indonesia[^"\']*)""", RegexOption.IGNORE_CASE)
            subtitlePattern.findAll(script).forEach { match ->
                val subtitleUrl = match.value
                // Gunakan factory function yang benar
                subtitles.add(createSubtitleFile("Indonesian", subtitleUrl))
            }
        }
        
    } catch (e: Exception) {
        Log.e("EmbeddedSubtitle", "Failed to extract embedded subtitles: ${e.message}")
    }
    
    return subtitles
}

/**
 * Clean subtitle filename untuk deteksi bahasa Indonesia
 */
fun isIndonesianSubtitle(filename: String): Boolean {
    val cleanName = filename.lowercase()
    return cleanName.contains("indonesia") || 
           cleanName.contains("indonesian") || 
           cleanName.contains("indo") ||
           cleanName.contains("idn") ||
           cleanName.contains(".id.") ||
           cleanName.contains("_id") ||
           cleanName.contains("[id]")
}

/**
 * Deteksi format subtitle dari URL
 */
fun getSubtitleFormat(url: String): String {
    return when {
        url.endsWith(".vtt", ignoreCase = true) -> "vtt"
        url.endsWith(".ass", ignoreCase = true) -> "ass"
        url.endsWith(".ssa", ignoreCase = true) -> "ssa"
        else -> "srt" // default
    }
}

/**
 * Extract subtitles dari halaman utama
 */
suspend fun extractSubtitlesFromPage(document: Document, pageUrl: String): List<SubtitleFile> {
    val subtitles = mutableListOf<SubtitleFile>()
    
    try {
        // Cari link subtitle berdasarkan keyword Indonesia/Indonesian
        val subtitleLinks = document.select("a").filter { element ->
            val text = element.text().lowercase()
            val href = element.attr("href").lowercase()
            
            (text.contains("subtitle") || text.contains("sub")) && (
                text.contains("indonesia") || 
                text.contains("indonesian") || 
                text.contains("indo") ||
                text.contains("idn") ||
                href.contains("indonesia") ||
                href.contains("indonesian") ||
                href.contains("indo")
            )
        }

        subtitleLinks.forEach { subtitleElement ->
            val subtitleUrl = subtitleElement.attr("href")
            if (subtitleUrl.isNotBlank()) {
                try {
                    val finalSubtitleUrl = when {
                        subtitleUrl.contains("safelink=") -> cinematickitloadBypass(subtitleUrl)
                        else -> fixUrl(subtitleUrl, getBaseUrl(pageUrl))
                    }
                    
                    if (finalSubtitleUrl != null) {
                        // Gunakan factory function yang benar
                        subtitles.add(createSubtitleFile("Indonesian", finalSubtitleUrl))
                        Log.d("Subtitle", "Added Indonesian subtitle: $finalSubtitleUrl")
                    }
                } catch (e: Exception) {
                    Log.e("SubtitleExtract", "Failed to process subtitle link: $subtitleUrl - ${e.message}")
                }
            }
        }

        // Juga cari di dalam konten halaman untuk link .srt/.vtt
        document.select("a[href$=.srt], a[href$=.vtt], a[href$=.ass], a[href$=.ssa]").forEach { element ->
            val subtitleUrl = element.attr("href")
            val text = element.text().lowercase()
            if (isIndonesianSubtitle(text) || isIndonesianSubtitle(subtitleUrl)) {
                val finalUrl = fixUrl(subtitleUrl, getBaseUrl(pageUrl))
                // Gunakan factory function yang benar
                subtitles.add(createSubtitleFile("Indonesian", finalUrl))
                Log.d("Subtitle", "Added Indonesian subtitle from file: $finalUrl")
            }
        }

    } catch (e: Exception) {
        Log.e("SubtitleExtract", "Error extracting subtitles from page: ${e.message}")
    }
    
    return subtitles
}

/**
 * Factory function untuk membuat SubtitleFile dengan cara yang benar
 */
fun createSubtitleFile(language: String, url: String): SubtitleFile {
    return SubtitleFile(language, url)
}
