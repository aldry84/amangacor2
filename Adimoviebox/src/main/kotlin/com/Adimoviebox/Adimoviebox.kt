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
    override var mainUrl = "https://lok-lok.cc" 
    private val apiUrl = "https://h5-api.aoneroom.com"

    override val instantLinkLoading = true
    override var name = "Adimoviebox"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override var lang = "id"
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
        TvType.AsianDrama
    )

    // Header Global
    private val apiHeaders = mapOf(
        "authority" to "lok-lok.cc",
        "accept" to "application/json",
        "origin" to "https://lok-lok.cc",
        "referer" to "https://lok-lok.cc/",
        "x-source" to "app-search",
        "user-agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Mobile Safari/537.36"
    )

    // Header khusus Poster (Jaga-jaga jika CDN butuh UA)
    private val imageHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Mobile Safari/537.36"
    )

    override val mainPage: List<MainPageData> = mainPageOf(
        "5283462032510044280" to "Indonesian Drama",
        "6528093688173053896" to "Indonesian Movies",
        "5848753831881965888" to "Indo Horror",
        "997144265920760504" to "Hollywood Movies",
        "4380734070238626200" to "K-Drama",
        "8624142774394406504" to "C-Drama",
        "3058742380078711608" to "Disney",
        "8449223314756747760" to "Pinoy Drama",
        "606779077307122552" to "Pinoy Movie",
        "872031290915189720" to "Bad Ending Romance",
        "filter_pinoy_romance" to "Pinoy Romance (Filter)"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest,
    ): HomePageResponse {
        val id = request.data
        
        val responseData = if (id == "filter_pinoy_romance") {
            // POST untuk Filter
            val postBody = mapOf(
                "page" to page,
                "perPage" to 24,
                "channelId" to 2,
                "genre" to "Romance",
                "country" to "Philippines"
            )
            
            app.post(
                "$apiUrl/wefeed-h5api-bff/subject/filter",
                headers = apiHeaders,
                json = postBody
            ).parsedSafe<Media>()?.data
            
        } else {
            // GET untuk Ranking
            val targetUrl = "$apiUrl/wefeed-h5api-bff/ranking-list/content?id=$id&page=$page&perPage=12"
            app.get(targetUrl, headers = apiHeaders).parsedSafe<Media>()?.data
        }

        val listFilm = responseData?.subjectList ?: responseData?.items

        val home = listFilm?.map {
            it.toSearchResponse(this)
        } ?: throw ErrorLoadingException("Gagal memuat kategori.")

        return newHomePageResponse(request.name, home)
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun search(query: String): List<SearchResponse> {
        val searchUrl = "$apiUrl/wefeed-h5api-bff/search/content?keyword=$query&page=1&perPage=20"
        
        val response = app.get(searchUrl, headers = apiHeaders).parsedSafe<Media>()
        val listFilm = response?.data?.subjectList ?: response?.data?.items

        if (!listFilm.isNullOrEmpty()) {
            return listFilm.map { it.toSearchResponse(this) }
        }
        return emptyList()
    }

    override suspend fun load(url: String): LoadResponse {
        val id = url.substringAfterLast("/")
        val isNumericId = id.all { it.isDigit() }
        
        val targetDetailUrl = if (isNumericId) {
            "$apiUrl/wefeed-h5api-bff/subject/detail?subjectId=$id"
        } else {
            "$apiUrl/wefeed-h5api-bff/detail?detailPath=$id"
        }

        val detailData = app.get(targetDetailUrl, headers = apiHeaders).parsedSafe<MediaDetail>()?.data
        val subject = detailData?.subject ?: throw ErrorLoadingException("Gagal memuat detail film.")
        
        val realId = subject.subjectId ?: id 
        val detailPath = subject.detailPath ?: id

        val title = subject.title ?: ""
        val poster = subject.cover?.url
        val tags = subject.genre?.split(",")?.map { it.trim() }
        val year = subject.releaseDate?.substringBefore("-")?.toIntOrNull()
        val tvType = if (subject.subjectType == 2) TvType.TvSeries else TvType.Movie
        val description = subject.description
        val trailer = subject.trailer?.videoAddress?.url
        val score = Score.from10(subject.imdbRatingValue?.toString())
        
        val actors = detailData.stars?.mapNotNull { cast ->
            ActorData(
                Actor(cast.name ?: return@mapNotNull null, cast.avatarUrl),
                roleString = cast.character
            )
        }?.distinctBy { it.actor }

        val recommendations = app.get(
            "$apiUrl/wefeed-h5api-bff/subject/detail-rec?subjectId=$realId&page=1&perPage=12", 
            headers = apiHeaders
        ).parsedSafe<Media>()?.data?.items?.map { it.toSearchResponse(this) }

        val episodeList = detailData.resource?.seasons?.map { season ->
            val eps = if (!season.allEp.isNullOrEmpty()) {
                season.allEp.split(",").map { it.toInt() }
            } else {
                (1..(season.maxEp ?: 1)).toList()
            }
            
            eps.map { epNum ->
                newEpisode(
                    LoadData(
                        id = realId, 
                        season = season.se,
                        episode = epNum,
                        detailPath = detailPath
                    ).toJson()
                ) {
                    this.season = season.se
                    this.episode = epNum
                    this.name = if (tvType == TvType.Movie) title else "Episode $epNum"
                    this.description = season.resolutions?.joinToString(", ") { "${it.resolution}p" }
                    this.posterUrl = poster
                }
            }
        }?.flatten() ?: emptyList()

        return if (tvType == TvType.TvSeries) {
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodeList) {
                this.posterUrl = poster
                this.posterHeaders = imageHeaders
                this.year = year
                this.plot = description
                this.tags = tags
                this.score = score
                this.actors = actors
                this.recommendations = recommendations
                addTrailer(trailer, addRaw = true)
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, LoadData(realId, 0, 1, detailPath).toJson()) {
                this.posterUrl = poster
                this.posterHeaders = imageHeaders
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
        val refererUrl = "$mainUrl/spa/videoPlayPage/movies/${media.detailPath}?id=${media.id}&utm_source=app-search"
        
        val playHeaders = apiHeaders.toMutableMap().apply {
            put("referer", refererUrl)
        }

        val playUrl = "$apiUrl/wefeed-h5api-bff/subject/play?subjectId=${media.id}&se=${media.season ?: 0}&ep=${media.episode ?: 0}&detailPath=${media.detailPath}"

        val streams = app.get(playUrl, headers = playHeaders).parsedSafe<Media>()?.data?.streams

        streams?.forEach { source ->
            callback.invoke(
                newExtractorLink(
                    this.name,
                    this.name,
                    source.url ?: return@forEach,
                    INFER_TYPE
                ) {
                    this.referer = mainUrl
                    this.quality = source.resolutions?.toIntOrNull() ?: Qualities.Unknown.value
                }
            )
        }

        val id = streams?.firstOrNull()?.id
        val format = streams?.firstOrNull()?.format

        if (id != null && format != null) {
             app.get(
                "$apiUrl/wefeed-h5api-bff/subject/caption?format=$format&id=$id&subjectId=${media.id}",
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

// --- DATA CLASSES ---

data class LoadData(
    val id: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val detailPath: String? = null,
)

data class Media(
    @param:JsonProperty("data") val data: Data? = null,
) {
    data class Data(
        @param:JsonProperty("subjectList") val subjectList: ArrayList<Items>? = arrayListOf(),
        @param:JsonProperty("items") val items: ArrayList<Items>? = arrayListOf(),
        @param:JsonProperty("streams") val streams: ArrayList<Streams>? = arrayListOf(),
        @param:JsonProperty("captions") val captions: ArrayList<Captions>? = arrayListOf(),
    ) {
        data class Streams(
            @param:JsonProperty("id") val id: String? = null,
            @param:JsonProperty("format") val format: String? = null,
            @param:JsonProperty("url") val url: String? = null,
            @param:JsonProperty("resolutions") val resolutions: String? = null,
        )

        data class Captions(
            @param:JsonProperty("lan") val lan: String? = null,
            @param:JsonProperty("lanName") val lanName: String? = null,
            @param:JsonProperty("url") val url: String? = null,
        )
    }
}

data class MediaDetail(
    @param:JsonProperty("data") val data: Data? = null,
) {
    data class Data(
        @param:JsonProperty("subject") val subject: Items? = null,
        @param:JsonProperty("stars") val stars: ArrayList<Stars>? = arrayListOf(),
        @param:JsonProperty("resource") val resource: Resource? = null,
    ) {
        data class Stars(
            @param:JsonProperty("name") val name: String? = null,
            @param:JsonProperty("character") val character: String? = null,
            @param:JsonProperty("avatarUrl") val avatarUrl: String? = null,
        )

        data class Resource(
            @param:JsonProperty("seasons") val seasons: ArrayList<Seasons>? = arrayListOf(),
        ) {
            data class Seasons(
                @param:JsonProperty("se") val se: Int? = null,
                @param:JsonProperty("maxEp") val maxEp: Int? = null,
                @param:JsonProperty("allEp") val allEp: String? = null,
                @param:JsonProperty("resolutions") val resolutions: ArrayList<Resolutions>? = arrayListOf()
            ) {
                data class Resolutions(
                    @param:JsonProperty("resolution") val resolution: Int? = null,
                    @param:JsonProperty("epNum") val epNum: Int? = null
                )
            }
        }
    }
}

data class Items(
    @param:JsonProperty("subjectId") val subjectId: String? = null,
    @param:JsonProperty("subjectType") val subjectType: Int? = null,
    @param:JsonProperty("title") val title: String? = null,
    @param:JsonProperty("description") val description: String? = null,
    @param:JsonProperty("releaseDate") val releaseDate: String? = null,
    @param:JsonProperty("duration") val duration: Long? = null,
    @param:JsonProperty("genre") val genre: String? = null,
    @param:JsonProperty("cover") val cover: Cover? = null,
    @param:JsonProperty("imdbRatingValue") val imdbRatingValue: String? = null,
    @param:JsonProperty("countryName") val countryName: String? = null,
    @param:JsonProperty("trailer") val trailer: Trailer? = null,
    @param:JsonProperty("detailPath") val detailPath: String? = null,
) {
    fun toSearchResponse(provider: Adimoviebox): SearchResponse {
        val finalId = detailPath ?: subjectId
        val url = "${provider.mainUrl}/detail/${finalId}"
        val posterImage = cover?.url

        return provider.newMovieSearchResponse(
            title ?: "No Title",
            url,
            if (subjectType == 1) TvType.Movie else TvType.TvSeries,
            false
        ) {
            this.posterUrl = posterImage
            this.posterHeaders = mapOf("User-Agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Mobile Safari/537.36")
            // Perbaikan: Hapus .toString() yang redundan
            this.score = Score.from10(imdbRatingValue)
            this.year = releaseDate?.substringBefore("-")?.toIntOrNull()
        }
    }

    data class Cover(
        @param:JsonProperty("url") val url: String? = null,
    )

    data class Trailer(
        @param:JsonProperty("videoAddress") val videoAddress: VideoAddress? = null,
    ) {
        data class VideoAddress(
            @param:JsonProperty("url") val url: String? = null,
        )
    }
}
