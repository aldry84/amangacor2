package com.AdiDrakor

import com.fasterxml.jackson.annotation.JsonProperty

data class Data(
    val title: String?,
    val eps: Int?,
    val id: Int?,
    val epsId: Int?,
)

data class Sources(
    @JsonProperty("Video") val video: String?,
    @JsonProperty("ThirdParty") val thirdParty: String?,
)

data class Subtitle(
    @JsonProperty("src") val src: String?,
    @JsonProperty("label") val label: String?,
)

data class Responses(
    @JsonProperty("data") val data: ArrayList<Media>? = arrayListOf(),
)

data class Media(
    @JsonProperty("episodesCount") val episodesCount: Int?,
    @JsonProperty("thumbnail") val thumbnail: String?,
    @JsonProperty("label") val label: String?,
    @JsonProperty("id") val id: Int?,
    @JsonProperty("title") val title: String?,
)

data class Episodes(
    @JsonProperty("id") val id: Int?,
    @JsonProperty("number") val number: Double?,
    @JsonProperty("sub") val sub: Int?,
)

data class MediaDetail(
    @JsonProperty("description") val description: String?,
    @JsonProperty("releaseDate") val releaseDate: String?,
    @JsonProperty("status") val status: String?,
    @JsonProperty("type") val type: String?,
    @JsonProperty("country") val country: String?,
    @JsonProperty("episodes") val episodes: ArrayList<MediaEpisodes>? = arrayListOf(),
    @JsonProperty("thumbnail") val thumbnail: String?,
    @JsonProperty("id") val id: Int?,
    @JsonProperty("title") val title: String?,
)

// Menggunakan nama MediaEpisodes untuk menghindari konflik nama jika ada
data class MediaEpisodes(
    @JsonProperty("id") val id: Int?,
    @JsonProperty("number") val number: Double?,
    @JsonProperty("sub") val sub: Int?,
)

data class Key(
    val id: String,
    val version: String,
    val key: String,
)
