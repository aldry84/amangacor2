package com.Adimoviebox

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.nicehttp.RequestBodyTypes
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

@Suppress("DEPRECATION")
class Adimoviebox : MainAPI() {
    override var mainUrl = "https://moviebox.ph"
    
    // API LAMA (Gudang/Player): Untuk Search, Detail, dan Playback
    private val apiUrl = "https://filmboom.top" 
    
    // API BARU (Etalase): Khusus untuk Halaman Depan & Kategori
    private val homeApiUrl = "https://h5-api.aoneroom.com"

    override val instantLinkLoading = true
    override var name = "Adimoviebox"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override var lang = "id"
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
        TvType.AsianDrama
    )

    // --- BAGIAN KATEGORI SESUAI REQUEST ---
    override val mainPage: List<MainPageData> = mainPageOf(
        "2|{\"classify\":\"All\",\"country\":\"Indonesia\",\"genre\":\"All\",\"sort\":\"Hottest\",\"year\":\"All\"}" to "Indo Film",
        "5|{\"country\":\"Indonesia\",\"genre\":\"All\",\"sort\":\"Hottest\",\"year\":\"All\"}" to "Indo Drama",
        "5|{\"country\":\"Korea\",\"genre\":\"All\",\"sort\":\"Hottest\",\"year\":\"All\"}" to "K-Drama",
        "2|{\"classify\":\"All\",\"country\":\"Indonesia\",\"genre\":\"Horror\",\"sort\":\"Hottest\",\"year\":\"All\"}" to "Horror Indo",
        "5|{\"classify\":\"All\",\"country\":\"All\",\"genre\":\"Anime\",\"sort\":\"Hottest\",\"year\":\"All\"}" to "Anime",
        "5|{\"classify\":\"All\",\"country\":\"China\",\"genre\":\"All\",\"sort\":\"Hottest\",\"year\":\"All\"}" to "C-Drama",
        "2|{\"classify\":\"All\",\"country\":\"United States\",\"genre\":\"All\",\"sort\":\"Hottest\",\"year\":\"All\"}" to "Hollywood Movie"
    )

    private fun getBaseHeaders(): Map<String, String> {
        return mapOf(
            "authority" to "h5-api.aoneroom.com",
            "accept" to "application/json",
            "content-type" to "application/json",
            "origin" to mainUrl,
            "referer" to "$mainUrl/",
            "user-agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Mobile Safari/537.36"
        )
    }

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest,
    ): HomePageResponse {
        val dataParts = request.data.split("|")
        val tabId = dataParts[0]
        val filterJson = dataParts.getOrNull(1) ?: ""
        
        // URL API Baru untuk Filter
        val targetUrl = "$homeApiUrl/wefeed-h5api-bff/home/movieFilter?tabId=$tabId&filterType=$filterJson&pageNo=$page&pageSize=18"

        // Struktur response filter API baru adalah { data: [item1, item2] }
        val responseData = app.get(targetUrl, headers = getBaseHeaders()).parsedSafe<FilterResponse>()
        
        val home = responseData?.data?.map {
            it.toSearchResponse(this)
        } ?: throw ErrorLoadingException("Gagal memuat kategori. Data kosong.")

        return newHomePageResponse(request.name, home)
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun search(query: String): List<SearchResponse> {
        return app.post(
            "$apiUrl/wefeed-h5-bff/web/subject/search", 
            requestBody = mapOf(
                "keyword" to query,
                "page" to "1",
                "perPage" to "0",
                "subjectType" to "0",
            ).toJson().toRequestBody(RequestBodyTypes.JSON.toMediaTypeOrNull())
        ).parsedSafe<Media>()?.data?.items?.map { it.toSearchResponse(this) }
            ?: throw ErrorLoadingException("Pencarian tidak ditemukan.")
    }

    override suspend fun load(url: String): LoadResponse {
        // Parsing URL internal (support id & path dari search response baru)
        val isInternalUrl = url.contains("?id=")
        
        val id = if (isInternalUrl) {
            url.substringAfter("id=").substringBefore("&")
        } else {
            url.substringAfterLast("/")
        }
        
        val path = if (isInternalUrl) {
            url.substringAfter("path=").substringBefore("&")
        } else {
            // Fallback untuk URL lama
            "" 
        }

        // Gunakan API detail dari source yang valid. 
        // Jika path ada, gunakan API baru (aoneroom), jika tidak, gunakan API lama (filmboom)
        val detailUrl = if (path.isNotEmpty()) {
            "$homeApiUrl/wefeed-h5api-bff/detail?detailPath=$path"
        } else {
            "$apiUrl/wefeed-h5-bff/web/subject/detail?subjectId=$id"
        }

        val document = app.get(detailUrl, headers = getBaseHeaders()).parsedSafe<MediaDetail>()?.data
        
        val subject = document?.subject
        val title = subject?.title ?: "Unknown"
        val poster = subject?.cover?.url ?: subject?.image?.url
        val tags = subject?.genre?.split(",")?.map { it.trim() }

        val year = subject?.releaseDate?.substringBefore("-")?.toIntOrNull()
        val tvType = if (subject?.subjectType == 1) TvType.Movie else TvType.TvSeries
        val description = subject?.description
        val trailer = subject?.trailer?.videoAddress?.url
        val score = Score.from10(subject?.imdbRatingValue?.toString()) 
        val actors = document?.stars?.mapNotNull { cast ->
            ActorData(
                Actor(
                    cast.name ?: return@mapNotNull null,
                    cast.avatarUrl
                ),
                roleString = cast.character
            )
        }?.distinctBy { it.actor }

        val recUrl = "$apiUrl/wefeed-h5-bff/web/subject/detail-rec?subjectId=$id&page=1&perPage=12"
        val recommendations = app.get(recUrl).parsedSafe<Media>()?.data?.items?.map {
             it.toSearchResponse(this)
        }

        if (tvType == TvType.TvSeries) {
            // Logika Episode: Coba ambil dari 'seasonList' (API Baru) atau 'resource' (API Lama)
            val episodeList = mutableListOf<Episode>()
            
            if (!document?.seasonList.isNullOrEmpty()) {
                // API BARU
                document?.seasonList?.forEach { season ->
                    val sNum = season.seasonNo ?: 1
                    season.episodeList?.forEach { ep ->
                        val epData = LoadData(id, sNum, ep.episodeNo, path).toJson()
                        episodeList.add(newEpisode(epData) {
                            this.name = ep.title
                            this.season = sNum
                            this.episode = ep.episodeNo
                            this.posterUrl = ep.cover?.url
                        })
                    }
                }
            } else {
                // API LAMA
                document?.resource?.seasons?.forEach { season ->
                    val sNum = season.se ?: 1
                    val eps = if (season.allEp.isNullOrEmpty()) (1..(season.maxEp ?: 1)).toList() else season.allEp.split(",").map { it.toInt() }
                    
                    eps.forEach { epNum ->
                        val epData = LoadData(id, sNum, epNum, subject?.detailPath).toJson()
                        episodeList.add(newEpisode(epData) {
                            this.season = sNum
                            this.episode = epNum
                        })
                    }
                }
            }

            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodeList) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.score = score
                this.actors = actors
                this.recommendations = recommendations
                addTrailer(trailer, addRaw = true)
            }
        } else {
            return newMovieLoadResponse(
                title,
                url,
                TvType.Movie,
                LoadData(id, 0, 0, subject?.detailPath ?: path).toJson()
            ) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.score = score
                this.actors = actors
                this.recommendations = recommendations
                addTrailer(trailer, addRaw = true)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val media = parseJson<LoadData>(data)
        
        // PENTING: Gunakan referer yang valid agar tidak 403
        val referer = "https://filmboom.top/spa/videoPlayPage/movies/${media.detailPath}?id=${media.id}&type=/movie/detail&lang=en"
        val playHeaders = mapOf(
            "authority" to "filmboom.top",
            "referer" to referer,
            "user-agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Mobile Safari/537.36",
            "x-client-info" to "{\"timezone\":\"Asia/Jayapura\"}"
        )

        val playUrl = "$apiUrl/wefeed-h5api-bff/subject/play?subjectId=${media.id}&se=${media.season ?: 0}&ep=${media.episode ?: 0}&detailPath=${media.detailPath}"
        
        val streams = app.get(playUrl, headers = playHeaders).parsedSafe<Media>()?.data?.streams

        streams?.reversed()?.distinctBy { it.url }?.map { source ->
            val qual = getQualityFromName(source.resolutions)
            // Fix ExtractorLink (Positional Argument)
            callback.invoke(
                ExtractorLink(
                    this.name,
                    "${this.name} ${source.format} ${source.resolutions}p",
                    source.url ?: return@map,
                    "https://filmboom.top/",
                    qual,
                    source.url.contains(".m3u8")
                )
            )
        }

        val id = streams?.firstOrNull()?.id
        val format = streams?.firstOrNull()?.format

        if (id != null
