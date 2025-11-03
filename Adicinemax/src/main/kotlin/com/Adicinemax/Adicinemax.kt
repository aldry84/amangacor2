package com.Adicinemax // Package diubah

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.* import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.nicehttp.RequestBodyTypes
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

class Adicinemax : MainAPI() { // Nama kelas diubah
    // Kunci API TMDb dimasukkan
    override var mainUrl = "https://api.themoviedb.org/3" 
    private val tmdbApiKey = "1cfadd9dbfc534abf6de40e1e7eaf4c7" // Kunci API Anda
    private val apiUrl = "https://fmoviesunblocked.net" // URL API streaming lama dipertahankan
    
    override val instantLinkLoading = true
    override var name = "Adicinemax" // Nama API diubah
    override val hasMainPage = false 
    override val hasQuickSearch = true
    override var lang = "en"
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
        TvType.AsianDrama
    )

    override val mainPage: List<MainPageData> = emptyList() // Dikosongkan karena tidak relevan dengan TMDb

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest,
    ): HomePageResponse {
        // Implementasi TMDb Discover diperlukan di sini
        return HomePageResponse(emptyList(), false)
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    // FUNGSI SEARCH (Diubah untuk memanggil TMDb, tetapi masih perlu logika konversi)
    override suspend fun search(query: String): List<SearchResponse> {
        val searchUrl = "$mainUrl/search/multi?api_key=$tmdbApiKey&query=$query&language=en-US&page=1"
        
        // Catatan: Anda perlu membuat data class untuk menangani respons JSON dari TMDb 
        // (misalnya, TmdbSearchResponse) dan kemudian memetakan hasilnya ke SearchResponse.
        
        // Untuk saat ini, fungsi ini hanya akan mengembalikan list kosong agar tidak error.
        val response = app.get(searchUrl).text
        
        // Logika konversi data dari response ke List<SearchResponse> HARUS DITAMBAHKAN di sini.
        // Contoh:
        // return parseJson<TmdbSearchResponse>(response)?.results?.mapNotNull { item -> 
        //     item.toSearchResponse(this)
        // } ?: emptyList()
        
        return emptyList()
    }

    // FUNGSI LOAD (Diubah untuk mengambil ID dari URL)
    override suspend fun load(url: String): LoadResponse {
        val id = url.substringAfterLast("/").substringBeforeLast("-")
        val detailUrl = "$mainUrl/movie/$id?api_key=$tmdbApiKey&append_to_response=credits,videos"

        // Catatan: Anda perlu membuat data class untuk detail TMDb 
        // dan mengonversi detailnya ke LoadResponse.

        throw NotImplementedError("Fungsi load harus diimplementasikan ulang dengan data class TMDb dan logika konversi.")
    }

    // FUNGSI loadLinks (Logika API Streaming Lama Dipertahankan)
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // ... (Logika loadLinks dipertahankan, menggunakan $apiUrl lama)

        val media = parseJson<LoadData>(data)
        // ... (Sisa kode loadLinks)
        
        val referer = "$apiUrl/spa/videoPlayPage/movies/${media.detailPath}?id=${media.id}&type=/movie/detail&lang=en"

        val streams = app.get(
            "$apiUrl/wefeed-h5-bff/web/subject/play?subjectId=${media.id}&se=${media.season ?: 0}&ep=${media.episode ?: 0}",
            referer = referer
        ).parsedSafe<Media>()?.data?.streams

        streams?.reversed()?.distinctBy { it.url }?.map { source ->
            callback.invoke(
                newExtractorLink(
                    this.name, 
                    this.name, 
                    source.url ?: return@map,
                    INFER_TYPE
                ) {
                    this.referer = "$apiUrl/"
                    this.quality = getQualityFromName(source.resolutions)
                }
            )
        }

        val id = streams?.first()?.id
        val format = streams?.first()?.format

        app.get(
            "$apiUrl/wefeed-h5-bff/web/subject/caption?format=$format&id=$id&subjectId=${media.id}",
            referer = referer
        ).parsedSafe<Media>()?.data?.captions?.map { subtitle ->
            subtitleCallback.invoke(
                SubtitleFile(
                    subtitle.lanName ?: "",
                    subtitle.url ?: return@map
                )
            )
        }

        return true
    }
}

// Data Classes (tetap dipertahankan dengan nama Adicinemax untuk konvensi)
// ... (Data classes LoadData, Media, MediaDetail, Items, dll. tetap sama)

data class LoadData(
    val id: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val detailPath: String? = null,
)

data class Media(
    @JsonProperty("data") val data: Data? = null,
) {
    data class Data(
        @JsonProperty("subjectList") val subjectList: ArrayList<Items>? = arrayListOf(),
        @JsonProperty("items") val items: ArrayList<Items>? = arrayListOf(),
        @JsonProperty("streams") val streams: ArrayList<Streams>? = arrayListOf(),
        @JsonProperty("captions") val captions: ArrayList<Captions>? = arrayListOf(),
    ) {
        data class Streams(
            @JsonProperty("id") val id: String? = null,
            @JsonProperty("format") val format: String? = null,
            @JsonProperty("url") val url: String? = null,
            @JsonProperty("resolutions") val resolutions: String? = null,
        )

        data class Captions(
            @JsonProperty("lan") val lan: String? = null,
            @JsonProperty("lanName") val lanName: String? = null,
            @JsonProperty("url") val url: String? = null,
        )
    }
}

data class MediaDetail(
    @JsonProperty("data") val data: Data? = null,
) {
    data class Data(
        @JsonProperty("subject") val subject: Items? = null,
        @JsonProperty("stars") val stars: ArrayList<Stars>? = arrayListOf(),
        @JsonProperty("resource") val resource: Resource? = null,
    ) {
        data class Stars(
            @JsonProperty("name") val name: String? = null,
            @JsonProperty("character") val character: String? = null,
            @JsonProperty("avatarUrl") val avatarUrl: String? = null,
        )

        data class Resource(
            @JsonProperty("seasons") val seasons: ArrayList<Seasons>? = arrayListOf(),
        ) {
            data class Seasons(
                @JsonProperty("se") val se: Int? = null,
                @JsonProperty("maxEp") val maxEp: Int? = null,
                @JsonProperty("allEp") val allEp: String? = null,
            )
        }
    }
}

data class Items(
    @JsonProperty("subjectId") val subjectId: String? = null,
    @JsonProperty("subjectType") val subjectType: Int? = null,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("description") val description: String? = null,
    @JsonProperty("releaseDate") val releaseDate: String? = null,
    @JsonProperty("duration") val duration: Long? = null,
    @JsonProperty("genre") val genre: String? = null,
    @JsonProperty("cover") val cover: Cover? = null,
    @JsonProperty("imdbRatingValue") val imdbRatingValue: String? = null,
    @JsonProperty("countryName") val countryName: String? = null,
    @JsonProperty("trailer") val trailer: Trailer? = null,
    @JsonProperty("detailPath") val detailPath: String? = null,
) {
    fun toSearchResponse(provider: Adicinemax): SearchResponse { // Diubah
        return provider.newMovieSearchResponse(
            title ?: "",
            subjectId ?: "",
            if (subjectType == 1) TvType.Movie else TvType.TvSeries,
            false
        ) {
            this.posterUrl = cover?.url
        }
    }

    data class Cover(
        @JsonProperty("url") val url: String? = null,
    )

    data class Trailer(
        @JsonProperty("videoAddress") val videoAddress: VideoAddress? = null,
    ) {
        data class VideoAddress(
            @JsonProperty("url") val url: String? = null,
        )
    }
}
