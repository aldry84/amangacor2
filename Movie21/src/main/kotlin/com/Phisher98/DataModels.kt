package com.Movie21

import com.google.gson.annotations.SerializedName

// --- Model untuk Latest Content dari vidsrc-embed.ru (Halaman Utama) ---
data class LatestContentList(
    @SerializedName("results") val results: List<LatestContent> = emptyList()
)

data class LatestContent(
    @SerializedName("title") val title: String,
    // Asumsi vidsrc-embed mengembalikan IMDB ID (string) atau TMDB ID (integer)
    @SerializedName("imdb") val imdb: String? = null,
    @SerializedName("tmdb") val tmdb: Int? = null, 
    @SerializedName("poster") val poster: String? = null
)

// --- Model untuk JSON Response dari Vidsrc.ts Vercel API ---
data class VidSrcResponse(
    @SerializedName("name") val name: String? = null,
    @SerializedName("image") val image: String? = null,
    @SerializedName("mediaId") val mediaId: String? = null,
    @SerializedName("stream") val streamUrl: String? // Link m3u8
)
