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
        val data = res?.data ?: throw ErrorLoadingException("Data Kosong")
        val subject = data.subject ?: throw ErrorLoadingException("Subject Null")

        val title = subject.title ?: ""
        val poster = subject.cover?.url
        val plot = subject.description
        val rating = subject.imdbRatingValue?.toDoubleOrNull()
        val actors = data.stars?.map { ActorData(Actor(it.name ?: "", it.avatarUrl), roleString = it.character) }

        return if (subject.subjectType == 1) { // Movie
            newMovieLoadResponse(title, url, TvType.Movie, "${subject.subjectId}|0|0|$url") {
                this.posterUrl = poster
                this.plot = plot
                this.actors = actors
                this.year = subject.releaseDate?.take(4)?.toIntOrNull()
                if (rating != null) this.score = Score.from10(rating)
            }
        } else { // Series
            val episodes = mutableListOf<Episode>()
            data.resource?.seasons?.forEach { season ->
                val sNum = season.se ?: 1
                val maxE = season.maxEp ?: 1
                for (i in 1..maxE) {
                    episodes.add(newEpisode("${subject.subjectId}|$sNum|$i|$url") {
                        this.season = sNum
                        this.episode = i
                        this.name = "Episode $i"
                    })
                }
            }
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = plot
                this.actors = actors
                if (rating != null) this.score = Score.from10(rating)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val parts = data.split("|")
        val id = parts.getOrNull(0) ?: return false
        val s = parts.getOrNull(1) ?: "0"
        val e = parts.getOrNull(2) ?: "0"
        val path = parts.getOrNull(3) ?: ""

        val playUrl = "https://lok-lok.cc/wefeed-h5api-bff/subject/play?subjectId=$id&se=$s&ep=$e&detailPath=$path"
        
        return try {
            val res = app.get(playUrl, headers = mapOf(
                "authority" to "lok-lok.cc", 
                "referer" to "https://lok-lok.cc/"
            )).parsedSafe<PlayResponse>()

            res?.data?.streams?.forEach { stream ->
                callback.invoke(
                    newExtractorLink(
                        source = this.name,
                        name = "${this.name} ${stream.resolutions}p",
                        url = stream.url ?: return@forEach,
                        type = ExtractorLinkType.VIDEO
                    ) {
                        this.referer = "https://lok-lok.cc/"
                        this.quality = stream.resolutions?.toIntOrNull() ?: 0
                    }
                )
            }
            true
        } catch (err: Exception) { false }
    }

    private fun Subject.toSearchResponse(): SearchResponse? {
        return newMovieSearchResponse(this.title ?: return null, this.detailPath ?: return null, TvType.Movie) {
            this.posterUrl = this@toSearchResponse.cover?.url ?: this@toSearchResponse.image?.url
            this@toSearchResponse.imdbRatingValue?.toDoubleOrNull()?.let { this.score = Score.from10(it) }
        }
    }

    data class TrendingResponse(@JsonProperty("data") val data: TrendingData?)
    data class TrendingData(@JsonProperty("subjectList") val subjectList: List<Subject>?)
    data class HomeResponse(@JsonProperty("data") val data: HomeData?)
    data class HomeData(@JsonProperty("operatingList") val operatingList: List<OperatingSection>?)
    data class OperatingSection(@JsonProperty("type") val type: String?, @JsonProperty("title") val title: String?, @JsonProperty("banner") val banner: BannerObj?, @JsonProperty("subjects") val subjects: List<Subject>?)
    data class BannerObj(@JsonProperty("items") val items: List<Subject>?)
    data class SearchDataResponse(@JsonProperty("data") val data: SearchResultData?)
    data class SearchResultData(@JsonProperty("items") val items: List<Subject>?)
    data class DetailFullResponse(@JsonProperty("data") val data: DetailData?)
    data class DetailData(@JsonProperty("subject") val subject: Subject?, @JsonProperty("resource") val resource: Resource?, @JsonProperty("stars") val stars: List<Star>?)
    data class Resource(@JsonProperty("seasons") val seasons: List<Season>?)
    data class Season(@JsonProperty("se") val se: Int?, @JsonProperty("maxEp") val maxEp: Int?)
    data class Star(@JsonProperty("name") val name: String?, @JsonProperty("avatarUrl") val avatarUrl: String?, @JsonProperty("character") val character: String?)
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
