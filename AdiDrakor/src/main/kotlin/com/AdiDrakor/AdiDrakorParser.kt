package com.AdiDrakor

import com.fasterxml.jackson.annotation.JsonProperty
import com.google.gson.annotations.SerializedName

// ==============================
// GENERAL & SUBTITLE APIS
// ==============================

data class SubtitlesAPI(
    val subtitles: List<Subtitle>,
    val cacheMaxAge: Long,
)

data class Subtitle(
    val id: String,
    val url: String,
    @JsonProperty("SubEncoding")
    val subEncoding: String,
    val lang: String,
    val m: String,
    val g: String,
)

data class WyZIESUB(
    val id: String,
    val url: String,
    val flagUrl: String,
    val format: String,
    val display: String,
    val language: String,
    val media: String,
    val isHearingImpaired: Boolean,
)

// ==============================
// CINEMA OS
// ==============================

data class CinemaOsSecretKeyRequest(
    val tmdbId: String,
    val seasonId: String,
    val episodeId: String
)

data class CinemaOSReponse(
    val data: CinemaOSReponseData,
    val encrypted: Boolean,
)

data class CinemaOSReponseData(
    val encrypted: String,
    val cin: String,
    val mao: String,
    val salt: String,
)

// ==============================
// VIDSRCCC / VIDSRCTO
// ==============================

data class Vidsrcccservers(
    val data: List<VidsrcccDaum>,
    val success: Boolean,
)

data class VidsrcccDaum(
    val name: String,
    val hash: String,
)

data class Vidsrcccm3u8(
    val data: VidsrcccData,
    val success: Boolean,
)

data class VidsrcccData(
    val type: String,
    val source: String,
)

// ==============================
// VIDLINK
// ==============================

data class Vidlink(
    val sourceId: String,
    val stream: VidlinkStream,
)

data class VidlinkStream(
    val id: String,
    val type: String,
    val playlist: String,
    val flags: List<String>,
    val captions: List<VidlinkCaption>,
    @JsonProperty("TTL")
    val ttl: Long,
)

data class VidlinkCaption(
    val id: String,
    val url: String,
    val language: String,
    val type: String,
    val hasCorsRestrictions: Boolean,
)

// ==============================
// RIVESTREAM
// ==============================

data class RiveStreamSource(
    val data: List<String>
)

data class RiveStreamResponse(
    val data: RiveStreamData,
)

data class RiveStreamData(
    val sources: List<RiveStreamSourceData>,
)

data class RiveStreamSourceData(
    val quality: String,
    val url: String,
    val source: String,
    val format: String,
)

// ==============================
// PLAYER4U
// ==============================

data class Player4uLinkData(
    val name: String,
    val url: String,
)

// ==============================
// WATCH32
// ==============================

data class Watch32(
    val type: String,
    val link: String,
)

// ==============================
// OXXFILE / HUBCLOUD (Required for XDMovies)
// ==============================

data class oxxfile(
    val id: String,
    val code: String,
    val fileName: String,
    val size: Long,
    val driveLinks: List<DriveLink>,
    val metadata: Metadata,
    val createdAt: String,
    val views: Long,
    val status: String,
    val hubcloudLink: String,
    val filepressLink: String,
    @SerializedName("credential_index")
    val credentialIndex: Long,
    val userName: String,
)

data class DriveLink(
    val fileId: String,
    val webViewLink: String,
    val driveLabel: String,
    val credentialIndex: Int,
    val isLoginDrive: Boolean,
    val isDrive2: Boolean
)

data class Metadata(
    val mimeType: String,
    val fileExtension: String,
    val modifiedTime: String,
    val createdTime: String,
)
