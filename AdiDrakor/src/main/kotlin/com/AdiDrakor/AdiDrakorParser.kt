package com.AdiDrakor

import com.fasterxml.jackson.annotation.JsonProperty

// --- KISSKH MODELS ---
data class KisskhResponse(@JsonProperty("data") val data: List<KisskhItem> = listOf())
data class KisskhItem(
    @JsonProperty("id") val id: Int? = null,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("thumbnail") val thumbnail: String? = null
)

// --- TMDB MODELS ---
data class TmdbResults(@JsonProperty("results") val results: List<TmdbMedia> = listOf())
data class TmdbMedia(
    @JsonProperty("id") val id: Int? = null,
    @JsonProperty("media_type") val mediaType: String? = null,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("poster_path") val posterPath: String? = null,
    @JsonProperty("backdrop_path") val backdropPath: String? = null,
    @JsonProperty("vote_average") val voteAverage: Double? = null
)

data class MediaDetail(
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("poster_path") val posterPath: String? = null,
    @JsonProperty("backdrop_path") val backdropPath: String? = null,
    @JsonProperty("release_date") val releaseDate: String? = null,
    @JsonProperty("first_air_date") val firstAirDate: String? = null,
    @JsonProperty("overview") val overview: String? = null,
    @JsonProperty("seasons") val seasons: ArrayList<Seasons>? = arrayListOf(),
    @JsonProperty("credits") val credits: Credits? = null,
    @JsonProperty("external_ids") val external_ids: ExternalIds? = null,
    @JsonProperty("videos") val videos: ResultsTrailer? = null,
    @JsonProperty("recommendations") val recommendations: TmdbResults? = null
)

data class Seasons(@JsonProperty("season_number") val seasonNumber: Int? = null)
data class MediaDetailEpisodes(@JsonProperty("episodes") val episodes: ArrayList<Episodes>? = arrayListOf())
data class Episodes(
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("overview") val overview: String? = null,
    @JsonProperty("air_date") val airDate: String? = null,
    @JsonProperty("still_path") val stillPath: String? = null,
    @JsonProperty("episode_number") val episodeNumber: Int? = null,
    @JsonProperty("season_number") val seasonNumber: Int? = null
)
data class Credits(@JsonProperty("cast") val cast: ArrayList<Cast>? = arrayListOf())
data class Cast(@JsonProperty("name") val name: String? = null, @JsonProperty("character") val character: String? = null, @JsonProperty("profile_path") val profilePath: String? = null)
data class ExternalIds(@JsonProperty("imdb_id") val imdb_id: String? = null)
data class ResultsTrailer(@JsonProperty("results") val results: ArrayList<Trailers>? = arrayListOf())
data class Trailers(@JsonProperty("key") val key: String? = null)

// --- EXTRACTOR MODELS ---
data class ResponseHash(@JsonProperty("embed_url") val embed_url: String, @JsonProperty("key") val key: String? = null)
data class VidsrcccResponse(@JsonProperty("data") val data: ArrayList<VidsrcccServer>? = arrayListOf())
data class VidsrcccServer(@JsonProperty("name") val name: String? = null, @JsonProperty("hash") val hash: String? = null)
data class VidsrcccResult(@JsonProperty("data") val data: VidsrcccSources? = null)
data class VidsrcccSources(@JsonProperty("subtitles") val subtitles: ArrayList<Subtitles>? = arrayListOf(), @JsonProperty("source") val source: String? = null)
data class Subtitles(@JsonProperty("label") val label: String? = null, @JsonProperty("file") val file: String? = null)
data class VidlinkSources(@JsonProperty("stream") val stream: Stream? = null) { data class Stream(@JsonProperty("playlist") val playlist: String? = null) }
data class WyzieSubtitle(@JsonProperty("display") val display: String? = null, @JsonProperty("url") val url: String? = null)
data class VidrockSource(@JsonProperty("resolution") val resolution: Int? = null, @JsonProperty("url") val url: String? = null)
