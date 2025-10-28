package com.Phisher98

import android.os.Build
import androidx.annotation.RequiresApi
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.api.Log
import com.lagradost.cloudstream3.app
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.nodes.Document
import java.net.URI
import java.net.URLEncoder
import java.util.Base64

data class DomainsParser(
    @JsonProperty("dramadrip")
    val dramadrip: String,
)


data class Meta(
    val id: String?,
    val imdb_id: String?,
    val type: String?,
    val poster: String?,
    val logo: String?,
    val background: String?,
    val moviedb_id: Int?,
    val name: String?,
    val description: String?,
    val genre: List<String>?,
    val releaseInfo: String?,
    val status: String?,
    val runtime: String?,
    val cast: List<String>?,
    val language: String?,
    val country: String?,
    
    // --- PERUBAHAN UTAMA DI SINI ---
    // Mengubah String? menjadi Double? untuk perhitungan skor
    // Menggunakan anotasi agar dapat mem-parse 'imdb_rating' dari Cinemeta
    @JsonProperty("imdb_rating")
    val imdbRating: Double?, 
    
    val slug: String?,
    val year: String?,
    val videos: List<EpisodeDetails>?
)

data class EpisodeDetails(
    val id: String?,
    val name: String?,
    val title: String?,
    val season: Int?,
    val episode: Int?,
    val released: String?,
    val overview: String?,
    val thumbnail: String?,
    val moviedb_id: Int?
)

data class ResponseData(
    val meta: Meta?
)
// ... (semua fungsi suspende lainnya tetap sama) ...
// ... (bypassHrefli, getBaseUrl, fixUrl, cinematickitBypass, cinematickitloadBypass, base64Decode)
// Karena fungsi-fungsi tersebut tidak memicu error yang dilaporkan, saya tidak menuliskannya lagi.
