package com.Adicinema

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.nicehttp.RequestBodyTypes
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup // Import Jsoup

class Adicinema : MainAPI() {
    private val API_KEY = "1d8730d33fc13ccbd8cdaaadb74892c7"
    override var mainUrl = "https://api.themoviedb.org/3" // PERBAIKAN: Base URL TMDb
    private val apiUrl = "" // Ganti dengan URL API yang sesuai (atau hapus jika tidak digunakan)
    override val instantLinkLoading = true
    override var name = "Adicinema"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override var lang = "en"
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
        TvType.AsianDrama
    )
    override val mainPage: List<MainPageData> = mainPageOf(
        "movie/popular" to "Movies Popular",
        "movie/top_rated" to "Movies Top Rated",
        "tv/popular" to "TV Shows Popular",
        "tv/top_rated" to "TV Shows Top Rated",
        "discover/movie?with_genres=16" to "Animation (Discover)", // Genre ID 16 untuk Animation/Kartun
    )
    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest,
    ): HomePageResponse {
        return try {
            val typeAndSort = request.data
            val url = if (typeAndSort.contains("discover")) {
                "$mainUrl/$typeAndSort&api_key=$API_KEY&language=en-US&page=$page"
            } else {
                "$mainUrl/$typeAndSort?api_key=$API_KEY&language=en-US&page=$page"
            }
            val home = app.get(url)
                .parsedSafe<TMDbPageResult>()?.results?.map { it.toSearchResponse(this) }
                ?: throw ErrorLoadingException("No Data Found")
            newHomePageResponse(request.name, home)
        } catch (e: Exception) {
            e.printStackTrace()
            throw ErrorLoadingException("Failed to load main page: ${e.message}")
        }
    }
    override suspend fun quickSearch(query: String): List<SearchResponse> {
        return search(query) ?: emptyList()
    }
    override suspend fun search(query: String): List<SearchResponse>? {
        return try {
            val url = "$mainUrl/search/multi?api_key=$API_KEY&query=$query&language=en-US&include_adult=false"
            val results = app.get(url)
                .parsedSafe<TMDbPageResult>()?.results
                ?.filter { it.media_type != "person" }
                ?.map { it.toSearchResponse(this) } ?: return null
            results
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    override suspend fun load(url: String): LoadResponse {
        return try {
            val id = url.substringAfterLast("/")
            val mediaType = if (url.contains("/tv/")) "tv" else "movie"
            val detailUrl = "$mainUrl/$mediaType/$id?api_key=$API_KEY&append_to_response=videos,credits,recommendations,external_ids"
            val document = app.get(detailUrl)
                .parsedSafe<TMDbDetailResult>()

            val title = document?.title ?: document?.name ?: ""
            val tags = document?.genres?.mapNotNull { it.name }
            // PERBAIKAN: Gunakan base URL untuk poster
            val poster = document?.poster_path?.let { "https://image.tmdb.org/t/p/w500$it" }
            val releaseDate = document?.release_date ?: document?.first_air_date
            val year = releaseDate?.substringBefore("-")?.toIntOrNull()
            val tvType = when (document?.media_type) {
                "tv" -> TvType.TvSeries
                "movie" -> TvType.Movie
                else -> TvType.Movie
            }
            val description = document?.overview
            // PERBAIKAN: Gunakan base URL untuk trailer YouTube
            val trailer = document?.videos?.results?.firstOrNull { it.type == "Trailer" }?.key?.let { "https://www.youtube.com/watch?v=$it" }
            val actors = document?.credits?.cast?.mapNotNull { cast ->
                ActorData(
                    Actor(
                        cast.name ?: return@mapNotNull null,
                        cast.profile_path?.let { "https://image.tmdb.org/t/p/w500$it" }
                    ),
                    roleString = cast.character
                )
            }?.distinctBy { it.actor }
            val recommendations = document?.recommendations?.results?.map { it.toSearchResponse(this) }
            val imdbId = document?.external_ids?.imdb_id
            val loadData = LoadData(
                id,
                imdbId,
                null,
                null
            )
            if (tvType == TvType.TvSeries) {
                val episodeList = listOf(newEpisode(loadData.toJson()))
                newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodeList) {
                    this.posterUrl = poster
                    this.year = year
                    this.plot = description
                    this.tags = tags
                    this.score = Score.from10(document?.vote_average?.toFloat())
                    this.actors = actors
                    this.recommendations = recommendations
                    addTrailer(trailer, addRaw = true)
                }
            } else {
                newMovieLoadResponse(
                    title,
                    url,
                    TvType.Movie,
                    loadData.toJson()
                ) {
                    this.posterUrl = poster
                    this.year = year
                    this.plot = description
                    this.tags = tags
                    this.score = Score.from10(document?.vote_average?.toFloat())
                    this.actors = actors
                    this.recommendations = recommendations
                    addTrailer(trailer, addRaw = true)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw ErrorLoadingException("Failed to load details: ${e.message}")
        }
    }
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val media = parseJson<LoadData>(data)
        val contentId = media.imdbId ?: media.id ?: return false
        val sources = listOf(
            "VidSrc.to",
            "flixhq.to",
            "moviesapi.club",
            "superstream.lol",
            "vidbinge.com"
        )
        val searchResults = sources.flatMap { source ->
            getLinksFromSource(source, contentId)
        }
        searchResults.forEach { link ->
            callback.invoke(link)
        }
        return searchResults.isNotEmpty()
    }
    private suspend fun getLinksFromSource(source: String, contentId: String): List<ExtractorLink> {
        return when (source) {
            "VidSrc.to" -> searchVidSrc(contentId)
            "flixhq.to" -> searchFlixHQ(contentId)
            "moviesapi.club" -> searchMoviesAPI(contentId)
            "superstream.lol" -> searchSuperStream(contentId)
            "vidbinge.com" -> searchVidBinge(contentId)
            else -> emptyList()
        }
    }

    // Implementasi contoh searchVidSrc menggunakan Jsoup
    private suspend fun searchVidSrc(contentId: String): List<ExtractorLink> {
        return try {
            val baseUrl = "https://vidsrc.to/embed/movie" // Atau "https://vidsrc.to/embed/tv" jika serial
            val url = "$baseUrl/$contentId"
            val response = app.get(url).text

            val doc = Jsoup.parse(response)
            val iframe = doc.select("iframe").first()
            val src = iframe?.attr("src")

            if (src != null && src.isNotEmpty()) {
                listOf(newExtractorLink("VidSrc", "VidSrc", src, INFER_TYPE))
            } else {
                println("No iframe found on VidSrc for IMDB ID: $contentId")
                emptyList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private suspend fun searchFlixHQ(contentId: String): List<ExtractorLink> {
        // ... (Implementasi untuk FlixHQ)
        return listOf(newExtractorLink("FlixHQ", "FlixHQ", "example.com/link-flixhq", INFER_TYPE))
    }

    private suspend fun searchMoviesAPI(contentId: String): List<ExtractorLink> {
        // ... (Implementasi untuk MoviesAPI)
        return listOf(newExtractorLink("MoviesAPI", "MoviesAPI", "example.com/link-moviesapi", INFER_TYPE))
    }

    private suspend fun searchSuperStream(contentId: String): List<ExtractorLink> {
        // ... (Implementasi untuk SuperStream)
        return listOf(newExtractorLink("SuperStream", "SuperStream", "example.com/link-superstream", INFER_TYPE))
    }

    private suspend fun searchVidBinge(contentId: String): List<ExtractorLink> {
        // ... (Implementasi untuk VidBinge)
        return listOf(newExtractorLink("VidBinge", "VidBinge", "example.com/link-vidbinge", INFER_TYPE))
    }

    data class LoadData(
        val id: String? = null, // TMDb ID
        val imdbId: String? = null, // IMDb ID - BARU
        val season: Int? = null,
        val episode: Int? = null,
        val detailPath: String? = null,
    )

    data class TMDbPageResult(
        @JsonProperty("results") val results: ArrayList<TMDbSearchItem>? = arrayListOf(),
    )

    data class TMDbSearchItem(
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("media_type") val media_type: String? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("poster_path") val poster_path: String? = null,
        @JsonProperty("vote_average") val vote_average: Double? = null,
    ) {
        fun toSearchResponse(provider: Adicinema): SearchResponse {
            val type = when (media_type) {
                "tv" -> TvType.TvSeries
                "movie" -> TvType.Movie
                else -> TvType.Movie
            }
            val url = "/$media_type/$id"
            return provider.newMovieSearchResponse(
                title ?: name ?: "",
                url,
                type, false
            ) {
                this.posterUrl = poster_path?.let { "https://image.tmdb.org/t/p/w500$it" }
                this.score = Score.from10(vote_average?.toFloat())
            }
        }
    }

    data class TMDbDetailResult(
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("media_type") val media_type: String? = null,
        @JsonProperty("poster_path") val poster_path: String? = null,
        @JsonProperty("overview") val overview: String? = null,
        @JsonProperty("release_date") val release_date: String? = null,
        @JsonProperty("first_air_date") val first_air_date: String? = null,
        @JsonProperty("vote_average") val vote_average: Double? = null,
        @JsonProperty("genres") val genres: List<Genre>? = null,
        @JsonProperty("videos") val videos: Videos? = null,
        @JsonProperty("credits") val credits: Credits? = null,
        @JsonProperty("recommendations") val recommendations: TMDbPageResult? = null,
        @JsonProperty("external_ids") val external_ids: ExternalIds? = null,
    ) {
        data class Genre(@JsonProperty("name") val name: String? = null)
        data class Videos(@JsonProperty("results") val results: List<VideoItem>? = null)
        data class VideoItem(@JsonProperty("key") val key: String? = null, @JsonProperty("type") val type: String? = null)
        data class Credits(@JsonProperty("cast") val cast: List<CastItem>? = null)
        data class CastItem(
            @JsonProperty("name") val name: String? = null,
            @JsonProperty("character") val character: String? = null,
            @JsonProperty("profile_path") val profile_path: String? = null
        )

        data class ExternalIds(@JsonProperty("imdb_id") val imdb_id: String? = null)
    }

    data class Media(
        @JsonProperty("data") val data: Data? = null,
    ) {
        data class Data(
            @JsonProperty("streams") val streams: ArrayList<Streams>? = arrayListOf(),
            @JsonProperty("captions") val captions: ArrayList<Captions>? = arrayListOf(),
        ) {
            data class Streams(
                @JsonProperty("id") val id: String? = null,
                @JsonProperty("format") val format: String? = null,
                @JsonProperty("url") val url: String? = null,
                @JsonProperty("resolutions") val resolutions: String? = null,
            )

            data class Captions(
                @JsonProperty("lanName") val lanName: String? = null,
                @JsonProperty("url") val url: String? = null,
            )
        }
    }
}
