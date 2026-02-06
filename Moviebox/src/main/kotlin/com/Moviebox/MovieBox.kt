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

class MovieBox : MainAPI() {
    override var mainUrl = "https://moviebox.ph"
    override var name = "MovieBox"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.AsianDrama)

    private val apiUrl = "https://h5-api.aoneroom.com/wefeed-h5api-bff"
    private val playerApiUrl = "https://123movienow.cc/wefeed-h5api-bff"

    private var cachedToken: String? = null

    // --- TOKEN ---
    private suspend fun getToken(): String {
        if (cachedToken != null) return cachedToken!!
        try {
            val response = app.get(mainUrl, timeout = 30L)
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
            "user-agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Mobile Safari/537.36"
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

    // --- 1. HOME PAGE (DINAMIS DARI JSON /home) ---
    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        // Kita hanya load halaman 1, karena endpoint /home memuat semua section sekaligus
        if (page > 1) return newHomePageResponse(emptyList(), false)

        val link = "$apiUrl/home?host=moviebox.ph"
        val homeLists = ArrayList<HomePageList>()

        try {
            val response = app.get(link, headers = getHeaders()).text
            val json = parseJson<HomeResponse>(response).data

            json?.operatingList?.forEach { section ->
                val title = section.title ?: "Untitled"
                val subjects = ArrayList<SubjectInfo>()

                // 1. Handle BANNER (Slide atas)
                if (section.type == "BANNER" && section.banner?.items != null) {
                    section.banner.items.forEach { bannerItem ->
                        bannerItem.subject?.let { subjects.add(it) }
                    }
                }
                
                // 2. Handle SUBJECTS_MOVIE & CUSTOM (List film biasa)
                if (!section.subjects.isNullOrEmpty()) {
                    subjects.addAll(section.subjects)
                }

                // Konversi ke format Cloudstream
                if (subjects.isNotEmpty()) {
                    val filmList = subjects.map { film ->
                        newMovieSearchResponse(film.title ?: "No Title", film.detailPath ?: "", TvType.Movie) {
                            this.posterUrl = film.cover?.url
                            // Parsing tahun dari "2026-02-05"
                            this.year = film.releaseDate?.take(4)?.toIntOrNull()
                        }
                    }
                    homeLists.add(HomePageList(title, filmList))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return newHomePageResponse(homeLists)
    }

    // --- 2. SEARCH ---
    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$apiUrl/subject/search-suggest"
        val body = mapOf("keyword" to query, "perPage" to 10)
        val requestBody = body.toJson().toRequestBody("application/json".toMediaTypeOrNull())

        return try {
            val response = app.post(
                url, 
                headers = getHeaders(),
                requestBody = requestBody
            ).text
            
            val json = parseJson<ResponseWrapper>(response).data
            json?.list?.map { film ->
                newMovieSearchResponse(film.title ?: "", film.detailPath ?: "", TvType.Movie) {
                    this.posterUrl = film.cover?.url
                    this.year = film.year
                }
            } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    // --- 3. LOAD DETAIL ---
    override suspend fun load(url: String): LoadResponse {
        val detailUrl = "$apiUrl/detail?detailPath=$url"
        val response = app.get(detailUrl, headers = getHeaders()).text
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

        val playUrl = "$playerApiUrl/subject/play?subjectId=$subjectId&se=$se&ep=$ep&detailPath=$detailPath"
        val playResponse = app.get(playUrl, headers = getHeaders(forPlayer = true)).text
        val playJson = parseJson<PlayWrapper>(playResponse)

        playJson.data?.streams?.forEach { stream ->
            stream.url?.let { videoUrl ->
                val qualityStr = stream.resolutions ?: "Unknown"
                val qualityInt = qualityStr.toIntOrNull() ?: Qualities.Unknown.value
                val format = stream.format ?: "MP4"
                
                callback.invoke(
                    newExtractorLink(
                        source = this.name,
                        name = "MovieBox $format $qualityStr",
                        url = videoUrl,
                        type = if (videoUrl.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                    ) {
                        this.referer = "https://123movienow.cc/"
                        this.quality = qualityInt
                    }
                )
            }
        }

        val subUrl = "$apiUrl/subject/caption?subjectId=$subjectId&id=0&detailPath=$detailPath"
        try {
            val subResponse = app.get(subUrl, headers = getHeaders()).text
            val subJson = parseJson<CaptionWrapper>(subResponse)
            
            subJson.data?.list?.forEach { sub ->
                if (!sub.url.isNullOrEmpty()) {
                    subtitleCallback.invoke(
                        SubtitleFile(
                            lang = sub.language ?: "Unknown",
                            url = sub.url
                        )
                    )
                }
            }
        } catch (e: Exception) {}

        return true
    }

    // --- JSON MODELS ---
    // Struktur JSON Utama untuk Home Page
    data class HomeResponse(@JsonProperty("data") val data: HomeData?)
    data class HomeData(@JsonProperty("operatingList") val operatingList: List<OperatingItem>?)
    
    data class OperatingItem(
        @JsonProperty("type") val type: String?, // BANNER, SUBJECTS_MOVIE, CUSTOM
        @JsonProperty("title") val title: String?,
        @JsonProperty("subjects") val subjects: List<SubjectInfo>?,
        @JsonProperty("banner") val banner: BannerData?
    )
    data class BannerData(@JsonProperty("items") val items: List<BannerItem>?)
    data class BannerItem(
        @JsonProperty("subject") val subject: SubjectInfo?
    )

    // Struktur untuk Search & Detail (Re-used)
    data class ResponseWrapper(@JsonProperty("data") val data: ListContainer?)
    data class ListContainer(@JsonProperty("list") val list: List<SubjectInfo>?)

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
        @JsonProperty("cover") val cover: CoverInfo?,
        @JsonProperty("detailPath") val detailPath: String?,
        @JsonProperty("year") val year: Int? = null // Helper field, diisi manual di logic jika perlu
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

    data class CaptionWrapper(@JsonProperty("data") val data: CaptionList?)
    data class CaptionList(@JsonProperty("list") val list: List<CaptionItem>?)
    data class CaptionItem(@JsonProperty("language") val language: String?, @JsonProperty("url") val url: String?)
}
