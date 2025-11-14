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
        app.get(url).parsedSafe()
    } catch (e: Exception) {
        Log.e("TMDb", "Failed to fetch TMDb data for $type ID $tmdbId: ${e.message}")
        null
    }
}

suspend fun fetchTMDbEpisode(tmdbId: String, season: Int, episode: Int): TMDbEpisode? {
    return try {
        val url = "${DramaDripProvider.TMDB_BASE_URL}/tv/$tmdbId/season/$season/episode/$episode?api_key=${DramaDripProvider.TMDB_API_KEY}"
        app.get(url).parsedSafe()
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

// Existing utility functions remain the same
suspend fun bypassHrefli(url: String): String? {
    // ... existing implementation unchanged
}

fun getBaseUrl(url: String): String {
    // ... existing implementation unchanged
}

fun fixUrl(url: String, domain: String): String {
    // ... existing implementation unchanged
}

@RequiresApi(Build.VERSION_CODES.O)
suspend fun cinematickitBypass(url: String): String? {
    // ... existing implementation unchanged
}

@RequiresApi(Build.VERSION_CODES.O)
suspend fun cinematickitloadBypass(url: String): String? {
    // ... existing implementation unchanged
}

@RequiresApi(Build.VERSION_CODES.O)
fun base64Decode(string: String): String {
    // ... existing implementation unchanged
}
