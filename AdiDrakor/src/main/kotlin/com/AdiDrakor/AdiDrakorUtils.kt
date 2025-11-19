package com.AdiDrakor

import android.os.Build
import android.util.Base64
import androidx.annotation.RequiresApi
import com.lagradost.cloudstream3.APIHolder.unixTimeMS
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.base64DecodeArray
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

val mimeType = arrayOf("video/x-matroska", "video/mp4", "video/x-msvideo")

fun getSeason(month: Int?): String? {
    val seasons = arrayOf("Winter", "Winter", "Spring", "Spring", "Spring", "Summer", "Summer", "Summer", "Fall", "Fall", "Fall", "Winter")
    if (month == null) return null
    return seasons[month - 1]
}

fun getDate(): TmdbDate {
    val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val calender = Calendar.getInstance()
    val today = formatter.format(calender.time)
    calender.add(Calendar.WEEK_OF_YEAR, 1)
    val nextWeek = formatter.format(calender.time)
    return TmdbDate(today, nextWeek)
}

fun isUpcoming(dateString: String?): Boolean {
    return try {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val dateTime = dateString?.let { format.parse(it)?.time } ?: return false
        unixTimeMS < dateTime
    } catch (t: Throwable) {
        logError(t)
        false
    }
}

fun String?.createSlug(): String? {
    return this?.filter { it.isWhitespace() || it.isLetterOrDigit() }
        ?.trim()
        ?.replace("\\s+".toRegex(), "-")
        ?.lowercase()
}

fun getKisskhTitle(str: String?): String? {
    return str?.replace(Regex("[^a-zA-Z\\d]"), "-")
}

fun getBaseUrl(url: String): String {
    return try {
        URI(url).let { "${it.scheme}://${it.host}" }
    } catch (e: Exception) {
        ""
    }
}

fun fixUrl(url: String, domain: String): String {
    if (url.startsWith("http")) return url
    if (url.isEmpty()) return ""
    val startsWithNoHttp = url.startsWith("//")
    if (startsWithNoHttp) return "https:$url"
    return if (url.startsWith('/')) domain + url else "$domain/$url"
}

// --- CRYPTO HELPERS ---

fun generateWpKey(r: String, m: String): String {
    val rList = r.split("\\x").toTypedArray()
    var n = ""
    val decodedM = String(android.util.Base64.decode(m.reversed(), android.util.Base64.DEFAULT))
    for (s in decodedM.split("|")) {
        n += "\\x" + rList[Integer.parseInt(s) + 1]
    }
    return n
}

@RequiresApi(Build.VERSION_CODES.O)
fun generateVrfAES(movieId: String, userId: String): String {
    val keyData = "secret_$userId".toByteArray(Charsets.UTF_8)
    val keyBytes = MessageDigest.getInstance("SHA-256").digest(keyData)
    val keySpec = SecretKeySpec(keyBytes, "AES")
    val ivSpec = IvParameterSpec(ByteArray(16))
    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
    cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
    val encrypted = cipher.doFinal(movieId.toByteArray(Charsets.UTF_8))
    return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(encrypted)
}

// Helper untuk VidZee
fun decryptVidzeeUrl(encrypted: String, key: ByteArray): String {
    val decoded = base64Decode(encrypted)
    val parts = decoded.split(":")
    if (parts.size != 2) throw IllegalArgumentException("Invalid encrypted format")

    val iv = base64DecodeArray(parts[0])
    val cipherData = base64DecodeArray(parts[1])

    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
    val secretKey = SecretKeySpec(key, "AES")
    cipher.init(Cipher.DECRYPT_MODE, secretKey, IvParameterSpec(iv))

    val decryptedBytes = cipher.doFinal(cipherData)
    return decryptedBytes.toString(Charsets.UTF_8)
}

// --- BYPASS HELPERS ---

suspend fun cinematickitBypass(url: String): String? {
    return try {
        val cleanedUrl = url.replace("&#038;", "&")
        val encodedLink = cleanedUrl.substringAfter("safelink=").substringBefore("-")
        if (encodedLink.isEmpty()) return null
        val decodedUrl = String(android.util.Base64.decode(encodedLink, android.util.Base64.DEFAULT))
        val doc = app.get(decodedUrl).document
        val goValue = doc.select("form#landing input[name=go]").attr("value")
        if (goValue.isBlank()) return null
        val decodedGoUrl = String(android.util.Base64.decode(goValue, android.util.Base64.DEFAULT)).replace("&#038;", "&")
        val responseDoc = app.get(decodedGoUrl).document
        val script = responseDoc.select("script").firstOrNull { it.data().contains("window.location.replace") }?.data() ?: return null
        val regex = Regex("""window\.location\.replace\s*\(\s*["'](.+?)["']\s*\)\s*;?""")
        val match = regex.find(script) ?: return null
        val redirectPath = match.groupValues[1]
        if (redirectPath.startsWith("http")) redirectPath else URI(decodedGoUrl).let { "${it.scheme}://${it.host}$redirectPath" }
    } catch (e: Exception) {
        null
    }
}

suspend fun bypassHrefli(url: String): String? {
    val host = getBaseUrl(url)
    var res = app.get(url).document
    var formUrl = res.select("form#landing").attr("action")
    var formData = res.select("form#landing input").associate { it.attr("name") to it.attr("value") }

    res = app.post(formUrl, data = formData).document
    formUrl = res.select("form#landing").attr("action")
    formData = res.select("form#landing input").associate { it.attr("name") to it.attr("value") }

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
