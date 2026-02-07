package com.Moviebox

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.MainAPI

class MovieBox : MainAPI() {
    override var name = "MovieBox.ph"
    override var mainUrl = "https://moviebox.ph"
    override val hasMainPage = true
    override var lang = "id"
    
    private val apiUrl = "https://h5-api.aoneroom.com/wefeed-h5api-bff"

    private val headers = mapOf(
        "authority" to "h5-api.aoneroom.com",
        "accept" to "application/json",
        "authorization" to "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1aWQiOjE4MTM0MjU0MjgwMjM4ODc4MDAsImF0cCI6MywiZXh0IjoiMTc3MDQxMTA5MCIsImV4cCI6MTc3ODE4NzA5MCwiaWF0IjoxNzcwNDEwNzkwfQ.-kW86pGAJX6jheH_yEM8xfGd4rysJFR_hM3djl32nAo",
        "content-type" to "application/json",
        "user-agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Mobile Safari/537.36",
        "x-request-lang" to "en"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val homeSets = mutableListOf<HomePageList>()

        try {
            val trendingUrl = "$apiUrl/subject/trending?page=0&perPage=18"
            val res = app.get(trendingUrl, headers = headers).parsedSafe<TrendingResponse>()
            res?.data?.subjectList?.mapNotNull { it.toSearchResponse() }?.let {
                if (it.isNotEmpty()) homeSets.add(HomePageList("🔥 Trending Now", it, false))
            }
        } catch (e: Exception) { e.printStackTrace() }

        try {
            val homeUrl = "$apiUrl/home?host=moviebox.ph"
            val res = app.get(homeUrl, headers = headers).parsedSafe<HomeResponse>()
            res?.data?.operatingList?.forEach { section ->
                val items = mutableListOf<Subject>()
                if (section.type == "BANNER") section.banner?.items?.let { items.addAll(it) }
                else section.subjects?.let { items.addAll(it) }

                val media = items.mapNotNull { it.toSearchResponse() }
                if (media.isNotEmpty() && section.type != "CUSTOM" && section.type != "FILTER") {
                    homeSets.add(HomePageList(section.title ?: "Featured", media, section.type == "BANNER"))
                }
            }
        } catch (e: Exception) { e.printStackTrace() }

        return newHomePageResponse(homeSets)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$apiUrl/subject/search?host=moviebox.ph"
        val body = mapOf("keyword" to query, "page" to 0, "perPage" to 20)
        return try {
            val res = app.post(url, headers = headers, json = body).parsedSafe<SearchDataResponse>()
            res?.data?.items?.mapNotNull { it.toSearchResponse() } ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }

    override suspend fun load(url: String): LoadResponse {
        val detailUrl = "$apiUrl/detail?detailPath=$url&host=moviebox.ph"
        val res = app.get(detailUrl, headers = headers).parsedSafe<DetailFullResponse>()
        val subject = res?.data?.subject ?: throw ErrorLoadingException("Data Kosong")

        val title = subject.title ?: ""
        val type = if (subject.subjectType == 1) TvType.Movie else TvType.TvSeries

        return if (type == TvType.Movie) {
            newMovieLoadResponse(title, url, type, subject.subjectId ?: "") {
                this.posterUrl = subject.cover?.url
                this.plot = subject.description
                this.year = subject.releaseDate?.take(4)?.toIntOrNull()
                subject.imdbRatingValue?.toDoubleOrNull()?.let { this.score = Score.from10(it) }
            }
        } else {
            val episodes = listOf(newEpisode(subject.subjectId ?: "") { this.name = "Play Movie" })
            newTvSeriesLoadResponse(title, url, type, episodes) {
                this.posterUrl = subject.cover?.url
                this.plot = subject.description
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val playUrl = "https://lok-lok.cc/wefeed-h5api-bff/subject/play?subjectId=$data&se=0&ep=0"
        return try {
            val res = app.get(playUrl, headers = mapOf("authority" to "lok-lok.cc", "referer" to "https://lok-lok.cc/")).parsedSafe<PlayResponse>()
            res?.data?.streams?.forEach { stream ->
                // PERBAIKAN: referer dan quality diatur di dalam blok initializer { ... }
                // Sesuai dengan definisi newExtractorLink di MainAPI.kt baris 1121
                callback.invoke(
                    newExtractorLink(
                        source = this.name,
                        name = "${this.name} ${stream.resolutions}p",
                        url = stream.url ?: return@forEach,
                        type = ExtractorLinkType.VIDEO
                    ) {
                        this.referer = "https://lok-lok.cc/"
                        this.quality = getQualityInt(stream.resolutions)
                    }
                )
            }
            true
        } catch (e: Exception) { false }
    }

    private fun getQualityInt(res: String?): Int {
        return when (res) {
            "720" -> 720
            "480" -> 480
            "360" -> 360
            else -> 0
        }
    }

    private fun Subject.toSearchResponse(): SearchResponse? {
        return newMovieSearchResponse(this.title ?: return null, this.detailPath ?: return null, TvType.Movie) {
            this.posterUrl = this@toSearchResponse.cover?.url ?: this@toSearchResponse.image?.url
            this@toSearchResponse.imdbRatingValue?.toDoubleOrNull()?.let { this.score = Score.from10(it) }
        }
    }

    data class HomeResponse(@JsonProperty("data") val data: HomeData?)
    data class HomeData(@JsonProperty("operatingList") val operatingList: List<OperatingSection>?)
    data class OperatingSection(@JsonProperty("type") val type: String?, @JsonProperty("title") val title: String?, @JsonProperty("banner") val banner: BannerObj?, @JsonProperty("subjects") val subjects: List<Subject>?)
    data class BannerObj(@JsonProperty("items") val items: List<Subject>?)
    data class TrendingResponse(@JsonProperty("data") val data: TrendingData?)
    data class TrendingData(@JsonProperty("subjectList") val subjectList: List<Subject>?)
    data class SearchDataResponse(@JsonProperty("data") val data: SearchResultData?)
    data class SearchResultData(@JsonProperty("items") val items: List<Subject>?)
    data class DetailFullResponse(@JsonProperty("data") val data: DetailData?)
    data class DetailData(@JsonProperty("subject") val subject: Subject?)
    data class PlayResponse(@JsonProperty("data") val data: PlayData?)
    data class PlayData(@JsonProperty("streams") val streams: List<VideoStream>?)
    data class VideoStream(@JsonProperty("url") val url: String?, @JsonProperty("resolutions") val resolutions: String?)
    data class Subject(
        @JsonProperty("subjectId") val subjectId: String?,
        @JsonProperty("title") val title: String?,
        @JsonProperty("cover") val cover: ImageObj?,
        @JsonProperty("image") val image: ImageObj?,
        @JsonProperty("detailPath") val detailPath: String?,
        @JsonProperty("subjectType") val subjectType: Int?,
        @JsonProperty("description") val description: String?,
        @JsonProperty("releaseDate") val releaseDate: String?,
        @JsonProperty("imdbRatingValue") val imdbRatingValue: String?
    )
    data class ImageObj(@JsonProperty("url") val url: String?)
}
