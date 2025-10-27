package com.Adimoviebox

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.nicehttp.RequestBodyTypes
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

// --- PERUBAHAN 1: Tambahkan import Score ---
import com.lagradost.cloudstream3.Score
// ------------------------------------------

class Adimoviebox : MainAPI() {
    override var mainUrl = "https://moviebox.ph"
    private val apiUrl = "https://fmoviesunblocked.net"
    
    override val instantLinkLoading = true
    override var name = "Adimoviebox" 
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
        "1,ForYou" to "Movie ForYou",
        "1,Hottest" to "Movie Hottest",
        "1,Latest" to "Movie Latest",
        "1,Rating" to "Movie Rating",
        "2,ForYou" to "TVShow ForYou",
        "2,Hottest" to "TVShow Hottest",
        "2,Latest" to "TVShow Latest",
        "2,Rating" to "TVShow Rating",
        "1006,ForYou" to "Animation ForYou",
        "1006,Hottest" to "Animation Hottest",
        "1006,Latest" to "Animation Latest",
        "1006,Rating" to "Animation Rating",
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest,
    ): HomePageResponse {
        val params = request.data.split(",")
        val body = mapOf(
            "channelId" to params.first(),
            "page" to page,
            "perPage" to "24",
            "sort" to params.last()
        ).toJson().toRequestBody(RequestBodyTypes.JSON.toMediaTypeOrNull())

        val home = app.post("$mainUrl/wefeed-h5-bff/web/filter", requestBody = body)
            .parsedSafe<Media>()?.data?.items?.map {
                it.toSearchResponse(this)
            } ?: throw ErrorLoadingException("No Data Found")

        return newHomePageResponse(request.name, home)
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> {
        return search(query) ?: emptyList()
    }

    override suspend fun search(query: String): List<SearchResponse>? {
        val results = app.post(
            "$mainUrl/wefeed-h5-bff/web/subject/search", requestBody = mapOf(
                "keyword" to query,
                "page" to "1",
                "perPage" to "0",
                "subjectType" to "0",
            ).toJson().toRequestBody(RequestBodyTypes.JSON.toMediaTypeOrNull())
        ).parsedSafe<Media>()?.data?.items?.map { it.toSearchResponse(this) }
            ?: return null
            
        return results 
    }

    override suspend fun load(url: String): LoadResponse {
        val id = url.substringAfterLast("/")
        val document = app.get("$mainUrl/wefeed-h5-bff/web/subject/detail?subjectId=$id")
            .parsedSafe<MediaDetail>()?.data
        val subject = document?.subject
        val title = subject?.title ?: ""
        val poster = subject?.cover?.url
        val tags = subject?.genre?.split(",")?.map { it.trim() }

        val year = subject?.releaseDate?.substringBefore("-")?.toIntOrNull()
        val tvType = if (subject?.subjectType == 2) TvType.TvSeries else TvType.Movie
        val description = subject?.description
        val trailer = subject?.trailer?.videoAddress?.url
        
        // --- PERUBAHAN 2: Proses Data Skor ---
        val scores = subject?.imdbRatingValue?.toFloatOrNull()?.let { rating ->
            // Pastikan nilai rating adalah Float, lalu buat objek Score
            listOf(
                Score(
                    score = rating,
                    type = "IMDb", // Berdasarkan nama properti: imdbRatingValue
                    maxScore = 10f // Standar rating IMDb adalah /10
                )
            )
        } ?: emptyList() // Jika nilai rating null atau tidak valid, gunakan list kosong
        // --------------------------------------

        val actors = document?.stars?.mapNotNull { cast ->
            ActorData(
                Actor(
                    cast.name ?: return@mapNotNull null,
                    cast.avatarUrl
                ),
                roleString = cast.character
            )
        )?.distinctBy { it.actor }

        val recommendations =
            app.get("$mainUrl/wefeed-h5-bff/web/subject/detail-rec?subjectId=$id&page=1&perPage=12")
                .parsedSafe<Media>()?.data?.items?.map {
                    it.toSearchResponse(this)
                }

        return if (tvType == TvType.TvSeries) {
            val episode = document?.resource?.seasons?.map { seasons ->
                (if (seasons.allEp.isNullOrEmpty()) (1..seasons.maxEp!!) else seasons.allEp.split(",")
                    .map { it.toInt() })
                    .map { episode ->
                        newEpisode(
                            LoadData(
                                id,
                                seasons.se,
                                episode,
                                subject?.detailPath
                            ).toJson()
                        ) {
                            this.season = seasons.se
                            this.episode = episode
                        }
                    }
            }?.flatten() ?: emptyList()
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episode) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.scores = scores // <-- PERUBAHAN 3: Menerapkan skor di sini
                this.actors = actors
                this.recommendations = recommendations
                addTrailer(trailer, addRaw = true)
            }
        } else {
            newMovieLoadResponse(
                title,
                url,
                TvType.Movie,
                LoadData(id, detailPath = subject?.detailPath).toJson()
            ) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.scores = scores // <-- PERUBAHAN 4: Menerapkan skor di sini
                this.actors = actors
                this.recommendations = recommendations
                addTrailer(trailer, addRaw = true)
            }
        }
    }
// ... kode loadLinks, LoadData, dan data class lainnya tetap sama ...
