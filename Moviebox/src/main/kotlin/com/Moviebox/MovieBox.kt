package com.Moviebox

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.fasterxml.jackson.annotation.JsonProperty
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.delay

class MovieBox : MainAPI() {
    override var mainUrl = "https://moviebox.ph"
    override var name = "MovieBox"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.AsianDrama)

    private val homeApiUrl = "https://h5-api.aoneroom.com"
    private val playerApiUrl = "https://123movienow.cc/wefeed-h5api-bff"
    private var cachedToken: String? = null

    // --- KATEGORI HALAMAN DEPAN ---
    override val mainPage = mainPageOf(
        "872031290915189720" to "Trending 🔥",
        "6528093688173053896" to "Indonesian Movies 🇮🇩",
        "5283462032510044280" to "Drama Indonesia 🎭",
        "4380734070238626200" to "K-Drama 🇰🇷",
        "8624142774394406504" to "C-Drama 🇨🇳",
        "3058742380078711608" to "Disney ✨",
        "8449223314756747760" to "Pinoy Drama 🇵🇭",
        "606779077307122552" to "Pinoy Movie 🇵🇭"
    )

    // --- TOKEN ---
    private suspend fun getToken(): String {
        if (cachedToken != null) return cachedToken!!
        try {
            val response = app.get(mainUrl, timeout = 60L)
            val token = response.cookies["mb_token"]?.removeSurrounding("\"")
            if (!token.isNullOrEmpty()) {
                cachedToken = "Bearer $token"
                return cachedToken!!
            }
        } catch (e: Exception) { e.printStackTrace() }
        return ""
    }

    private suspend fun getHeaders(forPlayer: Boolean = false): Map<String, String> {
        val token = getToken()
        val baseHeaders = mutableMapOf(
            "x-request-lang" to "en",
            "user-agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/113.0.0.0 Mobile Safari/537.36"
        )
        if (token.isNotEmpty()) baseHeaders["authorization"] = token
        
        if (forPlayer) {
            baseHeaders["origin"] = "https://123movienow.cc"
            baseHeaders["referer"] = "https://123movienow.cc/"
        } else {
            baseHeaders["origin"] = "https://moviebox.ph"
            baseHeaders["referer"] = "https://moviebox.ph/"
        }
        return baseHeaders
    }

    // --- 1. HOME PAGE ---
    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest,
    ): HomePageResponse {
        val id = request.data
        val targetUrl = "$homeApiUrl/wefeed-h5api-bff/subject/ranking-list?id=$id&page=$page&perPage=12"
        
        return try {
            if (page == 1) delay(200)
            val response = app.get(targetUrl, headers = getHeaders(), timeout = 60L).text
            val json = parseJson<ResponseWrapper>(response).data
            
            val filmList = json?.list?.map { film ->
                film.toSearchResponse()
            } ?: emptyList()

            newHomePageResponse(request.name, filmList, hasNext = filmList.isNotEmpty())
        } catch (e: Exception) {
            newHomePageResponse(request.name, emptyList(), hasNext = false)
        }
    }

    // --- 2. SEARCH ---
    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$homeApiUrl/wefeed-h5api-bff/subject/search"
        val body = mapOf("keyword" to query, "perPage" to 20, "page" to 0)
        val requestBody = body.toJson().toRequestBody("application/json".toMediaTypeOrNull())

        return try {
            val response = app.post(
                url, 
                headers = getHeaders(),
                requestBody = requestBody,
                timeout = 60L
            ).text
            
            val json = parseJson<ResponseWrapper>(response).data
            
            val results = ArrayList<SearchResponse>()
            json?.list?.forEach { results.add(it.toSearchResponse()) }
            json?.items?.forEach { item ->
                item.subject?.let { results.add(it.toSearchResponse()) }
            }
            results
        } catch (e: Exception) {
            emptyList()
        }
    }

    // --- 3. LOAD DETAIL ---
    override suspend fun load(url: String): LoadResponse {
        val detailUrl = "$homeApiUrl/wefeed-h5api-bff/detail?detailPath=$url"
        val response = app.get(detailUrl, headers = getHeaders(), timeout = 60L).text
        val json = parseJson<DetailWrapper>(response).data
        
        val subject = json?.subject
        val title = subject?.title ?: "No Title"
        val plot = subject?.description
        val poster = subject?.cover?.url
        val year = subject?.releaseDate?.take(4)?.toIntOrNull()
        val subjectId = subject?.subjectId ?: ""
        
        val isMovie = subject?.subjectType == 1
        
        val recommendations = json?.dubs?.map { 
            newMovieSearchResponse(it.lanName ?: "Dub", it.detailPath ?: "", TvType.Movie)
        }

        if (isMovie) {
            val episodeData = "$subjectId|0|0|$url"
            return newMovieLoadResponse(title, url, TvType.Movie, episodeData) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
                this.recommendations = recommendations
            }
        } else {
            val episodes = ArrayList<Episode>()
            val seasons = json?.resource?.seasons

            seasons?.forEach { season ->
                val seasonNum = season.se ?: 1
                val maxEp = season.maxEp ?: 1
                if (maxEp > 0) {
                    for (epNum in 1..maxEp) {
                        val episodeData = "$subjectId|$seasonNum|$epNum|$url"
                        episodes.add(
                            newEpisode(episodeData) {
                                this.name = "Episode $epNum"
                                this.season = seasonNum
                                this.episode = epNum
                                this.posterUrl = poster
                            }
                        )
                    }
                }
            }
            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
                this.recommendations = recommendations
            }
        }
    }

    // --- 4. LOAD LINKS ---
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val args = data.split("|")
        if (args.size < 4) return false

        val subjectId = args[0]
        val se = args[1]
        val ep = args[2]
        val detailPath = args[3]

        val specificReferer = "https://123movienow.cc/spa/videoPlayPage/movies/$detailPath?id=$subjectId&type=/movie/detail"
        val playHeaders = getHeaders(forPlayer = true).toMutableMap()
        playHeaders["referer"] = specificReferer

        val playUrl = "$playerApiUrl/subject/play?subjectId=$subjectId&se=$se&ep=$ep&detailPath=$detailPath"
        
        try {
            val playResponse = app.get(playUrl, headers = playHeaders, timeout = 60L).text
            val playJson = parseJson<PlayWrapper>(playResponse)

            playJson.data?.streams?.forEach { stream ->
                stream.url?.let { videoUrl ->
                    val qualityStr = stream.resolutions ?: "Unknown"
                    val qualityInt = qualityStr.toIntOrNull() ?: Qualities.Unknown.value
                    val format = stream.format ?: "MP4"
                    val isM3u8 = videoUrl.contains(".m3u8")
                    
                    callback.invoke(
                        newExtractorLink(
                            source = this.name,
                            name = "MovieBox $format $qualityStr",
                            url = videoUrl,
                            type = if (isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                        ) {
                            this.referer = specificReferer
                            this.quality = qualityInt
                        }
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val subUrl = "$homeApiUrl/wefeed-h5api-bff/subject/caption?format=MP4&id=0&subjectId=$subjectId&detailPath=$detailPath"
        try {
            val subResponse = app.get(subUrl, headers = getHeaders(), timeout = 30L).text
            val subJson = parseJson<CaptionWrapper>(subResponse)
            
            subJson.data?.captions?.forEach { sub ->
                if (!sub.url.isNullOrEmpty()) {
                    subtitleCallback.invoke(
                        SubtitleFile(
                            lang = sub.lanName ?: sub.lan ?: "Unknown",
                            url = sub.url
                        )
                    )
                }
            }
        } catch (e: Exception) {}

        return true
    }

    // --- HELPER FIX ---
    private fun SimpleSubject.toSearchResponse(): SearchResponse {
        return newMovieSearchResponse(this.title ?: "No Title", this.detailPath ?: "", TvType.Movie) {
            // FIX: Hapus 'this.' di depan cover, langsung akses variable 'cover' dari SimpleSubject
            this.posterUrl = cover?.url 
            this.year = year
        }
    }

    // --- JSON MODELS ---

    data class ResponseWrapper(@JsonProperty("data") val data: ListContainer?)
    data class ListContainer(
        @JsonProperty("list") val list: List<SimpleSubject>?,
        @JsonProperty("items") val items: List<SearchItem>?
    )
    data class SearchItem(@JsonProperty("subject") val subject: SimpleSubject?)

    data class SimpleSubject(
        @JsonProperty("title") val title: String?,
        // Kembalikan nama jadi 'cover' agar konsisten
        @JsonProperty("cover") val cover: CoverInfo?, 
        @JsonProperty("year") val year: Int?,
        @JsonProperty("detailPath") val detailPath: String?
    )

    data class DetailWrapper(@JsonProperty("data") val data: DetailData?)
    data class DetailData(
        @JsonProperty("subject") val subject: SubjectInfo?,
        @JsonProperty("resource") val resource: ResourceInfo?,
        @JsonProperty("dubs") val dubs: List<DubInfo>?
    )
    data class SubjectInfo(
        @JsonProperty("subjectId") val subjectId: String?,
        @JsonProperty("subjectType") val subjectType: Int?,
        @JsonProperty("title") val title: String?,
        @JsonProperty("description") val description: String?,
        @JsonProperty("releaseDate") val releaseDate: String?,
        @JsonProperty("cover") val cover: CoverInfo?
    )
    data class CoverInfo(@JsonProperty("url") val url: String?)
    data class ResourceInfo(@JsonProperty("seasons") val seasons: List<SeasonInfo>?)
    data class SeasonInfo(@JsonProperty("se") val se: Int?, @JsonProperty("maxEp") val maxEp: Int?)
    data class DubInfo(@JsonProperty("lanName") val lanName: String?, @JsonProperty("detailPath") val detailPath: String?)

    data class PlayWrapper(@JsonProperty("data") val data: PlayData?)
    data class PlayData(@JsonProperty("streams") val streams: List<StreamInfo>?)
    data class StreamInfo(
        @JsonProperty("url") val url: String?,
        @JsonProperty("resolutions") val resolutions: String?,
        @JsonProperty("format") val format: String?
    )

    data class CaptionWrapper(@JsonProperty("data") val data: CaptionData?)
    data class CaptionData(@JsonProperty("captions") val captions: List<CaptionItem>?)
    data class CaptionItem(
        @JsonProperty("lan") val lan: String?,
        @JsonProperty("lanName") val lanName: String?, 
        @JsonProperty("url") val url: String?
    )
}
