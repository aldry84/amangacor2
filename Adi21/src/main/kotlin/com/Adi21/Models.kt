package com.Adi21

// 🔍 Search results from TMDB
data class TmdbSearchResult(
    val results: List<TmdbItem>
)

data class TmdbItem(
    val id: Int,
    val name: String?,              // For TV series
    val title: String?,             // For movies
    val poster_path: String?,
    val release_date: String?,      // For movies
    val first_air_date: String?,    // For TV series
    val media_type: String? = null  // Used in combined credits
)

// 📄 Detailed metadata for movies and TV series
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

data class Genre(
    val name: String
)

data class TmdbSeason(
    val season_number: Int
)

// 📺 Episode list per season
data class TmdbSeasonDetail(
    val episodes: List<TmdbEpisode>
)

data class TmdbEpisode(
    val episode_number: Int,
    val name: String,
    val overview: String,
    val still_path: String?
)

// 🎬 Trailer and video info
data class TmdbVideoResult(
    val results: List<TmdbVideo>
)

data class TmdbVideo(
    val key: String,
    val site: String,
    val type: String
)

// 👥 Cast and crew
data class TmdbCredits(
    val cast: List<TmdbCast>
)

data class TmdbCast(
    val name: String,
    val character: String,
    val profile_path: String?
)

// 🗣️ Reviews and ratings
data class TmdbReviewResult(
    val results: List<TmdbReview>
)

data class TmdbReview(
    val author: String,
    val content: String,
    val author_details: AuthorDetails
)

data class AuthorDetails(
    val rating: Double?
)

// 🎭 Combined credits for actor-based search
data class TmdbCreditsResult(
    val cast: List<TmdbItem>
)
