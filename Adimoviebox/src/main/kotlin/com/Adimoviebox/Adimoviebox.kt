package com.Adimoviebox

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
// Import penting yang diminta
import com.lagradost.cloudstream3.utils.newExtractorLink

class Adimoviebox : MainAPI() {
    override var mainUrl = "https://moviebox.ph"
    private val apiUrl = "https://h5-api.aoneroom.com"
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

    private val myToken = "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1aWQiOjYwNjA4MTUzMzExNzY3Mjg0MzIsImF0cCI6MywiZXh0IjoiMTc2ODcxMjY4OCIsImV4cCI6MTc3NjQ4ODY4OCwiaWF0IjoxNzY4NzEyMzg4fQ.Q5b43M5wkYUZRqBhJIfnWVylfsmsaOg8_JxmNM2nyCM"

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
            val response = app.get(request.data, headers = getBaseHeaders()).parsedSafe<HomeResponse>()
            response?.data?.operatingList?.forEach { section ->
                val title = section.title ?: "Featured"
                val items = mutableListOf<SearchResponse>()
                
                section.subjects?.forEach { items.add(it.toSearchResponse(this)) }
                section.banner?.items?.forEach { items.add(it.toSearchResponse(this)) }
                
                // PERBAIKAN: Mapping manual dari CustomItem ke SubjectItem
                section.customData?.items?.forEach { item -> 
                    val subjectItem = item.subject ?: SubjectItem(
                        subjectId = item.subjectId,
                        title = item.title,
                        image = item.image,
                        cover = item.image
                    )
                    items.add(subjectItem.toSearchResponse(this))
                }
                
                if (items.isNotEmpty()) {
                    pages.add(HomePageList(title, items))
                }
            }
        } else {
            val parts = request.data.split("|")
            val filterUrl = "$apiUrl/wefeed-h5api-bff/home/movieFilter?tabId=${parts[0]}&filterType=${parts.getOrNull(1)}&pageNo=$page&pageSize=18"
            try {
                val response = app.get(filterUrl, headers = getBaseHeaders()).parsedSafe<FilterResponse>()
                val items = response?.data?.map { it.toSearchResponse(this) } ?: emptyList()
                if (items.isNotEmpty()) {
                    pages.add(HomePageList(request.name, items))
                }
            } catch (e: Exception) { }
        }
        
        return newHomePageResponse(pages, hasNext = !request.data.startsWith("http"))
    }

    override suspend fun load(url: String): LoadResponse {
        val uri = java.net.URI(url)
        val params = uri.query.split("&").associate {
            val (key, value) = it.split("=")
            key to value
        }

        val id = params["id"] ?: ""
        val path = params["path"] ?: ""
        val typeStr = params["type"] ?: "2"
        
        val detailApiUrl = "$apiUrl/wefeed-h5api-bff/detail?detailPath=$path"
        val response = app.get(detailApiUrl, headers = getBaseHeaders()).parsedSafe<DetailResponse>()
        
        val data = response?.data
        val title = data?.title ?: "Unknown Title"
        val desc = data?.description
        val poster = data?.cover?.url ?: data?.image?.url
        val ratingScore = Score.from10(data?.imdbRatingValue)

        if (typeStr == "1") {
            return newMovieLoadResponse(title, url, TvType.Movie, LinkData(id, path, 0, 0).toJson()) {
                this.posterUrl = poster
                this.plot = desc
                this.score = ratingScore
            }
        } else {
            val episodes = mutableListOf<Episode>()
            
            if (data?.seasonList.isNullOrEmpty()) {
                val totalEp = data?.episodesCount ?: 1
                for (i in 1..totalEp) {
                     episodes.add(newEpisode(LinkData(id, path, 1, i).toJson()) {
                        this.name = "Episode $i"
                        this.season = 1
                        this.episode = i
                    })
                }
            } else {
                data?.seasonList?.forEach { season ->
                    val seasonNum = season.seasonNo ?: 1
                    season.episodeList?.forEach { ep ->
                        episodes.add(newEpisode(LinkData(id, path, seasonNum, ep.episodeNo ?: 1).toJson()) {
                            this.name = "E${ep.episodeNo} - ${ep.title ?: ""}"
                            this.season = seasonNum
                            this.episode = ep.episodeNo
                            this.posterUrl = ep.cover?.url
                        })
                    }
                }
            }

            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = desc
                this.score = ratingScore
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val linkData = parseJson<LinkData>(data)
        val playApiUrl = "$playUrl/wefeed-h5api-bff/subject/play?subjectId=${linkData.id}&se=${linkData.se}&ep=${linkData.ep}&detailPath=${linkData.path}"
        
        val response = app.get(playApiUrl, headers = getPlayHeaders(linkData.path, linkData.id, linkData.se, linkData.ep)).parsedSafe<PlayResponse>()

        response?.data?.streams?.forEach { stream ->
            val qualityInt = stream.resolutions?.toIntOrNull() ?: Qualities.Unknown.value
            callback.invoke(
                // Menggunakan newExtractorLink karena sudah diimport
                newExtractorLink(
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
    
    data class DetailResponse(@JsonProperty("data") val data: SubjectDetail? = null)
    data class SubjectDetail(
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("description") val description: String? = null,
        @JsonProperty("cover") val cover: CoverObj? = null,
        @JsonProperty("image") val image: CoverObj? = null,
        @JsonProperty("imdbRatingValue") val imdbRatingValue: String? = null,
        @JsonProperty("episodesCount") val episodesCount: Int? = null,
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

    data class PlayResponse(@JsonProperty("data") val data: PlayData? = null)
    data class PlayData(@JsonProperty("streams") val streams: List<StreamItem>? = null)
    data class StreamItem(
        @JsonProperty("format") val format: String? = null,
        @JsonProperty("url") val url: String? = null,
        @JsonProperty("resolutions") val resolutions: String? = null
    )

    data class DataObj(@JsonProperty("operatingList") val operatingList: List<OperatingItem>? = null)
    data class OperatingItem(
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("subjects") val subjects: List<SubjectItem>? = null,
        @JsonProperty("banner") val banner: BannerObj? = null,
        @JsonProperty("customData") val customData: CustomDataObj? = null
    )
    data class BannerObj(@JsonProperty("items") val items: List<SubjectItem>? = null)
    data class CustomDataObj(@JsonProperty("items") val items: List<CustomItem>? = null)
    
    data class CustomItem(
        @JsonProperty("subject") val subject: SubjectItem? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("image") val image: CoverObj? = null,
        @JsonProperty("subjectId") val subjectId: String? = null,
        @JsonProperty("detailPath") val detailPath: String? = null
    )

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
            val id = subjectId ?: "0"
            val type = if (subjectType == 1) TvType.Movie else TvType.TvSeries
            val path = detailPath ?: ""
            // URL internal
            val url = "${provider.mainUrl}/detail?id=$id&path=$path&type=$subjectType"
            val posterImage = cover?.url ?: image?.url

            return provider.newMovieSearchResponse(title ?: "", url, type) {
                this.posterUrl = posterImage
                // Gunakan properti standar
            }
        }
    }
    data class CoverObj(@JsonProperty("url") val url: String? = null)
}
