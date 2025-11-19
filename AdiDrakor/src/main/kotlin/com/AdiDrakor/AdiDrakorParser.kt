package com.AdiDrakor

import com.fasterxml.jackson.annotation.JsonProperty

data class GpressSources(
    @JsonProperty("src") val src: String,
    @JsonProperty("file") val file: String? = null,
    @JsonProperty("label") val label: Int? = null,
    @JsonProperty("max") val max: String,
)

data class ResponseHash(
    @JsonProperty("embed_url") val embed_url: String,
    @JsonProperty("key") val key: String? = null,
    @JsonProperty("type") val type: String? = null,
)

data class VidsrcccResponse(@JsonProperty("data") val data: ArrayList<VidsrcccServer>? = arrayListOf())
data class VidsrcccServer(@JsonProperty("name") val name: String? = null, @JsonProperty("hash") val hash: String? = null)
data class VidsrcccResult(@JsonProperty("data") val data: VidsrcccSources? = null)
data class VidsrcccSources(@JsonProperty("subtitles") val subtitles: ArrayList<VidsrcccSubtitles>? = arrayListOf(), @JsonProperty("source") val source: String? = null)
data class VidsrcccSubtitles(@JsonProperty("label") val label: String? = null, @JsonProperty("file") val file: String? = null)

data class UpcloudResult(@JsonProperty("sources") val sources: ArrayList<UpcloudSources>? = arrayListOf())
data class UpcloudSources(@JsonProperty("file") val file: String? = null)

data class RageSources(@JsonProperty("url") val url: String? = null)
data class PrimeboxSources(
    @JsonProperty("streams") val streams: HashMap<String, String>? = null,
    @JsonProperty("subtitles") val subtitles: ArrayList<Subtitles>? = null,
) {
    data class Subtitles(@JsonProperty("file") val file: String? = null, @JsonProperty("label") val label: String? = null)
}

data class WatchsomuchResponses(@JsonProperty("movie") val movie: WatchsomuchMovies? = null)
data class WatchsomuchMovies(@JsonProperty("torrents") val torrents: ArrayList<WatchsomuchTorrents>? = arrayListOf())
data class WatchsomuchTorrents(
    @JsonProperty("id") val id: Int? = null,
    @JsonProperty("season") val season: Int? = null,
    @JsonProperty("episode") val episode: Int? = null,
)
data class WatchsomuchSubResponses(@JsonProperty("subtitles") val subtitles: ArrayList<WatchsomuchSubtitles>? = arrayListOf())
data class WatchsomuchSubtitles(@JsonProperty("url") val url: String? = null, @JsonProperty("label") val label: String? = null)

data class MappleSources(@JsonProperty("data") val data: Data? = null) {
    data class Data(@JsonProperty("stream_url") val stream_url: String? = null)
}
data class MappleSubtitle(@JsonProperty("display") val display: String? = null, @JsonProperty("url") val url: String? = null)

data class VidlinkSources(@JsonProperty("stream") val stream: Stream? = null) {
    data class Stream(@JsonProperty("playlist") val playlist: String? = null)
}

data class VidFastServers(@JsonProperty("name") val name: String? = null, @JsonProperty("data") val data: String? = null, @JsonProperty("description") val description: String? = null)
data class VidFastSources(@JsonProperty("url") val url: String? = null, @JsonProperty("tracks") val tracks: ArrayList<Tracks>? = null) {
    data class Tracks(@JsonProperty("file") val file: String? = null, @JsonProperty("label") val label: String? = null)
}

data class WyzieSubtitle(@JsonProperty("display") val display: String? = null, @JsonProperty("url") val url: String? = null)
data class VidsrccxSource(@JsonProperty("secureUrl") val secureUrl: String? = null)

data class VidrockSource(@JsonProperty("resolution") val resolution: Int? = null, @JsonProperty("url") val url: String? = null)
data class VidrockSubtitle(@JsonProperty("label") val label: String? = null, @JsonProperty("file") val file: String? = null)

data class VixsrcSource(val name: String, val url: String, val referer: String)
