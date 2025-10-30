package com.Adicinema // Diperbaiki

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.nicehttp.RequestBodyTypes
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder

class Adicinema : MainAPI() { // Diperbaiki
    // === KONFIGURASI DASAR ===
    override var mainUrl = "https://www.omdbapi.com"
    private val omdbApiKey = "8aabbe50" // Kunci API OMDb Anda
    private val apiUrl = "https://fmoviesunblocked.net"

    override var name = "Adicinema" // Diperbaiki
    override val hasMainPage = false
    override val hasQuickSearch = true
    override val instantLinkLoading = false
    override var lang = "en"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    // Cache sederhana untuk mempercepat load
    private val omdbCache = mutableMapOf<String, OmdbItemDetail>()

    // === PENCARIAN CEPAT ===
    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query) ?: emptyList()

    override suspend fun search(query: String): List<SearchResponse>? {
        val encoded = URLEncoder.encode(query, "utf-8")
        val response = app.get("$mainUrl/?s=$encoded&apikey=$omdbApiKey").parsedSafe<OmdbSearch>()?.Search
        return response?.mapNotNull { it.toSearchResponse(this) }
    }

    // === LOAD DETAIL FILM / SERIAL ===
    override suspend fun load(imdbID: String): LoadResponse {
        val encodedId = URLEncoder.encode(imdbID, "utf-8")

        // Gunakan cache jika sudah ada
        val detail = omdbCache.getOrPut(imdbID) {
            app.get("$mainUrl/?i=$encodedId&plot=full&apikey=$omdbApiKey")
                .parsedSafe<OmdbItemDetail>() ?: throw ErrorLoadingException("OMDb detail not found")
        }

        val title = detail.Title ?: throw ErrorLoadingException("Title not found in OMDb")
        val isSeries = detail.Type.equals("series", ignoreCase = true)

        // Cek fmoviesID. Jika ini gagal, ekstensi tidak akan dimuat.
        val fmoviesID = findFmoviesID(title) ?: throw ErrorLoadingException("No Fmovies ID found for $title (API mungkin berubah/down)")

        val posterUrl = detail.Poster
        val year = detail.Year?.substringBefore("-")?.toIntOrNull()
        val plot = detail.Plot
        val tags = detail.Genre?.split(",")?.map { it.trim() }
        val score = Score.from10(detail.imdbRating?.toFloatOrNull())

        return if (isSeries) {
            val episodes = mutableListOf<Episode>()
            val totalSeasons = detail.totalSeasons?.toIntOrNull() ?: 1

            for (season in 1..totalSeasons) {
                val seasonDetail = app.get("$mainUrl/?i=$encodedId&Season=$season&apikey=$omdbApiKey")
                    .parsedSafe<OmdbSeason>()
                seasonDetail?.Episodes?.forEach { ep ->
                    val episodeNum = ep.Episode?.toIntOrNull() ?: 1
                    val epData = EpisodeData(imdbID, season, episodeNum, fmoviesID)
                    episodes.add(
                        newEpisode(epData.toJson()) {
                            this.name = ep.Title
                            this.season = season
                            this.episode = episodeNum
                        }
                    )
                }
            }

            newTvSeriesLoadResponse(title, imdbID, TvType.TvSeries, episodes) {
                this.posterUrl = posterUrl
                this.year = year
                this.plot = plot
                this.tags = tags
                this.score = score
            }
        } else {
            val epData = EpisodeData(imdbID, 1, 1, fmoviesID)
            newMovieLoadResponse(title, imdbID, TvType.Movie, epData.toJson()) {
                this.posterUrl = posterUrl
                this.year = year
                this.plot = plot
                this.tags = tags
                this.score = score
            }
        }
    }

    // === PENGAMBILAN LINK STREAMING ===
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val episodeData = parseJson<EpisodeData>(data)
        val fmoviesID = episodeData.streamingPath
        val seasonNum = episodeData.seasonNum
        val episodeNum = episodeData.episodeNum

        // Referer yang lebih umum untuk fleksibilitas
        val referer = "$apiUrl/" 

        val streams = app.get(
            // Cek apakah endpoint play ini masih valid
            "$apiUrl/wefeed-h5-bff/web/subject/play?subjectId=$fmoviesID&se=$seasonNum&ep=$episodeNum",
            referer = referer
        ).parsedSafe<Media>()?.data?.streams

        streams?.reversed()?.distinctBy { it.url }?.forEach { src ->
            val url = src.url ?: return@forEach
            callback(
                newExtractorLink(
                    this.name,
                    this.name,
                    url,
                    INFER_TYPE
                ) {
                    this.referer = "$apiUrl/"
                    this.quality = src.resolutions?.let { getQualityFromName(it) } ?: Qualities.Unknown.value
                }
            )
        }

        // Subtitle
        val first = streams?.firstOrNull()
        val id = first?.id
        val format = first?.format

        if (id != null && format != null) {
            app.get(
                "$apiUrl/wefeed-h5-bff/web/subject/caption?format=$format&id=$id&subjectId=$fmoviesID",
                referer = referer
            ).parsedSafe<Media>()?.data?.captions?.forEach { sub ->
                val url = sub.url ?: return@forEach
                subtitleCallback(
                    newSubtitleFile(sub.lanName ?: "English", url)
                )
            }
        }

        return true
    }

    // === FUNGSI PEMBANTU ===
    private suspend fun findFmoviesID(query: String): String? {
        // Coba 2 tipe subject: 0 (movie), 1 (series)
        for (type in listOf("0", "1")) {
            val bodyWithType = mapOf(
                "keyword" to query,
                "page" to "1",
                "perPage" to "1",
                "subjectType" to type
            ).toJson().toRequestBody(RequestBodyTypes.JSON.toMediaTypeOrNull())

            val result = app.post("$apiUrl/wefeed-h5-bff/web/subject/search", requestBody = bodyWithType)
                .parsedSafe<Media>()
                ?.data
                ?.items
                ?.firstOrNull()
                ?.subjectId

            if (result != null) return result
        }
        return null
    }

    // === DATA CLASS ===
    data class EpisodeData(
        val imdbID: String,
        val seasonNum: Int,
        val episodeNum: Int,
        val streamingPath: String
    )

    data class OmdbSearch(
        @JsonProperty("Search") val Search: List<OmdbItem>? = null
    )

    data class OmdbItem(
        @JsonProperty("Title") val Title: String? = null,
        @JsonProperty("Year") val Year: String? = null,
        @JsonProperty("imdbID") val imdbID: String? = null,
        @JsonProperty("Type") val Type: String? = null,
        @JsonProperty("Poster") val Poster: String? = null,
    ) {
        fun toSearchResponse(provider: Adicinema): SearchResponse? { // Diperbaiki
            if (imdbID.isNullOrBlank() || Title.isNullOrBlank()) return null
            val type = if (Type.equals("series", ignoreCase = true)) TvType.TvSeries else TvType.Movie
            return provider.newMovieSearchResponse(Title, imdbID, type, true) {
                this.posterUrl = Poster
                this.year = Year?.toIntOrNull()
            }
        }
    }
    // ... data classes sisanya (OmdbItemDetail, OmdbSeason, Media, dll.) tetap sama ...
    
    data class OmdbItemDetail(
        @JsonProperty("Title") val Title: String? = null,
        @JsonProperty("Year") val Year: String? = null,
        @JsonProperty("Plot") val Plot: String? = null,
        @JsonProperty("Genre") val Genre: String? = null,
        @JsonProperty("imdbRating") val imdbRating: String? = null,
        @JsonProperty("totalSeasons") val totalSeasons: String? = null,
        @JsonProperty("Poster") val Poster: String? = null,
        @JsonProperty("Type") val Type: String? = null
    )

    data class OmdbSeason(
        @JsonProperty("Episodes") val Episodes: List<OmdbEpisode>? = null
    )

    data class OmdbEpisode(
        @JsonProperty("Title") val Title: String? = null,
        @JsonProperty("Episode") val Episode: String? = null
    )

    data class Media(
        @JsonProperty("data") val data: Data? = null,
    ) {
        data class Data(
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
                @JsonProperty("lanName") val lanName: String? = null,
                @JsonProperty("url") val url: String? = null,
            )
        }
    }

    data class Items(
        @JsonProperty("subjectId") val subjectId: String? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("cover") val cover: Cover? = null,
    ) {
        data class Cover(
            @JsonProperty("url") val url: String? = null,
        )
    }
}
