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
    // URL UTAMA: Tetap moviebox.ph sesuai permintaan
    override var mainUrl = "https://moviebox.ph"

    // API 1: BACKEND MOVIEBOX (Untuk Home & Search agar konten lengkap/18+ muncul)
    private val searchApiUrl = "https://h5-api.aoneroom.com"

    // API 2: PLAYER SERVER (Untuk Detail & Play agar video jalan sesuai CURL)
    private val videoApiUrl = "https://filmboom.top"

    // Header Dasar
    private val commonHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Mobile Safari/537.36",
        "Accept" to "application/json",
        "x-request-lang" to "en",
        "x-client-info" to "{\"timezone\":\"Asia/Jayapura\"}"
    )

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

    // --- 1. BAGIAN HOME & SEARCH (MENGGUNAKAN IDENTITAS MOVIEBOX.PH) ---
    
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
        "872031290915189720" to "Bad Ending Romance" 
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest,
    ): HomePageResponse {
        val id = request.data
        // URL API: aoneroom (Backend asli Moviebox)
        val targetUrl = "$searchApiUrl/wefeed-h5api-bff/ranking-list/content?id=$id&page=$page&perPage=12"

        // HEADER: Mengaku sebagai moviebox.ph agar server merespon benar
        val homeHeaders = commonHeaders + mapOf(
            "Authority" to "h5-api.aoneroom.com",
            "Origin" to "https://moviebox.ph",
            "Referer" to "https://moviebox.ph/"
        )

        val responseData = app.get(targetUrl, headers = homeHeaders).parsedSafe<Media>()?.data
        val listFilm = responseData?.subjectList ?: responseData?.items

        val home = listFilm?.map {
            it.toSearchResponse(this)
        } ?: throw ErrorLoadingException("Gagal memuat kategori.")

        return newHomePageResponse(request.name, home)
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun search(query: String): List<SearchResponse> {
        // Menggunakan API aoneroom agar hasil seperti "Mamasan" muncul (tidak disensor)
        val url = "$searchApiUrl/wefeed-h5api-bff/subject/search"
        
        val searchHeaders = commonHeaders + mapOf(
            "Authority" to "h5-api.aoneroom.com",
            "Origin" to "https://moviebox.ph",
            "Referer" to "https://moviebox.ph/"
        )

        val body = mapOf(
            "keyword" to query,
            "page" to "1",
            "perPage" to "20",
            // Tanpa subjectType agar hasil 18+ tampil
        )

        return app.post(
            url,
            headers = searchHeaders,
            requestBody = body.toJson().toRequestBody(RequestBodyTypes.JSON.toMediaTypeOrNull())
        ).parsedSafe<Media>()?.data?.items?.map { it.toSearchResponse(this) }
            ?: throw ErrorLoadingException("Pencarian tidak ditemukan.")
    }

    // --- 2. BAGIAN DETAIL & VIDEO (AUTO-SWITCH KE FILMBOOM AGAR VIDEO JALAN) ---

    override suspend fun load(url: String): LoadResponse {
        val id = url.substringAfterLast("/")

        // Switch ke FILMBOOM untuk detail karena playernya ada di sana
        val detailUrl = "$videoApiUrl/wefeed-h5-bff/web/subject/detail?subjectId=$id"
        
        // Header khusus Filmboom
        val detailHeaders = commonHeaders + mapOf(
            "Authority" to "filmboom.top",
            "Referer" to "$videoApiUrl/"
        )

        val document = app.get(detailUrl, headers = detailHeaders)
            .parsedSafe<MediaDetail>()?.data

        val subject = document?.subject
        val title = subject?.title ?: ""
        val poster = subject?.cover?.url
        
        // Ambil detailPath (PENTING untuk Playback)
        val detailPath = subject?.detailPath 

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

        val recommendations =
            app.get(
                "$videoApiUrl/wefeed-h5-bff/web/subject/detail-rec?subjectId=$id&page=1&perPage=12", 
                headers = detailHeaders
            ).parsedSafe<Media>()?.data?.items?.map {
                it.toSearchResponse(this)
            }

        // Simpan data penting untuk fungsi loadLinks
        val loadDataJson = LoadData(
            id = id,
            detailPath = detailPath
        ).toJson()

        return if (tvType == TvType.TvSeries) {
            val episode = document?.resource?.seasons?.map { seasons ->
                (if (seasons.allEp.isNullOrEmpty()) (1..(seasons.maxEp ?: 1)) else seasons.allEp.split(",")
                    .map { it.toInt() })
                    .map { episode ->
                        newEpisode(
                            LoadData(
                                id,
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
        
        // --- LOGIKA REFERER KHUSUS FILMBOOM ---
        val typePath = if(media.episode != null) "/tv/detail" else "/movie/detail"
        
        // Referer harus persis seperti di Browser agar tidak diblokir
        val specificReferer = "$videoApiUrl/spa/videoPlayPage/movies/${media.detailPath}?id=${media.id}&type=$typePath&lang=en"

        val playHeaders = commonHeaders + mapOf(
            "Authority" to "filmboom.top",
            "Referer" to specificReferer,
            "Sec-Fetch-Mode" to "cors",
            "Sec-Fetch-Site" to "same-origin"
        )

        // Request Play dengan parameter lengkap sesuai CURL
        val targetUrl = "$videoApiUrl/wefeed-h5-bff/web/subject/play?subjectId=${media.id}&se=${media.season ?: 0}&ep=${media.episode ?: 0}&detail_path=${media.detailPath}"

        val streams = app.get(targetUrl, headers = playHeaders).parsedSafe<Media>()?.data?.streams

        streams?.reversed()?.distinctBy { it.url }?.map { source ->
            callback.invoke(
                newExtractorLink(
                    this.name,
                    this.name,
                    source.url ?: return@map,
                    INFER_TYPE
                ) {
                    this.referer = specificReferer
                    this.quality = getQualityFromName(source.resolutions)
                }
            )
        }

        // Subtitle
        val id = streams?.firstOrNull()?.id
        val format = streams?.firstOrNull()?.format
        if (id != null) {
            app.get(
                "$videoApiUrl/wefeed-h5-bff/web/subject/caption?format=$format&id=$id&subjectId=${media.id}",
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
        // Tautan detail akan mengarah ke moviebox.ph di tampilan, tapi provider akan menanganinya via API
        val url = "${provider.mainUrl}/detail/${subjectId}"
        
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
