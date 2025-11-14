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
// Add to Utils.kt
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.SubtitleFile

// Improved logging function
fun logDebug(tag: String, message: String) {
    Log.d(tag, message)
}

// Improved URL validation
fun isValidUrl(url: String?): Boolean {
    return !url.isNullOrBlank() && (url.startsWith("http://") || url.startsWith("https://"))
}

// Existing DomainsParser and other data classes remain the same
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

// ========== TMDb DATA CLASSES ==========
data class TMDbResponse(
    val id: Int?,
    val title: String?, // for movies
    val name: String?, // for TV shows
    val overview: String?,
    val poster_path: String?,
    val backdrop_path: String?,
    val release_date: String?, // for movies
    val first_air_date: String?, // for TV shows
    val genres: List<TMDbGenre>?,
    val vote_average: Float?,
    val runtime: Int?, // for movies
    val episode_run_time: List<Int>?, // for TV shows
    val number_of_seasons: Int?,
    val number_of_episodes: Int?,
    val status: String?,
    val credits: TMDbCredits?,
    val videos: TMDbVideoResponse?
)

data class TMDbGenre(val id: Int?, val name: String?)

data class TMDbCredits(
    val cast: List<TMDbCast>?,
    val crew: List<TMDbCrew>?
)

data class TMDbCast(
    val name: String?, 
    val character: String?, 
    val profile_path: String?,
    val order: Int?
)

data class TMDbCrew(val name: String?, val job: String?)

data class TMDbVideoResponse(val results: List<TMDbVideo>?)

data class TMDbVideo(
    val key: String?, 
    val name: String?, 
    val type: String?, 
    val site: String?
)

data class TMDbEpisode(
    val id: Int?,
    val name: String?,
    val overview: String?,
    val still_path: String?,
    val season_number: Int?,
    val episode_number: Int?,
    val runtime: Int?,
    val vote_average: Float?,
    val air_date: String?
)

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
