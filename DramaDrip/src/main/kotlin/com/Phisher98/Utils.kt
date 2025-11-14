package com.Phisher98

import android.os.Build
import androidx.annotation.RequiresApi
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.api.Log
import com.lagradost.cloudstream3.app
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.nodes.Document
import java.net.URI
import java.net.URLEncoder
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

data class DomainsParser(
    @JsonProperty("dramadrip")
    val dramadrip: String,
)

// --- TAMBAHAN UNTUK VIDROCK ---
data class VidrockSource(
    @JsonProperty("resolution") val resolution: Int? = null,
    @JsonProperty("url") val url: String? = null,
)

data class VidrockSubtitle(
    @JsonProperty("label") val label: String? = null,
    @JsonProperty("file") val file: String? = null,
)
// -----------------------------

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

data class ResponseData(
    val meta: Meta?
)

suspend fun bypassHrefli(url: String): String? {
    fun Document.getFormUrl(): String {
        return this.select("form#landing").attr("action")
    }

    fun Document.getFormData(): Map<String, String> {
        return this.select("form#landing input").associate { it.attr("name") to it.attr("value") }
    }

    val host = getBaseUrl(url)
    var res = app.get(url).document
    var formUrl = res.getFormUrl()
    var formData = res.getFormData()

    res = app.post(formUrl, data = formData).document
    formUrl = res.getFormUrl()
    formData = res.getFormData()

    res = app.post(formUrl, data = formData).document
    val skToken = res.selectFirst("script:containsData(?go=)")?.data()?.substringAfter("?go=")
        ?.substringBefore("\"") ?: return null
    val driveUrl = app.get(
        "$host?go=$skToken", cookies = mapOf(
            skToken to "${formData["_wp_http2"]}"
        )
    ).document.selectFirst("meta[http-equiv=refresh]")?.attr("content")?.substringAfter("url=")
    val path = app.get(driveUrl ?: return null).text.substringAfter("replace(\"")
        .substringBefore("\")")
    if (path == "/404") return null
    return fixUrl(path, getBaseUrl(driveUrl))
}

fun getBaseUrl(url: String): String {
    return URI(url).let {
        "${it.scheme}://${it.host}"
    }
}


fun fixUrl(url: String, domain: String): String {
    if (url.startsWith("http")) {
        return url
    }
    if (url.isEmpty()) {
        return ""
    }

    val startsWithNoHttp = url.startsWith("//")
    if (startsWithNoHttp) {
        return "https:$url"
    } else {
        if (url.startsWith('/')) {
            return domain + url
        }
        return "$domain/$url"
    }
}

@RequiresApi(Build.VERSION_CODES.O)
suspend fun cinematickitBypass(url: String): String? {
    return try {
        val cleanedUrl = url.replace("&#038;", "&")
        val encodedLink = cleanedUrl.substringAfter("safelink=").substringBefore("-")
        if (encodedLink.isEmpty()) return null
        val decodedUrl = base64Decode(encodedLink)
        val doc = app.get(decodedUrl).document
        val goValue = doc.select("form#landing input[name=go]").attr("value")
        if (goValue.isBlank()) return null
        val decodedGoUrl = base64Decode(goValue).replace("&#038;", "&")
        val responseDoc = app.get(decodedGoUrl).document
        val script = responseDoc.select("script").firstOrNull { it.data().contains("window.location.replace") }?.data() ?: return null
        val regex = Regex("""window\.location\.replace\s*\(\s*["'](.+?)["']\s*\)\s*;?""")
        val match = regex.find(script) ?: return null
        val redirectPath = match.groupValues[1]
        return if (redirectPath.startsWith("http")) redirectPath else URI(decodedGoUrl).let { "${it.scheme}://${it.host}$redirectPath" }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}


@RequiresApi(Build.VERSION_CODES.O)
suspend fun cinematickitloadBypass(url: String): String? {
    return try {
        val cleanedUrl = url.replace("&#038;", "&")
        val encodedLink = cleanedUrl.substringAfter("safelink=").substringBefore("-")
        if (encodedLink.isEmpty()) return null
        val decodedUrl = base64Decode(encodedLink)
        val doc = app.get(decodedUrl).document
        val goValue = doc.select("form#landing input[name=go]").attr("value")
        Log.d("Phisher",goValue)
        return base64Decode(goValue)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

@RequiresApi(Build.VERSION_CODES.O)
fun base64Decode(string: String): String {
    val clean = string.trim().replace("\n", "").replace("\r", "")
    val padded = clean.padEnd((clean.length + 3) / 4 * 4, '=')
    return try {
        val decodedBytes = Base64.getDecoder().decode(padded)
        String(decodedBytes, Charsets.UTF_8)
    } catch (e: Exception) {
        e.printStackTrace()
        ""
    }
}

// --- TAMBAHAN UNTUK VIDROCK ---
@RequiresApi(Build.VERSION_CODES.O)
fun base64UrlEncode(input: ByteArray): String {
    return Base64.getEncoder().encodeToString(input)
        .replace("+", "-")
        .replace("/", "_")
        .replace("=", "")
}

object VidrockHelper {
    private const val Ww = "x7k9mPqT2rWvY8zA5bC3nF6hJ2lK4mN9"

    @RequiresApi(Build.VERSION_CODES.O)
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
// -----------------------------
