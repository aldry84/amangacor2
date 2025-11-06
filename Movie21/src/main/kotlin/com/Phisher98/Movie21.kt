package com.Phisher98

import android.os.Build
import androidx.annotation.RequiresApi
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.extractors.TmdbAPI // Baris ini mungkin tidak tersedia secara publik di semua versi CS3
import com.lagradost.api.Log // Tambahkan import Log yang hilang
import kotlinx.coroutines.runBlocking
import okhttp3.RequestBody.Companion.toRequestBody

// --- API Data Classes (Hapus ApiContent yang tidak digunakan) ---

data class ApiResponse(
    @JsonProperty("status") val status: Boolean? = null,
    @JsonProperty("msg") val msg: String? = null,
    // Menghapus 'data: List<ApiContent>' karena kita tidak menggunakan API ini untuk metadata
)

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

// PENTING: Karena 'TmdbAPI' mungkin bukan kelas dasar yang dapat diwarisi 
// (seperti MainAPI), kita akan GANTI pewarisan ke MainAPI dan menyertakan 
// fungsionalitas TMDb secara manual jika perlu, atau menganggap 'TmdbAPI' 
// sudah benar dan menambahkan kata kunci 'override' ke properti yang hilang.

// Jika Anda menggunakan versi Cloudstream3 yang mendukung warisan dari TmdbAPI (biasanya ada di repo developer)
class Movie21 : TmdbAPI() {
// Jika warisan TmdbAPI bermasalah, gunakan: class Movie21 : MainAPI() { ... }
// Dan tambahkan fungsi search, load (dengan API TMDb) secara manual.

    // Tambahkan kata kunci 'override' yang hilang (hanya jika Anda yakin 
    // Movie21 mewarisi dari TmdbAPI atau MainAPI yang memiliki properti ini)
    override var mainUrl = "https://tmdb.api.org" 
    override val name = "Movie21"
    override val lang = "id"
    override val hasMainPage = false // Atur ke false karena TMDbAPI tidak memiliki Main Page default
    override val hasQuickSearch = true 
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)
    
    // Properti khusus TmdbAPI, harus ditimpa jika TmdbAPI didefinisikan dengan baik
    override val TMDB_API = "1cfadd9dbfc534abf6de40e1e7eaf4c7"

    // --- Endpoint Streaming Kustom ---
    private val STREAM_BASE_URL = runBlocking {
        Movie21Provider.getDomains()?.movie21 ?: "https://dramadrip.com" 
    }
    private val API_KLASIK_LOAD = "/api/v1/klasik/load" 

    // loadLinks harus menggunakan 'override' jika Movie21 mewarisi dari MainAPI
    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val contentId = tryParseJson<String>(data) ?: return false

        val jsonPayload = mapOf(
            "auth" to "", 
            "id_content" to contentId
        ).toJson()

        val response = app.post(
            "$STREAM_BASE_URL$API_KLASIK_LOAD",
            requestBody = jsonPayload.toRequestBody(),
            headers = mapOf("Content-Type" to "application/json")
        ).parsedSafe<StreamLinkResponse>()

        val streamLinks = response?.streamData ?: emptyList()

        if (streamLinks.isEmpty()) {
            Log.e("Movie21", "API returned no stream links for ID: $contentId") // Perbaikan 'Log'
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
