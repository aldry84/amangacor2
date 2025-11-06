package com.Phisher98

import android.os.Build
import androidx.annotation.RequiresApi
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.getQualityFromName
// Hapus import: com.lagradost.cloudstream3.extractors.TmdbAPI
import com.lagradost.api.Log
import kotlinx.coroutines.runBlocking
import okhttp3.RequestBody.Companion.toRequestBody

// --- API Data Classes (Streaming Kustom) ---

data class StreamLinkResponse(
    @JsonProperty("status") val status: Boolean? = null,
    @JsonProperty("msg") val msg: String? = null,
    @JsonProperty("return") val streamData: List<StreamLinkData>? = null
)

data class StreamLinkData(
    @JsonProperty("link") val link: String? = null,
    @JsonProperty("link_raw") val linkRaw: String? = null,
    @JsonProperty("quality") val quality: String? = null,
    @JsonProperty("resolusi") val resolution: String? = null
)
// --- API Data Classes End ---

// --- TMDb V3 Data Classes (Minimal untuk Search/Load) ---
// (Disimpan untuk referensi di fungsi search dan load)
data class TmdbSearchResponse(
    @JsonProperty("results") val results: List<TmdbSearchResult>? = null
)

data class TmdbSearchResult(
    @JsonProperty("id") val id: Int,
    @JsonProperty("media_type") val media_type: String,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("poster_path") val poster_path: String? = null,
    @JsonProperty("overview") val overview: String? = null
)

data class TmdbLoadResponse(
    @JsonProperty("id") val id: Int,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("overview") val overview: String? = null,
    @JsonProperty("poster_path") val poster_path: String? = null,
    @JsonProperty("backdrop_path") val backdrop_path: String? = null,
    @JsonProperty("genres") val genres: List<TmdbGenre>? = null,
    @JsonProperty("release_date") val release_date: String? = null,
    @JsonProperty("first_air_date") val first_air_date: String? = null,
    @JsonProperty("number_of_seasons") val number_of_seasons: Int? = null,
    @JsonProperty("seasons") val seasons: List<TmdbSeason>? = null
)

data class TmdbSeason(
    @JsonProperty("season_number") val season_number: Int,
    @JsonProperty("episode_count") val episode_count: Int
)

data class TmdbGenre(
    @JsonProperty("name") val name: String
)
// --- TMDb V3 Data Classes End ---


// PASTIKAN Movie21 mewarisi dari MainAPI
class Movie21 : MainAPI() { 
    // Properti ini sekarang DITIMPA (override) dari MainAPI
    override var mainUrl = "https://api.themoviedb.org" 
    override val name = "Movie21 (TMDb V3)"
    override var lang = "id"
    override val hasMainPage = false
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)
    
    // Properti TMDbAPI yang lama ('TMDB_API') harus diganti dengan private val biasa
    private val TMDB_API_KEY = "1cfadd9dbfc534abf6de40e1e7eaf4c7"
    private val TMDB_IMAGE_BASE_URL = "https://image.tmdb.org/t/p/w500" 

    // --- Endpoint Streaming Kustom ---
    private val STREAM_BASE_URL = runBlocking {
        // Ini memastikan Movie21Provider.kt tidak menghasilkan error tipe
        Movie21Provider.getDomains()?.movie21 ?: "https://dramadrip.com" 
    }
    private val API_KLASIK_LOAD = "/api/v1/klasik/load" 
    
    // --- Implementasi Search TMDb V3 ---
    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/3/search/multi?api_key=$TMDB_API_KEY&query=${query.encodeUri()}#&language=id"
        
        val response = app.get(url).parsedSafe<TmdbSearchResponse>()
        
        return response?.results?.mapNotNull { result ->
            val type = when (result.media_type) {
                "movie" -> TvType.Movie
                "tv" -> TvType.TvSeries
                else -> return@mapNotNull null
            }
            val title = result.title ?: result.name ?: return@mapNotNull null
            val posterUrl = result.poster_path?.let { TMDB_IMAGE_BASE_URL + it }
            
            val link = "${result.id}-${result.media_type}"

            newMovieSearchResponse(title, link, type) {
                this.posterUrl = posterUrl
            }
        } ?: emptyList()
    }
    
    // --- Implementasi Load TMDb V3 ---
    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun load(url: String): LoadResponse {
        val parts = url.split("-")
        val tmdbId = parts.firstOrNull()?.toIntOrNull() ?: throw ErrorLoadingException("Invalid TMDb ID")
        val mediaType = parts.getOrNull(1) ?: throw ErrorLoadingException("Invalid media type")
        
        val tmdbUrl = "$mainUrl/3/$mediaType/$tmdbId?api_key=$TMDB_API_KEY&append_to_response=videos,credits,external_ids#&language=id"
        val detail = app.get(tmdbUrl).parsedSafe<TmdbLoadResponse>() ?: throw ErrorLoadingException("Failed to load TMDb detail")
        
        val title = detail.title ?: detail.name ?: "Judul Tidak Diketahui"
        val poster = detail.poster_path?.let { TMDB_IMAGE_BASE_URL + it }
        val background = detail.backdrop_path?.let { TMDB_IMAGE_BASE_URL + it }
        val plot = detail.overview
        val year = detail.release_date?.take(4)?.toIntOrNull() ?: detail.first_air_date?.take(4)?.toIntOrNull()
        val tags = detail.genres?.map { it.name }
        
        val finalUrl = "$mediaType/$tmdbId" 
        
        val apiStreamId = tmdbId.toString()
        
        if (mediaType == "tv") {
            val seasons = detail.seasons?.mapNotNull { tmdbSeason ->
                val episodeLinkData = "$apiStreamId-${tmdbSeason.season_number}".toJson()
                
                newEpisode(episodeLinkData) {
                    this.name = "Season ${tmdbSeason.season_number}"
                    this.season = tmdbSeason.season_number
                    this.episode = tmdbSeason.episode_count 
                }
            } ?: emptyList()

            return newTvSeriesLoadResponse(title, finalUrl, TvType.TvSeries, seasons) {
                this.backgroundPosterUrl = background
                this.posterUrl = poster
                this.plot = plot
                this.year = year
                this.tags = tags
                addTMDbId(tmdbId.toString())
            }
        } else {
            val linkForLoadLinks = apiStreamId.toJson()
            return newMovieLoadResponse(title, finalUrl, TvType.Movie, linkForLoadLinks) {
                this.backgroundPosterUrl = background
                this.posterUrl = poster
                this.plot = plot
                this.year = year
                this.tags = tags
                addTMDbId(tmdbId.toString())
            }
        }
    }

    // --- Implementasi LoadLinks API Streaming Kustom ---
    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // ... (Logika loadLinks tetap sama seperti yang sudah diperbaiki)
        val contentId = tryParseJson<String>(data) ?: return false

        val jsonPayload = mapOf(
            "auth" to "",
            "id_content" to contentId
        ).toJson()

        val response = app.post(
            "$STREAM_BASE_URL$API_KLASIK_LOAD",
            requestBody = jsonPayload.toRequestBody(),
            headers = mapOf("Content-Type" to "application/json")
        ).parsedSafe<StreamLinkResponse>()

        val streamLinks = response?.streamData ?: emptyList()

        if (streamLinks.isEmpty()) {
            Log.e("Movie21", "API returned no stream links for ID: $contentId")
            return false
        }

        for (stream in streamLinks) {
            val link = stream.link ?: stream.linkRaw ?: continue
            val qualityText = stream.quality ?: stream.resolution ?: "Unknown"
            
            val qualityInt = getQualityFromName(qualityText)

            callback(
                newExtractorLink(
                    source = "Movie21 API",
                    name = "Movie21 API $qualityText",
                    url = link,
                ) {
                    this.quality = qualityInt
                    this.referer = "$STREAM_BASE_URL/" 
                }
            )
        }

        return true
    }
}
