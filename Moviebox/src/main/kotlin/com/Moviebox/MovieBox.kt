package com.Moviebox

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.MainAPI

class MovieBox : MainAPI() {
    override var mainUrl = "https://moviebox.ph"
    override var name = "MovieBox.ph"
    override val hasMainPage = true
    override var lang = "id"
    
    // Server API Utama
    private val apiUrl = "https://h5-api.aoneroom.com/wefeed-h5api-bff"

    private val headers = mapOf(
        "authority" to "h5-api.aoneroom.com",
        "accept" to "application/json",
        "accept-language" to "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7",
        "authorization" to "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1aWQiOjE4MTM0MjU0MjgwMjM4ODc4MDAsImF0cCI6MywiZXh0IjoiMTc3MDQxMTA5MCIsImV4cCI6MTc3ODE4NzA5MCwiaWF0IjoxNzcwNDEwNzkwfQ.-kW86pGAJX6jheH_yEM8xfGd4rysJFR_hM3djl32nAo",
        "content-type" to "application/json",
        "origin" to "https://moviebox.ph",
        "referer" to "https://moviebox.ph/",
        "user-agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Mobile Safari/537.36",
        "x-request-lang" to "en"
    )

    // =================================================================================
    // 1. HOME PAGE
    // =================================================================================
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val homeSets = mutableListOf<HomePageList>()

        [span_2](start_span)// Ambil Data Trending[span_2](end_span)
        try {
            val trendingUrl = "$apiUrl/subject/trending?page=0&perPage=18"
            val trendingResponse = app.get(trendingUrl, headers = headers).parsedSafe<TrendingResponse>()
            
            trendingResponse?.data?.subjectList?.let { list ->
                val trendingMedia = list.mapNotNull { it.toSearchResponse() }
                if (trendingMedia.isNotEmpty()) {
                    homeSets.add(HomePageList("🔥 Trending Now", trendingMedia, isHorizontalImages = false))
                }
            }
        } catch (e: Exception) { e.printStackTrace() }

        [span_3](start_span)// Ambil Data Home Standard (Banner & Kategori)[span_3](end_span)
        try {
            val homeUrl = "$apiUrl/home?host=moviebox.ph"
            val homeResponse = app.get(homeUrl, headers = headers).parsedSafe<HomeResponse>()

            homeResponse?.data?.operatingList?.forEach { section ->
                val itemsToParse = ArrayList<Subject>()
                if (section.type == "BANNER" && section.banner?.items != null) {
                    itemsToParse.addAll(section.banner.items)
                } else if (section.subjects != null && section.subjects.isNotEmpty()) {
                    itemsToParse.addAll(section.subjects)
                }

                if (itemsToParse.isNotEmpty() && section.type != "CUSTOM" && section.type != "FILTER") {
                    val mediaList = itemsToParse.mapNotNull { it.toSearchResponse() }
                    if (mediaList.isNotEmpty()) {
                        homeSets.add(HomePageList(section.title ?: "Featured", mediaList, section.type == "BANNER"))
                    }
                }
            }
        } catch (e: Exception) { e.printStackTrace() }

        return newHomePageResponse(homeSets)
    }

    // =================================================================================
    // 2. SEARCH (MENGGUNAKAN POST)
    // =================================================================================
    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$apiUrl/subject/search?host=moviebox.ph"
        val jsonBody = mapOf("keyword" to query, "page" to 0, "perPage" to 20)

        return try {
            val response = app.post(url, headers = headers, json = jsonBody).parsedSafe<SearchDataResponse>()
            response?.data?.items?.mapNotNull { it.toSearchResponse() } ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }

    // =================================================================================
    // 3. LOAD DETAIL
    // =================================================================================
    override suspend fun load(url: String): LoadResponse {
        val detailUrl = "$apiUrl/detail?detailPath=$url&host=moviebox.ph"
        val response = app.get(detailUrl, headers = headers).parsedSafe<DetailFullResponse>()
        val data = response?.data ?: throw ErrorLoadingException("Data Error")
        val subject = data.subject ?: throw ErrorLoadingException("Subject Null")

        val title = subject.title ?: ""
        val poster = subject.cover?.url
        val plot = subject.description
        val rating = subject.imdbRatingValue?.toDoubleOrNull()

        return if (subject.subjectType == 1) { // Movie
            newMovieLoadResponse(title, url, TvType.Movie, subject.subjectId ?: "") {
                this.posterUrl = poster
                this.plot = plot
                this.year = subject.releaseDate?.take(4)?.toIntOrNull()
                if (rating != null) this.score = Score.from10(rating)
            }
        } else { // Series
            val episodes = listOf(newEpisode(subject.subjectId ?: "") { this.name = "Play Movie" })
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = plot
            }
        }
    }

    // =================================================================================
    // 4. LOAD LINKS
    // =================================================================================
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // Link video utama berasal dari lok-lok.cc
        val playUrl = "https://lok-lok.cc/wefeed-h5api-bff/subject/play?subjectId=$data&se=0&ep=0"
        val playHeaders = mapOf(
            "authority" to "lok-lok.cc",
            "referer" to "https://lok-lok.cc/",
            "user-agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36"
        )

        return try {
            val response = app.get(playUrl, headers = playHeaders).parsedSafe<PlayResponse>()
            response?.data?.streams?.forEach { stream ->
                callback.invoke(
                    ExtractorLink(
                        this.name, "${this.name} ${stream.resolutions}p",
                        stream.url ?: return@forEach, "https://lok-lok.cc/",
                        getQuality(stream.resolutions), false
                    )
                )
            }
            true
        } catch (e: Exception) { false }
    }

    private fun getQuality(res: String?): Int {
        return when (res) {
            "720" -> Qualities.P720.value
            "480" -> Qualities.P480.value
            "360" -> Qualities.P360.value
            else -> Qualities.Unknown.value
        }
    }

    private fun Subject.toSearchResponse(): SearchResponse? {
        return newMovieSearchResponse(this.title ?: return null, this.detailPath ?: return null, TvType.Movie) {
            this.posterUrl = this@toSearchResponse.cover?.url ?: this@toSearchResponse.image?.url
            this@toSearchResponse.imdbRatingValue?.toDoubleOrNull()?.let { this.score = Score.from10(it) }
        }
    }

    // ================= DATA CLASSES =================
    data class HomeResponse(@JsonProperty("data") val data: HomeData?)
    data class HomeData(@JsonProperty("operatingList") val operatingList: List<OperatingSection>?)
    data class OperatingSection(
        @JsonProperty("type") val type: String?,
        @JsonProperty("title") val title: String?,
        @JsonProperty("banner") val banner: BannerObj?,
        @JsonProperty("subjects") val subjects: List<Subject>?
    )
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
