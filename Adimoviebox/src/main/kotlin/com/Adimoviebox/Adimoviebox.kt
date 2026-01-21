package com.Adimoviebox

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
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

    // API Backend Pusat
    private val apiUrl = "https://h5-api.aoneroom.com/wefeed-h5api-bff"

    // Header Dasar
    private val baseHeaders = mapOf(
        "Accept" to "application/json",
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36",
        "x-client-info" to "{\"timezone\":\"Asia/Jakarta\"}"
    )

    // Helper Header Dinamis (Bunglon Mode 🦎)
    private fun getDynamicHeaders(isLokLok: Boolean): Map<String, String> {
        return baseHeaders + if (isLokLok) {
            mapOf("Origin" to "https://lok-lok.cc", "Referer" to "https://lok-lok.cc/")
        } else {
            mapOf("Origin" to "https://filmboom.top", "Referer" to "https://filmboom.top/")
        }
    }

    // ==========================================
    // 2. HALAMAN UTAMA
    // ==========================================
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val document = app.get(mainUrl).document
        val homeData = ArrayList<HomePageList>()

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
        return HomePageResponse(homeData)
    }

    // ==========================================
    // 3. LOAD DETAIL (Support Movie & Series!)
    // ==========================================
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

        // Cek apakah ini Series atau Movie
        val isSeries = resource?.seasons?.any { (it.maxEp ?: 0) > 1 } == true

        // Simpan data untuk loadLinks (Format: subjectId | detailPath | sourceFlag)
        val sourceFlag = if (isLokLok) "LOKLOK" else "MBOX"
        val dataId = "${subject.subjectId}|$detailPath|$sourceFlag"

        // Konversi rating string ke int (0-10) untuk addRating
        val ratingVal = subject.imdbRatingValue?.toFloatOrNull()?.times(10)?.toInt()

        if (isSeries) {
            // === LOGIKA SERIES (FIXED EPISODE CONSTRUCTOR) ===
            val episodes = ArrayList<Episode>()

            // Loop setiap Season
            resource?.seasons?.forEach { season ->
                val seasonNum = season.se ?: 1
                val maxEpisode = season.maxEp ?: 0

                // Generate list episode
                for (i in 1..maxEpisode) {
                    // FIX: Menggunakan newEpisode { ... } (Bukan constructor lama)
                    episodes.add(
                        newEpisode("$dataId|$seasonNum|$i") {
                            this.name = "Episode $i"
                            this.season = seasonNum
                            this.episode = i
                            this.posterUrl = subject.cover?.url
                        }
                    )
                }
            }

            return newTvSeriesLoadResponse(subject.title ?: "No Title", url, TvType.TvSeries, episodes) {
                this.posterUrl = subject.cover?.url
                this.plot = subject.description
                this.year = subject.releaseDate?.take(4)?.toIntOrNull()
                // FIX: Menggunakan addRating (Bukan this.rating =)
                addRating(ratingVal)
            }

        } else {
            // === LOGIKA MOVIE ===
            return newMovieLoadResponse(subject.title ?: "No Title", url, TvType.Movie, "$dataId|0|0") {
                this.posterUrl = subject.cover?.url
                this.plot = subject.description
                this.year = subject.releaseDate?.take(4)?.toIntOrNull()
                // FIX: Menggunakan addRating (Bukan this.rating =)
                addRating(ratingVal)
            }
        }
    }

    // ==========================================
    // 4. LOAD LINKS (Pemutar Video)
    // ==========================================
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: SubtitleCallback,
        callback: ExtractorLinkCallback
    ): Boolean {
        // Bongkar Data ID
        val args = data.split("|")
        val subjectId = args.getOrNull(0) ?: return false
        val detailPath = args.getOrNull(1) ?: ""
        val sourceFlag = args.getOrNull(2) ?: "MBOX"
        val seasonNum = args.getOrNull(3) ?: "0"
        val episodeNum = args.getOrNull(4) ?: "0"

        val isLokLok = sourceFlag == "LOKLOK"
        val headers = getDynamicHeaders(isLokLok)

        val playUrl = "$apiUrl/subject/play?subjectId=$subjectId&se=$seasonNum&ep=$episodeNum&detailPath=$detailPath"

        val response = app.get(playUrl, headers = headers).parsedSafe<MovieBoxPlayResponse>()
        val streams = response?.data?.streams

        if (streams.isNullOrEmpty()) return false

        streams.forEach { stream ->
            if (!stream.url.isNullOrEmpty()) {
                val qualityStr = stream.resolutions ?: "0"
                val quality = qualityStr.toIntOrNull() ?: Qualities.Unknown.value

                // FIX: Menggunakan SourceUrl(...).toExtractorLink(this)
                // Ini cara modern untuk menghindari error deprecated ExtractorLink constructor
                callback.invoke(
                    SourceUrl(
                        url = stream.url,
                        name = "Adimoviebox ${qualityStr}p",
                        referer = headers["Referer"] ?: "https://filmboom.top/",
                        quality = quality,
                        isM3u8 = stream.url.contains(".m3u8")
                    ).toExtractorLink(this)
                )
            }
        }
        return true
    }
}

// ==========================================
// DATA CLASSES (JSON Parsing)
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
