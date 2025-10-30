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
import org.jsoup.Jsoup

class Adicinema : MainAPI() {
    private val API_KEY = "1d8730d33fc13ccbd8cdaaadb74892c7"
    override var mainUrl = "https://api.themoviedb.org/3" // PERBAIKAN: Base URL TMDb
    private val apiUrl = "" // Placeholder - Ganti jika diperlukan
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
        "discover/movie?with_genres=16" to "Animation (Discover)",
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
            val detailUrl = "$mainUrl/$mediaType/$id?api_key=$API_KEY&append_to_response=videos,credits,recommendations,external_ids,seasons"
            val document = app.get(detailUrl)
                .parsedSafe<TMDbDetailResult>()
            val title = document?.title ?: document?.name ?: ""
            val tags = document?.genres?.mapNotNull { it.name }
            // PERBAIKAN: Menggunakan base URL untuk poster
            val poster = document?.poster_path?.let { "https://image.tmdb.org/t/p/w500$it" }
            val releaseDate = document?.release_date ?: document?.first_air_date
            val year = releaseDate?.substringBefore("-")?.toIntOrNull()
            val imdbId = document?.external_ids?.imdb_id
            val tvType = when (document?.media_type) {
                "tv" -> TvType.TvSeries
                "movie" -> TvType.Movie
                else -> TvType.Movie
            }
            val description = document?.overview
            // PERBAIKAN: Menggunakan base URL untuk trailer YouTube
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
            if (tvType == TvType.TvSeries) {
                val seasons = document?.seasons?.filter { it.season_number != 0 }
                val episodeList = seasons?.mapNotNull { season ->
                    val episodeCount = season.episode_count ?: 1
                    val seasonNumber = season.season_number ?: return@mapNotNull null
                    (1..episodeCount).map { episodeNumber ->
                        val loadData = LoadData(
                            id,
                            imdbId,
                            seasonNumber,
                            episodeNumber,
                        )
                        newEpisode(loadData.toJson()) {
                            this.name = "S${seasonNumber} E${episodeNumber}"
                            this.season = seasonNumber
                            this.episode = episodeNumber
                        }
                    }
                }?.flatten() ?: emptyList()
                return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodeList) {
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
                val loadData = LoadData(
                    id,
                    imdbId,
                    null,
                    null,
                )
                return newMovieLoadResponse(
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
            // ... sumber lainnya
        )
        val searchResults = sources.flatMap { source ->
            getLinksFromSource(source, contentId, media.season, media.episode)
        }
        searchResults.forEach { link ->
            callback.invoke(link)
        }
        return searchResults.isNotEmpty()
    }
    private suspend fun getLinksFromSource(source: String, contentId: String, season: Int?, episode: Int?): List<ExtractorLink> {
        return when (source) {
            "VidSrc.to" -> searchVidSrc(contentId, season, episode)
            "flixhq.to" -> searchFlixHQ(contentId, season, episode)
            // ...
            else -> emptyList()
        }
    }

    // PERBAIKAN KRITIS: Implementasi VidSrc yang menangani MOVIE dan TV SERIES
    private suspend fun searchVidSrc(contentId: String, season: Int?, episode: Int?): List<ExtractorLink> {
        return try {
            val embedUrl = if (season != null && episode != null) {
                // Jika ada season/episode, ini adalah TV SERIES
                // Contoh: "https://vidsrc.to/embed/tv/tt1234567/1/1" (IMDb ID, Season 1, Episode 1)
                "https://vidsrc.to/embed/tv/$contentId/$season/$episode"
            } else {
                // Jika tidak ada, ini adalah MOVIE
                // Contoh: "https://vidsrc.to/embed/movie/tt1234567" (IMDb ID)
                "https://vidsrc.to/embed/movie/$contentId"
            }

            val doc = Jsoup.connect(embedUrl).get()
            val iframe = doc.select("iframe").first()
            val src = iframe?.attr("src")

            if (src != null && src.isNotEmpty()) {
                val links = mutableListOf<ExtractorLink>()
                // Menggunakan loadExtractors untuk memanggil semua extractor yang kompatibel
                loadExtractors(src, embedUrl, null, subtitleCallback = null) { link ->
                    links.add(link)
                }
                links
            } else {
                println("No iframe found on VidSrc for content ID: $contentId, Season: $season, Episode: $episode")
                emptyList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private suspend fun searchFlixHQ(contentId: String, season: Int?, episode: Int?): List<ExtractorLink> {
        // Anda perlu mengimplementasikan logika FlixHQ di sini
        return listOf(newExtractorLink("FlixHQ", "FlixHQ", "example.com/link-flixhq", INFER_TYPE))
    }

    // ... (fungsi searchMoviesAPI, searchSuperStream, searchVidBinge yang lain)

    data class LoadData(
        val id: String? = null, // TMDb ID
        val imdbId: String? = null, // IMDb ID - BARU
        val season: Int? = null, // BARU
        val episode: Int? = null, // BARU
        val detailPath: String? = null,
    )

    // ... Data Class TMDbPageResult dan TMDbSearchItem tidak berubah

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
        @JsonProperty("seasons") val seasons: List<Season>? = null, // <-- BARU
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

        data class Season(
            @JsonProperty("season_number") val season_number: Int? = null,
            @JsonProperty("episode_count") val episode_count: Int? = null,
        )
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
