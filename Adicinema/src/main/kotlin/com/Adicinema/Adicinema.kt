package com.Adicinema

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.mvvm.suspendSafe
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.nicehttp.RequestBodyTypes
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

class Adicinema : MainAPI() {
    // API key telah dimasukkan
    private val API_KEY = "1d8730d33fc13ccbd8cdaaadb74892c7" 
    
    // Base URL TMDb untuk API data
    override var mainUrl = "https://api.themoviedb.org/3" 
    
    // URL ini dipertahankan, dan TIDAK LAGI DIGUNAKAN untuk loadLinks/extractor
    // private val apiUrl = "https://fmoviesunblocked.net" // Dihapus karena tidak relevan
    
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

    // PERUBAHAN: Mengubah Main Page untuk mencerminkan endpoint TMDb
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
        
        // Membangun URL untuk endpoint TMDb dengan API key dan parameter
        val url = if (typeAndSort.contains("discover")) {
            // Untuk endpoint discover, tambahkan page di akhir
            "$mainUrl/$typeAndSort&api_key=$API_KEY&page=$page"
        } else {
            // Untuk endpoint popular/top_rated, tambahkan page di akhir
            "$mainUrl/$typeAndSort?api_key=$API_KEY&page=$page"
        }

        val home = app.get(url)
            .parsedSafe<TMDbPageResult>()?.results?.map {
                it.toSearchResponse(this)
            } ?: throw ErrorLoadingException("No Data Found")

        return newHomePageResponse(request.name, home)
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> {
        return search(query) ?: emptyList()
    }

    override suspend fun search(query: String): List<SearchResponse>? {
        // Menggunakan endpoint search/multi
        val url = "$mainUrl/search/multi?api_key=$API_KEY&query=$query" 

        val results = app.get(url)
            .parsedSafe<TMDbPageResult>()?.results
            ?.filter { it.media_type != "person" } // Filter hasil yang berupa orang
            ?.map { it.toSearchResponse(this) }
            ?: return null
            
        return results 
    }

    override suspend fun load(url: String): LoadResponse {
        // Mengambil ID TMDb dari URL, misal: /movie/123456 -> 123456
        val id = url.substringAfterLast("/")
        val mediaType = if (url.contains("/tv/")) "tv" else "movie"

        // Endpoint detail TMDb dengan append info tambahan (Ditambahkan external_ids)
        val detailUrl = "$mainUrl/$mediaType/$id?api_key=$API_KEY&append_to_response=videos,credits,recommendations,external_ids"
        
        // Memuat detail dari TMDb
        val document = app.get(detailUrl)
            .parsedSafe<TMDbDetailResult>()
        
        val title = document?.title ?: document?.name ?: ""
        
        // Menggunakan mapNotNull untuk menghindari 'Assignment type mismatch' pada tags
        val tags = document?.genres?.mapNotNull { it.name }

        // Mendapatkan URL poster
        val poster = document?.poster_path?.let { "https://image.tmdb.org/t/p/w500$it" }
        
        // Tanggal rilis atau tanggal tayang pertama
        val releaseDate = document?.release_date ?: document?.first_air_date
        val year = releaseDate?.substringBefore("-")?.toIntOrNull()
        
        val tvType = when(mediaType) {
            "tv" -> TvType.TvSeries
            "movie" -> TvType.Movie
            else -> TvType.Movie
        }
        
        val description = document?.overview
        
        // Mengambil trailer
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

        val recommendations =
            document?.recommendations?.results?.map {
                it.toSearchResponse(this)
            }
            
        // MENDAPATKAN IMDB ID
        val imdbId = document?.external_ids?.imdb_id
        if (imdbId.isNullOrBlank()) throw ErrorLoadingException("IMDb ID not found for this title.")

        return if (tvType == TvType.TvSeries) {
            
            // 1. Ambil data Season dari TMDb
            val seasons = document?.seasons?.mapNotNull { season ->
                // Jangan masukkan Special Season (Season 0) jika ada season lain
                if (season.season_number == 0 && (document.seasons.size > 1)) return@mapNotNull null

                // URL untuk detail Season
                val seasonEpisodesUrl = "$mainUrl/tv/$id/season/${season.season_number}?api_key=$API_KEY"
                val episodesDoc = suspendSafe { app.get(seasonEpisodesUrl).parsedSafe<TMDbSeasonDetail>() }?.getOrNull()

                // 2. Map Episode ke EpisodeEntry
                val episodes = episodesDoc?.episodes?.map { episode ->
                    // Data yang akan disimpan untuk loadLinks()
                    val epLoadData = LoadData(
                        imdbId = imdbId,
                        season = episode.season_number,
                        episode = episode.episode_number,
                        isMovie = false
                    )

                    newEpisode(epLoadData.toJson()) {
                        this.name = "E${episode.episode_number}: ${episode.name}"
                        this.description = episode.overview
                        this.date = episode.air_date
                        this.rating = episode.vote_average?.times(10)?.toInt()
                        this.posterUrl = episode.still_path?.let { "https://image.tmdb.org/t/p/w500$it" }
                    }
                } ?: emptyList()

                // Buat Season untuk SeasonList
                newSeason(season.name ?: "Season ${season.season_number}", episodes)
            } ?: emptyList()

            newTvSeriesLoadResponse(title, url, TvType.TvSeries, seasons) {
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
            // Untuk Film, buat LoadData hanya dengan IMDB ID
            val loadData = LoadData(
                imdbId = imdbId,
                isMovie = true
            )

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
        // Mendapatkan data LoadData (imdbId, season, episode, isMovie)
        val media = parseJson<LoadData>(data)
        val imdbId = media.imdbId ?: return false

        // Tentukan path untuk film atau serial TV
        val type = if (media.isMovie) "movie" else "tv"
        // Movie: tt1234567. TV: tt1234567/1/1
        val path = if (media.isMovie) imdbId else "$imdbId/${media.season}/${media.episode}"

        // Menggunakan VidSrc.to sebagai agregator utama karena dapat menggunakan IMDB ID
        // VidSrc sering menggunakan sumber seperti VidBinge, FlixHQ, SuperStream, dll.
        val finalUrl = "https://vidsrc.to/embed/$type/$path"

        // Menggunakan loadExtractor untuk memuat pemutar dari URL VidSrc.to
        // Ekstraktor VidSrc.to akan secara otomatis mencoba berbagai sumber/player
        return loadExtractor(finalUrl, imdbId, callback)
    }
    
    // Data Class BARU untuk menyimpan data yang diperlukan untuk loadLinks
    data class LoadData(
        val imdbId: String? = null,
        val season: Int? = null,
        val episode: Int? = null,
        val isMovie: Boolean = true
    )

    // Data Class untuk hasil list dari TMDb (getMainPage dan search)
    data class TMDbPageResult(
        @JsonProperty("results") val results: ArrayList<TMDbSearchItem>? = arrayListOf(),
    )

    // Data Class untuk item list dari TMDb
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
            // URL baru akan berupa /tipe/id untuk digunakan di load()
            val url = "/$media_type/$id" 
            
            return provider.newMovieSearchResponse(
                title ?: name ?: "",
                url, // Menggunakan URL sebagai ID untuk load
                type,
                false
            ) {
                this.posterUrl = poster_path?.let { "https://image.tmdb.org/t/p/w500$it" }
                this.score = Score.from10(vote_average?.toFloat())
            }
        }
    }

    // Data Class untuk detail dari TMDb (load)
    data class TMDbDetailResult(
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("name") val name: String? = null, // Untuk TV Series
        @JsonProperty("media_type") val media_type: String? = null,
        @JsonProperty("poster_path") val poster_path: String? = null,
        @JsonProperty("overview") val overview: String? = null,
        @JsonProperty("release_date") val release_date: String? = null,
        @JsonProperty("first_air_date") val first_air_date: String? = null, // Untuk TV Series
        @JsonProperty("vote_average") val vote_average: Double? = null,
        @JsonProperty("genres") val genres: List<Genre>? = null,
        @JsonProperty("videos") val videos: Videos? = null,
        @JsonProperty("credits") val credits: Credits? = null,
        @JsonProperty("recommendations") val recommendations: TMDbPageResult? = null,
        
        // FIELD BARU UNTUK SERIAL TV
        @JsonProperty("number_of_seasons") val number_of_seasons: Int? = null,
        @JsonProperty("seasons") val seasons: List<TMDbSeason>? = null,
        @JsonProperty("last_episode_to_air") val last_episode_to_air: TMDbEpisodeItem? = null,
        
        // FIELD BARU UNTUK IMDB ID
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
        
        // DATA CLASS BARU UNTUK IMDB ID
        data class ExternalIds(
            @JsonProperty("imdb_id") val imdb_id: String? = null 
        )
        
        // DATA CLASS BARU UNTUK SEASONS
        data class TMDbSeason(
            @JsonProperty("id") val id: Int? = null,
            @JsonProperty("name") val name: String? = null,
            @JsonProperty("season_number") val season_number: Int? = null,
        )
    }

    // DATA CLASS BARU UNTUK DETAIL SEASON/EPISODE
    data class TMDbSeasonDetail(
        @JsonProperty("episodes") val episodes: List<TMDbEpisodeItem>? = null,
    )
    
    data class TMDbEpisodeItem(
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("overview") val overview: String? = null,
        @JsonProperty("vote_average") val vote_average: Double? = null,
        @JsonProperty("air_date") val air_date: String? = null,
        @JsonProperty("episode_number") val episode_number: Int? = null,
        @JsonProperty("season_number") val season_number: Int? = null,
        @JsonProperty("still_path") val still_path: String? = null
    )
    
    // Data Class lama telah dihapus (Media, Data, Streams, Captions)
}
