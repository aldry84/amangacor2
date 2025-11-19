package com.AdiDrakor

import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.nicehttp.RequestBodyTypes
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

// Variable global untuk cookie
var gomoviesCookies: Map<String, String>? = null

object AdiDrakorUtils {
    // Helper Enkripsi Vidsrc
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

    // Helper Enkripsi Vidrock
    object VidrockHelper {
        private const val Ww = "x7k9mPqT2rWvY8zA5bC3nF6hJ2lK4mN9"
        fun encrypt(r: Int?, e: String, t: Int?, n: Int?): String {
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
}

// Fungsi Global Helper
fun base64Decode(input: String): String = String(android.util.Base64.decode(input, android.util.Base64.DEFAULT))
fun base64Encode(input: ByteArray): String = android.util.Base64.encodeToString(input, android.util.Base64.DEFAULT).trim()

fun base64UrlEncode(input: ByteArray): String {
    return base64Encode(input)
        .replace("+", "-")
        .replace("/", "_")
        .replace("=", "")
}

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

fun getEpisodeSlug(season: Int? = null, episode: Int? = null): Pair<String, String> {
    return if (season == null && episode == null) {
        "" to ""
    } else {
        (if (season!! < 10) "0$season" else "$season") to (if (episode!! < 10) "0$episode" else "$episode")
    }
}

fun String?.createSlug(): String? {
    return this?.filter { it.isWhitespace() || it.isLetterOrDigit() }
        ?.trim()
        ?.replace("\\s+".toRegex(), "-")
        ?.lowercase()
}

fun fixUrl(url: String, domain: String): String {
    if (url.startsWith("http")) return url
    if (url.isEmpty()) return ""
    if (url.startsWith("//")) return "https:$url"
    return if (url.startsWith('/')) domain + url else "$domain/$url"
}

fun String.fixUrlBloat(): String = this.replace("\"", "").replace("\\", "")

fun getBaseUrl(url: String): String {
    return URI(url).let { "${it.scheme}://${it.host}" }
}

fun generateWpKey(r: String, m: String): String {
    val rList = r.split("\\x").toTypedArray()
    var n = ""
    fun safeBase64Decode(input: String): String {
        var paddedInput = input
        val remainder = input.length % 4
        if (remainder != 0) paddedInput += "=".repeat(4 - remainder)
        return base64Decode(paddedInput)
    }
    val decodedM = safeBase64Decode(m.reversed())
    for (s in decodedM.split("|")) {
        n += "\\x" + rList[Integer.parseInt(s) + 1]
    }
    return n
}

fun getQualityFromName(name: String): Int {
    return when {
        name.contains("4k", true) -> Qualities.P2160.value
        name.contains("1080", true) -> Qualities.P1080.value
        name.contains("720", true) -> Qualities.P720.value
        name.contains("480", true) -> Qualities.P480.value
        else -> Qualities.Unknown.value
    }
}

fun encode(input: String): String = URLEncoder.encode(input, "utf-8").replace("+", "%20")
