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
    private fun buildCanonicalString(
        method: String,
        accept: String?,
        contentType: String?,
        url: String,
        body: String?,
        timestamp: Long
    ): String {
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

    private fun generateXTrSignature(
        method: String,
        accept: String?,
        contentType: String?,
        url: String,
        body: String? = null,
        useAltKey: Boolean = false,
        hardcodedTimestamp: Long? = null
    ): String {
        val timestamp = hardcodedTimestamp ?: System.currentTimeMillis()
        val canonical = buildCanonicalString(method, accept, contentType, url, body, timestamp)
        val secret = if (useAltKey) secretKeyAlt else secretKeyDefault
        val secretBytes = base64DecodeArray(secret)

        val mac = Mac.getInstance("HmacMD5")
        mac.init(SecretKeySpec(secretBytes, "HmacMD5"))
        val signature = mac.doFinal(canonical.toByteArray(Charsets.UTF_8))
        val signatureB64 = base64Encode(signature)

        return "$timestamp|2|$signatureB64"
    }

    override val mainPage = mainPageOf(
        "5283462032510044280" to "Indonesian Drama",
        "6528093688173053896" to "Indonesian Movies",
        "5848753831881965888" to "Indo Horror",
        "997144265920760504" to "Hollywood Movies",
        "4380734070238626200" to "K-Drama",
        "8624142774394406504" to "C-Drama",
        "1|1;classify=Hindi dub;country=United States" to "USA (Movies)",
        "1|2;classify=Hindi dub;country=United States" to "USA (Series)",
        "1|1;country=Japan" to "Japan (Movies)",
        "1|2;country=Japan" to "Japan (Series)",
        "1|1;country=China" to "China (Movies)",
        "1|2;country=China" to "China (Series)",
        "1|1;country=Philippines" to "Philippines (Movies)",
        "1|2;country=Philippines" to "Philippines (Series)",
        "1|1;country=Thailand" to "Thailand(Movies)",
        "1|2;country=Thailand" to "Thailand(Series)",
        "1|1;country=Korea" to "South Korean (Movies)",
        "1|2;country=Korea" to "South Korean (Series)",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val perPage = 15
        val url = if (request.data.contains("|")) "$mainUrl/wefeed-mobile-bff/subject-api/list" else "$mainUrl/wefeed-mobile-bff/tab/ranking-list?tabId=0&categoryType=${request.data}&page=$page&perPage=$perPage"

        val data1 = request.data
        val mainParts = data1.substringBefore(";").split("|")
        val pg = mainParts.getOrNull(0)?.toIntOrNull() ?: 1
        val channelId = mainParts.getOrNull(1)

        val options = mutableMapOf<String, String>()
        data1.substringAfter(";", "").split(";").forEach {
            val (k, v) = it.split("=").let { p -> p.getOrNull(0) to p.getOrNull(1) }
            if (!k.isNullOrBlank() && !v.isNullOrBlank()) options[k] = v
        }

        val classify = options["classify"] ?: "All"
        val country  = options["country"] ?: "All"
        val year     = options["year"] ?: "All"
        val genre    = options["genre"] ?: "All"
        val sort     = options["sort"] ?: "ForYou"

        val jsonBody = """{"page":$pg,"perPage":$perPage,"channelId":"$channelId","classify":"$classify","country":"$country","year":"$year","genre":"$genre","sort":"$sort"}"""

        val xClientToken = generateXClientToken()
        val xTrSignature = generateXTrSignature("POST", "application/json", "application/json; charset=utf-8", url, jsonBody)
        val getxTrSignature = generateXTrSignature("GET", "application/json", "application/json", url)

        val headers = mapOf(
            "user-agent" to "com.community.mbox.in/50020042 (Linux; U; Android 16; en_IN; sdk_gphone64_x86_64; Build/BP22.250325.006; Cronet/133.0.6876.3)",
            "accept" to "application/json",
            "content-type" to "application/json",
            "connection" to "keep-alive",
            "x-client-token" to xClientToken,
            "x-tr-signature" to xTrSignature,
            "x-client-info" to """{"package_name":"com.community.mbox.in","version_name":"3.0.03.0529.03","version_code":50020042,"os":"android","os_version":"16","device_id":"da2b99c821e6ea023e4be55b54d5f7d8","install_store":"ps","gaid":"d7578036d13336cc","brand":"google","model":"sdk_gphone64_x86_64","system_language":"en","net":"NETWORK_WIFI","region":"IN","timezone":"Asia/Calcutta","sp_code":""}""",
            "x-client-status" to "0",
            "x-play-mode" to "2"
        )

        val getheaders = mapOf(
            "user-agent" to "com.community.mbox.in/50020042 (Linux; U; Android 16; en_IN; sdk_gphone64_x86_64; Build/BP22.250325.006; Cronet/133.0.6876.3)",
            "accept" to "application/json",
            "content-type" to "application/json",
            "connection" to "keep-alive",
            "x-client-token" to xClientToken,
            "x-tr-signature" to getxTrSignature,
            "x-client-info" to """{"package_name":"com.community.mbox.in","version_name":"3.0.03.0529.03","version_code":50020042,"os":"android","os_version":"16","device_id":"da2b99c821e6ea023e4be55b54d5f7d8","install_store":"ps","gaid":"d7578036d13336cc","brand":"google","model":"sdk_gphone64_x86_64","system_language":"en","net":"NETWORK_WIFI","region":"IN","timezone":"Asia/Calcutta","sp_code":""}""",
            "x-client-status" to "0",
        )

        val requestBody = jsonBody.toRequestBody("application/json".toMediaType())
        val response = if (request.data.contains("|")) app.post(url, headers = headers, requestBody = requestBody) else app.get(url, headers = getheaders)

        val responseBody = response.body.string()
        val data = try {
            val mapper = jacksonObjectMapper()
            val root = mapper.readTree(responseBody)
            val items = root["data"]?.get("items") ?: root["data"]?.get("subjects") ?: return newHomePageResponse(emptyList())
            items.mapNotNull { item ->
                val title = item["title"]?.asText()?.substringBefore("[") ?: return@mapNotNull null
                val id = item["subjectId"]?.asText() ?: return@mapNotNull null
                val coverImg = item["cover"]?.get("url")?.asText()
                val subjectType = item["subjectType"]?.asInt() ?: 1
                val type = if (subjectType == 2) TvType.TvSeries else TvType.Movie
                
                newMovieSearchResponse(name = title, url = id, type = type) {
                    this.posterUrl = coverImg
                    this.score = Score.from10(item["imdbRatingValue"]?.asText())
                }
            }
        } catch (_: Exception) { emptyList() }

        return newHomePageResponse(listOf(HomePageList(request.name, data)))
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/wefeed-mobile-bff/subject-api/search/v2"
        val jsonBody = """{"page": 1, "perPage": 10, "keyword": "$query"}"""
        val xClientToken = generateXClientToken()
        val xTrSignature = generateXTrSignature("POST", "application/json", "application/json; charset=utf-8", url, jsonBody)
        val headers = mapOf(
            "user-agent" to "com.community.mbox.in/50020042 (Linux; U; Android 16; en_IN; sdk_gphone64_x86_64; Build/BP22.250325.006; Cronet/133.0.6876.3)",
            "accept" to "application/json",
            "content-type" to "application/json",
            "connection" to "keep-alive",
            "x-client-token" to xClientToken,
            "x-tr-signature" to xTrSignature,
            "x-client-info" to """{"package_name":"com.community.mbox.in","version_name":"3.0.03.0529.03","version_code":50020042,"os":"android","os_version":"16","device_id":"da2b99c821e6ea023e4be55b54d5f7d8","install_store":"ps","gaid":"d7578036d13336cc","brand":"google","model":"sdk_gphone64_x86_64","system_language":"en","net":"NETWORK_WIFI","region":"IN","timezone":"Asia/Calcutta","sp_code":""}""",
            "x-client-status" to "0"
        )
        val requestBody = jsonBody.toRequestBody("application/json".toMediaType())
        val response = app.post(url, headers = headers, requestBody = requestBody)

        val responseBody = response.body.string()
        val mapper = jacksonObjectMapper()
        val root = mapper.readTree(responseBody)
        val results = root["data"]?.get("results") ?: return emptyList()
        val searchList = mutableListOf<SearchResponse>()
        for (result in results) {
            result["subjects"]?.forEach { subject ->
                val title = subject["title"]?.asText() ?: return@forEach
                val id = subject["subjectId"]?.asText() ?: return@forEach
                val coverImg = subject["cover"]?.get("url")?.asText()
                val subjectType = subject["subjectType"]?.asInt() ?: 1
                val type = if (subjectType == 2) TvType.TvSeries else TvType.Movie
                
                searchList.add(newMovieSearchResponse(name = title, url = id, type = type) {
                    this.posterUrl = coverImg
                    this.score = Score.from10(subject["imdbRatingValue"]?.asText())
                })
            }
        }
        return searchList
    }

    override suspend fun load(url: String): LoadResponse {
        val id = Regex("""subjectId=([^&]+)""").find(url)?.groupValues?.get(1) ?: url.substringAfterLast('/')
        val finalUrl = "$mainUrl/wefeed-mobile-bff/subject-api/get?subjectId=$id"
        
        val xClientToken = generateXClientToken()
        val xTrSignature = generateXTrSignature("GET", "application/json", "application/json", finalUrl)
        val headers = mapOf(
            "user-agent" to "com.community.mbox.in/50020042 (Linux; U; Android 16; en_IN; sdk_gphone64_x86_64; Build/BP22.250325.006; Cronet/133.0.6876.3)",
            "accept" to "application/json",
            "content-type" to "application/json",
            "connection" to "keep-alive",
            "x-client-token" to xClientToken,
            "x-tr-signature" to xTrSignature,
            "x-client-info" to """{"package_name":"com.community.mbox.in","version_name":"3.0.03.0529.03","version_code":50020042,"os":"android","os_version":"16","device_id":"da2b99c821e6ea023e4be55b54d5f7d8","install_store":"ps","gaid":"d7578036d13336cc","brand":"google","model":"sdk_gphone64_x86_64","system_language":"en","net":"NETWORK_WIFI","region":"IN","timezone":"Asia/Calcutta","sp_code":""}""",
            "x-client-status" to "0",
            "x-play-mode" to "2"
        )

        val response = app.get(finalUrl, headers = headers)
        val body = response.body.string()
        val mapper = jacksonObjectMapper()
        val root = mapper.readTree(body)
        val data = root["data"] ?: throw ErrorLoadingException("No data")

        val title = data["title"]?.asText()?.substringBefore("[") ?: throw ErrorLoadingException("No title found")
        val description = data["description"]?.asText()
        val releaseDate = data["releaseDate"]?.asText()
        val duration = data["duration"]?.asText()
        val genre = data["genre"]?.asText()
        val imdbRating = data["imdbRatingValue"]?.asText()?.toDoubleOrNull()?.times(10)?.toInt()
        val year = releaseDate?.substring(0, 4)?.toIntOrNull()
        val coverUrl = data["cover"]?.get("url")?.asText()
        val backgroundUrl = data["cover"]?.get("url")?.asText()

        // --- TRAILER FIX (Prioritas) ---
        val trailerUrl = data["trailer"]?.get("videoAddress")?.get("url")?.asText() 
            ?: data["trailer"]?.get("url")?.asText()

        val subjectType = data["subjectType"]?.asInt() ?: 1
        val detailPath = data["detailPath"]?.asText() ?: ""

        val actors = data["staffList"]?.mapNotNull { staff ->
            if (staff["staffType"]?.asInt() == 1) {
                val name = staff["name"]?.asText() ?: return@mapNotNull null
                ActorData(Actor(name, staff["avatarUrl"]?.asText()), roleString = staff["character"]?.asText())
            } else null
        }?.distinctBy { it.actor.name } ?: emptyList()

        val tags = genre?.split(",")?.map { it.trim() } ?: emptyList()
        val type = if (subjectType == 2) TvType.TvSeries else TvType.Movie
        val (tmdbId, imdbId) = identifyID(title.substringBefore("(").substringBefore("["), year, imdbRating?.toDouble())
        
        // Hapus logoUrl agar tidak crash
        // val logoUrl = ...

        val meta = if (!imdbId.isNullOrBlank()) fetchMetaData(imdbId, type) else null
        val Poster = meta?.get("poster")?.asText() ?: coverUrl
        val Background = meta?.get("background")?.asText() ?: backgroundUrl
        val Description = meta?.get("description")?.asText() ?: description
        val IMDBRating = meta?.get("imdbRating")?.asText()

        if (type == TvType.TvSeries) {
            val seasonUrl = "$mainUrl/wefeed-mobile-bff/subject-api/season-info?subjectId=$id"
            val seasonSig = generateXTrSignature("GET", "application/json", "application/json", seasonUrl)
            val seasonHeaders = headers.toMutableMap().apply { put("x-tr-signature", seasonSig) }

            val seasonResponse = app.get(seasonUrl, headers = seasonHeaders)
            val episodes = mutableListOf<Episode>()

            if (seasonResponse.code == 200) {
                val seasonRoot = mapper.readTree(seasonResponse.body.string())
                val seasons = seasonRoot["data"]?.get("seasons")
                seasons?.forEach { season ->
                    val seasonNumber = season["se"]?.asInt() ?: 1
                    val maxEp = season["maxEp"]?.asInt() ?: 1
                    for (episodeNumber in 1..maxEp) {
                        // FIX EPISODE DATA with detailPath
                        episodes.add(newEpisode("$id|$seasonNumber|$episodeNumber|$detailPath") {
                            this.name = "S${seasonNumber}E${episodeNumber}"
                            this.season = seasonNumber
                            this.episode = episodeNumber
                            this.posterUrl = coverUrl
                        })
                    }
                }
            }
            if (episodes.isEmpty()) episodes.add(newEpisode("$id|1|1|$detailPath") { name = "Episode 1"; season = 1; episode = 1; this.posterUrl = Poster })

            return newTvSeriesLoadResponse(title, finalUrl, type, episodes) {
                this.posterUrl = coverUrl ?: Poster
                this.backgroundPosterUrl = Background ?: backgroundUrl
                this.plot = Description ?: description
                this.year = year
                this.tags = tags
                this.actors = actors
                this.score = Score.from10(IMDBRating) ?: imdbRating?.let { Score.from10(it) }
                addImdbId(imdbId)
                addTMDbId(tmdbId.toString())
                if (!trailerUrl.isNullOrBlank()) addTrailer(trailerUrl)
            }
        }

        // FIX MOVIE DATA with detailPath
        return newMovieLoadResponse(title, finalUrl, type, "$id|0|0|$detailPath") {
            this.posterUrl = coverUrl ?: Poster
            this.backgroundPosterUrl = Background ?: backgroundUrl
            this.plot = Description ?: description
            this.year = year
            this.tags = tags
            this.actors = actors
            this.score = Score.from10(IMDBRating) ?: imdbRating?.let { Score.from10(it) }
            addImdbId(imdbId)
            addTMDbId(tmdbId.toString())
            if (!trailerUrl.isNullOrBlank()) addTrailer(trailerUrl)
        }
    }

    // --- LOAD LINKS BARU (SOURCE: LOK-LOK.CC) ---
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
            val detailPath = parts.getOrNull(3) ?: "" // DetailPath harus ada

            // Cek Dubbing (Header Aman)
            val xClientToken = generateXClientToken()
            val subUrl = "$mainUrl/wefeed-mobile-bff/subject-api/get?subjectId=$originalId"
            val sigSub = generateXTrSignature("GET", "application/json", "application/json", subUrl)
            
            // Header untuk cek dubbing tetap pakai header asli/aman
            val headersOri = mapOf(
                "user-agent" to "com.community.mbox.in/50020042 (Linux; U; Android 16; en_IN; sdk_gphone64_x86_64; Build/BP22.250325.006; Cronet/133.0.6876.3)",
                "accept" to "application/json",
                "x-client-token" to xClientToken,
                "x-tr-signature" to sigSub,
                "x-client-info" to """{"timezone":"Asia/Jakarta"}""",
                "x-client-status" to "0"
            )
            val subResp = app.get(subUrl, headers = headersOri)
            
            val ids = mutableListOf<Pair<String, String>>()
            try {
                val root = jacksonObjectMapper().readTree(subResp.body.string())
                val dubs = root?.get("data")?.let { it["dubs"] ?: it["subject"]?.get("dubs") } ?: root?.get("dubs")
                ids.add(originalId to "Original")
                dubs?.forEach { ids.add((it["subjectId"]?.asText() ?: "") to (it["lanName"]?.asText() ?: "Dub")) }
            } catch (e: Exception) {
                ids.add(originalId to "Original")
            }

            // Loop untuk Stream (Server Baru)
            ids.filter { it.first.isNotBlank() }.forEach { (id, lang) ->
                try {
                    val playUrl = "https://lok-lok.cc/wefeed-h5api-bff/subject/play?subjectId=$id&se=$se&ep=$ep&detailPath=$detailPath"
                    val headers = mapOf(
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

                    val playResp = app.get(playUrl, headers = headers)
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
                            referer = "https://lok-lok.cc/",
                            quality = qualityVal ?: Qualities.Unknown.value,
                            type = typeVal
                        ) {})
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

    private suspend fun identifyID(title: String, year: Int?, imdbRatingValue: Double?): Pair<Int?, String?> {
        // Logic TMDB asli kamu bisa dimasukkan di sini jika perlu
        return Pair(null, null)
    }

    private suspend fun fetchMetaData(imdbId: String?, type: TvType): JsonNode? {
        if (imdbId.isNullOrBlank()) return null
        val metaType = if (type == TvType.TvSeries) "series" else "movie"
        return try {
            val resp = app.get("https://aiometadata.elfhosted.com/stremio/b7cb164b-074b-41d5-b458-b3a834e197bb/meta/$metaType/$imdbId.json").text
            jacksonObjectMapper().readTree(resp)["meta"]
        } catch (_: Exception) { null }
    }
    
    suspend fun fetchTmdbLogoUrl(tmdbAPI: String, apiKey: String, type: TvType, tmdbId: Int?, appLangCode: String?): String? { return null }
}
