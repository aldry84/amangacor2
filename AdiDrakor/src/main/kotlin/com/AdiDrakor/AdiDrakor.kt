package com.AdiDrakor

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.nicehttp.RequestBodyTypes
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

class AdiDrakor : MainAPI() {
    override var mainUrl = "" // Ganti dengan URL yang benar
    private val apiUrl = "" // Ganti dengan URL yang benar

    override val instantLinkLoading = true
    override var name = "AdiDrakor"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override var lang = "en"
    override val supportedTypes = setOf(
        TvType.TvSeries,
        TvType.AsianDrama,
        TvType.Movie
    )

    override val mainPage: List<MainPageData> = mainPageOf(
        "2,ForYou" to "Drakor Pilihan",
        "2,Hottest" to "Drakor Terpopuler",
        "2,Latest" to "Drakor Terbaru",
        "2,Rating" to "Drakor Rating Tertinggi",
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

        return try {
            val home = app.post("$mainUrl/wefeed-h5-bff/web/filter", requestBody = body)
                .parsedSafe<Media>()?.data?.items
                ?.filter { it.countryName?.contains("Korea", ignoreCase = true) == true && (it.subjectType == 2 || it.subjectType == 1) }
                ?.map { it.toSearchResponse(this) }
                ?: throw ErrorLoadingException("Tidak ada Data Drakor Ditemukan")

            newHomePageResponse(request.name, home)
        } catch (e: Exception) {
            logError("Error loading main page: ${e.message}", e)
            throw ErrorLoadingException("Gagal memuat halaman utama: ${e.message}")
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> {
        return search(query) ?: emptyList()
    }

    override suspend fun search(query: String): List<SearchResponse>? {
        return try {
            val results = app.post(
                "$mainUrl/wefeed-h5-bff/web/subject/search", requestBody = mapOf(
                    "keyword" to query,
                    "page" to "1",
                    "perPage" to "0",
                    "subjectType" to "0",
                ).toJson().toRequestBody(RequestBodyTypes.JSON.toMediaTypeOrNull())
            ).parsedSafe<Media>()?.data?.items
                ?.filter { it.countryName?.contains("Korea", ignoreCase = true) == true }
                ?.map { it.toSearchResponse(this) }
                ?: return null

            results
        } catch (e: Exception) {
            logError("Error during search: ${e.message}", e)
            null // Atau throw exception, tergantung kebutuhan
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val id = url.substringAfterLast("/")

        return try {
            val document = app.get("$mainUrl/wefeed-h5-bff/web/subject/detail?subjectId=$id")
                .parsedSafe<MediaDetail>()?.data
            val subject = document?.subject
            val title = subject?.title ?: ""
            val poster = subject?.cover?.url
            val tags = subject?.genre?.split(",")?.map { it.trim() }
            val year = subject?.releaseDate?.substringBefore("-")?.toIntOrNull()

            val tvType = when (subject?.subjectType) {
                1 -> TvType.Movie
                2 -> TvType.TvSeries
                else -> {
                    logWarning("Unknown subjectType: ${subject?.subjectType}, defaulting to AsianDrama")
                    TvType.AsianDrama
                }
            }

            val description = subject?.description
            val trailer = subject?.trailer?.videoAddress?.url

            val actors = document?.stars?.mapNotNull { cast ->
                ActorData(
                    Actor(
                        cast.name ?: return@mapNotNull null,
                        cast.avatarUrl
                    ),
                    roleString = cast.character
                )
            }?.distinctBy { it.actor }

            val recommendations =
                app.get("$mainUrl/wefeed-h5-bff/web/subject/detail-rec?subjectId=$id&page=1&perPage=12")
                    .parsedSafe<Media>()?.data?.items
                    ?.filter { it.countryName?.contains("Korea", ignoreCase = true) == true }
                    ?.map { it.toSearchResponse(this) }

            if (tvType == TvType.Movie) {
                newMovieLoadResponse(title, url, TvType.Movie,
                    document?.resource?.seasons?.firstOrNull()?.allEp?.split(",")?.map { it.toInt() }
                        ?.map { ep ->
                            newEpisode(
                                LoadData(
                                    id,
                                    1,
                                    ep,
                                    subject?.detailPath
                                ).toJson()
                            ) {
                                this.episode = ep
                            }
                        } ?: emptyList()) {
                    this.posterUrl = poster
                    this.year = year
                    this.plot = description
                    this.tags = tags
                    this.score = Score.from10(subject?.imdbRatingValue)
                    this.actors = actors
                    this.recommendations = recommendations
                    addTrailer(trailer, addRaw = true)
                }
            } else {
                newTvSeriesLoadResponse(title, url, TvType.TvSeries,
                    document?.resource?.seasons?.map { seasons ->
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
                    }?.flatten() ?: emptyList()) {
                    this.posterUrl = poster
                    this.year = year
                    this.plot = description
                    this.tags = tags
                    this.score = Score.from10(subject?.imdbRatingValue)
                    this.actors = actors
                    this.recommendations = recommendations
                    addTrailer(trailer, addRaw = true)
                }
            }
        } catch (e: Exception) {
            logError("Error loading details for URL $url: ${e.message}", e)
            throw ErrorLoadingException("Gagal memuat detail: ${e.message}")
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val media = parseJson<LoadData>(data)
        val referer = "$apiUrl/spa/videoPlayPage/movies/${media.detailPath}?id=${media.id}&type=/movie/detail&lang=en"

        return try {
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

            true
        } catch (e: Exception) {
            logError("Error loading links for data $data: ${e.message}", e)
            false // Atau throw exception, tergantung kebutuhan
        }
    }

    private fun logError(message: String, e: Exception) {
        // Implementasi logging (misalnya, ke console atau file)
        println("ERROR: $message - ${e.stackTraceToString()}")
    }

    private fun logWarning(message: String) {
        // Implementasi logging untuk warning
        println("WARNING: $message")
    }

    // Fungsi pembantu untuk mengurangi duplikasi kode
    private fun createLoadResponse(
        title: String,
        url: String,
        tvType: TvType,
        poster: String?,
        year: Int?,
        description: String?,
        tags: List<String>?,
        imdbRatingValue: String?,
        actors: List<ActorData>?,
        recommendations: List<SearchResponse>?,
        trailer: String?,
        episodeList: List<Episode>, // Daftar episode
    ): LoadResponse {
        val loadResponse = if (tvType == TvType.Movie) {
            newMovieLoadResponse(title, url, tvType, episodeList) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.score = Score.from10(imdbRatingValue)
                this.actors = actors
                this.recommendations = recommendations
                addTrailer(trailer, addRaw = true)
            }
        } else {
            newTvSeriesLoadResponse(title, url, tvType, episodeList) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.score = Score.from10(imdbRatingValue)
                this.actors = actors
                this.recommendations = recommendations
                addTrailer(trailer, addRaw = true)
            }
        }
        return loadResponse
    }

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
        @JsonProperty("subjectType") val subjectType: Int? = null, // 2 = TvSeries/Drama, 1 = Movie
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("description") val description: String? = null,
        @JsonProperty("releaseDate") val releaseDate: String? = null,
        @JsonProperty("duration") val duration: Long? = null,
        @JsonProperty("genre") val genre: String? = null,
        @JsonProperty("cover") val cover: Cover? = null,
        @JsonProperty("imdbRatingValue") val imdbRatingValue: String? = null,
        @JsonProperty("countryName") val countryName: String? = null, // Digunakan untuk filter Korea
        @JsonProperty("trailer") val trailer: Trailer? = null,
        @JsonProperty("detailPath") val detailPath: String? = null,
    ) {
        fun toSearchResponse(provider: AdiDrakor): SearchResponse {
            val type = when (subjectType) {
                1 -> TvType.Movie
                2 -> TvType.TvSeries
                else -> TvType.AsianDrama
            }

            return provider.newMovieSearchResponse(
                title ?: "",
                subjectId ?: "",
                type,
                false
            ) {
                this.posterUrl = cover?.url
                this.score = Score.from10(imdbRatingValue)
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
}
