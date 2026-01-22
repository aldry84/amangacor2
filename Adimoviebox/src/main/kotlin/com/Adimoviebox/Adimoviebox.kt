package com.adimoviebox

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.nicehttp.NiceResponse

class Adimoviebox : MainAPI() {
    override var mainUrl = "https://moviebox.ph"
    // API Host baru
    private val apiUrl = "https://h5-api.aoneroom.com"
    override var name = "Adimoviebox"
    
    // Matikan QuickSearch karena butuh log endpoint search baru
    override val hasQuickSearch = false 
    
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.AsianDrama,
        TvType.Anime,
        TvType.Others // Untuk konten seperti Ruangguru/Course
    )

    // Token Authorization (HARDCODED dari log kamu)
    // PENTING: Jika nanti error/blank, token ini yang harus diganti dengan yang baru.
    private val myToken = "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1aWQiOjYwNjA4MTUzMzExNzY3Mjg0MzIsImF0cCI6MywiZXh0IjoiMTc2ODcxMjY4OCIsImV4cCI6MTc3NjQ4ODY4OCwiaWF0IjoxNzY4NzEyMzg4fQ.Q5b43M5wkYUZRqBhJIfnWVylfsmsaOg8_JxmNM2nyCM"

    private fun getHeaders(): Map<String, String> {
        return mapOf(
            "authority" to "h5-api.aoneroom.com",
            "accept" to "application/json",
            "accept-language" to "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7",
            "authorization" to myToken,
            "content-type" to "application/json",
            "origin" to mainUrl,
            "referer" to "$mainUrl/",
            "sec-ch-ua" to "\"Chromium\";v=\"137\", \"Not/A)Brand\";v=\"24\"",
            "sec-ch-ua-mobile" to "?0",
            "sec-ch-ua-platform" to "\"Linux\"",
            "user-agent" to "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36",
            "x-client-info" to "{\"timezone\":\"Asia/Jayapura\"}",
            "x-request-lang" to "en"
        )
    }

    // Definisi Halaman Utama & Kategori
    // Data kedua (String) berisi format: "tabId|filterTypeJson"
    override val mainPage = mainPageOf(
        "$apiUrl/wefeed-h5api-bff/home?host=moviebox.ph" to "Home",
        "2|{\"classify\":\"All\",\"country\":\"Indonesia\",\"genre\":\"All\",\"sort\":\"Hottest\",\"year\":\"All\"}" to "Indo Film",
        "5|{\"country\":\"Indonesia\",\"genre\":\"All\",\"sort\":\"Hottest\",\"year\":\"All\"}" to "Indo Drama",
        "5|{\"country\":\"Korea\",\"genre\":\"All\",\"sort\":\"Hottest\",\"year\":\"All\"}" to "K-Drama",
        "2|{\"classify\":\"All\",\"country\":\"Indonesia\",\"genre\":\"Horror\",\"sort\":\"Hottest\",\"year\":\"All\"}" to "Horror Indo",
        "5|{\"classify\":\"All\",\"country\":\"All\",\"genre\":\"Anime\",\"sort\":\"Hottest\",\"year\":\"All\"}" to "Anime",
        "5|{\"classify\":\"All\",\"country\":\"China\",\"genre\":\"All\",\"sort\":\"Hottest\",\"year\":\"All\"}" to "C-Drama",
        "2|{\"classify\":\"All\",\"country\":\"United States\",\"genre\":\"All\",\"sort\":\"Hottest\",\"year\":\"All\"}" to "Hollywood Movie",
        "5|{\"classify\":\"All\",\"country\":\"United States\",\"genre\":\"All\",\"sort\":\"Hottest\",\"year\":\"All\"}" to "Western Series"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val pages = mutableListOf<HomePageList>()

        if (request.data.startsWith("http")) {
            // --- Logic untuk Halaman HOME (URL langsung) ---
            val response = app.get(request.data, headers = getHeaders()).parsedSafe<HomeResponse>()
                ?: throw ErrorLoadingException("Gagal mengambil data Home")

            response.data?.operatingList?.forEach { section ->
                val title = section.title ?: "Featured"
                val items = mutableListOf<SearchResponse>()

                // 1. Ambil dari 'subjects' (List film biasa)
                section.subjects?.forEach { item -> items.add(item.toSearchResponse(this)) }

                // 2. Ambil dari 'banner' (Slider atas)
                section.banner?.items?.forEach { item -> items.add(item.toSearchResponse(this)) }

                // 3. Ambil dari 'customData' (Contoh: Ruangguru, Course)
                section.customData?.items?.forEach { item ->
                    // Custom item kadang punya object 'subject' di dalamnya
                    val realItem = item.subject ?: item
                    items.add(realItem.toSearchResponse(this))
                }

                if (items.isNotEmpty()) {
                    pages.add(HomePageList(title, items))
                }
            }
        } else {
            // --- Logic untuk KATEGORI (Indo Film, K-Drama, dll) ---
            // Format request.data: "tabId|filterTypeJson"
            val parts = request.data.split("|")
            val tabId = parts[0]
            val filterJson = parts.getOrNull(1) ?: "{}"
            
            // Construct URL Filter
            // Endpoint ini TEBAKAN berdasarkan pola log 'type=/home/movieFilter'. 
            // Jika error, berarti endpointnya beda.
            val filterUrl = "$apiUrl/wefeed-h5api-bff/home/movieFilter?tabId=$tabId&filterType=$filterJson&pageNo=$page&pageSize=18"
            
            // Note: Response filter biasanya list langsung atau ada di dalam 'data'
            // Kita coba parse sebagai HomeResponse dulu karena biasanya strukturnya mirip
            try {
                val response = app.get(filterUrl, headers = getHeaders()).parsedSafe<FilterResponse>()
                
                val items = response?.data?.mapNotNull { it.toSearchResponse(this) } ?: emptyList()
                
                if (items.isNotEmpty()) {
                    pages.add(HomePageList(request.name, items, isHorizontal = false))
                }
            } catch (e: Exception) {
                throw ErrorLoadingException("Gagal memuat kategori. Coba refresh atau cek log.")
            }
        }

        return HomePageResponse(pages, hasNext = !request.data.startsWith("http"))
    }

    override suspend fun load(url: String): LoadResponse {
        // PERINGATAN:
        // Fitur ini (Buka Detail Film) masih menggunakan data DUMMY.
        // Karena saya belum menerima log endpoint detail (saat klik film).
        // Kamu harus kirim log baru jika ingin fitur ini jalan normal.
        
        throw ErrorLoadingException("Butuh Log Baru! Silakan klik film, lalu kirim Network Log endpoint '/detail' atau '/episode'.")
    }

    // --- JSON Classes ---

    data class HomeResponse(
        @JsonProperty("code") val code: Int? = null,
        @JsonProperty("data") val data: DataObj? = null
    )

    data class FilterResponse(
        @JsonProperty("code") val code: Int? = null,
        @JsonProperty("data") val data: List<SubjectItem>? = null
    )

    data class DataObj(
        @JsonProperty("operatingList") val operatingList: List<OperatingItem>? = null
    )

    data class OperatingItem(
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("subjects") val subjects: List<SubjectItem>? = null,
        @JsonProperty("banner") val banner: BannerObj? = null,
        @JsonProperty("customData") val customData: CustomDataObj? = null
    )

    data class BannerObj(@JsonProperty("items") val items: List<SubjectItem>? = null)
    data class CustomDataObj(@JsonProperty("items") val items: List<CustomItem>? = null)

    // Wrapper untuk item di dalam customData yang kadang membungkus SubjectItem
    data class CustomItem(
        @JsonProperty("subject") val subject: SubjectItem? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("image") val image: CoverObj? = null,
        @JsonProperty("subjectId") val subjectId: String? = null
    ) {
        // Fallback agar CustomItem bisa diperlakukan seperti SubjectItem
        fun toSearchResponse(provider: Adimoviebox): SearchResponse {
            val finalId = subjectId ?: "0"
            val finalTitle = title ?: ""
            val finalImage = image?.url
            return provider.newMovieSearchResponse(finalTitle, "${provider.mainUrl}/detail/$finalId", TvType.Others) {
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
        @JsonProperty("subjectType") val subjectType: Int? = null
    ) {
        fun toSearchResponse(provider: Adimoviebox): SearchResponse {
            // Mapping Type: 1=Movie, 2=Series, 5=Course/Others
            val type = when (subjectType) {
                1 -> TvType.Movie
                2 -> TvType.TvSeries
                5 -> TvType.Others // Untuk Course
                else -> TvType.TvSeries
            }
            
            val finalUrl = "${provider.mainUrl}/detail/$subjectId"
            val posterImage = cover?.url ?: image?.url

            return provider.newMovieSearchResponse(
                name = title ?: "",
                url = finalUrl,
                type = type
            ) {
                this.posterUrl = posterImage
                this.plot = description
                this.rating = imdbRatingValue?.toDoubleOrNull()?.times(10)?.toInt()
            }
        }
    }

    data class CoverObj(@JsonProperty("url") val url: String? = null)
}
