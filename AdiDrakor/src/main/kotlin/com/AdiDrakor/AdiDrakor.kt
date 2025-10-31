package com.AdiDrakor // PERBAIKAN: Pastikan ini adalah baris pertama file tanpa spasi di atasnya

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.nicehttp.RequestBodyTypes
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

class AdiDrakor : MainAPI() {
    override var mainUrl = "https://moviebox.ph"
    private val apiUrl = "https://fmoviesunblocked.net"
    
    override val instantLinkLoading = true
    override var name = "AdiDrakor"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override var lang = "en"
    
    override val supportedTypes = setOf(
        TvType.TvSeries,
        TvType.AsianDrama
    )

    // PERUBAHAN 1: Tambahkan kategori baru untuk Film Korea (Movies)
    override val mainPage: List<MainPageData> = mainPageOf(
        "2,ForYou" to "Drakor Pilihan",
        "2,Hottest" to "Drakor Terpopuler",
        "2,Latest" to "Drakor Terbaru",
        "2,Rating" to "Drakor Rating Tertinggi",
        "1,LatestMovies" to "Drakor Movies Terbaru" // Kategori BARU: subjectType=1, sort=LatestMovies
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest,
    ): HomePageResponse {
        val params = request.data.split(",")
        val channelId = params.first() // Ini adalah subjectType yang kita gunakan (2 untuk series, 1 untuk movie)
        val sortType = params.last()

        val body = mapOf(
            "channelId" to channelId,
            "page" to page,
            "perPage" to "24",
            "sort" to sortType
        ).toJson().toRequestBody(RequestBodyTypes.JSON.toMediaTypeOrNull())

        val isMovieCategory = channelId == "1"
        
        val home = app.post("$mainUrl/wefeed-h5-bff/web/filter", requestBody = body)
            ?.parsedSafe<Media>()?.data?.items
            ?.filter { 
                 // LOGIKA BARU:
                 // Jika channelId adalah '1' (Drakor Movies), filter HANYA Movie Korea (subjectType=1)
                 // Jika channelId adalah '2' (Drakor Series), filter HANYA Series Korea (subjectType=2)
                 if (isMovieCategory) {
                    it.countryName?.contains("Korea", ignoreCase = true) == true && it.subjectType == 1
                 } else {
                    it.countryName?.contains("Korea", ignoreCase = true) == true && it.subjectType == 2
                 }
            } 
            ?.map {
                it.toSearchResponse(this)
            } ?: throw ErrorLoadingException("Tidak ada Data Drakor Ditemukan")

        return newHomePageResponse(request.name, home)
    }

// ... Sisa kode lainnya (quickSearch, search, load, loadLinks, data classes) tetap sama ...

    override suspend fun quickSearch(query: String): List<SearchResponse> {
        return search(query) ?: emptyList()
    }

    // Fungsi search dipertahankan hanya untuk mencari Series Drakor, seperti permintaan sebelumnya.
    override suspend fun search(query: String): List<SearchResponse>? {
        val results = app.post(
            "$mainUrl/wefeed-h5-bff/web/subject/search", requestBody = mapOf(
                "keyword" to query,
                "page" to "1",
                "perPage" to "0",
                "subjectType" to "0", // Cari semua tipe (0)
            ).toJson().toRequestBody(RequestBodyTypes.JSON.toMediaTypeOrNull())
        ).parsedSafe<Media>()?.data?.items
            // Filter yang STRIKT: HANYA konten dengan countryName mengandung 'Korea' DAN subjectType=2
            ?.filter { it.countryName?.contains("Korea", ignoreCase = true) == true && it.subjectType == 2 }
            ?.map { it.toSearchResponse(this) }
            ?: return null
            
        return results 
    }
// ...
