package com.AdiDrakor

import com.fasterxml.jackson.annotation.JsonProperty

data class ResponseHash(@JsonProperty("embed_url") val embed_url: String, @JsonProperty("key") val key: String? = null)
data class ZShowEmbed(@JsonProperty("m") val meta: String? = null)
data class KisskhKey(val key: String)
data class KisskhSources(@JsonProperty("Video") val video: String?, @JsonProperty("ThirdParty") val thirdParty: String?)
data class KisskhSubtitle(@JsonProperty("src") val src: String?, @JsonProperty("label") val label: String?)
data class RidoSearch(@JsonProperty("data") var data: RidoData? = null)
data class RidoData(@JsonProperty("items") var items: ArrayList<RidoItems>? = arrayListOf())
data class RidoItems(@JsonProperty("slug") var slug: String? = null, @JsonProperty("contentable") var contentable: RidoContentable? = null)
data class RidoContentable(@JsonProperty("imdbId") var imdbId: String? = null, @JsonProperty("tmdbId") var tmdbId: Int? = null)
data class RidoResponses(@JsonProperty("data") var data: ArrayList<RidoVideoData>? = arrayListOf())
data class RidoVideoData(@JsonProperty("url") var url: String? = null)
data class ShowflixSearchMovies(@JsonProperty("results") val resultsMovies: ArrayList<ShowflixResultsMovies>? = arrayListOf())
data class ShowflixResultsMovies(@JsonProperty("name") val name: String? = null, @JsonProperty("embedLinks") val embedLinks: Map<String, String>? = null)
data class ShowflixSearchSeries(@JsonProperty("results") val resultsSeries: ArrayList<ShowflixResultsSeries>? = arrayListOf())
data class ShowflixResultsSeries(@JsonProperty("seriesName") val seriesName: String? = null, @JsonProperty("streamwish") val streamwish: Map<String, List<String>>? = null, @JsonProperty("filelions") val filelions: Map<String, List<String>>? = null, @JsonProperty("streamruby") val streamruby: Map<String, List<String>>? = null)
data class NepuSearch(@JsonProperty("data") val data: ArrayList<NepuData>? = arrayListOf()) { data class NepuData(@JsonProperty("url") val url: String? = null, @JsonProperty("name") val name: String? = null, @JsonProperty("type") val type: String? = null) }
data class Vidsrcccservers(val data: List<VidsrcccDaum>)
data class VidsrcccDaum(val name: String, val hash: String)
data class Vidsrcccm3u8(val data: VidsrcccData)
data class VidsrcccData(val source: String)
data class TmdbDate(val today: String, val nextWeek: String)

data class KisskhResults(
    @JsonProperty("id") val id: Int?,
    @JsonProperty("title") val title: String?
)

data class KisskhDetail(
    @JsonProperty("episodes") val episodes: ArrayList<KisskhEpisodes>? = arrayListOf()
)

data class KisskhEpisodes(
    @JsonProperty("id") val id: Int?,
    @JsonProperty("number") val number: Int?
)

data class MoflixResponse(
    @JsonProperty("title") val title: Episode? = null,
    @JsonProperty("episode") val episode: Episode? = null,
) {
    data class Episode(
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("videos") val videos: ArrayList<Videos>? = arrayListOf(),
    ) {
        data class Videos(
            @JsonProperty("name") val name: String? = null,
            @JsonProperty("src") val src: String? = null,
            @JsonProperty("quality") val quality: String? = null,
            @JsonProperty("category") val category: String? = null,
        )
    }
}

data class EMovieServer(@JsonProperty("value") val value: String? = null)
data class EMovieSources(@JsonProperty("file") val file: String? = null)
data class EMovieTraks(@JsonProperty("file") val file: String? = null, @JsonProperty("label") val label: String? = null)
data class Watch32(val link: String)
data class AllMovielandPlaylist(@JsonProperty("file") val file: String? = null, @JsonProperty("key") val key: String? = null)
data class AllMovielandServer(@JsonProperty("title") val title: String? = null, @JsonProperty("id") val id: String? = null, @JsonProperty("file") val file: String? = null, @JsonProperty("folder") val folder: ArrayList<AllMovielandSeasonFolder>? = arrayListOf())
data class AllMovielandSeasonFolder(@JsonProperty("episode") val episode: String? = null, @JsonProperty("folder") val folder: ArrayList<AllMovielandEpisodeFolder>? = arrayListOf())
data class AllMovielandEpisodeFolder(@JsonProperty("title") val title: String? = null, @JsonProperty("file") val file: String? = null)
