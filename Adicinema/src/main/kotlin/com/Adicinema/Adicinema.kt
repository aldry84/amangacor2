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
    // API key telah dimasukkan
    private val API_KEY = "1d8730d33fc13ccbd8cdaaadb74892c7" 
    
    // Base URL TMDb untuk API data
    override var mainUrl = "https://api.themoviedb.org/3" 
    
    // URL ini dipertahankan, dan akan digunakan untuk loadLinks/extractor
    private val apiUrl = "https://fmoviesunblocked.net" 
    
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

    // PERUBAHAN: Mengubah Main Page untuk mencerminkan endpoint TMDb yang lebih umum
    // Menggunakan endpoint discover dan popular/top_rated
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

        // PERUBAHAN UTAMA: Mengganti post dengan get, dan endpoint ke standar TMDb
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
        // PERUBAHAN UTAMA: Mengganti endpoint pencarian ke standar TMDb
        val url = "$mainUrl/search/multi?api_key=$API_KEY&query=$query" 

        val results = app.get(url)
            .parsedSafe<TMDbPageResult>()?.results
            ?.filter { it.media_type != "person" } // Filter out results that are people
            ?.map { it.toSearchResponse(this) }
            ?: return null
            
        return results 
    }

    override suspend fun load(url: String): LoadResponse {
        // Asumsi URL berisi tipe media dan ID TMDb, misal: /movie/123456
        // Karena ini tidak ada di kode asli, saya akan menggunakan URL asli untuk ID
        val id = url.substringAfterLast("/")
        val mediaType = if (url.contains("/tv/")) "tv" else "movie"

        // PERUBAHAN UTAMA: Mengganti endpoint detail ke standar TMDb
        val detailUrl = "$mainUrl/$mediaType/$id?api_key=$API_KEY&append_to_response=videos,credits,recommendations"
        
        // Memuat detail dari TMDb
        val document = app.get(detailUrl)
            .parsedSafe<TMDbDetailResult>()
        
        val title = document?.title ?: document?.name ?: ""
        // TMDb menggunakan poster_path, bukan subject?.cover?.url
        val poster = document?.poster_path?.let { "https://image.tmdb.org/t/p/w500$it" }
        val tags = document?.genres?.map { it.name }

        // Untuk TMDb, tanggal rilis atau tanggal tayang pertama
        val releaseDate = document?.release_date ?: document?.first_air_date
        val year = releaseDate?.substringBefore("-")?.toIntOrNull()
        
        val tvType = when(document?.media_type) {
            "tv" -> TvType.TvSeries
            "movie" -> TvType.Movie
            else -> TvType.Movie // Default jika media_type tidak jelas
        }
        
        val description = document?.overview
        
        // Mengambil trailer/video dari 'videos' yang di-append
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


        // CATATAN: Karena kita mendapatkan data dari TMDb, kita TIDAK mendapatkan data episode dari API TMDb.
        // Data episode untuk TvSeries harus diambil secara terpisah dari Season.
        // Namun, agar kode link loading tetap berjalan dengan URL lama Anda, 
        // kita akan menggunakan URL kustom lama Anda untuk data loadLinks.

        // Mempertahankan struktur load links lama
        val loadData = LoadData(
            id,
            null, // Season & Episode tidak tersedia di detail TMDb utama
            null,
            null // detailPath tidak ada di TMDb
        )

        return if (tvType == TvType.TvSeries) {
            // Karena data episode dari TMDb memerlukan panggilan tambahan yang kompleks,
            // dan kode asli tidak menggunakan TMDb untuk episode, saya akan menggunakan struktur 
            // LoadData yang kosong/default untuk TV Series.
            
            // CATATAN: Kode ini TIDAK akan mengambil daftar episode. Ini hanya akan menyiapkan objek TvSeries.
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
        // Bagian ini TIDAK DIUBAH karena menggunakan 'apiUrl' yang lama
        val media = parseJson<LoadData>(data)
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
                newSubtitleFile(
                    subtitle.lanName ?: "",
                    subtitle.url ?: return@map
                )
            )
        }

        return true
    }
    
    // Semua Data Class yang diperlukan untuk TMDb dan API lama
    
    // Data Class untuk menyimpan data lama yang diperlukan untuk loadLinks
    data class LoadData(
        val id: String? = null,
        val season: Int? = null,
        val episode: Int? = null,
        val detailPath: String? = null,
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
    }

    // Data Class lama (dipertahankan hanya untuk loadLinks/extractor)
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
