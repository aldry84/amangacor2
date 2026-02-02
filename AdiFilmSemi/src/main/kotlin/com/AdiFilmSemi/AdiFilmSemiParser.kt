package com.AdiFilmSemi

import com.fasterxml.jackson.annotation.JsonProperty

// ... (Biarkan kode data class lain tetap ada)

// ================== ADIMOVIEBOX MODELS (EXISTING/UPDATED) ==================
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

// ================== ADIMOVIEBOX 2 DATA CLASSES (NEW) ==================
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
