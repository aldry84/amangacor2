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

    // 1. HEADER DESKTOP (Sesuai curl terbaru untuk navigasi & kategori)
    private val desktopHeaders = mapOf(
        "authority" to "h5-api.aoneroom.com",
        "accept" to "application/json",
        "accept-language" to "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7",
        "authorization" to "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1aWQiOjE4MTM0MjU0MjgwMjM4ODc4MDAsImF0cCI6MywiZXh0IjoiMTc3MDQxMTA5MCIsImV4cCI6MTc3ODE4NzA5MCwiaWF0IjoxNzcwNDEwNzkwfQ.-kW86pGAJX6jheH_yEM8xfGd4rysJFR_hM3djl32nAo",
        "content-type" to "application/json",
        "origin" to "https://moviebox.ph",
        "referer" to "https://moviebox.ph/",
        "sec-ch-ua" to "\"Chromium\";v=\"137\", \"Not/A)Brand\";v=\"24\"",
        "sec-ch-ua-mobile" to "?0",
        "sec-ch-ua-platform" to "\"Linux\"",
        "user-agent" to "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36",
        "x-client-info" to "{\"timezone\":\"Asia/Jayapura\"}",
        "x-request-lang" to "en"
    )

    // =================================================================================
    // 1. HALAMAN UTAMA (MENGGUNAKAN ENDPOINT /ranking-list/content)
    // =================================================================================
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val homeSets = mutableListOf<HomePageList>()
        
        // Daftar kategori sesuai ID yang Anda berikan
        val categories = listOf(
            Pair("6528093688173053896", "Film Indonesia"),
            Pair("5283462032510044280", "Drama Indonesia"),
            Pair("5848753831881965888", "Indonesia Horror"),
            Pair("4380734070238626200", "Drama Korea")
        )

        categories.forEach { (id, title) ->
            try {
                // Perbaikan: Menggunakan endpoint /ranking-list/content
                val url = "$apiUrl/ranking-list/content?id=$id&page=1&perPage=12"
                val res = app.get(url, headers = desktopHeaders, timeout = 60).parsedSafe<RankingContentResponse>()
                
                // Ambil dari subjectList sesuai struktur JSON terbaru
                val items = res?.data?.subjectList?.mapNotNull { it.toSearchResponse() }
                
                if (!items.isNullOrEmpty()) {
                    homeSets.add(HomePageList(title, items, isHorizontalImages = false))
                }
            } catch (e: Exception) { e.printStackTrace() }
        }

        return newHomePageResponse(homeSets)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$apiUrl/subject/search?host=moviebox.ph"
        val body = mapOf("keyword" to query, "page" to 0, "perPage" to 20)
        return try {
            val res = app.post(url, headers = desktopHeaders, json = body, timeout = 60).parsedSafe<SearchDataResponse>()
            res?.data?.items?.mapNotNull { it.toSearchResponse() } ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }

    override suspend fun load(url: String): LoadResponse {
        val detailUrl = "$apiUrl/detail?detailPath=$url"
        val res = app.get(detailUrl, headers = desktopHeaders, timeout = 60).parsedSafe<DetailFullResponse>()
        val data = res?.data ?: throw ErrorLoadingException("Data Kosong")
        val subject = data.subject ?: throw ErrorLoadingException("Film Tidak Ditemukan")

        val title = subject.title ?: ""
        val rating = subject.imdbRatingValue?.toDoubleOrNull()
        val actors = data.stars?.map { ActorData(Actor(it.name ?: "", it.avatarUrl), roleString = it.character) }

        return if (subject.subjectType == 1) { // Movie
            newMovieLoadResponse(title, url, TvType.Movie, "${subject.subjectId}|0|0|$url") {
                this.posterUrl = subject.cover?.url ?: subject.image?.url
                this.plot = subject.description
                this.actors = actors
                this.year = subject.releaseDate?.take(4)?.toIntOrNull()
                if (rating != null) this.score = Score.from10(rating) //
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
                this.posterUrl = subject.cover?.url ?: subject.image?.url
                this.plot = subject.description
                this.actors = actors
                if (rating != null) this.score = Score.from10(rating) //
            }
        }
    }

    // =================================================================================
    // 2. LOAD LINKS (MENGGUNAKAN HEADER MOBILE SESUAI CURL PLAY)
    // =================================================================================
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
        val refererUrl = "https://lok-lok.cc/spa/videoPlayPage/movies/$path?id=$id&utm_source=app-search"
        
        // Header Mobile Sesuai Curl Terakhir
        val playHeaders = mapOf(
            "authority" to "lok-lok.cc",
            "accept" to "application/json",
            "accept-language" to "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7",
            "cookie" to "_ga=GA1.1.683107572.1770449531; uuid=f73de7fd-ab7e-4c25-a1d7-dc984179f8fc; _ga_5W8GT0FPB7=GS2.1.s1770457948\$o2\$g1\$t1770458018\$j58\$l0\$h0",
            "referer" to refererUrl,
            "sec-ch-ua" to "\"Chromium\";v=\"137\", \"Not/A)Brand\";v=\"24\"",
            "sec-ch-ua-mobile" to "?1",
            "sec-ch-ua-platform" to "\"Android\"",
            "user-agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Mobile Safari/537.36",
            "x-client-info" to "{\"timezone\":\"Asia/Jayapura\"}",
            "x-source" to "app-search"
        )
        
        return try {
            val res = app.get(playUrl, headers = playHeaders, timeout = 60).parsedSafe<PlayResponse>()
            val streams = res?.data?.streams
            
            if (streams.isNullOrEmpty()) return false

            streams.forEach { stream ->
                callback.invoke(
                    newExtractorLink( //
                        source = this.name,
                        name = "${this.name} ${stream.resolutions}p",
                        url = stream.url ?: return@forEach,
                        type = ExtractorLinkType.VIDEO
                    ) {
                        this.referer = refererUrl //
                        this.quality = stream.resolutions?.toIntOrNull() ?: 0 //
                    }
                )
            }
            true
        } catch (err: Exception) { false }
    }

    private fun Subject.toSearchResponse(): SearchResponse? {
        return newMovieSearchResponse(this.title ?: return null, this.detailPath ?: return null, TvType.Movie) {
            this.posterUrl = this@toSearchResponse.cover?.url ?: this@toSearchResponse.image?.url
            this@toSearchResponse.imdbRatingValue?.toDoubleOrNull()?.let { this.score = Score.from10(it) } //
        }
    }

    // --- DATA CLASSES ---
    data class RankingContentResponse(@JsonProperty("data") val data: RankingData?)
    data class RankingData(@JsonProperty("subjectList") val subjectList: List<Subject>?)
    
    data class SearchDataResponse(@JsonProperty("data") val data: SearchResultData?)
    data class SearchResultData(@JsonProperty("items") val items: List<Subject>?)
    
    data class DetailFullResponse(@JsonProperty("data") val data: DetailData?)
    data class DetailData(
        @JsonProperty("subject") val subject: Subject?,
        @JsonProperty("resource") val resource: Resource?,
        @JsonProperty("stars") val stars: List<Star>?
    )
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
