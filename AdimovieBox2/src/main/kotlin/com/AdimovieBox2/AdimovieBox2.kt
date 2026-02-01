package com.AdimovieBox2

import android.annotation.SuppressLint
import android.net.Uri
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTMDbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
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

    // --- UTILS ---
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

    // --- HEADERS ANTI-BLOCK (Penting!) ---
    private fun getSafeHeaders(xClientToken: String, xTrSignature: String): Map<String, String> {
        return mapOf(
            "User-Agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Mobile Safari/537.36",
            "Accept" to "application/json",
            "Content-Type" to "application/json",
            "x-client-token" to xClientToken,
            "x-tr-signature" to xTrSignature,
            // HANYA kirim timezone. JANGAN kirim device_id.
            "x-client-info" to """{"timezone":"Asia/Jakarta"}"""
        )
    }

    // --- MAIN PAGE ---
    override val mainPage = mainPageOf(
        "5283462032510044280" to "Indonesian Drama",
        "6528093688173053896" to "Indonesian Movies",
        "5848753831881965888" to "Indo Horror",
        "997144265920760504" to "Hollywood Movies",
        "4380734070238626200" to "K-Drama",
        "8624142774394406504" to "C-Drama",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val perPage = 15
        val url = if (request.data.contains("|")) "$mainUrl/wefeed-mobile-bff/subject-api/list" else "$mainUrl/wefeed-mobile-bff/tab/ranking-list?tabId=0&categoryType=${request.data}&page=$page&perPage=$perPage"
        
        val xClientToken = generateXClientToken()
        val xTrSignature = generateXTrSignature("GET", "application/json", "application/json", url)
        
        val response = app.get(url, headers = getSafeHeaders(xClientToken, xTrSignature)).body.string()
        val items = try {
            val root = jacksonObjectMapper().readTree(response)
            root["data"]?.get("items") ?: root["data"]?.get("subjects")
        } catch (e: Exception) { null } ?: return newHomePageResponse(emptyList())

        val data = items.mapNotNull { item ->
            newMovieSearchResponse(
                item["title"]?.asText()?.substringBefore("[") ?: return@mapNotNull null,
                item["subjectId"]?.asText() ?: return@mapNotNull null,
                TvType.Movie
            ) {
                this.posterUrl = item["cover"]?.get("url")?.asText()
                this.score = Score.from10(item["imdbRatingValue"]?.asText())
            }
        }
        return newHomePageResponse(listOf(HomePageList(request.name, data)))
    }

    // --- SEARCH ---
    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/wefeed-mobile-bff/subject-api/search/v2"
        val jsonBody = """{"page": 1, "perPage": 10, "keyword": "$query"}"""
        val xClientToken = generateXClientToken()
        val xTrSignature = generateXTrSignature("POST", "application/json", "application/json; charset=utf-8", url, jsonBody)
        
        val response = app.post(url, headers = getSafeHeaders(xClientToken, xTrSignature), requestBody = jsonBody.toRequestBody("application/json".toMediaType())).body.string()
        val results = try { jacksonObjectMapper().readTree(response)["data"]?.get("results") } catch (e: Exception) { null } ?: return emptyList()
        
        val searchList = mutableListOf<SearchResponse>()
        results.forEach { res ->
            res["subjects"]?.forEach { sub ->
                searchList.add(newMovieSearchResponse(sub["title"].asText(), sub["subjectId"].asText(), TvType.Movie) {
                    this.posterUrl = sub["cover"]?.get("url")?.asText()
                })
            }
        }
        return searchList
    }

    // --- LOAD DETAIL (FIX TRAILER & ACTORS) ---
    override suspend fun load(url: String): LoadResponse {
        val id = Regex("""subjectId=([^&]+)""").find(url)?.groupValues?.get(1) ?: url.substringAfterLast('/')
        val finalUrl = "$mainUrl/wefeed-mobile-bff/subject-api/get?subjectId=$id"
        
        val xClientToken = generateXClientToken()
        val xTrSignature = generateXTrSignature("GET", "application/json", "application/json", finalUrl)
        
        val response = app.get(finalUrl, headers = getSafeHeaders(xClientToken, xTrSignature))
        val body = response.body.string()
        val data = jacksonObjectMapper().readTree(body)["data"] ?: throw ErrorLoadingException("No Data")
        val subject = data["subject"] ?: data

        val title = subject["title"]?.asText()?.substringBefore("[") ?: "Unknown"
        val description = subject["description"]?.asText()
        val coverUrl = subject["cover"]?.get("url")?.asText()
        val year = subject["releaseDate"]?.asText()?.take(4)?.toIntOrNull()
        val rating = subject["imdbRatingValue"]?.asText()?.toDoubleOrNull()?.times(10)?.toInt()
        val type = if (subject["subjectType"]?.asInt() == 2) TvType.TvSeries else TvType.Movie

        // Trailer Fix: Cek beberapa kemungkinan lokasi URL
        val trailerUrl = subject["trailer"]?.get("videoAddress")?.get("url")?.asText()
            ?: subject["trailer"]?.get("url")?.asText()

        val actors = (data["stars"] ?: data["staffList"])?.mapNotNull { star ->
            ActorData(Actor(star["name"].asText(), star["avatarUrl"]?.asText()), roleString = star["character"]?.asText())
        } ?: emptyList()

        if (type == TvType.TvSeries) {
            val seasonUrl = "$mainUrl/wefeed-mobile-bff/subject-api/season-info?subjectId=$id"
            val seasonSig = generateXTrSignature("GET", "application/json", "application/json", seasonUrl)
            val seasonResp = app.get(seasonUrl, headers = getSafeHeaders(xClientToken, seasonSig))
            
            val episodes = mutableListOf<Episode>()
            if (seasonResp.code == 200) {
                val seasons = jacksonObjectMapper().readTree(seasonResp.body.string())["data"]?.get("seasons")
                seasons?.forEach { s ->
                    val seNum = s["se"]?.asInt() ?: 1
                    for (i in 1..(s["maxEp"]?.asInt() ?: 1)) {
                        episodes.add(newEpisode("$id|$seNum|$i") {
                            this.name = "S${seNum}E${i}"; this.season = seNum; this.episode = i; this.posterUrl = coverUrl
                        })
                    }
                }
            }
            if (episodes.isEmpty()) episodes.add(newEpisode("$id|1|1") { name = "Episode 1"; season = 1; episode = 1 })

            return newTvSeriesLoadResponse(title, finalUrl, type, episodes) {
                this.posterUrl = coverUrl; this.plot = description; this.year = year; this.actors = actors
                this.score = Score.from10(rating)
                if (!trailerUrl.isNullOrBlank()) addTrailer(trailerUrl)
            }
        }

        return newMovieLoadResponse(title, finalUrl, type, id) {
            this.posterUrl = coverUrl; this.plot = description; this.year = year; this.actors = actors
            this.score = Score.from10(rating)
            if (!trailerUrl.isNullOrBlank()) addTrailer(trailerUrl)
        }
    }

    // --- LOAD LINKS (FIX 407 & STREAM) ---
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        try {
            val parts = data.split("|")
            val originalId = if (parts[0].contains("=")) parts[0].substringAfterLast('=') else parts[0].substringAfterLast('/')
            val se = parts.getOrNull(1)?.toIntOrNull() ?: 0
            val ep = parts.getOrNull(2)?.toIntOrNull() ?: 0

            val xClientToken = generateXClientToken()
            
            // 1. Ambil List ID (termasuk Dubbing)
            val subUrl = "$mainUrl/wefeed-mobile-bff/subject-api/get?subjectId=$originalId"
            val sigSub = generateXTrSignature("GET", "application/json", "application/json", subUrl)
            val subResp = app.get(subUrl, headers = getSafeHeaders(xClientToken, sigSub))
            
            val ids = mutableListOf<Pair<String, String>>()
            val root = try { jacksonObjectMapper().readTree(subResp.body.string()) } catch(e: Exception) { null }
            val dubs = root?.get("data")?.let { it["dubs"] ?: it["subject"]?.get("dubs") }
            
            ids.add(originalId to "Original")
            dubs?.forEach { ids.add((it["subjectId"]?.asText() ?: "") to (it["lanName"]?.asText() ?: "Dub")) }

            // 2. Loop setiap ID untuk cari Stream
            ids.filter { it.first.isNotBlank() }.forEach { (id, lang) ->
                try {
                    val playUrl = "$mainUrl/wefeed-mobile-bff/subject-api/play-info?subjectId=$id&se=$se&ep=$ep"
                    val sigPlay = generateXTrSignature("GET", "application/json", "application/json", playUrl)
                    val playResp = app.get(playUrl, headers = getSafeHeaders(xClientToken, sigPlay))
                    
                    val streams = jacksonObjectMapper().readTree(playResp.body.string())["data"]?.get("streams")
                    streams?.forEach { stream ->
                        val url = stream["url"]?.asText() ?: return@forEach
                        val quality = getHighestQuality(stream["resolutions"]?.asText() ?: "")
                        
                        callback.invoke(newExtractorLink(
                            source = "Adimovie $lang",
                            name = "Adimovie $lang",
                            url = url,
                            referer = mainUrl,
                            quality = quality ?: Qualities.Unknown.value,
                            type = if (url.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                        ))
                    }
                } catch (e: Exception) { }
            }
            return true
        } catch (e: Exception) { return false }
    }

    private fun getHighestQuality(str: String): Int? {
        return when {
            str.contains("2160") -> Qualities.P2160.value
            str.contains("1080") -> Qualities.P1080.value
            str.contains("720") -> Qualities.P720.value
            str.contains("480") -> Qualities.P480.value
            else -> Qualities.P360.value
        }
    }
}
