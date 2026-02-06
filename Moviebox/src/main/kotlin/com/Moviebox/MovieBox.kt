package com.Moviebox

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.LoadResponse.Companion.addScore
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.mapper // Import mapper global
import java.net.URLDecoder

class MovieBox : MainAPI() {
    // FIX: Gunakan 'var' sesuai definisi di MainAPI.kt
    override var name = "MovieBox"
    override var mainUrl = "https://123movienow.cc"
    private val apiUrl = "https://h5-api.aoneroom.com/wefeed-h5api-bff"
    override var lang = "en"
    
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    private val standardHeaders = mapOf(
        "Origin" to mainUrl,
        "Referer" to "$mainUrl/",
        "x-client-info" to "{\"timezone\":\"Asia/Jakarta\"}",
        "x-request-lang" to "en",
        "User-Agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Mobile Safari/537.36"
    )

    private var authToken: String? = null

    private suspend fun getAuthHeader(): Map<String, String> {
        if (authToken == null) {
            try {
                val response = app.get("https://moviebox.ph/")
                val cookieToken = response.cookies["mb_token"]
                if (!cookieToken.isNullOrEmpty()) {
                    authToken = URLDecoder.decode(cookieToken, "UTF-8").trim('"')
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return if (authToken != null) mapOf("Authorization" to "Bearer $authToken") else mapOf()
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val headers = standardHeaders + getAuthHeader()
        val response = app.get("$apiUrl/home?host=moviebox.ph", headers = headers).parsedSafe<HomeResponse>()
        val homeItems = ArrayList<HomePageList>()

        response?.data?.sections?.forEach { section ->
            if (!section.items.isNullOrEmpty() && section.title != null) {
                val list = section.items.map { item ->
                    val slug = item.title?.replace("[^a-zA-Z0-9-]".toRegex(), "-") ?: "video"
                    newMovieSearchResponse(
                        name = item.title ?: "Unknown",
                        url = "$mainUrl/movie/${item.id}/$slug",
                        type = if (item.category == 2) TvType.TvSeries else TvType.Movie,
                    ) {
                        this.posterUrl = item.cover
                    }
                }
                homeItems.add(HomePageList(section.title, list))
            }
        }
        return newHomePageResponse(homeItems)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val headers = standardHeaders + getAuthHeader()
        val url = "$apiUrl/subject/search-suggest"
        val jsonBody = mapOf("keyword" to query, "perPage" to 20)

        val response = app.post(url, headers = headers, json = jsonBody).parsedSafe<SearchApiResponse>()

        return response?.data?.list?.map { item ->
            newMovieSearchResponse(
                name = item.title ?: "",
                url = "$mainUrl/movie/${item.id}/search",
                type = if (item.domain == 2) TvType.TvSeries else TvType.Movie,
            ) {
                this.posterUrl = item.cover
                this.year = item.year
            }
        } ?: emptyList()
    }

    override suspend fun load(url: String): LoadResponse? {
        val headers = standardHeaders + getAuthHeader()
        
        val pathSegments = url.split("/")
        val numericId = pathSegments.getOrNull(pathSegments.size - 2)
        val rawSlug = pathSegments.lastOrNull()
        
        val lookupId = if (rawSlug == "search" && numericId != null) numericId else rawSlug
        val detailUrl = "$apiUrl/detail?detailPath=$lookupId"
        
        val responseText = app.get(detailUrl, headers = headers).text
        val response = parseJson<DetailResponse>(responseText)
        val subject = response.data?.subject ?: return null

        val title = subject.title ?: "Unknown"
        val poster = subject.cover?.url
        val desc = subject.description
        val realId = subject.id 
        val detailPath = subject.detailPath ?: lookupId.toString()
        val year = subject.releaseDate?.take(4)?.toIntOrNull()
        val trailerUrl = subject.trailer?.videoAddress?.url
        val rating = subject.imdbRatingValue

        val actorsList = subject.stars?.map { star ->
            Actor(star.name ?: "Unknown", star.avatarUrl) to star.character
        }

        val recommendations = subject.dubs?.map { dub ->
            newMovieSearchResponse(
                dub.lanName ?: "Unknown",
                "$mainUrl/movie/${dub.subjectId}/${dub.detailPath}",
                TvType.Movie
            ) {
                this.posterUrl = poster
            }
        }

        if (subject.type == 2) { 
            val episodes = ArrayList<Episode>()
            subject.episodeList?.forEach { season ->
                season.list?.forEach { ep ->
                    episodes.add(
                        newEpisode(
                            mapOf("subjectId" to realId, "se" to season.sort, "ep" to ep.sort, "detailPath" to detailPath).toJson(),
                        ) {
                            this.name = ep.title
                            this.season = season.sort
                            this.episode = ep.sort
                            this.posterUrl = ep.cover?.url ?: poster
                        }
                    )
                }
            }
            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = desc
                this.year = year
                this.recommendations = recommendations
                addScore(rating)
                if (trailerUrl != null) addTrailer(trailerUrl)
                if (actorsList != null) addActors(actorsList)
            }
        } else {
            return newMovieLoadResponse(title, url, TvType.Movie, 
                mapOf("subjectId" to realId, "se" to 0, "ep" to 0, "detailPath" to detailPath).toJson()
            ) {
                this.posterUrl = poster
                this.plot = desc
                this.year = year
                this.recommendations = recommendations
                addScore(rating)
                if (trailerUrl != null) addTrailer(trailerUrl)
                if (actorsList != null) addActors(actorsList)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val headers = standardHeaders + getAuthHeader()
        val params = parseJson<Map<String, Any>>(data)
        
        val subjectId = params["subjectId"].toString()
        val se = params["se"].toString().toDoubleOrNull()?.toInt() ?: 0
        val ep = params["ep"].toString().toDoubleOrNull()?.toInt() ?: 0
        val detailPath = params["detailPath"].toString()

        val playApiUrl = "$mainUrl/wefeed-h5api-bff/subject/play?subjectId=$subjectId&se=$se&ep=$ep&detailPath=$detailPath"
        
        val response = app.get(playApiUrl, headers = headers).parsedSafe<PlayResponse>()
        val playData = response?.data

        playData?.streams?.forEach { stream ->
            if (!stream.url.isNullOrEmpty()) {
                val quality = stream.resolutions?.toIntOrNull() ?: Qualities.Unknown.value
                val type = if (stream.format?.contains("HLS", true) == true) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO

                // FIX: Menggunakan pola Builder untuk newExtractorLink
                // Parameter 'referer' dan 'quality' dipindahkan ke dalam lambda initializer
                callback.invoke(
                    newExtractorLink(
                        source = this.name,
                        name = this.name,
                        url = stream.url,
                        type = type
                    ) {
                        this.referer = "$mainUrl/"
                        this.quality = quality
                    }
                )
            }
        }
        return true
    }

    // FIX: Menggunakan mapper global dari com.lagradost.cloudstream3.mapper
    private fun Any.toJson(): String {
        return mapper.writeValueAsString(this)
    }
    
    // Helper untuk parse JSON lokal
    private inline fun <reified T> parseJson(json: String): T {
        return mapper.readValue(json, T::class.java)
    }

    // --- DATA CLASSES ---

    data class HomeResponse(@JsonProperty("data") val data: HomeData?)
    data class HomeData(@JsonProperty("sections") val sections: List<HomeSection>?)
    data class HomeSection(@JsonProperty("title") val title: String?, @JsonProperty("items") val items: List<MovieItem>?)

    data class SearchApiResponse(@JsonProperty("data") val data: SearchDataList?)
    data class SearchDataList(@JsonProperty("list") val list: List<MovieItem>?)

    data class MovieItem(
        @JsonProperty("id") val id: String?,
        @JsonProperty("title") val title: String?,
        @JsonProperty("cover") val cover: String?, 
        @JsonProperty("category") val category: Int?,
        @JsonProperty("domain") val domain: Int?, 
        @JsonProperty("year") val year: Int?
    )

    data class DetailResponse(@JsonProperty("data") val data: DetailDataWrapper?)
    data class DetailDataWrapper(@JsonProperty("subject") val subject: SubjectData?)

    data class SubjectData(
        @JsonProperty("subjectId") val id: String?,
        @JsonProperty("title") val title: String?,
        @JsonProperty("description") val description: String?,
        @JsonProperty("cover") val cover: CoverObj?, 
        @JsonProperty("subjectType") val type: Int?, 
        @JsonProperty("detailPath") val detailPath: String?,
        @JsonProperty("releaseDate") val releaseDate: String?,
        @JsonProperty("imdbRatingValue") val imdbRatingValue: String?,
        @JsonProperty("trailer") val trailer: TrailerObj?,
        @JsonProperty("dubs") val dubs: List<DubItem>?,
        @JsonProperty("stars") val stars: List<StarItem>?,
        @JsonProperty("episodeList") val episodeList: List<SeasonList>?
    )

    data class CoverObj(@JsonProperty("url") val url: String?)
    data class TrailerObj(@JsonProperty("videoAddress") val videoAddress: VideoAddr?)
    data class VideoAddr(@JsonProperty("url") val url: String?)
    data class DubItem(@JsonProperty("subjectId") val subjectId: String?, @JsonProperty("lanName") val lanName: String?, @JsonProperty("detailPath") val detailPath: String?)
    
    data class StarItem(
        @JsonProperty("name") val name: String?,
        @JsonProperty("character") val character: String?,
        @JsonProperty("avatarUrl") val avatarUrl: String?
    )

    data class SeasonList(@JsonProperty("sort") val sort: Int?, @JsonProperty("list") val list: List<EpisodeItem>?)
    data class EpisodeItem(@JsonProperty("id") val id: String?, @JsonProperty("title") val title: String?, @JsonProperty("sort") val sort: Int?, @JsonProperty("cover") val cover: CoverObj?)

    data class PlayResponse(@JsonProperty("data") val data: PlayData?)
    data class PlayData(@JsonProperty("streams") val streams: List<StreamItem>?)
    data class StreamItem(@JsonProperty("url") val url: String?, @JsonProperty("resolutions") val resolutions: String?, @JsonProperty("format") val format: String?)
}
