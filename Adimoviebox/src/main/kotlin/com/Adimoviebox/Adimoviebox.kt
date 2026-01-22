package com.adimoviebox

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities

class Adimoviebox : MainAPI() {
    override var mainUrl = "https://moviebox.ph"
    // API Host Utama (Home & Detail)
    private val apiUrl = "https://h5-api.aoneroom.com"
    // API Host Player (Video)
    private val playUrl = "https://filmboom.top"
    
    override var name = "Adimoviebox"
    override val hasQuickSearch = false
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.AsianDrama,
        TvType.Anime,
        TvType.Others
    )

    // Token Auth (Masih menggunakan yang lama)
    private val myToken = "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1aWQiOjYwNjA4MTUzMzExNzY3Mjg0MzIsImF0cCI6MywiZXh0IjoiMTc2ODcxMjY4OCIsImV4cCI6MTc3NjQ4ODY4OCwiaWF0IjoxNzY4NzEyMzg4fQ.Q5b43M5wkYUZRqBhJIfnWVylfsmsaOg8_JxmNM2nyCM"

    // Header untuk Home & Detail
    private fun getBaseHeaders(): Map<String, String> {
        return mapOf(
            "authority" to "h5-api.aoneroom.com",
            "accept" to "application/json",
            "authorization" to myToken,
            "content-type" to "application/json",
            "origin" to mainUrl,
            "referer" to "$mainUrl/",
            "user-agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Mobile Safari/537.36",
            "x-client-info" to "{\"timezone\":\"Asia/Jayapura\"}",
            "x-request-lang" to "en"
        )
    }

    // Header Khusus Player (Domain filmboom.top)
    private fun getPlayHeaders(path: String, id: String, se: Int, ep: Int): Map<String, String> {
        return mapOf(
            "authority" to "filmboom.top",
            "accept" to "application/json",
            "origin" to "https://filmboom.top",
            "referer" to "https://filmboom.top/spa/videoPlayPage/movies/$path?id=$id&type=/movie/detail&detailSe=$se&detailEp=$ep&lang=en",
            "user-agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Mobile Safari/537.36",
            "x-client-info" to "{\"timezone\":\"Asia/Jayapura\"}"
        )
    }

    // --- MAIN PAGE & KATEGORI ---
    override val mainPage = mainPageOf(
        "$apiUrl/wefeed-h5api-bff/home?host=moviebox.ph" to "Home",
        "2|{\"classify\":\"All\",\"country\":\"Indonesia\",\"genre\":\"All\",\"sort\":\"Hottest\",\"year\":\"All\"}" to "Indo Film",
        "5|{\"country\":\"Indonesia\",\"genre\":\"All\",\"sort\":\"Hottest\",\"year\":\"All\"}" to "Indo Drama",
        "5|{\"country\":\"Korea\",\"genre\":\"All\",\"sort\":\"Hottest\",\"year\":\"All\"}" to "K-Drama",
        "2|{\"classify\":\"All\",\"country\":\"Indonesia\",\"genre\":\"Horror\",\"sort\":\"Hottest\",\"year\":\"All\"}" to "Horror Indo",
        "5|{\"classify\":\"All\",\"country\":\"All\",\"genre\":\"Anime\",\"sort\":\"Hottest\",\"year\":\"All\"}" to "Anime",
        "5|{\"classify\":\"All\",\"country\":\"China\",\"genre\":\"All\",\"sort\":\"Hottest\",\"year\":\"All\"}" to "C-Drama",
        "2|{\"classify\":\"All\",\"country\":\"United States\",\"genre\":\"All\",\"sort\":\"Hottest\",\"year\":\"All\"}" to "Hollywood Movie"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val pages = mutableListOf<HomePageList>()

        if (request.data.startsWith("http")) {
            // Home Page Logic
            val response = app.get(request.data, headers = getBaseHeaders()).parsedSafe<HomeResponse>()
            response?.data?.operatingList?.forEach { section ->
                val title = section.title ?: "Featured"
                val items = mutableListOf<SearchResponse>()
                section.subjects?.forEach { item -> items.add(item.toSearchResponse(this)) }
                section.banner?.items?.forEach { item -> items.add(item.toSearchResponse(this)) }
                section.customData?.items?.forEach { item -> 
                    val realItem = item.subject ?: item
                    items.add(realItem.toSearchResponse(this)) 
                }
                if (items.isNotEmpty()) pages.add(HomePageList(title, items))
            }
        } else {
            // Category/Filter Logic
            val parts = request.data.split("|")
            val filterUrl = "$apiUrl/wefeed-h5api-bff/home/movieFilter?tabId=${parts[0]}&filterType=${parts.getOrNull(1)}&pageNo=$page&pageSize=18"
            try {
                val response = app.get(filterUrl, headers = getBaseHeaders()).parsedSafe<FilterResponse>()
                val items = response?.data?.mapNotNull { it.toSearchResponse(this) } ?: emptyList()
                if (items.isNotEmpty()) pages.add(HomePageList(request.name, items, isHorizontal = false))
            } catch (e: Exception) { }
        }
        return HomePageResponse(pages, hasNext = !request.data.startsWith("http"))
    }

    // --- LOAD DETAIL (Saat film/series diklik) ---
    override suspend fun load(url: String): LoadResponse {
        // Parsing parameter internal
        val uri = java.net.URI(url)
        val params = uri.query.split("&").associate {
            val (key, value) = it.split("=")
            key to value
        }

        val id = params["id"] ?: ""
        val path = params["path"] ?: ""
        val typeStr = params["type"] ?: "2" // 1=Movie, 2=Series
        
        // Fetch API Detail untuk mendapatkan info Season & Episode
        val detailApiUrl = "$apiUrl/wefeed-h5api-bff/detail?detailPath=$path"
        val response = app.get(detailApiUrl, headers = getBaseHeaders()).parsedSafe<DetailResponse>()
        
        val data = response?.data
        val title = data?.title ?: "Unknown Title"
        val plot = data?.description
        val poster = data?.cover?.url ?: data?.image?.url
        val rating = data?.imdbRatingValue?.toDoubleOrNull()?.times(100)?.toInt()

        // Logic membedakan Movie dan Series
        if (typeStr == "1") {
            // MOVIE: Season 0, Episode 0
            return newMovieLoadResponse(title, url, TvType.Movie, LinkData(id, path, 0, 0).toJson()) {
                this.posterUrl = poster
                this.plot = plot
                this.rating = rating
            }
        } else {
            // SERIES: Parsing List Season dan Episode
            val episodes = mutableListOf<Episode>()
            
            // Cek struktur 'seasonList' (ini tebakan umum untuk API tipe ini, karena JSON detail belum terlihat)
            // Jika kosong, kita buat dummy episode agar user tetap bisa play (biasanya S1 E1)
            if (data?.seasonList.isNullOrEmpty()) {
                // Fallback jika parsing gagal: Anggap 1 Season, 1 Episode (atau banyak episode flat)
                val totalEp = data?.episodesCount ?: 1
                for (i in 1..totalEp) {
                     episodes.add(Episode(
                        data = LinkData(id, path, 1, i).toJson(),
                        name = "Episode $i",
                        season = 1,
                        episode = i
                    ))
                }
            } else {
                // Parsing struktur Season -> Episode
                data?.seasonList?.forEach { season ->
                    val seasonNum = season.seasonNo ?: 1
                    season.episodeList?.forEach { ep ->
                        episodes.add(Episode(
                            data = LinkData(id, path, seasonNum, ep.episodeNo ?: 1).toJson(),
                            name = "E${ep.episodeNo} - ${ep.title ?: ""}",
                            season = seasonNum,
                            episode = ep.episodeNo,
                            posterUrl = ep.cover?.url
                        ))
                    }
                }
            }

            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = plot
                this.rating = rating
            }
        }
    }

    // --- LOAD LINKS (Saat tombol play diklik) ---
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val linkData = parseJson<LinkData>(data)
        
        // Request Play dengan parameter Season (se) dan Episode (ep) yang benar
        val playApiUrl = "$playUrl/wefeed-h5api-bff/subject/play?subjectId=${linkData.id}&se=${linkData.se}&ep=${linkData.ep}&detailPath=${linkData.path}"
        
        val response = app.get(playApiUrl, headers = getPlayHeaders(linkData.path, linkData.id, linkData.se, linkData.ep)).parsedSafe<PlayResponse>()

        response?.data?.streams?.forEach { stream ->
            val qualityInt = stream.resolutions?.toIntOrNull() ?: Qualities.Unknown.value
            callback.invoke(
                ExtractorLink(
                    this.name,
                    "${this.name} ${stream.format} ${stream.resolutions}p",
                    stream.url ?: return@forEach,
                    referer = "https://filmboom.top/",
                    quality = qualityInt,
                    isM3u8 = stream.url.contains(".m3u8")
                )
            )
        }
        return true
    }

    // --- DATA CLASSES ---

    data class LinkData(val id: String, val path: String, val se: Int, val ep: Int)

    data class HomeResponse(@JsonProperty("data") val data: DataObj? = null)
    data class FilterResponse(@JsonProperty("data") val data: List<SubjectItem>? = null)
    
    // Struktur Detail (Disesuaikan untuk support Series)
    data class DetailResponse(@JsonProperty("data") val data: SubjectDetail? = null)
    data class SubjectDetail(
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("description") val description: String? = null,
        @JsonProperty("cover") val cover: CoverObj? = null,
        @JsonProperty("image") val image: CoverObj? = null,
        @JsonProperty("imdbRatingValue") val imdbRatingValue: String? = null,
        @JsonProperty("episodesCount") val episodesCount: Int? = null,
        // Struktur Series: Season List -> Episode List
        @JsonProperty("seasonList") val seasonList: List<SeasonObj>? = null 
    )
    data class SeasonObj(
        @JsonProperty("seasonNo") val seasonNo: Int? = null,
        @JsonProperty("episodeList") val episodeList: List<EpisodeObj>? = null
    )
    data class EpisodeObj(
        @JsonProperty("episodeNo") val episodeNo: Int? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("cover") val cover: CoverObj? = null
    )

    // Struktur Play Video (Sesuai Log terbaru)
    data class PlayResponse(@JsonProperty("data") val data: PlayData? = null)
    data class PlayData(@JsonProperty("streams") val streams: List<StreamItem>? = null)
    data class StreamItem(
        @JsonProperty("format") val format: String? = null, // "MP4"
        @JsonProperty("url") val url: String? = null,
        @JsonProperty("resolutions") val resolutions: String? = null // "1080", "480"
    )

    // Helper Objects (Home & Search)
    data class DataObj(@JsonProperty("operatingList") val operatingList: List<OperatingItem>? = null)
    data class OperatingItem(
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("subjects") val subjects: List<SubjectItem>? = null,
        @JsonProperty("banner") val banner: BannerObj? = null,
        @JsonProperty("customData") val customData: CustomDataObj? = null
    )
    data class BannerObj(@JsonProperty("items") val items: List<SubjectItem>? = null)
    data class CustomDataObj(@JsonProperty("items") val items: List<CustomItem>? = null)
    
    // CustomItem wrapper
    data class CustomItem(
        @JsonProperty("subject") val subject: SubjectItem? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("image") val image: CoverObj? = null,
        @JsonProperty("subjectId") val subjectId: String? = null
    ) {
        fun toSearchResponse(provider: Adimoviebox): SearchResponse {
            val finalId = subject?.subjectId ?: subjectId ?: "0"
            val finalPath = subject?.detailPath ?: ""
            val finalTitle = subject?.title ?: title ?: ""
            val finalImage = image?.url
            
            // Default ke Movie jika tidak ada info
            val url = "${provider.mainUrl}/detail?id=$finalId&path=$finalPath&type=1"
            
            return provider.newMovieSearchResponse(finalTitle, url, TvType.Others) {
                this.posterUrl = finalImage
            }
        }
    }

    data class SubjectItem(
        @JsonProperty("subjectId") val subjectId: String? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("cover") val cover: CoverObj? = null,
        @JsonProperty("image") val image: CoverObj? = null,
        @JsonProperty("description") val description: String? = null,
        @JsonProperty("imdbRatingValue") val imdbRatingValue: String? = null,
        @JsonProperty("subjectType") val subjectType: Int? = null,
        @JsonProperty("detailPath") val detailPath: String? = null
    ) {
        fun toSearchResponse(provider: Adimoviebox): SearchResponse {
            val type = if (subjectType == 1) TvType.Movie else TvType.TvSeries
            // URL internal untuk parsing di load()
            val url = "${provider.mainUrl}/detail?id=$subjectId&path=$detailPath&type=$subjectType"
            val posterImage = cover?.url ?: image?.url

            return provider.newMovieSearchResponse(title ?: "", url, type) {
                this.posterUrl = posterImage
                this.plot = description
                this.rating = imdbRatingValue?.toDoubleOrNull()?.times(10)?.toInt()
            }
        }
    }
    data class CoverObj(@JsonProperty("url") val url: String? = null)
}
