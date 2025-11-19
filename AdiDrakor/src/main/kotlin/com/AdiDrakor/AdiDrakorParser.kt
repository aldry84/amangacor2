package com.AdiDrakor

import com.fasterxml.jackson.annotation.JsonProperty

// --- Umum & Helper ---
data class ResponseHash(
    @JsonProperty("embed_url") val embed_url: String,
    @JsonProperty("key") val key: String? = null,
    @JsonProperty("type") val type: String? = null,
)

data class ZShowEmbed(
    @JsonProperty("m") val meta: String? = null,
)

// --- KissKh ---
data class KisskhResults(
    @JsonProperty("id") val id: Int?,
    @JsonProperty("title") val title: String?,
)

data class KisskhDetail(
    @JsonProperty("episodes") val episodes: ArrayList<KisskhEpisodes>? = arrayListOf(),
)

data class KisskhEpisodes(
    @JsonProperty("id") val id: Int?,
    @JsonProperty("number") val number: Int?,
)

data class KisskhKey(
    val id: String,
    val version: String,
    val key: String,
)

data class KisskhSources(
    @JsonProperty("Video") val video: String?,
    @JsonProperty("ThirdParty") val thirdParty: String?,
)

data class KisskhSubtitle(
    @JsonProperty("src") val src: String?,
    @JsonProperty("label") val label: String?,
)

// --- RidoMovies ---
data class RidoSearch(
    @JsonProperty("data") var data: RidoData? = null,
)

data class RidoData(
    @JsonProperty("items") var items: ArrayList<RidoItems>? = arrayListOf(),
)

data class RidoItems(
    @JsonProperty("slug") var slug: String? = null,
    @JsonProperty("contentable") var contentable: RidoContentable? = null,
)

data class RidoContentable(
    @JsonProperty("imdbId") var imdbId: String? = null,
    @JsonProperty("tmdbId") var tmdbId: Int? = null,
)

data class RidoResponses(
    @JsonProperty("data") var data: ArrayList<RidoVideoData>? = arrayListOf(),
)

data class RidoVideoData(
    @JsonProperty("url") var url: String? = null,
)

// --- Showflix ---
data class ShowflixSearchMovies(
    @JsonProperty("results") val resultsMovies: ArrayList<ShowflixResultsMovies>? = arrayListOf()
)

data class ShowflixResultsMovies(
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("embedLinks") val embedLinks: Map<String, String>? = null,
)

data class ShowflixSearchSeries(
    @JsonProperty("results") val resultsSeries: ArrayList<ShowflixResultsSeries>? = arrayListOf()
)

data class ShowflixResultsSeries(
    @JsonProperty("seriesName") val seriesName: String? = null,
    @JsonProperty("streamwish") val streamwish: Map<String, List<String>>? = null,
    @JsonProperty("filelions") val filelions: Map<String, List<String>>? = null,
    @JsonProperty("streamruby") val streamruby: Map<String, List<String>>? = null,
)

// --- Nepu ---
data class NepuSearch(
    @JsonProperty("data") val data: ArrayList<NepuData>? = arrayListOf(),
) {
    data class NepuData(
        @JsonProperty("url") val url: String? = null,
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("type") val type: String? = null,
    )
}

// --- Vidsrccc ---
data class Vidsrcccservers(
    val data: List<VidsrcccDaum>,
    val success: Boolean,
)

data class VidsrcccDaum(
    val name: String,
    val hash: String,
)

data class Vidsrcccm3u8(
    val data: VidsrcccData,
)

data class VidsrcccData(
    val source: String,
)

// --- TMDB & Umum ---
data class TmdbDate(
    val today: String,
    val nextWeek: String,
)
