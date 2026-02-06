package com.Moviebox

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.LoadResponse.Companion.addScore
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.mapper
import java.net.URLDecoder

class MovieBox : MainAPI() {
    override var name = "MovieBox"
    override var mainUrl = "https://123movienow.cc"
    private val apiUrl = "https://h5-api.aoneroom.com/wefeed-h5api-bff"
    override var lang = "en"
    
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    // FIX 1: Wajib definisikan mainPage agar CloudStream meload halaman
    override val mainPage = mainPageOf(
        "$apiUrl/home?host=moviebox.ph" to "Home"
    )

    private val standardHeaders = mapOf(
        "Origin" to mainUrl,
        "Referer" to "$mainUrl/",
        "x-client-info" to "{\"timezone\":\"Asia/Jakarta\"}",
        "x-request-lang" to "en",
        "User-Agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Mobile Safari/537.36"
    )

    private val customCategories = listOf(
        Pair("Movie Trending", "8821254238245470240"),
        Pair("Indonesian Movies", "6528093688173053896"),
        Pair("Indonesian Drama", "5283462032510044280"),
        Pair("Horror Indo", "5848753831881965888"),
        Pair("Komedi Horror", "3528002473103362040"),
        Pair("Drakor", "4380734070238626200"),
        Pair("Drama Ahok", "8624142774394406504")
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

    // --- 1. HOME PAGE ---
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val headers = standardHeaders + getAuthHeader()
        val homeItems = ArrayList<HomePageList>()

        // 1. Default Home
        try {
            // Menggunakan request.data (yang isinya URL dari mainPage variabel di atas)
            val response = app.get(request.data, headers = headers).parsedSafe<HomeResponse>()
            response?.data?.sections?.forEach { section ->
                if (!section.items.isNullOrEmpty() && section.title != null) {
                    val list = section.items.map { item ->
                        item.toSearchResponse()
                    }
                    homeItems.add(HomePageList(section.title, list))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Custom Categories
        customCategories.forEach { (catName, catId) ->
            try {
                val rankingUrl = "$apiUrl/subject/ranking-list?id=$catId&page=0&perPage=20"
                val response = app.get(rankingUrl, headers = headers).parsedSafe<SearchApiResponse>()
                
                // Gunakan elvis operator ?: emptyList() agar aman
                val list = response?.data?.list?.map { item ->
                    item.toSearchResponse()
                } ?: emptyList()

                if (list.isNotEmpty()) {
                    homeItems.add(HomePageList(catName, list))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return newHomePageResponse(homeItems)
    }

    // --- 2. SEARCH ---
    override suspend fun search(query: String): List<SearchResponse> {
        val headers = standardHeaders + getAuthHeader()
        val url = "$apiUrl/subject/search-suggest"
        val jsonBody = mapOf("keyword" to query, "perPage" to 20)

        val response = app.post(url, headers = headers, json = jsonBody).parsedSafe<SearchApiResponse>()

        return response?.data?.list?.map { item ->
            item.toSearchResponse()
        } ?: emptyList()
    }

    // --- 3. LOAD DETAIL ---
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

    // --- 4. LOAD LINKS ---
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

    // --- HELPER FUNCTIONS ---

    // FIX 2: Helper ini sekarang menangani CoverObj dengan benar
    private fun MovieItem.toSearchResponse(): SearchResponse {
        val slug = this.title?.replace("[^a-zA-Z0-9-]".toRegex(), "-") ?: "video"
        return newMovieSearchResponse(
            name = this.title ?: "Unknown",
            url = "$mainUrl/movie/${this.id}/$slug",
            type = if (this.category == 2 || this.domain == 2) TvType.TvSeries else TvType.Movie,
        ) {
            // PENTING: Ambil URL dari object CoverObj, bukan string langsung
            this.posterUrl = this@toSearchResponse.cover?.url 
            this.year = this@toSearchResponse.year
        }
    }

    private fun Any.toJson(): String {
        return mapper.writeValueAsString(this)
    }
    
    private inline fun <reified T> parseJson(json: String): T {
        return mapper.readValue(json, T::class.java)
    }

    // --- DATA CLASSES ---

    data class HomeResponse(@JsonProperty("data") val data: HomeData?)
    data class HomeData(@JsonProperty("sections") val sections: List<HomeSection>?)
    data class HomeSection(@JsonProperty("title") val title: String?, @JsonProperty("items") val items: List<MovieItem>?)

    data class SearchApiResponse(@JsonProperty("data") val data: SearchDataList?)
    data class SearchDataList(@JsonProperty("list") val list: List<MovieItem>?)

    // FIX 3: cover diubah jadi CoverObj? (bukan String?)
    // Karena di Home/Search/Detail, JSON-nya mengembalikan objek { "url": "...", ... }
    data class MovieItem(
        @JsonProperty("id") val id: String?,
        @JsonProperty("title") val title: String?,
        @JsonProperty("cover") val cover: CoverObj?, // Ubah String? menjadi CoverObj?
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

    // Data class untuk Cover Image yang berisi URL
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
