package com.AdiDrakor

import android.os.Build
import androidx.annotation.RequiresApi
import com.AdiDrakor.AdiDrakor.Companion.anilistAPI
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.APIHolder.unixTimeMS
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.nicehttp.RequestBodyTypes
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.*
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.KeySpec
import java.text.SimpleDateFormat
import java.util.*
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.min

var gomoviesCookies: Map<String, String>? = null

val mimeType = arrayOf(
    "video/x-matroska",
    "video/mp4",
    "video/x-msvideo"
)

// --- StreamPlay Utils Port Start ---

object CryptoAES {
    private const val KEY_SIZE = 32 // 256 bits
    private const val IV_SIZE = 16 // 128 bits
    private const val SALT_SIZE = 8 // 64 bits
    private const val HASH_CIPHER = "AES/CBC/PKCS7PADDING"
    private const val HASH_CIPHER_FALLBACK = "AES/CBC/PKCS5PADDING"
    private const val AES = "AES"
    private const val KDF_DIGEST = "MD5"

    fun decryptWithSalt(cipherText: String, salt: String, password: String): String {
        return try {
            val ctBytes = base64DecodeArray(cipherText)
            val md5: MessageDigest = MessageDigest.getInstance("MD5")
            val keyAndIV = generateKeyAndIV(
                KEY_SIZE,
                IV_SIZE,
                1,
                salt.decodeHex(),
                password.toByteArray(Charsets.UTF_8),
                md5,
            )
            decryptAES(
                ctBytes,
                keyAndIV?.get(0) ?: ByteArray(KEY_SIZE),
                keyAndIV?.get(1) ?: ByteArray(IV_SIZE),
            )
        } catch (e: Exception) {
            ""
        }
    }

    private fun decryptAES(
        cipherTextBytes: ByteArray,
        keyBytes: ByteArray,
        ivBytes: ByteArray
    ): String {
        return try {
            val cipher = try {
                Cipher.getInstance(HASH_CIPHER)
            } catch (e: Throwable) {
                Cipher.getInstance(HASH_CIPHER_FALLBACK)
            }
            val keyS = SecretKeySpec(keyBytes, AES)
            cipher.init(Cipher.DECRYPT_MODE, keyS, IvParameterSpec(ivBytes))
            cipher.doFinal(cipherTextBytes).toString(Charsets.UTF_8)
        } catch (e: Exception) {
            ""
        }
    }

    private fun generateKeyAndIV(
        keyLength: Int,
        ivLength: Int,
        iterations: Int,
        salt: ByteArray,
        password: ByteArray,
        md: MessageDigest,
    ): Array<ByteArray?>? {
        val digestLength = md.digestLength
        val requiredLength = (keyLength + ivLength + digestLength - 1) / digestLength * digestLength
        val generatedData = ByteArray(requiredLength)
        var generatedLength = 0
        return try {
            md.reset()
            while (generatedLength < keyLength + ivLength) {
                if (generatedLength > 0) md.update(
                    generatedData,
                    generatedLength - digestLength,
                    digestLength
                )
                md.update(password)
                md.update(salt, 0, SALT_SIZE)
                md.digest(generatedData, generatedLength, digestLength)
                for (i in 1 until iterations) {
                    md.update(generatedData, generatedLength, digestLength)
                    md.digest(generatedData, generatedLength, digestLength)
                }
                generatedLength += digestLength
            }
            val result = arrayOfNulls<ByteArray>(2)
            result[0] = generatedData.copyOfRange(0, keyLength)
            if (ivLength > 0) result[1] = generatedData.copyOfRange(keyLength, keyLength + ivLength)
            result
        } catch (e: Exception) {
            throw e
        } finally {
            Arrays.fill(generatedData, 0.toByte())
        }
    }

    private fun String.decodeHex(): ByteArray {
        check(length % 2 == 0) { "Must have an even length" }
        return chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }
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
    return Base64.getUrlEncoder().withoutPadding().encodeToString(encrypted)
}

fun vidrockEncode(tmdb: String, type: String, season: Int? = null, episode: Int? = null): String {
    val base = if (type == "tv" && season != null && episode != null) {
        "$tmdb-$season-$episode"
    } else {
        val map = mapOf(
            '0' to 'a', '1' to 'b', '2' to 'c', '3' to 'd', '4' to 'e',
            '5' to 'f', '6' to 'g', '7' to 'h', '8' to 'i', '9' to 'j'
        )
        tmdb.map { map[it] ?: it }.joinToString("")
    }
    val reversed = base.reversed()
    val firstEncode = base64Encode(reversed.toByteArray())
    val doubleEncode = base64Encode(firstEncode.toByteArray())
    return doubleEncode
}

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

fun derivePbkdf2Key(password: String, salt: ByteArray, iterations: Int, keyLength: Int): ByteArray {
    val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
    val spec: KeySpec = PBEKeySpec(password.toCharArray(), salt, iterations, keyLength * 8)
    return factory.generateSecret(spec).encoded
}

fun unpadData(data: ByteArray): ByteArray {
    val padding = data[data.size - 1].toInt() and 0xFF
    if (padding < 1 || padding > data.size) return data
    return data.copyOf(data.size - padding)
}

fun hexStringToByteArray2(hex: String): ByteArray {
    val result = ByteArray(hex.length / 2)
    for (i in hex.indices step 2) {
        val value = hex.substring(i, i + 2).toInt(16)
        result[i / 2] = value.toByte()
    }
    return result
}

fun toHex(bytes: ByteArray): String {
    return bytes.joinToString("") { "%02x".format(it) }
}

fun generateKeyIv(keySize: Int = 32): KeyIvResult {
    val secureRandom = SecureRandom()
    val keyBytes = ByteArray(keySize)
    secureRandom.nextBytes(keyBytes)
    val ivBytes = ByteArray(16)
    secureRandom.nextBytes(ivBytes)
    return KeyIvResult(
        keyBytes = keyBytes,
        ivBytes = ivBytes,
        keyHex = toHex(keyBytes),
        ivHex = toHex(ivBytes)
    )
}

fun hasHost(url: String): Boolean {
    return try {
        val host = URL(url).host
        !host.isNullOrEmpty()
    } catch (e: Exception) {
        false
    }
}

// --- End StreamPlay Utils Port ---

suspend fun convertTmdbToAnimeId(
    title: String?,
    date: String?,
    airedDate: String?,
    type: TvType
): AniIds {
    val sDate = date?.split("-")
    val sAiredDate = airedDate?.split("-")

    val year = sDate?.firstOrNull()?.toIntOrNull()
    val airedYear = sAiredDate?.firstOrNull()?.toIntOrNull()
    val season = getSeason(sDate?.get(1)?.toIntOrNull())
    val airedSeason = getSeason(sAiredDate?.get(1)?.toIntOrNull())

    return if (type == TvType.AnimeMovie) {
        tmdbToAnimeId(title, airedYear, "", type)
    } else {
        val ids = tmdbToAnimeId(title, year, season, type)
        if (ids.id == null && ids.idMal == null) tmdbToAnimeId(
            title,
            airedYear,
            airedSeason,
            type
        ) else ids
    }
}

suspend fun tmdbToAnimeId(title: String?, year: Int?, season: String?, type: TvType): AniIds {
    val query = """
        query (
          ${'$'}page: Int = 1
          ${'$'}search: String
          ${'$'}sort: [MediaSort] = [POPULARITY_DESC, SCORE_DESC]
          ${'$'}type: MediaType
          ${'$'}season: MediaSeason
          ${'$'}seasonYear: Int
          ${'$'}format: [MediaFormat]
        ) {
          Page(page: ${'$'}page, perPage: 20) {
            media(
              search: ${'$'}search
              sort: ${'$'}sort
              type: ${'$'}type
              season: ${'$'}season
              seasonYear: ${'$'}seasonYear
              format_in: ${'$'}format
            ) {
              id
              idMal
            }
          }
        }
    """.trimIndent().trim()

    val variables = mapOf(
        "search" to title,
        "sort" to "SEARCH_MATCH",
        "type" to "ANIME",
        "season" to season?.uppercase(),
        "seasonYear" to year,
        "format" to listOf(if (type == TvType.AnimeMovie) "MOVIE" else "TV")
    ).filterValues { value -> value != null && value.toString().isNotEmpty() }

    val data = mapOf(
        "query" to query,
        "variables" to variables
    ).toJson().toRequestBody(RequestBodyTypes.JSON.toMediaTypeOrNull())

    val res = app.post(anilistAPI, requestBody = data)
        .parsedSafe<AniSearch>()?.data?.Page?.media?.firstOrNull()
    return AniIds(res?.id, res?.idMal)

}

fun generateWpKey(r: String, m: String): String {
    val rList = r.split("\\x").toTypedArray()
    var n = ""
    val decodedM = safeBase64Decode(m.reversed())
    for (s in decodedM.split("|")) {
        n += "\\x" + rList[Integer.parseInt(s) + 1]
    }
    return n
}

fun safeBase64Decode(input: String): String {
    var paddedInput = input
    val remainder = input.length % 4
    if (remainder != 0) {
        paddedInput += "=".repeat(4 - remainder)
    }
    return base64Decode(paddedInput)
}

fun getSeason(month: Int?): String? {
    val seasons = arrayOf(
        "Winter", "Winter", "Spring", "Spring", "Spring", "Summer",
        "Summer", "Summer", "Fall", "Fall", "Fall", "Winter"
    )
    if (month == null) return null
    return seasons[month - 1]
}

fun getEpisodeSlug(
    season: Int? = null,
    episode: Int? = null,
): Pair<String, String> {
    return if (season == null && episode == null) {
        "" to ""
    } else {
        (if (season!! < 10) "0$season" else "$season") to (if (episode!! < 10) "0$episode" else "$episode")
    }
}

fun getTitleSlug(title: String? = null): Pair<String?, String?> {
    val slug = title.createSlug()
    return slug?.replace("-", "\\W") to title?.replace(" ", "_")
}

fun getIndexQuery(
    title: String? = null,
    year: Int? = null,
    season: Int? = null,
    episode: Int? = null
): String {
    val (seasonSlug, episodeSlug) = getEpisodeSlug(season, episode)
    return (if (season == null) {
        "$title ${year ?: ""}"
    } else {
        "$title S${seasonSlug}E${episodeSlug}"
    }).trim()
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
        matchingIndex(
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
    mediaName: String?,
    mediaMimeType: String?,
    title: String?,
    year: Int?,
    season: Int?,
    episode: Int?,
    include720: Boolean = false
): Boolean {
    val (wSlug, dwSlug) = getTitleSlug(title)
    val (seasonSlug, episodeSlug) = getEpisodeSlug(season, episode)
    return (if (season == null) {
        mediaName?.contains(Regex("(?i)(?:$wSlug|$dwSlug).*$year")) == true
    } else {
        mediaName?.contains(Regex("(?i)(?:$wSlug|$dwSlug).*S${seasonSlug}.?E${episodeSlug}")) == true
    }) && mediaName?.contains(
        if (include720) Regex("(?i)(2160p|1080p|720p)") else Regex("(?i)(2160p|1080p)")
    ) == true && ((mediaMimeType in mimeType) || mediaName.contains(Regex("\\.mkv|\\.mp4|\\.avi")))
}

fun decodeIndexJson(json: String): String {
    val slug = json.reversed().substring(24)
    return base64Decode(slug.substring(0, slug.length - 20))
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

fun String?.createSlug(): String? {
    return this?.filter { it.isWhitespace() || it.isLetterOrDigit() }
        ?.trim()
        ?.replace("\\s+".toRegex(), "-")
        ?.lowercase()
}

fun getLanguage(str: String): String {
    return if (str.contains("(in_ID)")) "Indonesian" else str
}

fun bytesToGigaBytes(number: Double): Double = number / 1024000000

fun getKisskhTitle(str: String?): String? {
    return str?.replace(Regex("[^a-zA-Z\\d]"), "-")
}

fun String.getFileSize(): Float? {
    val size = Regex("(?i)(\\d+\\.?\\d+\\sGB|MB)").find(this)?.groupValues?.get(0)?.trim()
    val num = Regex("(\\d+\\.?\\d+)").find(size ?: return null)?.groupValues?.get(0)?.toFloat()
        ?: return null
    return when {
        size.contains("GB") -> num * 1000000
        else -> num * 1000
    }
}

fun getUhdTags(str: String?): String {
    return Regex("\\d{3,4}[Pp]\\.?(.*?)\\[").find(str ?: "")?.groupValues?.getOrNull(1)
        ?.replace(".", " ")?.trim()
        ?: str ?: ""
}

fun getIndexQualityTags(str: String?, fullTag: Boolean = false): String {
    return if (fullTag) Regex("(?i)(.*)\\.(?:mkv|mp4|avi)").find(str ?: "")?.groupValues?.get(1)
        ?.trim() ?: str ?: "" else Regex("(?i)\\d{3,4}[pP]\\.?(.*?)\\.(mkv|mp4|avi)").find(
        str ?: ""
    )?.groupValues?.getOrNull(1)
        ?.replace(".", " ")?.trim() ?: str ?: ""
}

fun getIndexQuality(str: String?): Int {
    return Regex("(\\d{3,4})[pP]").find(str ?: "")?.groupValues?.getOrNull(1)?.toIntOrNull()
        ?: Qualities.Unknown.value
}

fun getIndexSize(str: String?): String? {
    return Regex("(?i)([\\d.]+\\s*(?:gb|mb))").find(str ?: "")?.groupValues?.getOrNull(1)?.trim()
}

fun getQuality(str: String): Int {
    return when (str) {
        "360p" -> Qualities.P240.value
        "480p" -> Qualities.P360.value
        "720p" -> Qualities.P480.value
        "1080p" -> Qualities.P720.value
        "1080p Ultra" -> Qualities.P1080.value
        else -> getQualityFromName(str)
    }
}

fun getQualityFromName(qualityName: String): Int {
    return when (qualityName.replace("p", "").trim()) {
        "360" -> Qualities.P360.value
        "480" -> Qualities.P480.value
        "720" -> Qualities.P720.value
        "1080" -> Qualities.P1080.value
        "1440" -> Qualities.P1440.value
        "2160", "4k", "4K" -> Qualities.P2160.value
        else -> Qualities.Unknown.value
    }
}

fun getLanguageNameFromCode(code: String?): String? {
    return code?.split("_")?.first()?.let { langCode ->
        try {
            Locale(langCode).displayLanguage.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
            }
        } catch (e: Exception) {
            langCode
        }
    }
}

fun getDeviceId(length: Int = 16): String {
    val allowedChars = ('a'..'f') + ('0'..'9')
    return (1..length)
        .map { allowedChars.random() }
        .joinToString("")
}

fun String.encodeUrl(): String {
    val url = URL(this)
    val uri = URI(url.protocol, url.userInfo, url.host, url.port, url.path, url.query, url.ref)
    return uri.toURL().toString()
}

fun getBaseUrl(url: String): String {
    return URI(url).let {
        "${it.scheme}://${it.host}"
    }
}

fun String.fixUrlBloat(): String {
    return this.replace("\"", "").replace("\\", "")
}

fun String.getHost(): String {
    return fixTitle(URI(this).host.substringBeforeLast(".").substringAfterLast("."))
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

fun getDate(): TmdbDate {
    val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val calender = Calendar.getInstance()
    val today = formatter.format(calender.time)
    calender.add(Calendar.WEEK_OF_YEAR, 1)
    val nextWeek = formatter.format(calender.time)
    return TmdbDate(today, nextWeek)
}

fun decode(input: String): String = URLDecoder.decode(input, "utf-8")

fun encode(input: String): String = URLEncoder.encode(input, "utf-8").replace("+", "%20")

fun base64DecodeAPI(api: String): String {
    return api.chunked(4).map { base64Decode(it) }.reversed().joinToString("")
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

fun base64UrlEncode(input: ByteArray): String {
    return base64Encode(input)
        .replace("+", "-")
        .replace("/", "_")
        .replace("=", "")
}


// --- Vidsrc Decryption Methods Map (Required for Extractor) ---
val decryptMethods: Map<String, (String) -> String> = mapOf(
    "TsA2KGDGux" to { inputString ->
        inputString.reversed().replace("-", "+").replace("_", "/").let {
            val decoded = String(android.util.Base64.decode(it, android.util.Base64.DEFAULT))
            decoded.map { ch -> (ch.code - 7).toChar() }.joinToString("")
        }
    },
    "ux8qjPHC66" to { inputString ->
        val reversed = inputString.reversed()
        val hexPairs = reversed.chunked(2).map { it.toInt(16).toChar() }.joinToString("")
        val key = "X9a(O;FMV2-7VO5x;Ao\u0005:dN1NoFs?j,"
        hexPairs.mapIndexed { i, ch -> (ch.code xor key[i % key.length].code).toChar() }
            .joinToString("")
    },
    "xTyBxQyGTA" to { inputString ->
        val filtered = inputString.reversed().filterIndexed { i, _ -> i % 2 == 0 }
        String(android.util.Base64.decode(filtered, android.util.Base64.DEFAULT))
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
        String(android.util.Base64.decode(rot13.reversed(), android.util.Base64.DEFAULT))
    },
    "o2VSUnjnZl" to { inputString ->
        val substitutionMap =
            ("xyzabcdefghijklmnopqrstuvwXYZABCDEFGHIJKLMNOPQRSTUVW" zip "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ").toMap()
        inputString.map { substitutionMap[it] ?: it }.joinToString("")
    },
    "eSfH1IRMyL" to { inputString ->
        val reversed = inputString.reversed()
        val shifted = reversed.map { (it.code - 1).toChar() }.joinToString("")
        shifted.chunked(2).map { it.toInt(16).toChar() }.joinToString("")
    },
    "Oi3v1dAlaM" to { inputString ->
        inputString.reversed().replace("-", "+").replace("_", "/").let {
            val decoded = String(android.util.Base64.decode(it, android.util.Base64.DEFAULT))
            decoded.map { ch -> (ch.code - 5).toChar() }.joinToString("")
        }
    },
    "sXnL9MQIry" to { inputString ->
        val xorKey = "pWB9V)[*4I`nJpp?ozyB~dbr9yt!_n4u"
        val hexDecoded = inputString.chunked(2).map { it.toInt(16).toChar() }.joinToString("")
        val decrypted =
            hexDecoded.mapIndexed { i, ch -> (ch.code xor xorKey[i % xorKey.length].code).toChar() }
                .joinToString("")
        val shifted = decrypted.map { (it.code - 3).toChar() }.joinToString("")
        String(android.util.Base64.decode(shifted, android.util.Base64.DEFAULT))
    },
    "JoAHUMCLXV" to { inputString ->
        inputString.reversed().replace("-", "+").replace("_", "/").let {
            val decoded = String(android.util.Base64.decode(it, android.util.Base64.DEFAULT))
            decoded.map { ch -> (ch.code - 3).toChar() }.joinToString("")
        }
    },
    "KJHidj7det" to { input ->
        val decoded = String(
            android.util.Base64.decode(
                input.drop(10).dropLast(16),
                android.util.Base64.DEFAULT
            )
        )
        val key = """3SAY~#%Y(V%>5d/Yg${'$'}G[Lh1rK4a;7ok"""
        decoded.mapIndexed { i, ch -> (ch.code xor key[i % key.length].code).toChar() }
            .joinToString("")
    },
    "playerjs" to { x ->
        try {
            var a = x.drop(2)
            val b1: (String) -> String = { str ->
                android.util.Base64.encodeToString(
                    str.toByteArray(),
                    android.util.Base64.NO_WRAP
                )
            }
            val b2: (String) -> String =
                { str -> String(android.util.Base64.decode(str, android.util.Base64.DEFAULT)) }
            val patterns = listOf(
                "*,4).(_)()", "33-*.4/9[6", ":]&*1@@1=&", "=(=:19705/", "%?6497.[:4"
            )
            patterns.forEach { k -> a = a.replace("/@#@/" + b1(k), "") }
            b2(a)
        } catch (e: Exception) {
            "Failed to decode: ${e.message}"
        }
    }
)
