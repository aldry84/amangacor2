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

class Adimoviebox : MainAPI() {
    // Diperbarui berdasarkan Source 1 & 2: Domain utama lok-lok.cc
    override var mainUrl = "https://lok-lok.cc"
    
    // Diperbarui berdasarkan Source 1 & 4: API untuk detail ada di aoneroom.com
    private val apiUrl = "https://h5-api.aoneroom.com"

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

    [span_9](start_span)// Header standar berdasarkan Network Log[span_9](end_span)
    private val baseHeaders = mapOf(
        "origin" to mainUrl,
        "x-client-info" to """{"timezone":"Asia/Jakarta"}""", // Disesuaikan dari log
        "user-agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Mobile Safari/537.36",
        "accept-language" to "en-US,en;q=0.9"
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

        // Menggunakan path 'wefeed-h5api-bff' (estimasi berdasarkan perubahan endpoint lain)
        val home = app.post(
            "$mainUrl/wefeed-h5api-bff/web/filter", 
            requestBody = body, 
            headers = baseHeaders
        ).parsedSafe<Media>()?.data?.items?.map {
            it.toSearchResponse(this)
        } ?: throw ErrorLoadingException("No Data Found")

        return newHomePageResponse(request.name, home)
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun search(query: String): List<SearchResponse> {
        return app.post(
            "$mainUrl/wefeed-h5api-bff/web/subject/search",
            headers = baseHeaders,
            requestBody = mapOf(
                "keyword" to query,
                "page" to "1",
                "perPage" to "0",
                "subjectType" to "0",
            ).toJson().toRequestBody(RequestBodyTypes.JSON.toMediaTypeOrNull())
        ).parsedSafe<Media>()?.data?.items?.map { it.toSearchResponse(this) }
            ?: throw ErrorLoadingException("Search failed")
    }

    override suspend fun load(url: String): LoadResponse {
        [span_10](start_span)// Log[span_10](end_span) menunjukkan detailPath adalah parameter kunci (misal: mamasan-EaxxOMCxk56)
        val detailPath = url.substringAfterLast("/") // ID/Slug ada di akhir URL
        
        [span_11](start_span)// Log[span_11](end_span) Request ke h5-api.aoneroom.com
        val document = app.get(
            "$apiUrl/wefeed-h5api-bff/detail?detailPath=$detailPath",
            headers = baseHeaders
        ).parsedSafe<MediaDetail>()?.data

        val subject = document?.subject
        // subjectId diperlukan untuk loadLinks nanti
        val id = subject?.subjectId ?: throw ErrorLoadingException("No Subject ID found")
        
        val title = subject?.title ?: ""
        val poster = subject?.cover?.url
        val tags = subject?.genre?.split(",")?.map { it.trim() }

        val year = subject?.releaseDate?.substringBefore("-")?.toIntOrNull()
        val tvType = if (subject?.subjectType == 2) TvType.TvSeries else TvType.Movie
        val description = subject?.description
        val trailer = subject?.trailer?.videoAddress?.url
        val score = Score.from10(subject?.imdbRatingValue?.toString())
        
        val actors = document?.stars?.mapNotNull { cast ->
            ActorData(
                Actor(cast.name ?: return@mapNotNull null, cast.avatarUrl),
                roleString = cast.character
            )
        }?.distinctBy { it.actor }

        val recommendations = app.get(
            "$apiUrl/wefeed-h5api-bff/detail-rec?detailPath=$detailPath&page=1&perPage=12",
            headers = baseHeaders
        ).parsedSafe<Media>()?.data?.items?.map {
            it.toSearchResponse(this)
        }

        // Data yang akan dikirim ke loadLinks (termasuk detailPath baru)
        val loadDataJson = LoadData(
            id = id,
            detailPath = detailPath // Penting: ini digunakan di parameter request play
        ).toJson()

        return if (tvType == TvType.TvSeries) {
            val episode = document?.resource?.seasons?.map { seasons ->
                (if (seasons.allEp.isNullOrEmpty()) (1..seasons.maxEp!!) else seasons.allEp.split(",")
                    .map { it.toInt() })
                    .map { episode ->
                        newEpisode(
                            LoadData(
                                id = id,
                                season = seasons.se,
                                episode = episode,
                                detailPath = detailPath
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
                this.score = score
                this.actors = actors
                this.recommendations = recommendations
                addTrailer(trailer, addRaw = true)
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, loadDataJson) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.score = score
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
        
        [span_12](start_span)// Log[span_12](end_span) Referer harus format ini
        val referer = "$mainUrl/spa/videoPlayPage/movies/${media.detailPath}?id=${media.id}&utm_source=app-search"

        [span_13](start_span)[span_14](start_span)// Headers khusus untuk Play Endpoint[span_13](end_span)[span_14](end_span)
        val playHeaders = baseHeaders.toMutableMap().apply {
            put("referer", referer)
            put("x-source", "app-search") // Wajib berdasarkan log
        }

        [span_15](start_span)[span_16](start_span)// Log[span_15](end_span)[span_16](end_span) Request ke lok-lok.cc/wefeed-h5api-bff/subject/play
        // Perhatikan parameter: subjectId, se, ep, DAN detailPath (Wajib)
        val streams = app.get(
            "$mainUrl/wefeed-h5api-bff/subject/play?subjectId=${media.id}&se=${media.season ?: 0}&ep=${media.episode ?: 0}&detailPath=${media.detailPath}",
            headers = playHeaders
        ).parsedSafe<Media>()?.data?.streams

        streams?.reversed()?.distinctBy { it.url }?.map { source ->
            callback.invoke(
                newExtractorLink(
                    this.name,
                    this.name,
                    source.url ?: return@map,
                    INFER_TYPE
                ) {
                    this.referer = mainUrl
                    this.quality = getQualityFromName(source.resolutions)
                }
            )
        }

        // Subtitles (Caption) - path disesuaikan ke h5api
        val id = streams?.firstOrNull()?.id
        val format = streams?.firstOrNull()?.format
        
        if (id != null) {
             app.get(
                "$mainUrl/wefeed-h5api-bff/web/subject/caption?format=$format&id=$id&subjectId=${media.id}",
                headers = playHeaders
            ).parsedSafe<Media>()?.data?.captions?.map { subtitle ->
                subtitleCallback.invoke(
                    newSubtitleFile(
                        subtitle.lanName ?: "",
                        subtitle.url ?: return@map
                    )
                )
            }
        }

        return true
    }
}

// --- Data Classes ---

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
    fun toSearchResponse(provider: Adimoviebox): SearchResponse {
        // detailPath digunakan sebagai slug URL utama
        val url = "${provider.mainUrl}/detail/${detailPath ?: subjectId}"

        return provider.newMovieSearchResponse(
            title ?: "",
            url,
            if (subjectType == 1) TvType.Movie else TvType.TvSeries,
            false
        ) {
            this.posterUrl = cover?.url
            this.score = Score.from10(imdbRatingValue?.toString())
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
