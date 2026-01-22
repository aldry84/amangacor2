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
    override var mainUrl = "https://moviebox.ph" 
    
    // API UTAMA (Aoneroom) - Jantung aplikasi
    private val apiUrl = "https://api.aoneroom.com" 

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

    override val mainPage: List<MainPageData> = mainPageOf(
        "5283462032510044280" to "Indonesian Drama",
        "6528093688173053896" to "Indonesian Movies",
        "5848753831881965888" to "Indo Horror",
        "997144265920760504" to "Hollywood Movies",
        "4380734070238626200" to "K-Drama",
        "8624142774394406504" to "C-Drama",
        "872031290915189720" to "Bad Ending Romance" 
    )

    // --- SEARCH LOGIC ---
    override suspend fun search(query: String): List<SearchResponse> {
        // HAPUS subjectType: Membiarkan API mencari ke semua kategori (termasuk konten dewasa/lok-lok)
        val postData = mapOf(
            "keyword" to query,
            "page" to 1,
            "perPage" to 20
        )

        return app.post(
            "$apiUrl/wefeed-h5-bff/web/subject/search", 
            requestBody = postData.toJson().toRequestBody(RequestBodyTypes.JSON.toMediaTypeOrNull())
        ).parsedSafe<Media>()?.data?.items?.map { it.toSearchResponse(this) }
            ?: throw ErrorLoadingException("Pencarian '$query' tidak ditemukan.")
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val id = request.data 
        val targetUrl = "$apiUrl/wefeed-h5api-bff/ranking-list/content?id=$id&page=$page&perPage=12"
        val responseData = app.get(targetUrl).parsedSafe<Media>()?.data
        val listFilm = responseData?.subjectList ?: responseData?.items

        val home = listFilm?.map { it.toSearchResponse(this) } 
            ?: throw ErrorLoadingException("Gagal memuat kategori.")

        return newHomePageResponse(request.name, home)
    }

    override suspend fun load(url: String): LoadResponse {
        val id = url.substringAfterLast("/")
        
        val document = app.get("$apiUrl/wefeed-h5-bff/web/subject/detail?subjectId=$id")
            .parsedSafe<MediaDetail>()?.data
        
        val subject = document?.subject
        val title = subject?.title ?: ""
        val poster = subject?.cover?.url
        val tags = subject?.genre?.split(",")?.map { it.trim() }
        val year = subject?.releaseDate?.substringBefore("-")?.toIntOrNull()
        val description = subject?.description
        val trailer = subject?.trailer?.videoAddress?.url
        val score = Score.from10(subject?.imdbRatingValue) 

        val recommendations = app.get("$apiUrl/wefeed-h5-bff/web/subject/detail-rec?subjectId=$id&page=1&perPage=12")
                .parsedSafe<Media>()?.data?.items?.map { it.toSearchResponse(this) }

        val isSeries = subject?.subjectType == 2 
        
        // PENTING: Kita kirim detailPath ke LoadData agar bisa dipakai di loadLinks nanti
        val commonLoadData = LoadData(id, detailPath = subject?.detailPath)

        if (isSeries) {
            val episodes = document?.resource?.seasons?.flatMap { season ->
                val epList = if (season.allEp.isNullOrEmpty()) {
                    (1..(season.maxEp ?: 1)).map { it }
                } else {
                    season.allEp.split(",").mapNotNull { it.trim().toIntOrNull() }
                }
                
                epList.map { epNum ->
                    newEpisode(
                        // Masukkan detailPath ke setiap episode
                        commonLoadData.copy(season = season.se, episode = epNum).toJson()
                    ) {
                        this.season = season.se
                        this.episode = epNum
                    }
                }
            } ?: emptyList()

            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.score = score
                this.recommendations = recommendations
                addTrailer(trailer)
            }
        } else {
            return newMovieLoadResponse(title, url, TvType.Movie, commonLoadData.toJson()) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.score = score
                this.recommendations = recommendations
                addTrailer(trailer)
            }
        }
    }

    // --- LINK HANDLING (DIPERBAIKI BERDASARKAN LOG KIWI BROWSER) ---
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val media = parseJson<LoadData>(data)
        
        // TRIK JITU: Menggunakan Referer Lok-Lok agar server mau memberi link
        // Ini meniru kelakuan browser di screenshot 1000115539.jpg
        val fakeReferer = "https://lok-lok.cc/" 

        // Membentuk URL Play persis seperti di Log Network:
        // play?subjectId=...&se=...&ep=...&detailPath=...
        val playUrl = "$apiUrl/wefeed-h5-bff/web/subject/play?subjectId=${media.id}&se=${media.season ?: 0}&ep=${media.episode ?: 0}&detailPath=${media.detailPath}"
        
        val response = app.get(playUrl, referer = fakeReferer).parsedSafe<Media>()
        val streams = response?.data?.streams

        if (!streams.isNullOrEmpty()) {
            streams.reversed().distinctBy { it.url }.forEach { source ->
                val url = source.url ?: return@forEach
                
                callback.invoke(
                    newExtractorLink(
                        this.name, 
                        "Aoneroom/LokLok ${source.resolutions ?: "HD"}", 
                        url, 
                        Referer = fakeReferer, 
                        quality = getQualityFromName(source.resolutions)
                    )
                )
            }
        }

        // Subtitle juga butuh detailPath kadang-kadang
        val id = streams?.firstOrNull()?.id
        val format = streams?.firstOrNull()?.format
        if (id != null && format != null) {
            app.get("$apiUrl/wefeed-h5-bff/web/subject/caption?format=$format&id=$id&subjectId=${media.id}&detailPath=${media.detailPath}", referer = fakeReferer)
                .parsedSafe<Media>()?.data?.captions?.forEach { subtitle ->
                    subtitleCallback.invoke(newSubtitleFile(subtitle.lanName ?: "Unknown", subtitle.url ?: return@forEach))
                }
        }

        return true
    }
}

// --- DATA CLASSES ---
data class LoadData(val id: String? = null, val season: Int? = null, val episode: Int? = null, val detailPath: String? = null)

data class Media(@field:JsonProperty("data") val data: Data? = null) {
    data class Data(
        @field:JsonProperty("subjectList") val subjectList: ArrayList<Items>? = arrayListOf(),
        @field:JsonProperty("items") val items: ArrayList<Items>? = arrayListOf(),
        @field:JsonProperty("streams") val streams: ArrayList<Streams>? = arrayListOf(),
        @field:JsonProperty("captions") val captions: ArrayList<Captions>? = arrayListOf(),
    ) {
        data class Streams(
            @field:JsonProperty("id") val id: String? = null,
            @field:JsonProperty("format") val format: String? = null,
            @field:JsonProperty("url") val url: String? = null,
            @field:JsonProperty("resolutions") val resolutions: String? = null,
        )
        data class Captions(
            @field:JsonProperty("lan") val lan: String? = null,
            @field:JsonProperty("lanName") val lanName: String? = null,
            @field:JsonProperty("url") val url: String? = null,
        )
    }
}

data class MediaDetail(@field:JsonProperty("data") val data: Data? = null) {
    data class Data(
        @field:JsonProperty("subject") val subject: Items? = null,
        @field:JsonProperty("stars") val stars: ArrayList<Stars>? = arrayListOf(),
        @field:JsonProperty("resource") val resource: Resource? = null,
    ) {
        data class Stars(@field:JsonProperty("name") val name: String? = null, @field:JsonProperty("character") val character: String? = null, @field:JsonProperty("avatarUrl") val avatarUrl: String? = null)
        data class Resource(@field:JsonProperty("seasons") val seasons: ArrayList<Seasons>? = arrayListOf()) {
            data class Seasons(@field:JsonProperty("se") val se: Int? = null, @field:JsonProperty("maxEp") val maxEp: Int? = null, @field:JsonProperty("allEp") val allEp: String? = null)
        }
    }
}

data class Items(
    @field:JsonProperty("subjectId") val subjectId: String? = null,
    @field:JsonProperty("subjectType") val subjectType: Int? = null,
    @field:JsonProperty("title") val title: String? = null,
    @field:JsonProperty("description") val description: String? = null,
    @field:JsonProperty("releaseDate") val releaseDate: String? = null,
    @field:JsonProperty("imdbRatingValue") val imdbRatingValue: String? = null,
    @field:JsonProperty("cover") val cover: Cover? = null,
    @field:JsonProperty("trailer") val trailer: Trailer? = null,
    @field:JsonProperty("detailPath") val detailPath: String? = null,
) {
    fun toSearchResponse(provider: Adimoviebox): SearchResponse {
        val url = "${provider.mainUrl}/detail/${subjectId}"
        return provider.newMovieSearchResponse(title ?: "No Title", url, if (subjectType == 1) TvType.Movie else TvType.TvSeries, false) {
            this.posterUrl = cover?.url
            this.score = Score.from10(imdbRatingValue)
            this.year = releaseDate?.substringBefore("-")?.toIntOrNull()
        }
    }
    data class Cover(@field:JsonProperty("url") val url: String? = null)
    data class Trailer(@field:JsonProperty("videoAddress") val videoAddress: VideoAddress? = null) {
        data class VideoAddress(@field:JsonProperty("url") val url: String? = null)
    }
}
