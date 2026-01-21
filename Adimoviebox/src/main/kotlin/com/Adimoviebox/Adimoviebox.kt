package com.Adimoviebox

import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.fixUrl
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.Score
import com.fasterxml.jackson.annotation.JsonProperty

class Adimoviebox : MainAPI() {
    // ==========================================
    // 1. KONFIGURASI UTAMA
    // ==========================================
    override var mainUrl = "https://moviebox.ph"
    override var name = "Adimoviebox"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.AsianDrama)

    private val apiUrl = "https://h5-api.aoneroom.com/wefeed-h5api-bff"

    private val baseHeaders = mapOf(
        "Accept" to "application/json",
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36",
        "x-client-info" to "{\"timezone\":\"Asia/Jakarta\"}"
    )

    private fun getDynamicHeaders(isLokLok: Boolean): Map<String, String> {
        return baseHeaders + if (isLokLok) {
            mapOf("Origin" to "https://lok-lok.cc", "Referer" to "https://lok-lok.cc/")
        } else {
            mapOf("Origin" to "https://filmboom.top", "Referer" to "https://filmboom.top/")
        }
    }

    // ==========================================
    // 2. DAFTAR KATEGORI
    // ==========================================
    override val mainPage = com.lagradost.cloudstream3.mainPageOf(
        "home" to "Home",
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

    // ==========================================
    // 3. HALAMAN UTAMA
    // ==========================================
    @Suppress("DEPRECATION")
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val homeData = ArrayList<HomePageList>()
        
        val targetUrl = if (request.name == "Home") {
            mainUrl
        } else {
            "$mainUrl/ranking-list/category?id=${request.data}"
        }

        val document = app.get(targetUrl).document

        if (request.name == "Home") {
            document.select("div.movie-card-list-box").forEach { section ->
                val sectionName = section.select(".top-title-action .title").text().trim()
                val movies = section.select("div.movie-card-list a.movie-card").mapNotNull { element ->
                    val title = element.select("p").text().trim()
                    val href = fixUrl(element.attr("href"))
                    newMovieSearchResponse(title, href, TvType.Movie) {
                        this.posterUrl = null
                    }
                }
                if (movies.isNotEmpty()) {
                    homeData.add(HomePageList(sectionName, movies))
                }
            }
        } else {
            val movies = document.select("a.movie-card").mapNotNull { element ->
                val title = element.select("p").text().trim()
                val href = fixUrl(element.attr("href"))
                newMovieSearchResponse(title, href, TvType.Movie) {
                    this.posterUrl = null
                }
            }
            if (movies.isNotEmpty()) {
                homeData.add(HomePageList(request.name, movies))
            }
        }
        
        return newHomePageResponse(homeData)
    }

    // ==========================================
    // 4. LOAD DETAIL (SCORE FIXED)
    // ==========================================
    @Suppress("DEPRECATION")
    override suspend fun load(url: String): LoadResponse? {
        val isLokLok = url.contains("lok-lok.cc")
        val regex = "(?:detail\\/|movies\\/)([^?]+)".toRegex()
        val matchResult = regex.find(url)
        val detailPath = matchResult?.groupValues?.get(1)
            ?: throw ErrorLoadingException("Link tidak valid")

        val targetUrl = "$apiUrl/detail?detailPath=$detailPath"
        val headers = getDynamicHeaders(isLokLok)

        val response = app.get(targetUrl, headers = headers).parsedSafe<MovieBoxDetailResponse>()
            ?: throw ErrorLoadingException("Gagal mengambil data")

        val subject = response.data?.subject ?: throw ErrorLoadingException("Film tidak ditemukan")
        val resource = response.data.resource
        val isSeries = resource?.seasons?.any { (it.maxEp ?: 0) > 1 } == true
        
        val sourceFlag = if (isLokLok) "LOKLOK" else "MBOX"
        val dataId = "${subject.subjectId}|$detailPath|$sourceFlag"

        // SOLUSI UTAMA 1: Menggunakan Score.from10 sesuai kode lama
        val scoreObj = Score.from10(subject.imdbRatingValue)

        if (isSeries) {
            val episodes = ArrayList<com.lagradost.cloudstream3.Episode>()
            resource?.seasons?.forEach { season ->
                val seasonNum = season.se ?: 1
                val maxEpisode = season.maxEp ?: 0
                for (i in 1..maxEpisode) {
                    val epData = newEpisode("$dataId|$seasonNum|$i") {
                        this.name = "Episode $i"
                        this.season = seasonNum
                        this.episode = i
                        this.posterUrl = subject.cover?.url
                    }
                    episodes.add(epData)
                }
            }

            return newTvSeriesLoadResponse(subject.title ?: "No Title", url, TvType.TvSeries, episodes) {
                this.posterUrl = subject.cover?.url
                this.plot = subject.description
                this.year = subject.releaseDate?.take(4)?.toIntOrNull()
                this.score = scoreObj // Assign Score object
            }

        } else {
            return newMovieLoadResponse(subject.title ?: "No Title", url, TvType.Movie, "$dataId|0|0") {
                this.posterUrl = subject.cover?.url
                this.plot = subject.description
                this.year = subject.releaseDate?.take(4)?.toIntOrNull()
                this.score = scoreObj // Assign Score object
            }
        }
    }

    // ==========================================
    // 5. LOAD LINKS (FIXED: TYPE & REFERER HEADER)
    // ==========================================
    @Suppress("DEPRECATION")
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        
        val args = data.split("|")
        val subjectId = args.getOrNull(0) ?: return false
        val detailPath = args.getOrNull(1) ?: ""
        val sourceFlag = args.getOrNull(2) ?: "MBOX"
        val seasonNum = args.getOrNull(3) ?: "0"
        val episodeNum = args.getOrNull(4) ?: "0"

        val isLokLok = sourceFlag == "LOKLOK"
        val headers = getDynamicHeaders(isLokLok)
        val refererUrl = headers["Referer"] ?: "https://filmboom.top/"

        val playUrl = "$apiUrl/subject/play?subjectId=$subjectId&se=$seasonNum&ep=$episodeNum&detailPath=$detailPath"

        val response = app.get(playUrl, headers = headers).parsedSafe<MovieBoxPlayResponse>()
        val streams = response?.data?.streams

        if (streams.isNullOrEmpty()) return false

        streams.forEach { stream ->
            if (!stream.url.isNullOrEmpty()) {
                val qualityStr = stream.resolutions ?: "0"
                val qualityInt = qualityStr.toIntOrNull() ?: Qualities.Unknown.value
                
                // Tentukan tipe file
                val type = if (stream.url.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO

                // SOLUSI UTAMA 2: Menggunakan newExtractorLink + Lambda Headers
                callback.invoke(
                    newExtractorLink(
                        name,                                // Source
                        "Adimoviebox ${qualityStr}p",        // Name
                        stream.url,                          // Url
                        type                                 // Type (M3U8/VIDEO)
                    ) {
                        // Kunci: Masukkan Referer ke Headers Map, bukan this.referer = ...
                        this.headers = mapOf("Referer" to refererUrl) 
                        this.quality = qualityInt
                    }
                )
            }
        }
        return true
    }
}

// ==========================================
// DATA CLASSES
// ==========================================

data class MovieBoxDetailResponse(
    @JsonProperty("data") val data: MBDetailData?
)

data class MBDetailData(
    @JsonProperty("subject") val subject: MBSubject?,
    @JsonProperty("resource") val resource: MBResource?
)

data class MBSubject(
    @JsonProperty("subjectId") val subjectId: String?,
    @JsonProperty("title") val title: String?,
    @JsonProperty("description") val description: String?,
    @JsonProperty("cover") val cover: MBImage?,
    @JsonProperty("releaseDate") val releaseDate: String?,
    @JsonProperty("imdbRatingValue") val imdbRatingValue: String?
)

data class MBResource(
    @JsonProperty("seasons") val seasons: List<MBSeason>?
)

data class MBSeason(
    @JsonProperty("se") val se: Int?,
    @JsonProperty("maxEp") val maxEp: Int?
)

data class MBImage(
    @JsonProperty("url") val url: String?
)

data class MovieBoxPlayResponse(
    @JsonProperty("data") val data: MBPlayData?
)

data class MBPlayData(
    @JsonProperty("streams") val streams: List<MBStream>?
)

data class MBStream(
    @JsonProperty("url") val url: String?,
    @JsonProperty("resolutions") val resolutions: String?,
    @JsonProperty("format") val format: String?
)
