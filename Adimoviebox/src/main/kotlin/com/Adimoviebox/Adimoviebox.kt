package com.Moviebox

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.utils.AppUtils.toJson //
import com.lagradost.cloudstream3.utils.AppUtils.parseJson //

class MovieBox : MainAPI() {
    override var name = "MovieBox.ph"
    override var mainUrl = "https://moviebox.ph"
    override val hasMainPage = true
    override var lang = "id"
    
    private val apiUrl = "https://h5-api.aoneroom.com/wefeed-h5api-bff"

    // Header Desktop Sesuai Analisis Kamu
    private val desktopHeaders = mapOf(
        "authority" to "h5-api.aoneroom.com",
        "accept" to "application/json",
        "authorization" to "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1aWQiOjE4MTM0MjU0MjgwMjM4ODc4MDAsImF0cCI6MywiZXh0IjoiMTc3MDQxMTA5MCIsImV4cCI6MTc3ODE4NzA5MCwiaWF0IjoxNzcwNDEwNzkwfQ.-kW86pGAJX6jheH_yEM8xfGd4rysJFR_hM3djl32nAo",
        "content-type" to "application/json",
        "user-agent" to "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36",
        "x-client-info" to "{\"timezone\":\"Asia/Jayapura\"}"
    )

    // Data class pembawa kunci playback
    data class LoadData(
        val id: String,
        val season: Int? = null,
        val episode: Int? = null,
        val detailPath: String
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val homeSets = mutableListOf<HomePageList>()
        val categories = listOf(
            Pair("6528093688173053896", "Film Indonesia"),
            Pair("5283462032510044280", "Drama Indonesia"),
            Pair("5848753831881965888", "Indonesia Horror"),
            Pair("4380734070238626200", "Drama Korea")
        )
        categories.forEach { (id, title) ->
            try {
                val url = "$apiUrl/ranking-list/content?id=$id&page=1&perPage=12"
                val res = app.get(url, headers = desktopHeaders).parsedSafe<RankingContentResponse>()
                val items = res?.data?.subjectList?.mapNotNull { it.toSearchResponse() }
                if (!items.isNullOrEmpty()) homeSets.add(HomePageList(title, items))
            } catch (e: Exception) { e.printStackTrace() }
        }
        return newHomePageResponse(homeSets) //
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$apiUrl/subject/search?host=moviebox.ph"
        return try {
            val res = app.post(url, headers = desktopHeaders, json = mapOf("keyword" to query, "page" to 0, "perPage" to 20)).parsedSafe<SearchDataResponse>()
            res?.data?.items?.mapNotNull { it.toSearchResponse() } ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }

    override suspend fun load(url: String): LoadResponse {
        val detailUrl = "$apiUrl/detail?detailPath=$url"
        val res = app.get(detailUrl, headers = desktopHeaders).parsedSafe<DetailFullResponse>()
        val data = res?.data ?: throw ErrorLoadingException("Data Kosong")
        val subject = data.subject ?: throw ErrorLoadingException("Film Tidak Ditemukan")
        
        // Logika Dosen: Tentukan tipe TV
        val tvType = if (subject.subjectType == 2) TvType.TvSeries else TvType.Movie 

        return if (tvType == TvType.Movie) {
            newMovieLoadResponse(subject.title ?: "", url, tvType, 
                LoadData(subject.subjectId ?: "", 0, 0, url).toJson() // JSON format
            ) {
                this.posterUrl = subject.cover?.url ?: subject.image?.url
                this.plot = subject.description
                subject.imdbRatingValue?.toDoubleOrNull()?.let { this.score = Score.from10(it) } //
            }
        } else {
            val episodes = mutableListOf<Episode>()
            // Logika Dosen: Ambil Season & Episode
            data.resource?.seasons?.forEach { season ->
                val epList = if (season.allEp.isNullOrEmpty()) {
                    (1..(season.maxEp ?: 1)).toList()
                } else {
                    season.allEp.split(",").mapNotNull { it.toIntOrNull() }
                }

                epList.forEach { epNum ->
                    episodes.add(newEpisode(
                        LoadData(subject.subjectId ?: "", season.se, epNum, url).toJson() // JSON format
                    ) {
                        this.season = season.se
                        this.episode = epNum
                        this.name = "Episode $epNum"
                    })
                }
            }
            newTvSeriesLoadResponse(subject.title ?: "", url, tvType, episodes) {
                this.posterUrl = subject.cover?.url ?: subject.image?.url
                this.plot = subject.description
                subject.imdbRatingValue?.toDoubleOrNull()?.let { this.score = Score.from10(it) } //
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // Logika Dosen: Parse JSON LoadData
        val media = parseJson<LoadData>(data) 

        val playUrl = "https://lok-lok.cc/wefeed-h5api-bff/subject/play?subjectId=${media.id}&se=${media.season ?: 0}&ep=${media.episode ?: 0}&detailPath=${media.detailPath}"
        
        // Logika Dosen: Referer Harus Match
        val refererUrl = "$mainUrl/spa/videoPlayPage/movies/${media.detailPath}?id=${media.id}&type=/movie/detail&lang=en"
        
        val playHeaders = mapOf(
            "authority" to "lok-lok.cc",
            "accept" to "application/json",
            "cookie" to "_ga=GA1.1.683107572.1770449531; uuid=f73de7fd-ab7e-4c25-a1d7-dc984179f8fc; _ga_5W8GT0FPB7=GS2.1.s1770457948\$o2\$g1\$t1770458018\$j58\$l0\$h0",
            "referer" to refererUrl,
            "user-agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36",
            "x-source" to "app-search"
        )

        return try {
            val res = app.get(playUrl, headers = playHeaders, timeout = 60).parsedSafe<PlayResponse>()
            val streams = res?.data?.streams ?: return false

            streams.reversed().distinctBy { it.url }.forEach { stream ->
                callback.invoke(
                    newExtractorLink(this.name, "${this.name} ${stream.resolutions}", stream.url ?: return@forEach, type = ExtractorLinkType.VIDEO) {
                        this.referer = refererUrl //
                        this.quality = getQualityFromName(stream.resolutions) //
                    }
                )

                // Subtitle logic remains
                try {
                    val captionUrl = "https://h5-api.aoneroom.com/wefeed-h5api-bff/subject/caption?format=MP4&id=${stream.id}&subjectId=${media.id}&detailPath=${media.detailPath}"
                    val capRes = app.get(captionUrl, headers = desktopHeaders).parsedSafe<CaptionResponse>()
                    capRes?.data?.captions?.forEach { cap ->
                        subtitleCallback.invoke(newSubtitleFile(cap.lanName ?: "Unknown", cap.url ?: return@forEach))
                    }
                } catch (ce: Exception) { }
            }
            true
        } catch (err: Exception) { false }
    }

    private fun Subject.toSearchResponse(): SearchResponse? {
        val type = if (this.subjectType == 2) TvType.TvSeries else TvType.Movie
        return newMovieSearchResponse(this.title ?: return null, this.detailPath ?: return null, type) {
            this.posterUrl = this@toSearchResponse.cover?.url ?: this@toSearchResponse.image?.url
            this@toSearchResponse.imdbRatingValue?.toDoubleOrNull()?.let { this.score = Score.from10(it) }
        }
    }

    // --- DATA CLASSES ---
    data class RankingContentResponse(@JsonProperty("data") val data: RankingData?)
    data class RankingData(@JsonProperty("subjectList") val subjectList: List<Subject>?)
    data class SearchDataResponse(@JsonProperty("data") val data: SearchResultData?)
    data class SearchResultData(@JsonProperty("items") val items: List<Subject>?)
    data class DetailFullResponse(@JsonProperty("data") val data: DetailData?)
    data class DetailData(@JsonProperty("subject") val subject: Subject?, @JsonProperty("resource") val resource: Resource?, @JsonProperty("stars") val stars: List<Star>?)
    data class Resource(@JsonProperty("seasons") val seasons: List<Season>?)
    data class Season(@JsonProperty("se") val se: Int?, @JsonProperty("maxEp") val maxEp: Int?, @JsonProperty("allEp") val allEp: String?)
    data class Star(@JsonProperty("name") val name: String?, @JsonProperty("avatarUrl") val avatarUrl: String?, @JsonProperty("character") val character: String?)
    data class PlayResponse(@JsonProperty("data") val data: PlayData?)
    data class PlayData(@JsonProperty("streams") val streams: List<VideoStream>?)
    data class VideoStream(@JsonProperty("id") val id: String?, @JsonProperty("url") val url: String?, @JsonProperty("resolutions") val resolutions: String?)
    data class CaptionResponse(@JsonProperty("data") val data: CaptionData?)
    data class CaptionData(@JsonProperty("captions") val captions: List<CaptionItem>?)
    data class CaptionItem(@JsonProperty("lanName") val lanName: String?, @JsonProperty("url") val url: String?)
    data class Subject(@JsonProperty("subjectId") val subjectId: String?, @JsonProperty("title") val title: String?, @JsonProperty("cover") val cover: ImageObj?, @JsonProperty("image") val image: ImageObj?, @JsonProperty("detailPath") val detailPath: String?, @JsonProperty("subjectType") val subjectType: Int?, @JsonProperty("description") val description: String?, @JsonProperty("releaseDate") val releaseDate: String?, @JsonProperty("imdbRatingValue") val imdbRatingValue: String?)
    data class ImageObj(@JsonProperty("url") val url: String?)
}
