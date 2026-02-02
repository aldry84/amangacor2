package com.AdiDrakor

import com.fasterxml.jackson.annotation.JsonProperty
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

// ================== IDLIX DATA CLASSES ==================
data class AesData(
    @JsonProperty("m") val m: String,
)

data class ResponseHash(
    @JsonProperty("embed_url") val embed_url: String,
    @JsonProperty("key") val key: String,
)

// ================== EXISTING DATA CLASSES ==================

data class AniIds(var id: Int? = null, var idMal: Int? = null)

data class TmdbDate(
    val today: String,
    val nextWeek: String,
)

data class VixsrcSource(
    val name: String,
    val url: String,
    val referer: String,
)

data class VidrockSource(
    @JsonProperty("resolution") val resolution: Int? = null,
    @JsonProperty("url") val url: String? = null,
)

data class VidrockSubtitle(
    @JsonProperty("label") val label: String? = null,
    @JsonProperty("file") val file: String? = null,
)

data class VidsrccxSource(
    @JsonProperty("secureUrl") val secureUrl: String? = null,
)

data class WyzieSubtitle(
    @JsonProperty("display") val display: String? = null,
    @JsonProperty("url") val url: String? = null,
)

data class VidFastSources(
    @JsonProperty("url") val url: String? = null,
    @JsonProperty("tracks") val tracks: ArrayList<Tracks>? = null,
) {
    data class Tracks(
        @JsonProperty("file") val file: String? = null,
        @JsonProperty("label") val label: String? = null,
    )
}

data class VidFastServers(
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("description") val description: String? = null,
    @JsonProperty("data") val data: String? = null,
) {
    data class Stream(
        @JsonProperty("playlist") val playlist: String? = null,
    )
}

data class VidlinkSources(
    @JsonProperty("stream") val stream: Stream? = null,
) {
    data class Stream(
        @JsonProperty("playlist") val playlist: String? = null,
    )
}

data class MappleSubtitle(
    @JsonProperty("display") val display: String? = null,
    @JsonProperty("url") val url: String? = null,
)

data class MappleSources(
    @JsonProperty("data") val data: Data? = null,
) {
    data class Data(
        @JsonProperty("stream_url") val stream_url: String? = null,
    )
}

data class PrimeboxSources(
    @JsonProperty("streams") val streams: HashMap<String, String>? = null,
    @JsonProperty("subtitles") val subtitles: ArrayList<Subtitles>? = null,
) {
    data class Subtitles(
        @JsonProperty("file") val file: String? = null,
        @JsonProperty("label") val label: String? = null,
    )
}

data class RageSources(
    @JsonProperty("url") val url: String? = null,
)

data class VidsrcccServer(
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("hash") val hash: String? = null,
)

data class VidsrcccResponse(
    @JsonProperty("data") val data: ArrayList<VidsrcccServer>? = arrayListOf(),
)

data class VidsrcccResult(
    @JsonProperty("data") val data: VidsrcccSources? = null,
)

data class VidsrcccSources(
    @JsonProperty("subtitles") val subtitles: ArrayList<VidsrcccSubtitles>? = arrayListOf(),
    @JsonProperty("source") val source: String? = null,
)

data class VidsrcccSubtitles(
    @JsonProperty("label") val label: String? = null,
    @JsonProperty("file") val file: String? = null,
)

data class UpcloudSources(
    @JsonProperty("file") val file: String? = null,
)

data class UpcloudResult(
    @JsonProperty("sources") val sources: ArrayList<UpcloudSources>? = arrayListOf(),
)

data class AniMedia(
    @JsonProperty("id") var id: Int? = null,
    @JsonProperty("idMal") var idMal: Int? = null
)

data class AniPage(@JsonProperty("media") var media: java.util.ArrayList<AniMedia> = arrayListOf())

data class AniData(@JsonProperty("Page") var Page: AniPage? = AniPage())

data class AniSearch(@JsonProperty("data") var data: AniData? = AniData())

data class GpressSources(
    @JsonProperty("src") val src: String,
    @JsonProperty("file") val file: String? = null,
    @JsonProperty("label") val label: Int? = null,
    @JsonProperty("max") val max: String,
)

data class WatchsomuchTorrents(
    @JsonProperty("id") val id: Int? = null,
    @JsonProperty("movieId") val movieId: Int? = null,
    @JsonProperty("season") val season: Int? = null,
    @JsonProperty("episode") val episode: Int? = null,
)

data class WatchsomuchMovies(
    @JsonProperty("torrents") val torrents: ArrayList<WatchsomuchTorrents>? = arrayListOf(),
)

data class WatchsomuchSubtitles(
    @JsonProperty("url") val url: String? = null,
    @JsonProperty("label") val label: String? = null,
)

data class WatchsomuchResponses(
    @JsonProperty("movie") val movie: WatchsomuchMovies? = null,
)

data class WatchsomuchSubResponses(
    @JsonProperty("subtitles") val subtitles: ArrayList<WatchsomuchSubtitles>? = arrayListOf(),
)

data class AdiDewasaSearchResponse(
    @JsonProperty("data") val data: ArrayList<AdiDewasaItem>? = arrayListOf(),
    @JsonProperty("success") val success: Boolean? = null
)

data class AdiDewasaItem(
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("slug") val slug: String? = null,
    @JsonProperty("image") val image: String? = null,
    @JsonProperty("year") val year: String? = null 
)

// ================== ADIMOVIEBOX DATA CLASSES (V1) ==================
data class AdimovieboxResponse(
    @JsonProperty("data") val data: AdimovieboxData? = null,
)

data class AdimovieboxData(
    @JsonProperty("items") val items: ArrayList<AdimovieboxItem>? = arrayListOf(),
    @JsonProperty("streams") val streams: ArrayList<AdimovieboxStreamItem>? = arrayListOf(),
    @JsonProperty("captions") val captions: ArrayList<AdimovieboxCaptionItem>? = arrayListOf(),
)

data class AdimovieboxItem(
    @JsonProperty("subjectId") val subjectId: String? = null,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("releaseDate") val releaseDate: String? = null,
    @JsonProperty("detailPath") val detailPath: String? = null,
)

data class AdimovieboxStreamItem(
    @JsonProperty("id") val id: String? = null,
    @JsonProperty("format") val format: String? = null,
    @JsonProperty("url") val url: String? = null,
    @JsonProperty("resolutions") val resolutions: String? = null,
)

data class AdimovieboxCaptionItem(
    @JsonProperty("lanName") val lanName: String? = null,
    @JsonProperty("url") val url: String? = null,
)

// ================== ADIMOVIEBOX 2 DATA CLASSES (V2 - NEW) ==================
data class Adimoviebox2SearchResponse(
    @JsonProperty("data") val data: Adimoviebox2SearchData? = null
)

data class Adimoviebox2SearchData(
    @JsonProperty("results") val results: ArrayList<Adimoviebox2SearchResult>? = arrayListOf()
)

data class Adimoviebox2SearchResult(
    @JsonProperty("subjects") val subjects: ArrayList<Adimoviebox2Subject>? = arrayListOf()
)

data class Adimoviebox2Subject(
    @JsonProperty("subjectId") val subjectId: String? = null,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("releaseDate") val releaseDate: String? = null,
    @JsonProperty("subjectType") val subjectType: Int? = null // 1=Movie, 2=Series
)

data class Adimoviebox2PlayResponse(
    @JsonProperty("data") val data: Adimoviebox2PlayData? = null
)

data class Adimoviebox2PlayData(
    @JsonProperty("streams") val streams: ArrayList<Adimoviebox2Stream>? = arrayListOf()
)

data class Adimoviebox2Stream(
    @JsonProperty("id") val id: String? = null,
    @JsonProperty("url") val url: String? = null,
    @JsonProperty("format") val format: String? = null,
    @JsonProperty("resolutions") val resolutions: String? = null,
)

data class Adimoviebox2SubtitleResponse(
    @JsonProperty("data") val data: Adimoviebox2SubtitleData? = null
)

data class Adimoviebox2SubtitleData(
    @JsonProperty("extCaptions") val extCaptions: ArrayList<Adimoviebox2Caption>? = arrayListOf()
)

data class Adimoviebox2Caption(
    @JsonProperty("url") val url: String? = null,
    @JsonProperty("language") val language: String? = null,
    @JsonProperty("lanName") val lanName: String? = null,
    @JsonProperty("lan") val lan: String? = null
)
