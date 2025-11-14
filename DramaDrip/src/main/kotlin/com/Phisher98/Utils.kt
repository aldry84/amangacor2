package com.Phisher98

import android.os.Build
import androidx.annotation.RequiresApi
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.api.Log
import com.lagradost.cloudstream3.app
import kotlinx.coroutines.delay
import org.jsoup.nodes.Document
import java.net.URI
import java.util.Base64
import java.util.concurrent.TimeUnit

// ========== CACHE CONFIGURATION ==========
private object CacheConfig {
    const val TMDB_CACHE_DURATION_MINUTES = 60L // 1 hour cache
    const val MAX_RETRY_ATTEMPTS = 3
    const val RETRY_DELAY_MS = 1000L
}

// ========== CACHE MANAGEMENT ==========
private data class CacheEntry<T>(
    val data: T,
    val timestamp: Long,
    val expiresIn: Long = TimeUnit.MINUTES.toMillis(CacheConfig.TMDB_CACHE_DURATION_MINUTES)
) {
    fun isExpired(): Boolean = System.currentTimeMillis() - timestamp > expiresIn
}

private object CacheManager {
    private val tmdbCache = mutableMapOf<String, CacheEntry<Any>>()
    
    @Synchronized
    fun <T> get(key: String): T? {
        val entry = tmdbCache[key] as? CacheEntry<T> ?: return null
        return if (entry.isExpired()) {
            tmdbCache.remove(key)
            null
        } else {
            entry.data
        }
    }
    
    @Synchronized
    fun <T> put(key: String, data: T) {
        tmdbCache[key] = CacheEntry(data as Any, System.currentTimeMillis())
    }
    
    @Synchronized
    fun clearExpired() {
        tmdbCache.entries.removeAll { it.value.isExpired() }
    }
}

// ========== RETRY MECHANISM ==========
suspend fun <T> withRetry(
    attempts: Int = CacheConfig.MAX_RETRY_ATTEMPTS,
    delayMs: Long = CacheConfig.RETRY_DELAY_MS,
    operation: suspend () -> T?
): T? {
    repeat(attempts) { attempt ->
        try {
            return operation()
        } catch (e: Exception) {
            Log.w("Retry", "Attempt ${attempt + 1}/$attempts failed: ${e.message}")
            if (attempt == attempts - 1) throw e
            delay(delayMs * (attempt + 1))
        }
    }
    return null
}

// Existing data classes remain the same
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

// ========== ENHANCED TMDb FUNCTIONS ==========
suspend fun fetchTMDbData(tmdbId: String, type: String): TMDbResponse? {
    if (tmdbId.isEmpty()) return null
    
    val cacheKey = "tmdb_${type}_$tmdbId"
    return CacheManager.get<TMDbResponse>(cacheKey) ?: withRetry {
        try {
            val url = when (type.lowercase()) {
                "movie" -> "${DramaDripProvider.TMDB_BASE_URL}/movie/$tmdbId?api_key=${DramaDripProvider.TMDB_API_KEY}&append_to_response=credits,videos"
                "tv" -> "${DramaDripProvider.TMDB_BASE_URL}/tv/$tmdbId?api_key=${DramaDripProvider.TMDB_API_KEY}&append_to_response=credits,videos"
                else -> return@withRetry null
            }
            val response = app.get(url).parsedSafe<TMDbResponse>()
            response?.let { CacheManager.put(cacheKey, it) }
            response
        } catch (e: Exception) {
            Log.e("TMDb", "Failed to fetch TMDb data for $type ID $tmdbId: ${e.message}")
            null
        }
    }
}

suspend fun fetchTMDbEpisode(tmdbId: String, season: Int, episode: Int): TMDbEpisode? {
    if (tmdbId.isEmpty()) return null
    
    val cacheKey = "tmdb_episode_${tmdbId}_${season}_${episode}"
    return CacheManager.get<TMDbEpisode>(cacheKey) ?: withRetry {
        try {
            val url = "${DramaDripProvider.TMDB_BASE_URL}/tv/$tmdbId/season/$season/episode/$episode?api_key=${DramaDripProvider.TMDB_API_KEY}"
            val response = app.get(url).parsedSafe<TMDbEpisode>()
            response?.let { CacheManager.put(cacheKey, it) }
            response
        } catch (e: Exception) {
            Log.e("TMDb", "Failed to fetch episode data: ${e.message}")
            null
        }
    }
}

fun getTMDbImageUrl(path: String?, size: String = "w500"): String? {
    return if (!path.isNullOrEmpty()) {
        "${DramaDripProvider.TMDB_IMAGE_BASE_URL}/$size$path"
    } else {
        null
    }
}

// ========== ENHANCED BYPASS FUNCTIONS ==========
suspend fun bypassHrefli(url: String): String? {
    return withRetry {
        try {
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
                ?.substringBefore("\"") ?: return@withRetry null
            val driveUrl = app.get(
                "$host?go=$skToken", cookies = mapOf(
                    skToken to "${formData["_wp_http2"]}"
                )
            ).document.selectFirst("meta[http-equiv=refresh]")?.attr("content")?.substringAfter("url=")
            val path = app.get(driveUrl ?: return@withRetry null).text.substringAfter("replace(\"")
                .substringBefore("\")")
            if (path == "/404") return@withRetry null
            fixUrl(path, getBaseUrl(driveUrl))
        } catch (e: Exception) {
            Log.e("Bypass", "Hrefli bypass failed: ${e.message}")
            null
        }
    }
}

fun getBaseUrl(url: String): String {
    return try {
        URI(url).let {
            "${it.scheme}://${it.host}"
        }
    } catch (e: Exception) {
        Log.e("URL", "Failed to parse base URL: ${e.message}")
        ""
    }
}

fun fixUrl(url: String, domain: String): String {
    return try {
        if (url.startsWith("http")) {
            url
        } else if (url.isEmpty()) {
            ""
        } else {
            val startsWithNoHttp = url.startsWith("//")
            if (startsWithNoHttp) {
                "https:$url"
            } else {
                if (url.startsWith('/')) {
                    domain + url
                } else {
                    "$domain/$url"
                }
            }
        }
    } catch (e: Exception) {
        Log.e("URL", "Failed to fix URL: ${e.message}")
        ""
    }
}

@RequiresApi(Build.VERSION_CODES.O)
suspend fun cinematickitBypass(url: String): String? {
    return withRetry {
        try {
            val cleanedUrl = url.replace("&#038;", "&")
            val encodedLink = cleanedUrl.substringAfter("safelink=").substringBefore("-")
            if (encodedLink.isEmpty()) return@withRetry null
            val decodedUrl = base64Decode(encodedLink)
            val doc = app.get(decodedUrl).document
            val goValue = doc.select("form#landing input[name=go]").attr("value")
            if (goValue.isBlank()) return@withRetry null
            val decodedGoUrl = base64Decode(goValue).replace("&#038;", "&")
            val responseDoc = app.get(decodedGoUrl).document
            val script = responseDoc.select("script").firstOrNull { it.data().contains("window.location.replace") }?.data() ?: return@withRetry null
            val regex = Regex("""window\.location\.replace\s*\(\s*["'](.+?)["']\s*\)\s*;?""")
            val match = regex.find(script) ?: return@withRetry null
            val redirectPath = match.groupValues[1]
            if (redirectPath.startsWith("http")) redirectPath else URI(decodedGoUrl).let { "${it.scheme}://${it.host}$redirectPath" }
        } catch (e: Exception) {
            Log.e("Bypass", "Cinematickit bypass failed: ${e.message}")
            null
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
suspend fun cinematickitloadBypass(url: String): String? {
    return withRetry {
        try {
            val cleanedUrl = url.replace("&#038;", "&")
            val encodedLink = cleanedUrl.substringAfter("safelink=").substringBefore("-")
            if (encodedLink.isEmpty()) return@withRetry null
            val decodedUrl = base64Decode(encodedLink)
            val doc = app.get(decodedUrl).document
            val goValue = doc.select("form#landing input[name=go]").attr("value")
            Log.d("Phisher", goValue)
            base64Decode(goValue)
        } catch (e: Exception) {
            Log.e("Bypass", "Cinematickit load bypass failed: ${e.message}")
            null
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
fun base64Decode(string: String): String {
    return try {
        val clean = string.trim().replace("\n", "").replace("\r", "")
        val padded = clean.padEnd((clean.length + 3) / 4 * 4, '=')
        val decodedBytes = Base64.getDecoder().decode(padded)
        String(decodedBytes, Charsets.UTF_8)
    } catch (e: Exception) {
        Log.e("Base64", "Failed to decode base64 string: ${e.message}")
        ""
    }
}

// Cache cleanup function
fun clearExpiredCache() {
    CacheManager.clearExpired()
}
