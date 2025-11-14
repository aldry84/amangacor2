package com.Phisher98

import android.os.Build
import androidx.annotation.RequiresApi
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.api.Log
import com.lagradost.cloudstream3.app
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.nodes.Document
import java.net.URI
import java.net.URLEncoder
import java.util.Base64

// ... (data classes tetap sama sampai bagian fungsi)

// ========== TMDb FUNCTIONS ==========
suspend fun fetchTMDbData(tmdbId: String, type: String): TMDbResponse? {
    if (tmdbId.isEmpty()) return null
    
    return try {
        val url = when (type.lowercase()) {
            "movie" -> "${DramaDripProvider.TMDB_BASE_URL}/movie/$tmdbId?api_key=${DramaDripProvider.TMDB_API_KEY}&append_to_response=credits,videos"
            "tv" -> "${DramaDripProvider.TMDB_BASE_URL}/tv/$tmdbId?api_key=${DramaDripProvider.TMDB_API_KEY}&append_to_response=credits,videos"
            else -> return null
        }
        val response = app.get(url)
        com.lagradost.cloudstream3.utils.AppUtils.tryParseJson(response.text)
    } catch (e: Exception) {
        Log.e("TMDb", "Failed to fetch TMDb data for $type ID $tmdbId: ${e.message}")
        null
    }
}

suspend fun fetchTMDbEpisode(tmdbId: String, season: Int, episode: Int): TMDbEpisode? {
    return try {
        val url = "${DramaDripProvider.TMDB_BASE_URL}/tv/$tmdbId/season/$season/episode/$episode?api_key=${DramaDripProvider.TMDB_API_KEY}"
        val response = app.get(url)
        com.lagradost.cloudstream3.utils.AppUtils.tryParseJson(response.text)
    } catch (e: Exception) {
        Log.e("TMDb", "Failed to fetch episode data: ${e.message}")
        null
    }
}

fun getTMDbImageUrl(path: String?, size: String = "w500"): String? {
    return if (!path.isNullOrEmpty()) {
        "${DramaDripProvider.TMDB_IMAGE_BASE_URL}/$size$path"
    } else {
        null
    }
}

// ========== EXISTING UTILITY FUNCTIONS ==========
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
    return try {
        URI(url).let {
            "${it.scheme}://${it.host}"
        }
    } catch (e: Exception) {
        ""
    }
}

// HANYA SATU FUNGSI fixUrl - hapus yang lain jika ada duplikat
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
        e.printStackTrace()
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
        base64Decode(goValue)
    } catch (e: Exception) {
        e.printStackTrace()
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
        e.printStackTrace()
        ""
    }
}

// HAPUS SEMUA KODE DI BAWAH INI JIKA ADA
// JANGAN ADA FUNGSI fixUrl LAIN DI BAWAH SINI
