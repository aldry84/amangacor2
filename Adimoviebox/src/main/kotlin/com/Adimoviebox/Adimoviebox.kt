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
    
    // API untuk streaming
    private val apiUrl = "https://lok-lok.cc" 
    
    // API untuk Metadata (Home, Detail, Search)
    private val homeApiUrl = "https://h5-api.aoneroom.com"

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

    private val commonHeaders = mapOf(
        "origin" to mainUrl,
        "referer" to "$mainUrl/",
        "x-client-info" to "{\"timezone\":\"Asia/Jakarta\"}",
        "accept-language" to "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7"
    )

    // Kategori Lama
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
        val channelId = params.first()
        val sort = params.last()

        val body = mapOf(
            "channelId" to channelId,
            "page" to page,
            "perPage" to "12",
            "sort" to sort
        ).toJson().toRequestBody(RequestBodyTypes.JSON.toMediaTypeOrNull())

        // PERBAIKAN: Menggunakan homeApiUrl (aoneroom) bukan apiUrl (lok-lok)
        // Jika endpoint 'filter' gagal, kita akan mencoba endpoint 'ranking-list' sebagai fallback
        var items = try {
            app.post(
                "$homeApiUrl/wefeed-h5api-bff/filter", 
                headers = commonHeaders, 
                requestBody = body
            ).parsedSafe<Media>()?.data?.items
        } catch (e: Exception) {
            null
        }

        // FALLBACK: Jika filter lama tidak jalan di API baru, gunakan ranking default
        if (items.isNullOrEmpty()) {
             // Mapping kasar ID kategori lama ke ID Ranking baru supaya tidak blank
             val fallbackId = when(channelId) {
                 "1" -> "997144265920760504" // Hollywood Movies
                 "2" -> "4380734070238626200" // K-Drama (sebagai contoh TV)
                 "1006" -> "3058742380078711608" // Disney/Anime
                 else -> "6528093688173053896" // Indo Movies
             }
             
             items = app.get(
                 "$homeApiUrl/wefeed-h5api-bff/ranking-list/content?id=$fallbackId&page=$page&perPage=12",
                 headers = commonHeaders
             ).parsedSafe<Media>()?.data?.subjectList
        }

        val home = items?.map {
            it.toSearchResponse(this)
        } ?: throw ErrorLoadingException("No Data Found")

        return newHomePageResponse(request.name, home)
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun search(query: String): List<SearchResponse> {
        // PERBAIKAN: Menggunakan homeApiUrl untuk search juga agar konsisten
        return app.post(
            "$homeApiUrl/wefeed-h5api-bff/subject/search", 
            headers = commonHeaders,
            requestBody = mapOf(
                "keyword" to query,
                "page" to "1",
                "perPage" to "12", // Ubah 0 jadi 12 agar tidak berat
                "subjectType" to "0",
            ).toJson().toRequestBody(RequestBodyTypes.JSON.toMediaTypeOrNull())
        ).parsedSafe<Media>()?.data?.items?.map { it.toSearchResponse(this) }
            ?: throw ErrorLoadingException("Pencarian tidak ditemukan.")
    }

    override suspend fun load(url: String): LoadResponse {
        val id = url.substringAfterLast("?id=")
            .ifEmpty { url.substringAfterLast("/") } 
        
        val detailUrl = "$homeApiUrl/wefeed-h5api-bff/detail?detailPath=$id" 
        
        val response = app.get(detailUrl, headers = commonHeaders).parsedSafe<MediaDetail>()
        
        val document = response?.data ?: app.get("$homeApiUrl/wefeed-h5api-bff/subject/detail?subjectId=$id", headers = commonHeaders)
            .parsedSafe<MediaDetail>()?.data
            ?: throw ErrorLoadingException("Gagal memuat detail konten.")
        
        val subject = document.subject
        val title = subject?.title ?: ""
        val poster = subject?.cover?.url
        val tags = subject?.genre?.split(",")?.map { it.trim() }

        val year = subject?.releaseDate?.substringBefore("-")?.toIntOrNull()
        val tvType = if (subject?.subjectType == 2) TvType.TvSeries else TvType.Movie
        val description = subject?.description
        val trailer = subject?.trailer?.videoAddress?.url
        val score = Score.from10(subject?.imdbRatingValue?.toString()) 
        val realId = subject?.subjectId ?: id
        val detailPath = subject?.detailPath ?: id 

        val actors = document.stars?.mapNotNull { cast ->
            ActorData(
                Actor(
                    cast.name ?: return@mapNotNull null,
                    cast.avatarUrl
                ),
                roleString = cast.character
            )
        }?.distinctBy { it.actor }

        val recommendations =
            app.get("$homeApiUrl/wefeed-h5api-bff/subject/detail-rec?subjectId=$realId&page=1&perPage=12", headers = commonHeaders)
                .parsedSafe<Media>()?.data?.items?.map {
                    it.toSearchResponse(this)
                }

        return if (tvType == TvType.TvSeries) {
            val episode = document.resource?.seasons?.map { seasons ->
                (if (seasons.allEp.isNullOrEmpty()) (1..(seasons.maxEp ?: 1)) else seasons.allEp.split(",")
                    .map { it.toInt() })
                    .map { episode ->
                        newEpisode(
                            LoadData(
                                realId,
                                seasons.se,
                                episode,
                                detailPath 
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
            newMovieLoadResponse(
                title,
                url,
                TvType.Movie,
                LoadData(realId, detailPath = detailPath).toJson()
            ) {
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
        // Referer tetap ke lok-lok untuk playback
        val referer = "$mainUrl/spa/videoPlayPage/movies/${media.detailPath}?id=${media.id}&type=/movie/detail&lang=en"
        val specificHeaders = commonHeaders + ("referer" to referer)

        // Request Playback tetap ke apiUrl (lok-lok)
        val streams = app.get(
            "$apiUrl/wefeed-h5api-bff/subject/play?subjectId=${media.id}&se=${media.season ?: 0}&ep=${media.episode ?: 0}&detailPath=${media.detailPath}",
            headers = specificHeaders
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

        val id = streams?.firstOrNull()?.id
        val format = streams?.firstOrNull()?.format

        if (id != null && format != null) {
            app.get(
                "$apiUrl/wefeed-h5api-bff/subject/caption?format=$format&id=$id&subjectId=${media.id}",
                headers = specificHeaders
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
        val url = "${provider.mainUrl}/detail/${detailPath ?: subjectId}"
        val posterImage = cover?.url

        return provider.newMovieSearchResponse(
            title ?: "No Title",
            url,
            if (subjectType == 1) TvType.Movie else TvType.TvSeries,
            false
        ) {
            this.posterUrl = posterImage
            this.score = Score.from10(imdbRatingValue?.toString())
            this.year = releaseDate?.substringBefore("-")?.toIntOrNull()
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
