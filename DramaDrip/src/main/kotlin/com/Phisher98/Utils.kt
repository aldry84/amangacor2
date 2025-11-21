package com.Phisher98

import android.os.Build
import android.util.Base64
import androidx.annotation.RequiresApi
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.Qualities
import java.net.URI
import java.security.MessageDigest
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.text.isLowerCase

// --- Data Classes ---
data class Meta(
    val id: String?, val imdb_id: String?, val type: String?, val poster: String?, val logo: String?, val background: String?,
    val moviedb_id: Int?, val name: String?, val description: String?, val genre: List<String>?, val releaseInfo: String?,
    val status: String?, val runtime: String?, val cast: List<String>?, val language: String?, val country: String?,
    val imdbRating: String?, val slug: String?, val year: String?, val videos: List<EpisodeDetails>?
)

data class EpisodeDetails(
    val id: String?, val name: String?, val title: String?, val season: Int?, val episode: Int?,
    val released: String?, val overview: String?, val thumbnail: String?, val moviedb_id: Int?
)

data class ResponseData(val meta: Meta?)

// --- Helpers ---
fun getLanguageNameFromCode(code: String?): String? {
    return code?.split("_")?.first()?.let { langCode ->
        try {
            Locale(langCode).displayLanguage.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
            }
        } catch (e: Exception) { langCode }
    }
}

fun getEpisodeSlug(season: Int? = null, episode: Int? = null): Pair<String, String> {
    return if (season == null && episode == null) "" to "" else (if (season!! < 10) "0$season" else "$season") to (if (episode!! < 10) "0$episode" else "$episode")
}

fun String?.createSlug(): String? = this?.filter { it.isWhitespace() || it.isLetterOrDigit() }?.trim()?.replace("\\s+".toRegex(), "-")?.lowercase()

fun getQualityFromName(str: String?): Int {
    return when {
        str == null -> Qualities.Unknown.value
        str.contains("4k", true) || str.contains("2160", true) -> Qualities.P2160.value
        str.contains("1080", true) -> Qualities.P1080.value
        str.contains("720", true) -> Qualities.P720.value
        str.contains("480", true) -> Qualities.P480.value
        str.contains("360", true) -> Qualities.P360.value
        else -> Qualities.Unknown.value
    }
}

fun getBaseUrl(url: String): String = URI(url).let { "${it.scheme}://${it.host}" }

fun fixUrl(url: String, domain: String): String {
    if (url.startsWith("http")) return url
    if (url.isEmpty()) return ""
    if (url.startsWith("//")) return "https:$url"
    return if (url.startsWith('/')) domain + url else "$domain/$url"
}

fun String.fixUrlBloat(): String = this.replace("\"", "").replace("\\", "")

@RequiresApi(Build.VERSION_CODES.O)
fun base64Decode(string: String): String {
    val clean = string.trim().replace("\n", "").replace("\r", "")
    val padded = clean.padEnd((clean.length + 3) / 4 * 4, '=')
    return try { String(Base64.decode(padded, Base64.DEFAULT), Charsets.UTF_8) } catch (e: Exception) { "" }
}

fun base64UrlEncode(input: ByteArray): String = Base64.encodeToString(input, Base64.DEFAULT).replace("+", "-").replace("/", "_").replace("=", "").trim()

fun String.xorDecrypt(key: String): String {
    val sb = StringBuilder()
    var i = 0
    while (i < this.length) {
        var j = 0
        while (j < key.length && i < this.length) {
            sb.append((this[i].code xor key[j].code).toChar())
            j++
            i++
        }
    }
    return sb.toString()
}

fun generateWpKey(r: String, m: String): String {
    val rList = r.split("\\x").toTypedArray()
    var n = ""
    val decodedM = base64Decode(m.reversed())
    for (s in decodedM.split("|")) { n += "\\x" + rList[Integer.parseInt(s) + 1] }
    return n
}

// --- Kriptografi ---
object VidrockHelper {
    private const val Ww = "x7k9mPqT2rWvY8zA5bC3nF6hJ2lK4mN9"
    fun encrypt(r: Int?, e: String, t: Int?, n: Int?): String {
        val s = if (e == "tv") "${r}_${t}_${n}" else r.toString()
        val keyBytes = Ww.toByteArray(Charsets.UTF_8)
        val ivBytes = Ww.substring(0, 16).toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"), IvParameterSpec(ivBytes))
        return base64UrlEncode(cipher.doFinal(s.toByteArray(Charsets.UTF_8)))
    }
}

object VidsrcHelper {
    fun encryptAesCbc(plainText: String, keyText: String): String {
        val sha256 = MessageDigest.getInstance("SHA-256")
        val keyBytes = sha256.digest(keyText.toByteArray(Charsets.UTF_8))
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"), IvParameterSpec(ByteArray(16) { 0 }))
        return base64UrlEncode(cipher.doFinal(plainText.toByteArray(Charsets.UTF_8)))
    }
}

// --- Bypass Functions ---
suspend fun bypassHrefli(url: String): String? {
    return try {
        val res = app.get(url).documentLarge
        val form = res.select("form#landing")
        val data = form.select("input").associate { it.attr("name") to it.attr("value") }
        val res2 = app.post(form.attr("action"), data = data).documentLarge
        val skToken = res2.selectFirst("script:containsData(?go=)")?.data()?.substringAfter("?go=")?.substringBefore("\"") ?: return null
        app.get(getBaseUrl(url) + "?go=$skToken", cookies = mapOf(skToken to (data["_wp_http2"] ?: ""))).documentLarge
            .selectFirst("meta[http-equiv=refresh]")?.attr("content")?.substringAfter("url=")
    } catch (e: Exception) { null }
}

@RequiresApi(Build.VERSION_CODES.O)
suspend fun cinematickitBypass(url: String): String? {
    return try {
        val encoded = url.substringAfter("safelink=").substringBefore("-")
        val goValue = app.get(base64Decode(encoded)).documentLarge.select("input[name=go]").attr("value")
        val nextUrl = base64Decode(goValue)
        if(nextUrl.startsWith("http")) nextUrl else null
    } catch (e: Exception) { null }
}

// --- FUNGSI YANG HILANG DIKEMBALIKAN ---
@RequiresApi(Build.VERSION_CODES.O)
suspend fun cinematickitloadBypass(url: String): String? {
    return try {
        val encodedLink = url.replace("&#038;", "&").substringAfter("safelink=").substringBefore("-")
        if (encodedLink.isEmpty()) return null
        val goValue = app.get(base64Decode(encodedLink)).documentLarge.select("form#landing input[name=go]").attr("value")
        return base64Decode(goValue)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
