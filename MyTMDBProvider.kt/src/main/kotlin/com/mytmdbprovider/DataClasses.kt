// DataClasses.kt

package mytmdbprovider // Harus sama dengan package provider dan extractor

import com.google.gson.annotations.SerializedName

// Model untuk data 'sources' yang diekstrak dari Vidsrc
data class SourceData(
    @SerializedName("file") val file: String,
    @SerializedName("label") val label: String
)

// Model untuk data 'tracks' (Subtitle) yang diekstrak dari Vidsrc
data class SubtitleData(
    @SerializedName("file") val file: String,
    @SerializedName("label") val label: String,
    @SerializedName("kind") val kind: String
)

// Anda juga akan menambahkan class data untuk JSON TMDB di sini (misalnya, TMDBResult, TMDBMovie, dll.)
