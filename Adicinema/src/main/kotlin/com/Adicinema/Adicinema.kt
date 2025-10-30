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

class Adicinema : MainAPI() {
    private val API_KEY = "1d8730d33fc13ccbd8cdaaadb74892c7"

    // Base URL TMDb untuk API data
    override var mainUrl = "https://api.themoviedb.org/3"

    // URL ini dipertahankan, dan akan digunakan untuk loadLinks/extractor
    private val apiUrl = "https://api.example.com" // Ganti dengan URL API yang sesuai

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
        val typeAndSort = request.data
        val url = if (typeAndSort.contains("discover")) {
            // Ditambahkan &language=en-US (praktik baik)
            "$mainUrl/$typeAndSort&api_key=$API_KEY&language=en-US&page=$page"
        } else {
            // Ditambahkan &language=en-US (praktik baik)
            "$mainUrl/$typeAndSort?api_key=$API_KEY&language=en-US&page=$page"
        }
        val home = app.get(url)
            .parsedSafe<TMDbPageResult>()?.results?.map { it.toSearchResponse(this) }
            ?: throw ErrorLoadingException("No Data Found")
        return newHomePageResponse(request.name, home)
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> {
        return search(query) ?: emptyList()
    }

    override suspend fun search(query: String): List<SearchResponse>? {
        // Menggunakan endpoint search/multi dengan language dan filter dewasa
        val url = "$mainUrl/search/multi?api_key=$API_KEY&query=$query&language=en-US&include_adult=false"

        val results = app.get(url)
            .parsedSafe<TMDbPageResult>()?.results
            ?.filter { it.media_type != "person" } // Filter hasil yang berupa orang
            ?.map { it.toSearchResponse(this) } ?: return null

        return results
    }

    override suspend fun load(url: String): LoadResponse {
        // Mengambil ID TMDb dari URL, misal: /movie/123456 -> 123456
        val id = url.substringAfterLast("/")
        val mediaType = if (url.contains("/tv/")) "tv" else "movie"
        
        // PERBAIKAN KRITIS 1: Menambahkan external_ids untuk mendapatkan IMDb ID
        val detailUrl = "$mainUrl/$mediaType/$id?api_key=$API_KEY&append_to_response=videos,credits,recommendations,external_ids"

        // Memuat detail dari TMDb
        val document = app.get(detailUrl)
            .parsedSafe<TMDbDetailResult>()

        val title = document?.title ?: document?.name ?: ""
        val tags = document?.genres?.mapNotNull { it.name }
        val poster = document?.poster_path?.let { "https://image.tmdb.org/t/p/w500$it" }
        val releaseDate = document?.release_date ?: document?.first_air_date
        val year = releaseDate?.substringBefore("-")?.toIntOrNull()

        val tvType = when (document?.media_type) {
            "tv" -> TvType.TvSeries
            "movie" -> TvType.Movie
            else -> TvType.Movie
        }

        val description = document?.overview

        val trailer = document?.videos?.results?.firstOrNull { it.type == "Trailer" }?.key?.let {
            "https://www.youtube.com/watch?v=$it"
        }

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
        
        // PERBAIKAN KRITIS 2: Menyimpan IMDb ID ke dalam LoadData
        val imdbId = document?.external_ids?.imdb_id

        val loadData = LoadData(
            id,
            imdbId, // Menyimpan IMDb ID
            null,
            null
        )
        
        return if (tvType == TvType.TvSeries) {
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
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val media = parseJson<LoadData>(data)
        
        // PERBAIKAN KRITIS 3: Mengutamakan IMDb ID. Fallback ke TMDb ID jika IMDb ID kosong.
        // Sumber link pihak ketiga umumnya menggunakan IMDb ID.
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
        // Karena sebagian besar sumber link eksternal menggunakan IMDb ID,
        // kita akan menggunakannya sebagai 'contentId' di sini.
        // Format link di bawah ini masih merupakan placeholder.
        return when (source) {
            "VidSrc.to" -> searchVidSrc(contentId)
            "flixhq.to" -> searchFlixHQ(contentId)
            "moviesapi.club" -> searchMoviesAPI(contentId)
            "superstream.lol" -> searchSuperStream(contentId)
            "vidbinge.com" -> searchVidBinge(contentId)
            else -> emptyList()
        }
    }

    // Fungsi-fungsi pencarian link tetap merupakan placeholder.
    private suspend fun searchVidSrc(contentId: String): List<ExtractorLink> {
        // Implementasi untuk mencari dan mendapatkan link dari VidSrc.to
        // Pastikan Anda membedakan antara ID film (muatan ID) dan ID serial TV.
        val url = "https://vidsrc.to/embed/movie/$contentId" // Ganti 'movie' dengan 'tv' jika serial
        // ...
        return listOf(newExtractorLink("VidSrc", "VidSrc", "example.com/link-vidsrc", INFER_TYPE))
    }

    private suspend fun searchFlixHQ(contentId: String): List<ExtractorLink> {
        // ...
        return listOf(newExtractorLink("FlixHQ", "FlixHQ", "example.com/link-flixhq", INFER_TYPE))
    }

    private suspend fun searchMoviesAPI(contentId: String): List<ExtractorLink> {
        // ...
        return listOf(newExtractorLink("MoviesAPI", "MoviesAPI", "example.com/link-moviesapi", INFER_TYPE))
    }

    private suspend fun searchSuperStream(contentId: String): List<ExtractorLink> {
        // ...
        return listOf(newExtractorLink("SuperStream", "SuperStream", "example.com/link-superstream", INFER_TYPE))
    }

    private suspend fun searchVidBinge(contentId: String): List<ExtractorLink> {
        // ...
        return listOf(newExtractorLink("VidBinge", "VidBinge", "example.com/link-vidbinge", INFER_TYPE))
    }

    // PERBAIKAN DATA CLASS: Menambahkan 'imdbId'
    data class LoadData(
        val id: String? = null, // TMDb ID
        val imdbId: String? = null, // IMDb ID - BARU
        val season: Int? = null,
        val episode: Int? = null,
        val detailPath: String? = null,
    )

    // Data Class untuk hasil list dari TMDb (getMainPage dan search) - TIDAK ADA PERUBAHAN
    data class TMDbPageResult(
        @JsonProperty("results") val results: ArrayList<TMDbSearchItem>? = arrayListOf(),
    )

    // Data Class untuk item list dari TMDb - TIDAK ADA PERUBAHAN
    data class TMDbSearchItem(
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("media_type") val media_type: String? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("name") val name: String? = null, // Untuk TV Series
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
                url, // Menggunakan URL sebagai ID untuk load
                type, false
            ) {
                this.posterUrl = poster_path?.let { "https://image.tmdb.org/t/p/w500$it" }
                this.score = Score.from10(vote_average?.toFloat())
            }
        }
    }

    // PERBAIKAN DATA CLASS: Menambahkan ExternalIds
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
        @JsonProperty("external_ids") val external_ids: ExternalIds? = null, // <-- BARU
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
        // BARU: Data Class untuk External ID
        data class ExternalIds(@JsonProperty("imdb_id") val imdb_id: String? = null)
    }

    // Data Class lama (dipertahankan)
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
