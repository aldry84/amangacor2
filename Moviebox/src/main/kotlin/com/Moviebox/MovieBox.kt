package com.Moviebox

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.MainAPI

class MovieBox : MainAPI() {
    override var mainUrl = "https://moviebox.ph"
    override var name = "MovieBox.ph"
    override val hasMainPage = true
    override var lang = "id"
    
    // API URL Base
    private val apiUrl = "https://h5-api.aoneroom.com/wefeed-h5api-bff"

    private val headers = mapOf(
        "authority" to "h5-api.aoneroom.com",
        "accept" to "application/json",
        "accept-language" to "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7",
        // Token dari data curl kamu
        "authorization" to "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1aWQiOjE4MTM0MjU0MjgwMjM4ODc4MDAsImF0cCI6MywiZXh0IjoiMTc3MDQxMTA5MCIsImV4cCI6MTc3ODE4NzA5MCwiaWF0IjoxNzcwNDEwNzkwfQ.-kW86pGAJX6jheH_yEM8xfGd4rysJFR_hM3djl32nAo",
        "content-type" to "application/json",
        "origin" to "https://moviebox.ph",
        "referer" to "https://moviebox.ph/",
        "sec-ch-ua" to "\"Chromium\";v=\"137\", \"Not/A)Brand\";v=\"24\"",
        "sec-ch-ua-mobile" to "?1",
        "sec-ch-ua-platform" to "\"Android\"",
        "x-client-info" to "{\"timezone\":\"Asia/Jayapura\"}",
        "x-request-lang" to "en"
    )

    // =================================================================================
    // 1. HOME PAGE LOGIC
    // =================================================================================
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val homeSets = mutableListOf<HomePageList>()

        // A. Ambil Trending
        try {
            val trendingUrl = "$apiUrl/subject/trending?page=0&perPage=18"
            val trendingResponse = app.get(trendingUrl, headers = headers).parsedSafe<TrendingResponse>()
            
            trendingResponse?.data?.subjectList?.let { list ->
                val trendingMedia = list.mapNotNull { it.toSearchResponse() }
                if (trendingMedia.isNotEmpty()) {
                    homeSets.add(HomePageList("🔥 Trending Now", trendingMedia, isHorizontalImages = false))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // B. Ambil Home Standard
        try {
            val homeUrl = "$apiUrl/home?host=moviebox.ph"
            val homeResponse = app.get(homeUrl, headers = headers).parsedSafe<HomeResponse>()

            homeResponse?.data?.operatingList?.forEach { section ->
                val itemsToParse = ArrayList<Subject>()
                if (section.type == "BANNER" && section.banner?.items != null) {
                    itemsToParse.addAll(section.banner.items)
                } else if (section.subjects != null && section.subjects.isNotEmpty()) {
                    itemsToParse.addAll(section.subjects)
                }

                if (itemsToParse.isNotEmpty() && section.type != "CUSTOM" && section.type != "FILTER") {
                    val mediaList = itemsToParse.mapNotNull { it.toSearchResponse() }
                    if (mediaList.isNotEmpty()) {
                        homeSets.add(
                            HomePageList(
                                name = section.title ?: "Featured",
                                list = mediaList,
                                isHorizontalImages = section.type == "BANNER"
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return newHomePageResponse(homeSets)
    }

    // =================================================================================
    // 2. SEARCH FUNCTIONALITY (FIXED with POST)
    // =================================================================================
    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$apiUrl/subject/search?host=moviebox.ph"
        
        // Body JSON sesuai temuan di Termux
        val jsonBody = mapOf(
            "keyword" to query,
            "page" to 0,
            "perPage" to 20
        )

        return try {
            // Menggunakan POST request
            val response = app.post(url, headers = headers, json = jsonBody).parsedSafe<SearchDataResponse>()
            
            // Mapping data 'items' ke SearchResponse
            response?.data?.items?.mapNotNull { it.toSearchResponse() } ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    // Helper: Ubah data JSON 'Subject' jadi format Cloudstream
    private fun Subject.toSearchResponse(): SearchResponse? {
        val title = this.title ?: return null
        val urlDetail = this.detailPath ?: return null
        val poster = this.cover?.url ?: this.image?.url

        return newMovieSearchResponse(title, urlDetail, TvType.Movie) {
            this.posterUrl = poster
            this@toSearchResponse.imdbRatingValue?.trim()?.toDoubleOrNull()?.let { ratingVal ->
                this.score = Score.from10(ratingVal)
            }
        }
    }

    // =================================================================================
    // DATA CLASSES
    // =================================================================================
    
    // --- Home ---
    data class HomeResponse(@JsonProperty("data") val data: HomeData? = null)
    data class HomeData(@JsonProperty("operatingList") val operatingList: List<OperatingSection>? = null)
    data class OperatingSection(
        @JsonProperty("type") val type: String? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("banner") val banner: BannerObj? = null,
        @JsonProperty("subjects") val subjects: List<Subject>? = null
    )
    data class BannerObj(@JsonProperty("items") val items: List<Subject>? = null)

    // --- Trending ---
    data class TrendingResponse(@JsonProperty("data") val data: TrendingData? = null)
    data class TrendingData(@JsonProperty("subjectList") val subjectList: List<Subject>? = null)

    // --- Search (New!) ---
    data class SearchDataResponse(@JsonProperty("data") val data: SearchResultData? = null)
    data class SearchResultData(@JsonProperty("items") val items: List<Subject>? = null)

    // --- Core Object ---
    data class Subject(
        @JsonProperty("subjectId") val subjectId: String? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("cover") val cover: ImageObj? = null,
        @JsonProperty("image") val image: ImageObj? = null,
        @JsonProperty("detailPath") val detailPath: String? = null,
        @JsonProperty("imdbRatingValue") val imdbRatingValue: String? = null
    )

    data class ImageObj(@JsonProperty("url") val url: String? = null)
}
