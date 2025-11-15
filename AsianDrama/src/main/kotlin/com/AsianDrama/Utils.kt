package com.AsianDrama

import android.os.Build
import android.util.Base64
import androidx.annotation.RequiresApi
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.APIHolder.unixTimeMS
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.nicehttp.RequestBodyTypes
import com.lagradost.api.Log
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.nodes.Document
import java.net.*
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.text.isLowerCase
import com.fasterxml.jackson.annotation.JsonProperty // FIX: Tambahkan import JsonProperty

// API dari SoraStream (Diperlukan oleh beberapa fungsi)
const val anilistAPI = "https://graphql.anilist.co"

var gomoviesCookies: Map<String, String>? = null

val mimeType = arrayOf(
    "video/x-matroska",
    "video/mp4",
    "video/x-msvideo"
)

// ========= Fungsi dari SoraUtils.kt - Mulai =========

suspend fun convertTmdbToAnimeId(
// ... (fungsi ini tidak berubah) ...
)

suspend fun tmdbToAnimeId(title: String?, year: Int?, season: String?, type: TvType): AniIds {
// ... (fungsi ini tidak berubah) ...
}

fun generateWpKey(r: String, m: String): String {
// ... (fungsi ini tidak berubah) ...
}

fun safeBase64Decode(input: String): String {
// ... (fungsi ini tidak berubah) ...
}

fun getSeason(month: Int?): String? {
// ... (fungsi ini tidak berubah) ...
}

fun getEpisodeSlug(
// ... (fungsi ini tidak berubah) ...
): Pair<String, String> {
// ... (fungsi ini tidak berubah) ...
}

fun getTitleSlug(title: String? = null): Pair<String?, String?> {
    val slug = title.createSlug()
    return slug?.replace("-", "\\W") to title?.replace(" ", "_")
}

fun getIndexQuery(
// ... (fungsi ini tidak berubah) ...
): String {
// ... (fungsi ini tidak berubah) ...
}

fun searchIndex(
    title: String? = null,
    season: Int? = null,
    episode: Int? = null,
    year: Int? = null,
    response: String,
    isTrimmed: Boolean = true,
): List<IndexMedia>? {
    val files = tryParseJson<IndexSearch>(response)?.data?.files?.filter { media ->
        matchingIndex( // FIX: matchingIndex
            media.name ?: return null,
            media.mimeType ?: return null,
            title ?: return null,
            year,
            season,
            episode
        )
    }?.distinctBy { it.name }?.sortedByDescending { it.size?.toLongOrNull() ?: 0 } ?: return null

    return if (isTrimmed) {
        files.let { file ->
            listOfNotNull(
                file.find { it.name?.contains("2160p", true) == true },
                file.find { it.name?.contains("1080p", true) == true }
            )
        }
    } else {
        files
    }
}

fun matchingIndex(
// ... (fungsi ini tidak berubah) ...
): Boolean {
// ... (fungsi ini tidak berubah) ...
}

fun decodeIndexJson(json: String): String {
// ... (fungsi ini tidak berubah) ...
}

fun String.xorDecrypt(key: String): String {
// ... (fungsi ini tidak berubah) ...
}

fun vidsrctoDecrypt(text: String): String {
// ... (fungsi ini tidak berubah) ...
}

fun String?.createSlug(): String? {
// ... (fungsi ini tidak berubah) ...
}

// ... (semua fungsi getLanguage hingga fixUrl) ...

fun isUpcoming(dateString: String?): Boolean {
// ... (fungsi ini tidak berubah) ...
}

fun getDate(): TmdbDate {
// ... (fungsi ini tidak berubah) ...
}

fun decode(input: String): String = URLDecoder.decode(input, "utf-8")

fun encode(input: String): String = URLEncoder.encode(input, "utf-8").replace("+", "%20")

fun base64DecodeAPI(api: String): String {
// ... (fungsi ini tidak berubah) ...
}

fun base64UrlEncode(input: ByteArray): String {
// ... (fungsi ini tidak berubah) ...
}

// Pindahkan enum Symbol ke luar fungsi Int.toRomanNumeral
private enum class Symbol(val decimalValue: Int) {
    I(1),
    IV(4),
    V(5),
    IX(9),
    X(10);

    companion object {
        fun closestBelow(value: Int) =
            entries.toTypedArray()
                .sortedByDescending { it.decimalValue }
                .firstOrNull { value >= it.decimalValue }
    }
}

fun Int.toRomanNumeral(): String = Symbol.closestBelow(this) // FIX: Symbol
    .let { symbol ->
        if (symbol != null) {
            "${symbol.name}${(this - symbol.decimalValue).toRomanNumeral()}"
        } else {
            ""
        }
    }

// Pindahkan objects VidrockHelper dan VidsrcHelper ke luar
object VidrockHelper {
    private const val Ww = "x7k9mPqT2rWvY8zA5bC3nF6hJ2lK4mN9"

    fun encrypt(
        r: Int?,
        e: String,
        t: Int?,
        n: Int?
    ): String {
        val s = if (e == "tv") "${r}_${t}_${n}" else r.toString()
        val keyBytes = Ww.toByteArray(Charsets.UTF_8)
        val ivBytes = Ww.substring(0, 16).toByteArray(Charsets.UTF_8)

        val secretKey = SecretKeySpec(keyBytes, "AES")
        val ivSpec = IvParameterSpec(ivBytes)

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec)
        val encrypted = cipher.doFinal(s.toByteArray(Charsets.UTF_8))
        return base64UrlEncode(encrypted)
    }
}

object VidsrcHelper {

    fun encryptAesCbc(plainText: String, keyText: String): String {
        val sha256 = MessageDigest.getInstance("SHA-256")
        val keyBytes = sha256.digest(keyText.toByteArray(Charsets.UTF_8))
        val secretKey = SecretKeySpec(keyBytes, "AES")

        val iv = ByteArray(16) { 0 }
        val ivSpec = IvParameterSpec(iv)

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec)

        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return base64UrlEncode(encrypted)
    }

}
// ========= Fungsi dari SoraUtils.kt - Selesai =========

// ... (Fungsi-fungsi lama: cinematickitBypass, bypassHrefli, dll. tidak berubah) ...

// Tambahkan Data Class lama yang masih digunakan oleh AsianDrama.kt
data class DomainsParser(
    @JsonProperty("dramadrip")
    val dramadrip: String,
)

data class ResponseData(
    val meta: Meta?
)

data class Meta(
    val id: String?,
    val imdb_id: String?,
    val type: String?,
    val poster: String?,
    val logo: String?,
    val background: String?,
    val moviedb_id: Int?,
    val name: String?,
    val description: String?,
    val genre: List<String>?,
    val releaseInfo: String?,
    val status: String?,
    val runtime: String?,
    val cast: List<String>?,
    val language: String?,
    val country: String?,
    val imdbRating: String?,
    val slug: String?,
    val year: String?,
    val videos: List<EpisodeDetails>?
)

data class EpisodeDetails(
    val id: String?,
    val name: String?,
    val title: String?,
    val season: Int?,
    val episode: Int?,
    val released: String?,
    val overview: String?,
    val thumbnail: String?,
    val moviedb_id: Int?
)
