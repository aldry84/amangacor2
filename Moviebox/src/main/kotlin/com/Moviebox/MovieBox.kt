package com.moviebox

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.fasterxml.jackson.annotation.JsonProperty

class MovieBoxPh : MainAPI() {
    override var mainUrl = "https://moviebox.ph"
    override var name = "MovieBox"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.AsianDrama)

    // API Metadata (Untuk Info Film & Subtitle)
    private val apiUrl = "https://h5-api.aoneroom.com/wefeed-h5api-bff"
    // API Player (Untuk Link Video)
    private val playerApiUrl = "https://123movienow.cc/wefeed-h5api-bff"

    // --- KATEGORI HALAMAN DEPAN (Gaya 'to') ---
    private val homePageCategories = listOf(
        "872031290915189720"  to "Trending 🔥",
        "6528093688173053896" to "Indonesia Movie 🇮🇩",
        "5283462032510044280" to "Drama Indonesia 🎭",
        "4380734070238626200" to "Drakor (Korean Drama) 🇰🇷",
        "5848753831881965888" to "Horror Indonesia 👻"
    )

    private var cachedToken: String? = null

    // --- SISTEM AUTO-TOKEN ---
    private suspend fun getToken(): String {
        if (cachedToken != null) return cachedToken!!
        try {
            // Ambil cookie 'mb_token' dari halaman utama
            val response = app.get(mainUrl)
            val token = response.cookies["mb_token"]?.removesurrounding("\"")
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
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        // Load semua kategori secara paralel (Concurrent) menggunakan apmap
        val homeItems = homePageCategories.apmap { (id, name) ->
            val link = "$apiUrl/subject/ranking-list?id=$id&page=0&perPage=20"
            try {
                val response = app.get(link, headers = getHeaders()).text
                val json = parseJson<ResponseWrapper>(response).data
                
                val filmList = json?.list?.map { film ->
                    newMovieSearchResponse(film.title ?: "No Title", film.detailPath ?: "", TvType.Movie) {
                        this.posterUrl = film.cover?.url ?: film.coverUrl
                        this.year = film.year
                    }
                } ?: emptyList()

                if (filmList.isNotEmpty()) {
                    HomePageList(name, filmList)
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }.filterNotNull()

        return HomePageResponse(homeItems)
    }

    // --- 2. SEARCH (POST JSON) ---
    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$apiUrl/subject/search-suggest"
        val body = mapOf("keyword" to query, "perPage" to 10)
        
        return try {
            val response = app.post(
                url, 
                headers = getHeaders().plus("content-type" to "application/json"),
                data = toJson(body)
            ).text
            
            val json = parseJson<ResponseWrapper>(response).data
            json?.list?.map { film ->
                newMovieSearchResponse(film.title ?: "", film.detailPath ?: "", TvType.Movie) {
                    this.posterUrl = film.cover?.url ?: film.coverUrl
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
        
        // Cek Tipe: 1 = Movie, 2 = Series
        val isMovie = subject?.subjectType == 1
        
        val recommendations = json?.dubs?.map { 
            newMovieSearchResponse(it.lanName ?: "Dub", it.detailPath ?: "", TvType.Movie)
        }

        if (isMovie) {
            // Logic Movie: Kirim se=0 & ep=0
            val episodeData = "$subjectId|0|0|$url"
            return newMovieLoadResponse(title, url, TvType.Movie, episodeData) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
                this.recommendations = recommendations
            }
        } else {
            // Logic Series: Loop Season & Episode
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

    // --- 4. LOAD LINKS & SUBTITLES ---
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

        // 1. Get Video (Dari 123movienow.cc)
        val playUrl = "$playerApiUrl/subject/play?subjectId=$subjectId&se=$se&ep=$ep&detailPath=$detailPath"
        val playResponse = app.get(playUrl, headers = getHeaders(forPlayer = true)).text
        val playJson = parseJson<PlayWrapper>(playResponse)

        playJson.data?.streams?.forEach { stream ->
            stream.url?.let { videoUrl ->
                val qualityStr = stream.resolutions ?: "Unknown"
                val qualityInt = qualityStr.toIntOrNull() ?: Qualities.Unknown.value
                val format = stream.format ?: "MP4"
                
                callback.invoke(
                    ExtractorLink(
                        source = this.name,
                        name = "MovieBox $format $qualityStr", // Tampil: "MovieBox MP4 720"
                        url = videoUrl,
                        referer = "https://123movienow.cc/",
                        quality = qualityInt,
                        isM3u8 = videoUrl.contains(".m3u8")
                    )
                )
            }
        }

        // 2. Get Subtitles (Dari aoneroom.com)
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

    // --- JSON DATA MODELS ---

    data class ResponseWrapper(@JsonProperty("data") val data: ListContainer?)
    data class ListContainer(@JsonProperty("list") val list: List<SimpleSubject>?)
    
    data class SimpleSubject(
        @JsonProperty("title") val title: String?,
        @JsonProperty("cover") val cover: CoverInfo?, 
        @JsonProperty("coverUrl") val coverUrl: String?,
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

    data class CaptionWrapper(@JsonProperty("data") val data: CaptionList?)
    data class CaptionList(@JsonProperty("list") val list: List<CaptionItem>?)
    data class CaptionItem(@JsonProperty("language") val language: String?, @JsonProperty("url") val url: String?)
}
