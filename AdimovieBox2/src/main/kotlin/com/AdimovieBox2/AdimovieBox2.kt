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
    private val secretKeyAlt = base64Decode("WHFuMm5uTzQxL0w5Mm8xaXVYaFNMSFRiWHZZNFo1Wlo2Mm04bVNMQQ==")

    private fun md5(input: ByteArray): String {
        return MessageDigest.getInstance("MD5").digest(input)
            .joinToString("") { "%02x".format(it) }
    }

    private fun reverseString(input: String): String = input.reversed()

    private fun generateXClientToken(hardcodedTimestamp: Long? = null): String {
        val timestamp = (hardcodedTimestamp ?: System.currentTimeMillis()).toString()
        val reversed = reverseString(timestamp)
        val hash = md5(reversed.toByteArray())
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

    private fun generateXTrSignature(method: String, accept: String?, contentType: String?, url: String, body: String? = null, useAltKey: Boolean = false, hardcodedTimestamp: Long? = null): String {
        val timestamp = hardcodedTimestamp ?: System.currentTimeMillis()
        val canonical = buildCanonicalString(method, accept, contentType, url, body, timestamp)
        val secret = if (useAltKey) secretKeyAlt else secretKeyDefault
        val secretBytes = base64DecodeArray(secret)
        val mac = Mac.getInstance("HmacMD5")
        mac.init(SecretKeySpec(secretBytes, "HmacMD5"))
        val signatureB64 = base64Encode(mac.doFinal(canonical.toByteArray(Charsets.UTF_8)))
        return "$timestamp|2|$signatureB64"
    }

    // --- KODE ORI (HEADER ASLI) AGAR MAIN PAGE TIDAK HITAM ---
    private val headersOri = mapOf(
        "user-agent" to "com.community.mbox.in/50020042 (Linux; U; Android 16; en_IN; sdk_gphone64_x86_64; Build/BP22.250325.006; Cronet/133.0.6876.3)",
        "accept" to "application/json",
        "connection" to "keep-alive",
        "x-client-info" to """{"package_name":"com.community.mbox.in","version_name":"3.0.03.0529.03","version_code":50020042,"os":"android","os_version":"16","device_id":"da2b99c821e6ea023e4be55b54d5f7d8","install_store":"ps","gaid":"d7578036d13336cc","brand":"google","model":"sdk_gphone64_x86_64","system_language":"en","net":"NETWORK_WIFI","region":"IN","timezone":"Asia/Calcutta","sp_code":""}""",
        "x-client-status" to "0",
        "x-play-mode" to "2"
    )

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
        val response = if (request.data.contains("|")) {
            val jsonBody = """{"page":$page,"perPage":$perPage,"channelId":"${request.data.substringBefore(";").split("|").getOrNull(1)}"}"""
            val xTrSignature = generateXTrSignature("POST", "application/json", "application/json; charset=utf-8", url, jsonBody)
            val headers = headersOri + mapOf("content-type" to "application/json", "x-client-token" to xClientToken, "x-tr-signature" to xTrSignature)
            app.post(url, headers = headers, requestBody = jsonBody.toRequestBody("application/json".toMediaType()))
        } else {
            val xTrSignature = generateXTrSignature("GET", "application/json", "application/json", url)
            val headers = headersOri + mapOf("content-type" to "application/json", "x-client-token" to xClientToken, "x-tr-signature" to xTrSignature)
            app.get(url, headers = headers)
        }
        
        val items = try {
            val mapper = jacksonObjectMapper()
            val root = mapper.readTree(response.body.string())
            root["data"]?.get("items") ?: root["data"]?.get("subjects") ?: return newHomePageResponse(emptyList())
        } catch (e: Exception) { return newHomePageResponse(emptyList()) }

        val data = items.mapNotNull { item ->
            newMovieSearchResponse(item["title"]?.asText()?.substringBefore("[") ?: return@mapNotNull null, item["subjectId"]?.asText() ?: return@mapNotNull null, TvType.Movie) {
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
        val headers = headersOri + mapOf("content-type" to "application/json", "x-client-token" to xClientToken, "x-tr-signature" to xTrSignature)
        val response = app.post(url, headers = headers, requestBody = jsonBody.toRequestBody("application/json".toMediaType()))
        
        val results = try { jacksonObjectMapper().readTree(response.body.string())["data"]?.get("results") } catch (e: Exception) { null } ?: return emptyList()
        val searchList = mutableListOf<SearchResponse>()
        results.forEach { res -> res["subjects"]?.forEach { sub ->
            searchList.add(newMovieSearchResponse(sub["title"].asText(), sub["subjectId"].asText(), TvType.Movie) { this.posterUrl = sub["cover"]?.get("url")?.asText() })
        }}
        return searchList
    }

    override suspend fun load(url: String): LoadResponse {
        val id = Regex("""subjectId=([^&]+)""").find(url)?.groupValues?.get(1) ?: url.substringAfterLast('/')
        val finalUrl = "$mainUrl/wefeed-mobile-bff/subject-api/get?subjectId=$id"
        val xClientToken = generateXClientToken()
        val xTrSignature = generateXTrSignature("GET", "application/json", "application/json", finalUrl)
        val headers = headersOri + mapOf("content-type" to "application/json", "x-client-token" to xClientToken, "x-tr-signature" to xTrSignature)

        val response = app.get(finalUrl, headers = headers)
        val body = response.body.string()
        val mapper = jacksonObjectMapper()
        val root = mapper.readTree(body)
        val data = root["data"] ?: throw ErrorLoadingException("No data")
        
        val subject = data["subject"] ?: data
        val title = subject["title"]?.asText()?.substringBefore("[") ?: "Unknown"
        val description = subject["description"]?.asText()
        val coverUrl = subject["cover"]?.get("url")?.asText()
        val year = subject["releaseDate"]?.asText()?.take(4)?.toIntOrNull()
        val rating = subject["imdbRatingValue"]?.asText()?.toDoubleOrNull()?.times(10)?.toInt()
        val type = if (subject["subjectType"]?.asInt() == 2) TvType.TvSeries else TvType.Movie
        
        // PENTING: Ambil detailPath untuk loadLinks
        val detailPath = subject["detailPath"]?.asText() ?: ""

        val trailerUrl = subject["trailer"]?.get("videoAddress")?.get("url")?.asText() ?: subject["trailer"]?.get("url")?.asText()
        val actors = (data["stars"] ?: data["staffList"])?.mapNotNull { star ->
            ActorData(Actor(star["name"].asText(), star["avatarUrl"]?.asText()), roleString = star["character"]?.asText())
        } ?: emptyList()

        // Sync TMDB/IMDB
        val (tmdbId, imdbId) = identifyID(title.substringBefore("(").substringBefore("["), year, rating?.toDouble())
        
        // --- FIX CRASH: LOGO URL DIHAPUS TOTAL ---
        
        if (type == TvType.TvSeries) {
            val seasonUrl = "$mainUrl/wefeed-mobile-bff/subject-api/season-info?subjectId=$id"
            val seasonSig = generateXTrSignature("GET", "application/json", "application/json", seasonUrl)
            val seasonHeaders = headersOri + mapOf("content-type" to "application/json", "x-client-token" to xClientToken, "x-tr-signature" to seasonSig)
            val seasonResp = app.get(seasonUrl, headers = seasonHeaders)
            val episodes = mutableListOf<Episode>()
            if (seasonResp.code == 200) {
                val seasons = mapper.readTree(seasonResp.body.string())["data"]?.get("seasons")
                seasons?.forEach { s ->
                    val seNum = s["se"]?.asInt() ?: 1
                    for (i in 1..(s["maxEp"]?.asInt() ?: 1)) {
                        // Pass detailPath ke episode
                        episodes.add(newEpisode("$id|$seNum|$i|$detailPath") {
                            this.name = "S${seNum}E${i}"; this.season = seNum; this.episode = i; this.posterUrl = coverUrl
                        })
                    }
                }
            }
            if (episodes.isEmpty()) episodes.add(newEpisode("$id|1|1|$detailPath") { name = "Episode 1"; season = 1; episode = 1 })
            
            return newTvSeriesLoadResponse(title, finalUrl, type, episodes) {
                this.posterUrl = coverUrl; this.plot = description; this.year = year; this.actors = actors
                this.score = Score.from10(rating)
                addImdbId(imdbId); addTMDbId(tmdbId.toString())
                if (!trailerUrl.isNullOrBlank()) addTrailer(trailerUrl)
            }
        }

        // Pass detailPath ke movie
        return newMovieLoadResponse(title, finalUrl, type, "$id|0|0|$detailPath") {
            this.posterUrl = coverUrl; this.plot = description; this.year = year; this.actors = actors
            this.score = Score.from10(rating)
            addImdbId(imdbId); addTMDbId(tmdbId.toString())
            if (!trailerUrl.isNullOrBlank()) addTrailer(trailerUrl)
        }
    }

    // --- LOAD LINKS BARU (Pake LokLok) ---
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        try {
            val parts = data.split("|")
            val originalId = parts[0]
            val se = parts.getOrNull(1)?.toIntOrNull() ?: 0
            val ep = parts.getOrNull(2)?.toIntOrNull() ?: 0
            val detailPath = parts.getOrNull(3) ?: "" // detailPath WAJIB ADA

            // 1. Cek Dubbing (Pakai Header Ori biar sync)
            val xClientToken = generateXClientToken()
            val subUrl = "$mainUrl/wefeed-mobile-bff/subject-api/get?subjectId=$originalId"
            val sigSub = generateXTrSignature("GET", "application/json", "application/json", subUrl)
            val subHeaders = headersOri + mapOf("content-type" to "application/json", "x-client-token" to xClientToken, "x-tr-signature" to sigSub)
            
            val ids = mutableListOf<Pair<String, String>>()
            try {
                val subResp = app.get(subUrl, headers = subHeaders)
                val root = jacksonObjectMapper().readTree(subResp.body.string())
                val dubs = root?.get("data")?.let { it["dubs"] ?: it["subject"]?.get("dubs") }
                ids.add(originalId to "Original")
                dubs?.forEach { ids.add((it["subjectId"]?.asText() ?: "") to (it["lanName"]?.asText() ?: "Dub")) }
            } catch (e: Exception) {
                ids.add(originalId to "Original")
            }

            // 2. Request ke LokLok (Tanpa Signature, Header Aman)
            ids.filter { it.first.isNotBlank() }.forEach { (id, lang) ->
                try {
                    val playUrl = "https://lok-lok.cc/wefeed-h5api-bff/subject/play?subjectId=$id&se=$se&ep=$ep&detailPath=$detailPath"
                    val headersLok = mapOf(
                        "authority" to "lok-lok.cc",
                        "accept" to "application/json",
                        "accept-language" to "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7",
                        "referer" to "https://lok-lok.cc/",
                        "sec-fetch-dest" to "empty",
                        "sec-fetch-mode" to "cors",
                        "sec-fetch-site" to "same-origin",
                        "user-agent" to "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36",
                        "x-client-info" to """{"timezone":"Asia/Jayapura"}""",
                        "x-source" to "app-search"
                    )

                    val playResp = app.get(playUrl, headers = headersLok)
                    val streams = jacksonObjectMapper().readTree(playResp.body.string())["data"]?.get("streams")
                    
                    streams?.forEach { stream ->
                        val url = stream["url"]?.asText() ?: return@forEach
                        val resolution = stream["resolutions"]?.asText() ?: ""
                        val qualityVal = getHighestQuality(resolution)
                        val typeVal = if (url.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                        
                        callback.invoke(newExtractorLink(
                            source = "LokLok $lang",
                            name = "LokLok $lang",
                            url = url,
                            type = typeVal
                        ) {
                            this.headers = mapOf("Referer" to "https://lok-lok.cc/")
                            this.quality = qualityVal ?: Qualities.Unknown.value
                        })
                    }
                } catch (e: Exception) {}
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

    private suspend fun identifyID(title: String, year: Int?, imdbRatingValue: Double?): Pair<Int?, String?> { return Pair(null, null) }
    private suspend fun fetchMetaData(imdbId: String?, type: TvType): JsonNode? { return null }
}
