package com.AdiDrakor

import android.os.Build
import android.util.Base64
import com.lagradost.cloudstream3.APIHolder.unixTimeMS
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.nicehttp.RequestBodyTypes
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.KeySpec
import java.text.SimpleDateFormat
import java.util.Arrays
import java.util.Calendar
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.max

// ==========================================
// DATA CLASS UTILS
// ==========================================

data class TmdbDate(
    val today: String,
    val nextWeek: String,
)

// ==========================================
// DOMAIN MANAGER
// ==========================================
data class DomainConfig(
    val vidsrccc: String? = null,
    val vidsrcxyz: String? = null,
    val player4u: String? = null,
    val xdmovies: String? = null,
    val vidlink: String? = null,
    val watch32: String? = null,
    val cinemaos: String? = null,
    val rivestream: String? = null
)

object DomainManager {
    private const val CONFIG_URL = "https://raw.githubusercontent.com/phisher98/TVVVV/refs/heads/main/domains.json"

    var vidsrcccAPI = "https://vidsrc.cc"
    var vidsrcxyzAPI = "https://vidsrc-embed.su"
    var player4uAPI = "https://player4u.xyz"
    var xdmoviesAPI = "https://xdmovies.site"
    var vidlinkAPI = "https://vidlink.pro"
    var watch32API = "https://watch32.sx"
    var cinemaosAPI = "https://cinemaos.tech"
    var rivestreamAPI = "https://rivestream.org"

    // API Subtitle
    const val WyZIESUBAPI = "https://sub.wyzie.ru"
    const val SubtitlesAPI = "https://opensubtitles-v3.strem.io"

    suspend fun updateDomains() {
        try {
            val response = app.get(CONFIG_URL).parsedSafe<Map<String, String>>()
            response?.let {
                vidsrcccAPI = it["vidsrccc"] ?: vidsrcccAPI
                vidsrcxyzAPI = it["vidsrcxyz"] ?: vidsrcxyzAPI
                xdmoviesAPI = it["xdmovies"] ?: xdmoviesAPI
                watch32API = it["watch32"] ?: watch32API
            }
        } catch (e: Exception) {
            logError(e)
        }
    }
}

// ==========================================
// UTILITIES UMUM
// ==========================================

const val anilistAPI = "https://graphql.anilist.co"

fun getSeason(month: Int?): String? {
    val seasons = arrayOf("Winter", "Winter", "Spring", "Spring", "Spring", "Summer", "Summer", "Summer", "Fall", "Fall", "Fall", "Winter")
    return if (month == null) null else seasons[month - 1]
}

fun getDate(): TmdbDate {
    val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val cal = Calendar.getInstance()
    val today = formatter.format(cal.time)
    cal.add(Calendar.WEEK_OF_YEAR, 1)
    val nextWeek = formatter.format(cal.time)
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

fun getLanguage(code: String): String {
    val map = mapOf("id" to "Indonesian", "en" to "English", "ko" to "Korean")
    return map[code.lowercase()] ?: code
}

fun String.decodeHex(): ByteArray {
    check(length % 2 == 0) { "Must have an even length" }
    return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}

// ==========================================
// CRYPTO UTILS
// ==========================================

fun generateVrfAES(movieId: String, userId: String): String {
    val keyData = "secret_$userId".toByteArray(Charsets.UTF_8)
    val keyBytes = MessageDigest.getInstance("SHA-256").digest(keyData)
    val keySpec = SecretKeySpec(keyBytes, "AES")
    val ivSpec = IvParameterSpec(ByteArray(16))
    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
    cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
    val encrypted = cipher.doFinal(movieId.toByteArray(Charsets.UTF_8))
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(encrypted)
    } else {
        Base64.encodeToString(encrypted, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }
}

fun generateHashedString(): String {
    val s = "a8f7e9c2d4b6a1f3e8c9d2t4a7f6e9c2d4z6a1f3e8c9d2b4a7f5e9c2d4b6a1f3"
    val a = "2"
    val algorithm = "HmacSHA512"
    val keySpec = SecretKeySpec(s.toByteArray(StandardCharsets.UTF_8), algorithm)
    val mac = Mac.getInstance(algorithm)
    mac.init(keySpec)
    val input = "crypto_rotation_v${a}_seed_2025"
    val hmacBytes = mac.doFinal(input.toByteArray(StandardCharsets.UTF_8))
    val hex = hmacBytes.joinToString("") { "%02x".format(it) }
    return hex.repeat(3).substring(0, max(s.length, 128))
}

fun cinemaOSGenerateHash(t: CinemaOsSecretKeyRequest, isSeries: Boolean): String {
    val c = generateHashedString()
    val m: String = if (isSeries) "content_v3::contentId=${t.tmdbId}::partId=${t.episodeId}::seriesId=${t.seasonId}::environment=production" 
                    else "content_v3::contentId=${t.tmdbId}::environment=production"

    val hmac384 = Mac.getInstance("HmacSHA384")
    hmac384.init(SecretKeySpec(c.toByteArray(Charsets.UTF_8), "HmacSHA384"))
    hmac384.update(m.toByteArray(Charsets.UTF_8))
    val x = hmac384.doFinal().joinToString("") { "%02x".format(it) }

    val hmac512 = Mac.getInstance("HmacSHA512")
    hmac512.init(SecretKeySpec(x.toByteArray(Charsets.UTF_8), "HmacSHA512"))
    hmac512.update(c.takeLast(64).toByteArray(Charsets.UTF_8))
    return hmac512.doFinal().joinToString("") { "%02x".format(it) }
}

fun cinemaOSDecryptResponse(e: CinemaOSReponseData?): Any {
    val encrypted = e?.encrypted ?: return ""
    val cin = e.cin
    val mao = e.mao
    val salt = e.salt

    val keyBytes = "a1b2c3d4e4f6477658455678901477567890abcdef1234567890abcdef123456".toByteArray()
    val ivBytes = hexStringToByteArray(cin)
    val authTagBytes = hexStringToByteArray(mao)
    val encryptedBytes = hexStringToByteArray(encrypted)
    val saltBytes = hexStringToByteArray(salt)

    val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
    val spec = PBEKeySpec(keyBytes.map { it.toInt().toChar() }.toCharArray(), saltBytes, 100000, 256)
    val tmp = factory.generateSecret(spec)
    val key = SecretKeySpec(tmp.encoded, "AES")

    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    val gcmSpec = GCMParameterSpec(128, ivBytes)
    cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec)
    val decryptedBytes = cipher.doFinal(encryptedBytes + authTagBytes)
    return String(decryptedBytes)
}

fun hexStringToByteArray(hex: String): ByteArray {
    val len = hex.length
    val data = ByteArray(len / 2)
    var i = 0
    while (i < len) {
        data[i / 2] = ((Character.digit(hex[i], 16) shl 4) + Character.digit(hex[i + 1], 16)).toByte()
        i += 2
    }
    return data
}

val decryptMethods: Map<String, (String) -> String> = mapOf(
    "TsA2KGDGux" to { inputString ->
        inputString.reversed().replace("-", "+").replace("_", "/").let {
            val decoded = String(Base64.decode(it, Base64.DEFAULT))
            decoded.map { ch -> (ch.code - 7).toChar() }.joinToString("")
        }
    },
    "xTyBxQyGTA" to { inputString ->
        val filtered = inputString.reversed().filterIndexed { i, _ -> i % 2 == 0 }
        String(Base64.decode(filtered, Base64.DEFAULT))
    },
    "IhWrImMIGL" to { inputString ->
        val reversed = inputString.reversed()
        val rot13 = reversed.map { ch ->
            when {
                ch in 'a'..'m' || ch in 'A'..'M' -> (ch.code + 13).toChar()
                ch in 'n'..'z' || ch in 'N'..'Z' -> (ch.code - 13).toChar()
                else -> ch
            }
        }.joinToString("")
        String(Base64.decode(rot13.reversed(), Base64.DEFAULT))
    }
)
