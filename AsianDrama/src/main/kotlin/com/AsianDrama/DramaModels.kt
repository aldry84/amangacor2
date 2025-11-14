package com.AsianDrama

import com.fasterxml.jackson.annotation.JsonProperty

// Domain response model
data class DomainResponse(
    @JsonProperty("dramadrip") val dramadrip: String
)

// Cinemeta response model
data class CinemetaResponse(
    @JsonProperty("meta") val meta: CinemetaMeta?
)

data class CinemetaMeta(
    @JsonProperty("id") val id: String?,
    @JsonProperty("imdb_id") val imdbId: String?,
    @JsonProperty("type") val type: String?,
    @JsonProperty("name") val name: String?,
    @JsonProperty("poster") val poster: String?,
    @JsonProperty("background") val background: String?,
    @JsonProperty("logo") val logo: String?,
    @JsonProperty("description") val description: String?,
    @JsonProperty("releaseInfo") val releaseInfo: String?,
    @JsonProperty("runtime") val runtime: String?,
    @JsonProperty("cast") val cast: List<String>?,
    @JsonProperty("genre") val genre: List<String>?,
    @JsonProperty("imdbRating") val rating: String?,
    @JsonProperty("trailer") val trailer: String?
)

// Link data model for passing between load and loadLinks
data class AsianDramaLinkData(
    val title: String? = null,
    val year: Int? = null,
    val tmdbId: Int? = null,
    val imdbId: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val rawLinks: List<String> = emptyList()
)
