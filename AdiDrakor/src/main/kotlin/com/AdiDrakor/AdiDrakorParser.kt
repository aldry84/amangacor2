package com.AdiDrakor
import com.fasterxml.jackson.annotation.JsonProperty

// KISSKH
data class KisskhResponse(@JsonProperty("data") val data: List<KisskhItem> = listOf())
data class KisskhItem(@JsonProperty("id") val id: Int?, @JsonProperty("title") val title: String?, @JsonProperty("thumbnail") val thumbnail: String?)

// TMDB & COMMON
data class TmdbResults(@JsonProperty("results") val results: List<TmdbMedia> = listOf())
data class TmdbMedia(@JsonProperty("id") val id: Int?, @JsonProperty("media_type") val mediaType: String?, @JsonProperty("title") val title: String?, @JsonProperty("name") val name: String?, @JsonProperty("poster_path") val posterPath: String?, @JsonProperty("backdrop_path") val backdropPath: String?, @JsonProperty("vote_average") val voteAverage: Double?)
data class MediaDetail(@JsonProperty("title") val title: String?, @JsonProperty("name") val name: String?, @JsonProperty("poster_path") val posterPath: String?, @JsonProperty("backdrop_path") val backdropPath: String?, @JsonProperty("release_date") val releaseDate: String?, @JsonProperty("first_air_date") val firstAirDate: String?, @JsonProperty("overview") val overview: String?, @JsonProperty("seasons") val seasons: List<Seasons>?, @JsonProperty("credits") val credits: Credits?, @JsonProperty("external_ids") val external_ids: ExternalIds?, @JsonProperty("videos") val videos: ResultsTrailer?, @JsonProperty("recommendations") val recommendations: TmdbResults?)
data class Seasons(@JsonProperty("season_number") val seasonNumber: Int?)
data class MediaDetailEpisodes(@JsonProperty("episodes") val episodes: List<Episodes>?)
data class Episodes(@JsonProperty("name") val name: String?, @JsonProperty("overview") val overview: String?, @JsonProperty("air_date") val airDate: String?, @JsonProperty("still_path") val stillPath: String?, @JsonProperty("episode_number") val episodeNumber: Int?, @JsonProperty("season_number") val seasonNumber: Int?)
data class Credits(@JsonProperty("cast") val cast: List<Cast>?)
data class Cast(@JsonProperty("name") val name: String?, @JsonProperty("character") val character: String?, @JsonProperty("profile_path") val profilePath: String?)
data class ExternalIds(@JsonProperty("imdb_id") val imdb_id: String?)
data class ResultsTrailer(@JsonProperty("results") val results: List<Trailers>?)
data class Trailers(@JsonProperty("key") val key: String?)

// EXTRACTORS
data class ResponseHash(@JsonProperty("embed_url") val embed_url: String, @JsonProperty("key") val key: String?)
data class ResponseSource(@JsonProperty("hls") val hls: Boolean, @JsonProperty("videoSource") val videoSource: String)
data class JeniusTracks(@JsonProperty("file") val file: String, @JsonProperty("label") val label: String?)
data class VidsrcccResponse(@JsonProperty("data") val data: List<VidsrcccServer>?)
data class VidsrcccServer(@JsonProperty("name") val name: String?, @JsonProperty("hash") val hash: String?)
data class VidsrcccResult(@JsonProperty("data") val data: VidsrcccSources?)
data class VidsrcccSources(@JsonProperty("subtitles") val subtitles: List<Subtitles>?, @JsonProperty("source") val source: String?)
data class Subtitles(@JsonProperty("label") val label: String?, @JsonProperty("file") val file: String?)
data class VidlinkSources(@JsonProperty("stream") val stream: Stream?) { data class Stream(@JsonProperty("playlist") val playlist: String?) }
data class WyzieSubtitle(@JsonProperty("display") val display: String?, @JsonProperty("url") val url: String?)
data class VidrockSource(@JsonProperty("resolution") val resolution: Int?, @JsonProperty("url") val url: String?)
