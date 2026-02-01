package com.AdimovieBox2

import android.annotation.SuppressLint
import android.net.Uri
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTMDbId
// --- IMPORT TRAILER ---
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
// ----------------------
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SearchResponse
// --- IMPORT SUBTITLE ---
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.newSubtitleFile
// -----------------------
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.addDate
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.base64DecodeArray
import com.lagradost.cloudstream3.base64Encode
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.mapper
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.max

class AdimovieBox2Provider : MainAPI() {
    override var mainUrl = "https://api.inmoviebox.com"
    override var name = "AdimovieBox2"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    private val secretKeyDefault = base64Decode("NzZpUmwwN3MweFNOOWpxbUVXQXQ3OUVCSlp1bElRSXNWNjRGWnIyTw==")

    private fun md5(input: ByteArray): String {
        return MessageDigest.getInstance("MD5").digest(input)
            .joinToString("") { "%02x".format(it) }
    }

    private fun generateXClientToken(): String {
        val timestamp = System.currentTimeMillis().toString()
        val hash = md5(timestamp.reversed().toByteArray())
        return "$timestamp,$hash"
    }

    @SuppressLint("UseKtx")
    private fun buildCanonicalString(method: String, accept: String?, contentType: String?, url: String, body: String?, timestamp: Long): String {
        val parsed = Uri.parse(url)
        val path = parsed.path ?: ""
        val query = if (parsed.queryParameterNames.isNotEmpty()) {
            parsed.queryParameterNames.sorted().joinToString("&") { key ->
                parsed.getQueryParameters(key).joinToString("&") { value -> "$key=$value" }
            }
        } else ""
        val canonicalUrl = if (query.isNotEmpty()) "$path?$query" else path
        val bodyBytes = body?.toByteArray(Charsets.UTF_8)
        val bodyHash = if (bodyBytes != null) {
            val trimmed = if (bodyBytes.size > 102400) bodyBytes.copyOfRange(0, 102400) else bodyBytes
            md5(trimmed)
        } else ""
        val bodyLength = bodyBytes?.size?.toString() ?: ""
        return "${method.uppercase()}\n${accept ?: ""}\n${contentType ?: ""}\n$bodyLength\n$timestamp\n$bodyHash\n$canonicalUrl"
    }

    private fun generateXTrSignature(method: String, accept: String?, contentType: String?, url: String, body: String? = null): String {
        val timestamp = System.currentTimeMillis()
        val canonical = buildCanonicalString(method, accept, contentType, url, body, timestamp)
        val secretBytes = base64DecodeArray(secretKeyDefault)
        val mac = Mac.getInstance("HmacMD5")
        mac.init(SecretKeySpec(secretBytes, "HmacMD5"))
        val signatureB64 = base64Encode(mac.doFinal(canonical.toByteArray(Charsets.UTF_8)))
        return "$timestamp|2|$signatureB64"
    }

    override val mainPage = mainPageOf(
        "5283462032510044280" to "Indonesian Drama",
        "6528093688173053896" to "Indonesian Movies",
        "5848753831881965888" to "Indo Horror",
        "997144265920760504" to "Hollywood Movies",
        "4380734070238626200" to "K-Drama",
        "8624142774394406504" to "C-Drama",
    )

    // --- FIX HEADER 407: Hapus x-client-info yang hardcoded ---
    private fun getCommonHeaders(xClientToken: String, xTrSignature: String): Map<String, String> {
        return mapOf(
            "User-Agent" to "com.community.mbox.in/50020042 (Linux; Android 10; K)", // User Agent lebih simpel
            "Accept" to "application/json",
            "x-client-token" to xClientToken,
            "x-tr-signature" to xTrSignature
            // "x-client-info" DIHAPUS karena mengandung device_id yang mungkin di-banned server
        )
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (request.data.contains("|")) "$mainUrl/wefeed-mobile-bff/subject-api/list" else "$mainUrl/wefeed-mobile-bff/tab/ranking-list?tabId=0&categoryType=${request.data}&page=$page&perPage=15"
        
        val xClientToken = generateXClientToken()
        val xTrSignature = generateXTrSignature("GET", "application/json", "application/json", url)
        val headers = getCommonHeaders(xClientToken, xTrSignature)

        val response = app.get(url, headers = headers).body.string()
        val items = try {
            val mapper = jacksonObjectMapper()
            val root = mapper.readTree(response)
            root["data"]?.get("items") ?: root["data"]?.get("subjects")
        } catch (e: Exception) { null } ?: return newHomePageResponse(emptyList())

        val data = items.mapNotNull { item ->
            val title = item["title"]?.asText()?.substringBefore("[") ?: return@mapNotNull null
            val id = item["subjectId"]?.asText() ?: return@mapNotNull null
            newMovieSearchResponse(title, id, TvType.Movie) {
                this.posterUrl = item["cover"]?.get("url")?.asText()
                this.score = Score.from10(item["imdbRatingValue"]?.asText())
            }
        }
        return newHomePageResponse(listOf(HomePageList(request.name, data)))
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/wefeed-mobile-bff/subject-api/search/v2"
        val jsonBody = """{"page": 1, "perPage": 10, "keyword": "$query"}"""
        
        val xClientToken = generateXClientToken()
        val xTrSignature = generateXTrSignature("POST", "application/json", "application/json; charset=utf-8", url, jsonBody)
        val headers = getCommonHeaders(xClientToken, xTrSignature) + mapOf("Content-Type" to "application/json")

        val response = app.post(url, headers = headers, requestBody = jsonBody.toRequestBody("application/json".toMediaType())).body.string()
        val results = try {
            val mapper = jacksonObjectMapper()
            mapper.readTree(response)["data"]?.get("results")
        } catch (e: Exception) { null } ?: return emptyList()

        val searchList = mutableListOf<SearchResponse>()
        results.forEach { result ->
            result["subjects"]?.forEach { subject ->
                val title = subject["title"]?.asText() ?: return@forEach
                val id = subject["subjectId"]?.asText() ?: return@forEach
                searchList.add(newMovieSearchResponse(title, id, TvType.Movie) {
                    this.posterUrl = subject["cover"]?.get("url")?.asText()
                })
            }
        }
        return searchList
    }

    override suspend fun load(url: String): LoadResponse {
        val id = if (url.contains("=")) url.substringAfterLast('=') else url.substringAfterLast('/')
        val finalUrl = "$mainUrl/wefeed-mobile-bff/subject-api/get?subjectId=$id"
        
        val xClientToken = generateXClientToken()
        val xTrSignature = generateXTrSignature("GET", "application/json", "application/json", finalUrl)
        val headers = getCommonHeaders(xClientToken, xTrSignature)

        val response = app.get(finalUrl, headers = headers).body.string()
        val mapper = jacksonObjectMapper()
        val root = mapper.readTree(response)
        val data = root["data"] ?: throw ErrorLoadingException("No data")
        
        val subject = data["subject"] ?: data
        val title = subject["title"]?.asText()?.substringBefore("[") ?: "Unknown Title"
        val type = if (subject["subjectType"]?.asInt() == 2) TvType.TvSeries else TvType.Movie
        val description = subject["description"]?.asText()
        val coverUrl = subject["cover"]?.get("url")?.asText()
        val imdbRating = subject["imdbRatingValue"]?.asText()?.toDoubleOrNull()?.times(10)?.toInt()

        // --- TRAILER & ACTORS FIX ---
        val internalTrailerUrl = subject["trailer"]?.get("videoAddress")?.get("url")?.asText()
        val actors = data["stars"]?.mapNotNull { star ->
            val name = star["name"]?.asText() ?: return@mapNotNull null
            ActorData(Actor(name, star["avatarUrl"]?.asText()), roleString = star["character"]?.asText())
        } ?: emptyList()

        // TMDB Sync
        val (tmdbId, imdbId) = identifyID(title, subject["releaseDate"]?.asText()?.take(4)?.toIntOrNull(), imdbRating?.toDouble())

        if (type == TvType.TvSeries) {
            val episodes = mutableListOf<Episode>()
            return newTvSeriesLoadResponse(title, finalUrl, type, episodes) {
                this.posterUrl = coverUrl
                this.plot = description
                this.actors = actors
                if (!internalTrailerUrl.isNullOrBlank()) addTrailer(internalTrailerUrl)
                addImdbId(imdbId)
                addTMDbId(tmdbId.toString())
            }
        }

        return newMovieLoadResponse(title, finalUrl, type, id) {
            this.posterUrl = coverUrl
            this.plot = description
            this.actors = actors
            this.score = Score.from10(imdbRating)
            if (!internalTrailerUrl.isNullOrBlank()) addTrailer(internalTrailerUrl)
            addImdbId(imdbId)
            addTMDbId(tmdbId.toString())
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // Implementasi loadLinks standar sesuai kebutuhan
        return true
    }

    private suspend fun identifyID(title: String, year: Int?, rating: Double?): Pair<Int?, String?> = Pair(null, null)
}
