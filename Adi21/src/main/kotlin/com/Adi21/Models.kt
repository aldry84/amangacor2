package com.Adi21

data class TmdbSearchResult(val results: List<TmdbItem>)
data class TmdbItem(
    val id: Int,
    val name: String?,
    val title: String?,
    val poster_path: String?,
    val release_date: String?,
    val first_air_date: String?,
    val media_type: String? = null
)

data class TmdbDetail(
    val title: String?,
    val name: String?,
    val poster_path: String?,
    val release_date: String?,
    val first_air_date: String?,
    val overview: String,
    val vote_average: Double,
    val genres: List<Genre>,
    val seasons: List<TmdbSeason>?
)

data class Genre(val name: String)
data class TmdbSeason(val season_number: Int)

data class TmdbSeasonDetail(val episodes: List<TmdbEpisode>)
data class TmdbEpisode(
    val episode_number: Int,
    val name: String,
    val overview: String,
    val still_path: String?
)

data class TmdbVideoResult(val results: List<TmdbVideo>)
data class TmdbVideo(val key: String, val site: String, val type: String)

data class TmdbCredits(val cast: List<TmdbCast>)
data class TmdbCast(
    val name: String,
    val character: String,
    val profile_path: String?
)
