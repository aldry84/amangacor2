package com.AdimovieBox2

import android.annotation.SuppressLint
import android.net.Uri
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTMDbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.SubtitleFile
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
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
        val bodyHash = if (bodyBytes != null) md5(if (bodyBytes.size > 102400) bodyBytes.copyOfRange(0, 102400) else bodyBytes) else ""
        return "${method.uppercase()}\n${accept ?: ""}\n${contentType ?: ""}\n${bodyBytes?.size?.toString() ?: ""}\n$timestamp\n$bodyHash\n$canonicalUrl"
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
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = "$mainUrl/wefeed-mobile-bff/tab/ranking-list?tabId=0&categoryType=${request.data}&page=$page&perPage=15"
        val headers = mapOf(
            "user-agent" to "com.community.mbox.in/50020042",
            "accept" to "application/json",
            "x-client-token" to generateXClientToken(),
            "x-tr-signature" to generateXTrSignature("GET", "application/json", "application/json", url)
        )
        val response = app.get(url, headers = headers).body.string()
        val items = mapper.readTree(response)["data"]?.get("subjects") ?: return newHomePageResponse(emptyList())
        val data = items.mapNotNull { item ->
            newMovieSearchResponse(item["title"].asText().substringBefore("["), item["subjectId"].asText(), TvType.Movie) {
                this.posterUrl = item["cover"]?.get("url")?.asText()
            }
        }
        return newHomePageResponse(listOf(HomePageList(request.name, data)))
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/wefeed-mobile-bff/subject-api/search/v2"
        val jsonBody = """{"page": 1, "perPage": 10, "keyword": "$query"}"""
        val headers = mapOf(
            "user-agent" to "com.community.mbox.in/50020042",
            "content-type" to "application/json",
            "x-client-token" to generateXClientToken(),
            "x-tr-signature" to generateXTrSignature("POST", "application/json", "application/json; charset=utf-8", url, jsonBody)
        )
        val response = app.post(url, headers = headers, requestBody = jsonBody.toRequestBody("application/json".toMediaType())).body.string()
        val results = mapper.readTree(response)["data"]?.get("results") ?: return emptyList()
        val searchList = mutableListOf<SearchResponse>()
        results.forEach { it["subjects"]?.forEach { s -> searchList.add(newMovieSearchResponse(s["title"].asText(), s["subjectId"].asText(), TvType.Movie) { this.posterUrl = s["cover"]?.get("url")?.asText() }) } }
        return searchList
    }

    override suspend fun load(url: String): LoadResponse {
        val id = if (url.contains("=")) url.substringAfterLast('=') else url.substringAfterLast('/')
        val finalUrl = "$mainUrl/wefeed-mobile-bff/subject-api/get?subjectId=$id"
        
        val headers = mapOf(
            "user-agent" to "com.community.mbox.in/50020042",
            "accept" to "application/json",
            "x-client-token" to generateXClientToken(),
            "x-tr-signature" to generateXTrSignature("GET", "application/json", "application/json", finalUrl)
        )

        val response = app.get(finalUrl, headers = headers).body.string()
        val data = mapper.readTree(response)["data"] ?: throw ErrorLoadingException("No data")
        
        val subject = data["subject"] ?: data
        val title = subject["title"]?.asText()?.substringBefore("[") ?: "Unknown Title"
        val type = if (subject["subjectType"]?.asInt() == 2) TvType.TvSeries else TvType.Movie

        // Trailer Internal
        val internalTrailerUrl = subject["trailer"]?.get("videoAddress")?.get("url")?.asText()

        // Aktor dari 'stars'
        val actors = data["stars"]?.mapNotNull { star ->
            ActorData(Actor(star["name"].asText(), star["avatarUrl"]?.asText()), roleString = star["character"]?.asText())
        } ?: emptyList()

        if (type == TvType.TvSeries) {
            return newTvSeriesLoadResponse(title, finalUrl, type, emptyList()) {
                this.posterUrl = subject["cover"]?.get("url")?.asText()
                this.plot = subject["description"]?.asText()
                this.actors = actors
                if (!internalTrailerUrl.isNullOrBlank()) this.addTrailer(internalTrailerUrl)
            }
        }

        return newMovieLoadResponse(title, finalUrl, type, id) {
            this.posterUrl = subject["cover"]?.get("url")?.asText()
            this.plot = subject["description"]?.asText()
            this.actors = actors
            this.score = Score.from10(subject["imdbRatingValue"]?.asText())
            if (!internalTrailerUrl.isNullOrBlank()) this.addTrailer(internalTrailerUrl)
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean = true
}
