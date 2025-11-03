package com.Adicinemax // Package diubah

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.* import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.nicehttp.RequestBodyTypes
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

class Adicinemax : MainAPI() { // Nama kelas diubah
    // Kunci API TMDb dimasukkan
    override var mainUrl = "https://api.themoviedb.org/3" 
    private val tmdbApiKey = "1cfadd9dbfc534abf6de40e1e7eaf4c7" // Kunci API Anda
    private val apiUrl = "https://fmoviesunblocked.net" // URL API streaming lama dipertahankan
    
    override val instantLinkLoading = true
    override var name = "Adicinemax" // Nama API diubah
    override val hasMainPage = false 
    override val hasQuickSearch = true
    override var lang = "en"
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
        TvType.AsianDrama
    )

    override val mainPage: List<MainPageData> = emptyList()

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest,
    ): HomePageResponse {
        // PERBAIKAN: Menggunakan newHomePageResponse
        return newHomePageResponse(emptyList(), false)
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    // FUNGSI SEARCH: Menggunakan TMDb
    override suspend fun search(query: String): List<SearchResponse> {
        val searchUrl = "$mainUrl/search/multi?api_key=$tmdbApiKey&query=$query&language=en-US&page=1"
        
        return app.get(searchUrl)
            .parsedSafe<TmdbSearchResponse>() 
            ?.results
            ?.mapNotNull { item -> 
                item.toSearchResponse(this) 
            } ?: emptyList()
    }

    // FUNGSI LOAD: Menggunakan TMDb
    override suspend fun load(url: String): LoadResponse {
        // url: Adicinemax/{mediaType}/{id}
        val type = url.substringAfter('/').substringBefore('/') 
        val id = url.substringAfterLast('/')

        // Endpoint TMDb memerlukan 'append_to_response'
        val detailUrl = "$mainUrl/$type/$id?api_key=$tmdbApiKey&append_to_response=credits,videos&language=en-US"
        
        val document = app.get(detailUrl).parsedSafe<TmdbDetail>()
            ?: throw ErrorLoadingException("Gagal memuat detail TMDb untuk ID: $id")
        
        val isMovie = type == "movie"
        val title = document.title ?: document.name ?: throw ErrorLoadingException("Judul tidak ditemukan")
        val year = (document.releaseDate ?: document.firstAirDate)?.substringBefore("-")?.toIntOrNull()
        val plot = document.overview
        val tags = document.genres?.mapNotNull { it.name }
        val score = Score.from10(document.voteAverage)

        val posterBaseUrl = "https://image.tmdb.org/t/p/w500"
        val poster = document.posterPath?.let { posterBaseUrl + it }
        
        val actors = document.credits?.cast?.take(20)?.mapNotNull { cast ->
            ActorData(
                Actor(
                    cast.name ?: return@mapNotNull null,
                    cast.profilePath?.let { posterBaseUrl + it } 
                ),
                roleString = cast.character
            )
        }

        val trailer = document.videos?.results?.find { it.site == "YouTube" && it.type == "Trailer" }?.key

        return if (isMovie) {
            newMovieLoadResponse(
                title,
                url,
                TvType.Movie,
                // ID TMDb dan jenis media untuk fungsi loadLinks
                LoadData(id, detailPath = type).toJson() 
            ) {
                this.posterUrl = poster
                this.year = year
                this.plot = plot
                this.tags = tags
                this.score = score
                this.actors = actors
                trailer?.let { addTrailer(it) }
            }
        } else {
            // Logika untuk TV Series
            val episodes = document.seasons
                ?.filter { (it.episodeCount ?: 0) > 0 } 
                ?.mapNotNull { season ->
                    val seasonNumber = season.seasonNumber ?: return@mapNotNull null
                    (1..(season.episodeCount ?: 0)).map { episodeNumber ->
                        newEpisode(
                            LoadData(
                                id, 
                                seasonNumber, 
                                episodeNumber,
                                type // detailPath di sini adalah 'tv'
                            ).toJson()
                        ) {
                            this.season = seasonNumber
                            this.episode = episodeNumber
                        }
                    }
                }?.flatten() ?: emptyList()

            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.year = year
                this.plot = plot
                this.tags = tags
                this.score = score
                this.actors = actors
                trailer?.let { addTrailer(it) }
            }
        }
    }

    // FUNGSI loadLinks: Logika API Streaming Lama Dipertahankan
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val media = parseJson<LoadData>(data)
        
        // Logika ini menggunakan $apiUrl dan struktur API lama
        val referer = "$apiUrl/spa/videoPlayPage/movies/${media.detailPath}?id=${media.id}&type=/movie/detail&lang=en"

        val streams = app.get(
            "$apiUrl/wefeed-h5-bff/web/subject/play?subjectId=${media.id}&se=${media.season ?: 0}&ep=${media.episode ?: 0}",
            referer = referer
        ).parsedSafe<Media>()?.data?.streams

        streams?.reversed()?.distinctBy { it.url }?.map { source ->
            callback.invoke(
                newExtractorLink(
                    this.name, 
                    this.name, 
                    source.url ?: return@map,
                    INFER_TYPE
                ) {
                    this.referer = "$apiUrl/"
                    this.quality = getQualityFromName(source.resolutions)
                }
            )
        }

        val id = streams?.first()?.id
        val format = streams?.first()?.format

        app.get(
            "$apiUrl/wefeed-h5-bff/web/subject/caption?format=$format&id=$id&subjectId=${media.id}",
            referer = referer
        ).parsedSafe<Media>()?.data?.captions?.map { subtitle ->
            subtitleCallback.invoke(
                SubtitleFile(
                    subtitle.lanName ?: "",
                    subtitle.url ?: return@map
                )
            )
        }

        return true
    }
}

// ====================================================================
// --- DATA CLASS UNTUK API STREAMING LAMA ($apiUrl) ---
// ====================================================================

data class LoadData(
    val id: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val detailPath: String? = null, 
)

data class Media(
    @JsonProperty("data") val data: Data? = null,
) {
    data class Data(
        @JsonProperty("subjectList") val subjectList: ArrayList<Items>? = arrayListOf(),
        @JsonProperty("items") val items: ArrayList<Items>? = arrayListOf(),
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
            @JsonProperty("lan") val lan: String? = null,
            @JsonProperty("lanName") val lanName: String? = null,
            @JsonProperty("url") val url: String? = null,
        )
    }
}

data class MediaDetail(
    @JsonProperty("data") val data: Data? = null,
) {
    data class Data(
        @JsonProperty("subject") val subject: Items? = null,
        @JsonProperty("stars") val stars: ArrayList<Stars>? = arrayListOf(),
        @JsonProperty("resource") val resource: Resource? = null,
    ) {
        data class Stars(
            @JsonProperty("name") val name: String? = null,
            @JsonProperty("character") val character: String? = null,
            @JsonProperty("avatarUrl") val avatarUrl: String? = null,
        )

        data class Resource(
            @JsonProperty("seasons") val seasons: ArrayList<Seasons>? = arrayListOf(),
        ) {
            data class Seasons(
                @JsonProperty("se") val se: Int? = null,
                @JsonProperty("maxEp") val maxEp: Int? = null,
                @JsonProperty("allEp") val allEp: String? = null,
            )
        }
    }
}

data class Items(
    @JsonProperty("subjectId") val subjectId: String? = null,
    @JsonProperty("subjectType") val subjectType: Int? = null,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("description") val description: String? = null,
    @JsonProperty("releaseDate") val releaseDate: String? = null,
    @JsonProperty("duration") val duration: Long? = null,
    @JsonProperty("genre") val genre: String? = null,
    @JsonProperty("cover") val cover: Cover? = null,
    @JsonProperty("imdbRatingValue") val imdbRatingValue: String? = null,
    @JsonProperty("countryName") val countryName: String? = null,
    @JsonProperty("trailer") val trailer: Trailer? = null,
    @JsonProperty("detailPath") val detailPath: String? = null,
) {
    fun toSearchResponse(provider: Adicinemax): SearchResponse { 
        return provider.newMovieSearchResponse(
            title ?: "",
            subjectId ?: "",
            if (subjectType == 1) TvType.Movie else TvType.TvSeries,
            false
        ) {
            this.posterUrl = cover?.url
        }
    }

    data class Cover(
        @JsonProperty("url") val url: String? = null,
    )

    data class Trailer(
        @JsonProperty("videoAddress") val videoAddress: VideoAddress? = null,
    ) {
        data class VideoAddress(
            @JsonProperty("url") val url: String? = null,
        )
    }
}


// ====================================================================
// --- DATA CLASS BARU UNTUK TMDb ---
// ====================================================================

data class TmdbSearchResponse(
    @JsonProperty("page") val page: Int? = null,
    @JsonProperty("results") val results: ArrayList<TmdbSearchItem>? = arrayListOf(),
    @JsonProperty("total_pages") val totalPages: Int? = null,
    @JsonProperty("total_results") val totalResults: Int? = null
)

data class TmdbSearchItem(
    @JsonProperty("id") val id: Int? = null,
    @JsonProperty("media_type") val mediaType: String? = null, 
    @JsonProperty("title") val title: String? = null, 
    @JsonProperty("name") val name: String? = null, 
    @JsonProperty("poster_path") val posterPath: String? = null,
    @JsonProperty("release_date") val releaseDate: String? = null, 
    @JsonProperty("first_air_date") val firstAirDate: String? = null, 
) {
    fun toSearchResponse(provider: Adicinemax): SearchResponse? {
        if (mediaType == "person" || id == null) return null

        val type = when (mediaType) {
            "movie" -> TvType.Movie
            "tv" -> TvType.TvSeries
            else -> return null
        }
        
        val finalTitle = title ?: name ?: return null

        // URL yang akan digunakan di fungsi load: Adicinemax/movie/{id} atau Adicinemax/tv/{id}
        val url = provider.name + "/${mediaType}/${id}"

        val posterBaseUrl = "https://image.tmdb.org/t/p/w500"
        val posterUrl = if (posterPath != null) "$posterBaseUrl$posterPath" else null

        return provider.newMovieSearchResponse(
            finalTitle,
            url,
            type,
            false
        ) {
            this.posterUrl = posterUrl
            val year = (releaseDate ?: firstAirDate)?.substringBefore("-")?.toIntOrNull()
            this.year = year
        }
    }
}

data class TmdbDetail(
    @JsonProperty("id") val id: Int? = null,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("overview") val overview: String? = null,
    @JsonProperty("poster_path") val posterPath: String? = null,
    @JsonProperty("backdrop_path") val backdropPath: String? = null,
    @JsonProperty("release_date") val releaseDate: String? = null,
    @JsonProperty("first_air_date") val firstAirDate: String? = null,
    @JsonProperty("vote_average") val voteAverage: Double? = null,
    @JsonProperty("genres") val genres: ArrayList<TmdbGenre>? = arrayListOf(),
    @JsonProperty("credits") val credits: TmdbCredits? = null, 
    @JsonProperty("videos") val videos: TmdbVideos? = null, 
    @JsonProperty("seasons") val seasons: ArrayList<TmdbSeason>? = arrayListOf(), 
) {
    data class TmdbGenre(
        @JsonProperty("name") val name: String? = null,
    )
    
    data class TmdbCredits(
        @JsonProperty("cast") val cast: ArrayList<TmdbCast>? = arrayListOf(),
    )
    
    data class TmdbCast(
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("character") val character: String? = null,
        @JsonProperty("profile_path") val profilePath: String? = null,
    )

    data class TmdbVideos(
        @JsonProperty("results") val results: ArrayList<TmdbVideoItem>? = arrayListOf(),
    )

    data class TmdbVideoItem(
        @JsonProperty("site") val site: String? = null,
        @JsonProperty("type") val type: String? = null,
        @JsonProperty("key") val key: String? = null,
    )

    data class TmdbSeason(
        @JsonProperty("season_number") val seasonNumber: Int? = null,
        @JsonProperty("episode_count") val episodeCount: Int? = null,
    )
}
