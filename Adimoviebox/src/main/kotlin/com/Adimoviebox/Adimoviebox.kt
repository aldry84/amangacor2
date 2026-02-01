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
    // UPDATED: Domain baru sesuai log curl Anda
    override var mainUrl = "https://moviebox.ph"
    
    // API Backend tetap sama (aoneroom)
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

    // Header disesuaikan dengan moviebox.ph
    private val commonHeaders = mapOf(
        "origin" to mainUrl,
        "referer" to "$mainUrl/",
        "x-client-info" to "{\"timezone\":\"Asia/Jakarta\"}",
        "accept-language" to "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7",
        "x-source" to "app-search" 
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
        "872031290915189720" to "Bad Ending Romance" 
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest,
    ): HomePageResponse {
        val id = request.data 
        val targetUrl = "$homeApiUrl/wefeed-h5api-bff/ranking-list/content?id=$id&page=$page&perPage=12"
        val responseData = app.get(targetUrl, headers = commonHeaders).parsedSafe<Media>()?.data
        val listFilm = responseData?.subjectList ?: responseData?.items
        val home = listFilm?.map { it.toSearchResponse(this) } 
            ?: throw ErrorLoadingException("Gagal memuat kategori.")
        return newHomePageResponse(request.name, home)
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    // --- FIX PENCARIAN UTAMA ---
    override suspend fun search(query: String): List<SearchResponse> {
        // STRATEGI BARU: Request Terpisah untuk Movie (1) dan TV (2)
        // Ini akan membuang hasil "Music" (3) yang menimbun film "Unli Pop"
        
        val searchUrl = "$homeApiUrl/wefeed-h5api-bff/subject/search"
        
        // Request 1: Cari khusus Movie
        val reqMovie = mapOf(
            "keyword" to query,
            "page" to "1",
            "perPage" to "30",
            "subjectType" to "1" // 1 = Movie Only
        )
        
        // Request 2: Cari khusus TV Series
        val reqTv = mapOf(
            "keyword" to query,
            "page" to "1",
            "perPage" to "30",
            "subjectType" to "2" // 2 = TV Only
        )

        // Eksekusi request (Sequential agar aman)
        val resMovie = try {
            app.post(searchUrl, headers = commonHeaders, 
                requestBody = reqMovie.toJson().toRequestBody(RequestBodyTypes.JSON.toMediaTypeOrNull())
            ).parsedSafe<Media>()?.data?.items?.map { it.toSearchResponse(this) } ?: emptyList()
        } catch (e: Exception) { emptyList() }

        val resTv = try {
            app.post(searchUrl, headers = commonHeaders,
                requestBody = reqTv.toJson().toRequestBody(RequestBodyTypes.JSON.toMediaTypeOrNull())
            ).parsedSafe<Media>()?.data?.items?.map { it.toSearchResponse(this) } ?: emptyList()
        } catch (e: Exception) { emptyList() }

        // Gabungkan hasil Movie dan TV, hapus duplikat jika ada
        val combined = (resMovie + resTv).distinctBy { it.url }
        
        if (combined.isEmpty()) throw ErrorLoadingException("Pencarian tidak ditemukan.")
        return combined
    }

    override suspend fun load(url: String): LoadResponse {
        val id = url.substringAfterLast("?id=") 
            .ifEmpty { url.substringAfterLast("/") } 
        
        // Gunakan homeApiUrl untuk detail agar data konsisten
        val detailUrl = "$homeApiUrl/wefeed-h5api-bff/detail?detailPath=$id"
        
        val response = app.get(detailUrl, headers = commonHeaders).parsedSafe<MediaDetail>()
        val document = response?.data 
            ?: app.get("$homeApiUrl/wefeed-h5api-bff/subject/detail?subjectId=$id", headers = commonHeaders)
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
        val score = Score.from10(subject?.imdbRatingValue) 
        
        val realId = subject?.subjectId ?: id
        val detailPath = subject?.detailPath ?: id 

        val actors = document.stars?.mapNotNull { cast ->
            ActorData(Actor(cast.name ?: return@mapNotNull null, cast.avatarUrl), roleString = cast.character)
        }?.distinctBy { it.actor }

        val recommendations = app.get("$homeApiUrl/wefeed-h5api-bff/subject/detail-rec?subjectId=$realId&page=1&perPage=12", headers = commonHeaders)
                .parsedSafe<Media>()?.data?.items?.map { it.toSearchResponse(this) }

        return if (tvType == TvType.TvSeries) {
            val episode = document.resource?.seasons?.map { seasons ->
                (if (seasons.allEp.isNullOrEmpty()) (1..(seasons.maxEp ?: 1)) else seasons.allEp.split(",")
                    .map { it.toInt() })
                    .map { episode ->
                        newEpisode(LoadData(realId, seasons.se, episode, detailPath).toJson()) {
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
            newMovieLoadResponse(title, url, TvType.Movie, LoadData(realId, detailPath = detailPath).toJson()) {
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
        
        // Referer menggunakan moviebox.ph
        val referer = "$mainUrl/spa/videoPlayPage/movies/${media.detailPath}?id=${media.id}&type=/movie/detail&lang=en"
        val specificHeaders = commonHeaders + ("referer" to referer)

        // API Playback tetap bisa menggunakan backend atau mainUrl, kita pakai moviebox.ph agar aman dari CORS
        val streams = app.get(
            "$mainUrl/wefeed-h5api-bff/subject/play?subjectId=${media.id}&se=${media.season ?: 0}&ep=${media.episode ?: 0}&detailPath=${media.detailPath}",
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
                "$mainUrl/wefeed-h5api-bff/subject/caption?format=$format&id=$id&subjectId=${media.id}&detailPath=${media.detailPath}",
                headers = specificHeaders
            ).parsedSafe<Media>()?.data?.captions?.map { subtitle ->
                subtitleCallback.invoke(newSubtitleFile(subtitle.lanName ?: "", subtitle.url ?: return@map))
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
            )
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
        val url = "${provider.mainUrl}/detail/${detailPath ?: subjectId}"
        val posterImage = cover?.url
        return provider.newMovieSearchResponse(
            title ?: "No Title",
            url,
            if (subjectType == 1) TvType.Movie else TvType.TvSeries,
            false
        ) {
            this.posterUrl = posterImage
            this.score = Score.from10(imdbRatingValue)
            this.year = releaseDate?.substringBefore("-")?.toIntOrNull()
        }
    }
    data class Cover(@param:JsonProperty("url") val url: String? = null)
    data class Trailer(@param:JsonProperty("videoAddress") val videoAddress: VideoAddress? = null) {
        data class VideoAddress(@param:JsonProperty("url") val url: String? = null)
    }
}
