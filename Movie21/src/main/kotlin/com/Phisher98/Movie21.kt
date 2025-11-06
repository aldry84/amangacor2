package com.Phisher98

import android.os.Build
import androidx.annotation.RequiresApi
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.extractors.TmdbAPI
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.getQualityFromName
import kotlinx.coroutines.runBlocking
import okhttp3.RequestBody.Companion.toRequestBody

// --- API Data Classes (Dipertahankan dari API Streaming Kustom) ---
data class ApiResponse(
    @JsonProperty("status") val status: Boolean? = null,
    @JsonProperty("msg") val msg: String? = null,
    @JsonProperty("return") val data: List<ApiContent>? = null
)

// Catatan: ApiContent tidak relevan untuk Movie21, karena metadata dari TMDb.
// Kita hanya butuh data untuk loadLinks.

data class StreamLinkResponse(
    @JsonProperty("status") val status: Boolean? = null,
    @JsonProperty("msg") val msg: String? = null,
    @JsonProperty("return") val streamData: List<StreamLinkData>? = null
)

data class StreamLinkData(
    @JsonProperty("link") val link: String? = null,
    @JsonProperty("link_raw") val linkRaw: String? = null,
    @JsonProperty("quality") val quality: String? = null,
    @JsonProperty("resolusi") val resolution: String? = null
)
// --- API Data Classes End ---

// Warisi dari TmdbAPI dan timpa loadLinks
class Movie21 : TmdbAPI() {
    override var mainUrl = "https://tmdb.api.org" // Tidak relevan, tapi harus ada
    override var name = "Movie21"
    override var lang = "id"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)
    
    // API Key TMDb Anda
    override val TMDB_API = "1cfadd9dbfc534abf6de40e1e7eaf4c7"

    // --- Endpoint Streaming Kustom ---
    // GANTI "https://dramadrip.com" dengan URL domain Anda yang sebenarnya. 
    // Saya menggunakan dramadrip.com karena ada di kode lama Anda.
    private val STREAM_BASE_URL = runBlocking {
        Movie21Provider.getDomains()?.movie21 ?: "https://dramadrip.com" 
    }
    private val API_KLASIK_LOAD = "/api/v1/klasik/load" // Endpoint untuk mendapatkan link

    // Fungsi getTmdbId dan getTmdbSearch dibiarkan kosong, TmdbAPI yang menanganinya.

    // Kita hanya perlu menimpa loadLinks untuk mengambil data dari API kustom
    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // Data dari TmdbAPI biasanya berisi ID TMDb dan tipe, tapi kita akan asumsikan 
        // kita memerlukan ID konten unik untuk API streaming Anda. 
        // Asumsi: data adalah ID konten unik yang diperlukan oleh API Anda (misalnya id_content).
        val contentId = tryParseJson<String>(data) ?: return false

        // --- Panggilan API untuk Tautan Streaming ---
        val jsonPayload = mapOf(
            "auth" to "", // Auth token kosong
            "id_content" to contentId
        ).toJson()

        val response = app.post(
            "$STREAM_BASE_URL$API_KLASIK_LOAD",
            requestBody = jsonPayload.toRequestBody(),
            headers = mapOf("Content-Type" to "application/json")
        ).parsedSafe<StreamLinkResponse>()

        val streamLinks = response?.streamData ?: emptyList()

        if (streamLinks.isEmpty()) {
            Log.e("Movie21", "API returned no stream links for ID: $contentId")
            return false
        }

        for (stream in streamLinks) {
            val link = stream.link ?: stream.linkRaw ?: continue
            val qualityText = stream.quality ?: stream.resolution ?: "Unknown"
            val qualityInt = getQualityFromName(qualityText)

            callback(
                newExtractorLink(
                    source = "Movie21 API",
                    name = "Movie21 API $qualityText",
                    url = link,
                ) {
                    this.quality = qualityInt
                    this.referer = "$STREAM_BASE_URL/" 
                }
            )
        }

        return true
    }
}
