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
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.newSubtitleFile
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

    // --- FUNGSI PENTING: Header Bersih (Anti-Banned 407) ---
    private fun getSafeHeaders(xClientToken: String, xTrSignature: String): Map<String, String> {
        return mapOf(
            "user-agent" to "com.community.mbox.in/50020042 (Linux; U; Android 16; en_IN; sdk_gphone64_x86_64; Build/BP22.250325.006; Cronet/133.0.6876.3)",
            "accept" to "application/json",
            "content-type" to "application/json",
            "connection" to "keep-alive",
            "x-client-token" to xClientToken,
            "x-tr-signature" to xTrSignature,
            // HANYA kirim timezone. HAPUS device_id, gaid, dll yang bikin error 407
            "x-client-info" to """{"timezone":"Asia/Jakarta"}""",
            "x-client-status" to "0"
        )
    }

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
        val requestBody = jsonBody.toRequestBody("application/json".toMediaType())
        
        val response = if (request.data.contains("|")) {
            val xTrSignature = generateXTrSignature("POST", "application/json", "application/json; charset=utf-8", url, jsonBody)
            app.post(url, headers = getSafeHeaders(xClientToken, xTrSignature), requestBody = requestBody)
        } else {
            val getxTrSignature = generateXTrSignature("GET", "application/json", "application/json", url)
            app.get(url, headers = getSafeHeaders(xClientToken, getxTrSignature))
        }

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
        
        val headers = getSafeHeaders(xClientToken, xTrSignature)
        val requestBody = jsonBody.toRequestBody("application/json".toMediaType())
        val response = app.post(url, headers = headers, requestBody = requestBody)

        val responseBody = response.body.string()
        val mapper = jacksonObjectMapper()
        val root = mapper.readTree(responseBody)
        val results = root["data"]?.get("results") ?: return emptyList()
        val searchList = mutableListOf<SearchResponse>()
        for (result in results) {
            val subjects = result["subjects"] ?: continue
            for (subject in subjects) {
                val title = subject["title"]?.asText() ?: continue
                val id = subject["subjectId"]?.asText() ?: continue
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
        val headers = getSafeHeaders(xClientToken, xTrSignature)

        val response = app.get(finalUrl, headers = headers)
        if (response.code != 200) throw ErrorLoadingException("Failed to load data")

        val body = response.body.string()
        val mapper = jacksonObjectMapper()
        val root = mapper.readTree(body)
        val data = root["data"] ?: throw ErrorLoadingException("No data")

        // Support structure API baru & lama
        val subject = data["subject"] ?: data 
        val title = subject["title"]?.asText()?.substringBefore("[") ?: throw ErrorLoadingException("No title found")
        val description = subject["description"]?.asText()
        val releaseDate = subject["releaseDate"]?.asText()
        val duration = subject["duration"]?.asText()
        val genre = subject["genre"]?.asText()
        val imdbRating = subject["imdbRatingValue"]?.asText()?.toDoubleOrNull()?.times(10)?.toInt()
        val year = releaseDate?.substring(0, 4)?.toIntOrNull()
        val coverUrl = subject["cover"]?.get("url")?.asText()
        val backgroundUrl = subject["cover"]?.get("url")?.asText()

        // --- TRAILER FIX ---
        val trailerUrl = subject["trailer"]?.get("videoAddress")?.get("url")?.asText()

        val subjectType = subject["subjectType"]?.asInt() ?: 1
        
        val actors = data["stars"]?.mapNotNull { star ->
            val name = star["name"]?.asText() ?: return@mapNotNull null
            ActorData(Actor(name, star["avatarUrl"]?.asText()), roleString = star["character"]?.asText())
        } ?: data["staffList"]?.mapNotNull { staff ->
             if (staff["staffType"]?.asInt() == 1) {
                val name = staff["name"]?.asText() ?: return@mapNotNull null
                ActorData(Actor(name, staff["avatarUrl"]?.asText()), roleString = staff["character"]?.asText())
            } else null
        } ?: emptyList()

        val tags = genre?.split(",")?.map { it.trim() } ?: emptyList()
        val durationMinutes = duration?.let { dur ->
            val regex = """(\d+)h\s*(\d+)m""".toRegex()
            val m = regex.find(dur)
            if (m != null) (m.groupValues[1].toIntOrNull() ?: 0) * 60 + (m.groupValues[2].toIntOrNull() ?: 0)
            else dur.replace("m", "").toIntOrNull()
        }

        val type = if (subjectType == 2) TvType.TvSeries else TvType.Movie
        val (tmdbId, imdbId) = identifyID(title.substringBefore("(").substringBefore("["), year, imdbRating?.toDouble())
        
        // HAPUS logoUrl agar tidak crash di HP kamu
        // val logoUrl = ...

        val meta = if (!imdbId.isNullOrBlank()) fetchMetaData(imdbId, type) else null
        val Poster = meta?.get("poster")?.asText() ?: coverUrl
        val Background = meta?.get("background")?.asText() ?: backgroundUrl
        val Description = meta?.get("description")?.asText() ?: description
        val IMDBRating = meta?.get("imdbRating")?.asText()

        if (type == TvType.TvSeries) {
            val seasonUrl = "$mainUrl/wefeed-mobile-bff/subject-api/season-info?subjectId=$id"
            val seasonSig = generateXTrSignature("GET", "application/json", "application/json", seasonUrl)
            val seasonHeaders = getSafeHeaders(xClientToken, seasonSig)

            val seasonResponse = app.get(seasonUrl, headers = seasonHeaders)
            val episodes = mutableListOf<Episode>()

            if (seasonResponse.code == 200) {
                val seasonRoot = mapper.readTree(seasonResponse.body.string())
                val seasons = seasonRoot["data"]?.get("seasons")
                seasons?.forEach { season ->
                    val seasonNumber = season["se"]?.asInt() ?: 1
                    val maxEp = season["maxEp"]?.asInt() ?: 1
                    for (episodeNumber in 1..maxEp) {
                        episodes.add(newEpisode("$id|$seasonNumber|$episodeNumber") {
                            this.name = "S${seasonNumber}E${episodeNumber}"
                            this.season = seasonNumber
                            this.episode = episodeNumber
                            this.posterUrl = coverUrl
                        })
                    }
                }
            }
            if (episodes.isEmpty()) episodes.add(newEpisode("$id|1|1") { this.name = "Episode 1"; this.season = 1; this.episode = 1; this.posterUrl = Poster })

            return newTvSeriesLoadResponse(title, finalUrl, type, episodes) {
                this.posterUrl = coverUrl ?: Poster
                this.backgroundPosterUrl = Background ?: backgroundUrl
                // Logo DIHAPUS
                this.plot = Description ?: description
                this.year = year
                this.tags = tags
                this.actors = actors
                this.score = Score.from10(IMDBRating) ?: imdbRating?.let { Score.from10(it) }
                this.duration = durationMinutes
                addImdbId(imdbId)
                addTMDbId(tmdbId.toString())
                if (!trailerUrl.isNullOrBlank()) addTrailer(trailerUrl)
            }
        }

        return newMovieLoadResponse(title, finalUrl, type, id) {
            this.posterUrl = coverUrl ?: Poster
            this.backgroundPosterUrl = Background ?: backgroundUrl
            // Logo DIHAPUS
            this.plot = Description ?: description
            this.year = year
            this.tags = tags
            this.actors = actors
            this.score = Score.from10(IMDBRating) ?: imdbRating?.let { Score.from10(it) }
            this.duration = durationMinutes
            addImdbId(imdbId)
            addTMDbId(tmdbId.toString())
            if (!trailerUrl.isNullOrBlank()) addTrailer(trailerUrl)
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        try {
            val parts = data.split("|")
            val originalSubjectId = if (parts[0].contains("subjectId=")) Regex("""subjectId=([^&]+)""").find(parts[0])?.groupValues?.get(1) ?: parts[0] else parts[0].substringAfterLast('/')
            val season = parts.getOrNull(1)?.toIntOrNull() ?: 0
            val episode = parts.getOrNull(2)?.toIntOrNull() ?: 0
            
            val xClientToken = generateXClientToken()
            
            // --- FIX LOAD LINKS: Pakai Header Aman ---
            val subjectUrl = "$mainUrl/wefeed-mobile-bff/subject-api/get?subjectId=$originalSubjectId"
            val sigSubject = generateXTrSignature("GET", "application/json", "application/json", subjectUrl)
            val subResponse = app.get(subjectUrl, headers = getSafeHeaders(xClientToken, sigSubject)) // SAFE HEADER
            
            val subjectIds = mutableListOf<Pair<String, String>>()
            var originalLanguageName = "Original"
            if (subResponse.code == 200) {
                val mapper = jacksonObjectMapper()
                val subDataJson = mapper.readTree(subResponse.body.string())["data"]
                // Handle subject or root data
                val subData = subDataJson?.get("subject") ?: subDataJson
                val dubs = subData?.get("dubs") ?: subDataJson?.get("dubs")
                
                dubs?.forEach { dub ->
                    val dubId = dub["subjectId"]?.asText()
                    val lang = dub["lanName"]?.asText()
                    if (dubId != null && lang != null) {
                        if (dubId == originalSubjectId) originalLanguageName = lang else subjectIds.add(Pair(dubId, lang))
                    }
                }
            }
            subjectIds.add(0, Pair(originalSubjectId, originalLanguageName))

            for ((subjectId, language) in subjectIds) {
                try {
                    val url = "$mainUrl/wefeed-mobile-bff/subject-api/play-info?subjectId=$subjectId&se=$season&ep=$episode"
                    val sigPlay = generateXTrSignature("GET", "application/json", "application/json", url)
                    
                    // --- FIX LINK REQUEST: Pakai Header Aman ---
                    val response = app.get(url, headers = getSafeHeaders(xClientToken, sigPlay)) // SAFE HEADER
                    
                    if (response.code == 200) {
                        val mapper = jacksonObjectMapper()
                        val root = mapper.readTree(response.body.string())
                        val streams = root["data"]?.get("streams")
                        streams?.forEach { stream ->
                            val streamUrl = stream["url"]?.asText() ?: return@forEach
                            val resolution = stream["resolutions"]?.asText() ?: ""
                            val quality = getHighestQuality(resolution)
                            
                            callback.invoke(
                                newExtractorLink(
                                    source = "$name $language",
                                    name = "$name ($language)",
                                    url = streamUrl,
                                    type = if (streamUrl.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                                ) {
                                    this.quality = quality ?: Qualities.Unknown.value
                                }
                            )
                        }
                    }
                } catch (_: Exception) {}
            }
            return true
        } catch (_: Exception) { return false }
    }
}

fun getHighestQuality(input: String): Int? {
    val qualities = listOf(
        "2160" to Qualities.P2160.value,
        "1080" to Qualities.P1080.value,
        "720" to Qualities.P720.value,
        "480" to Qualities.P480.value,
        "360" to Qualities.P360.value
    )
    return qualities.firstOrNull { input.contains(it.first) }?.second
}

private suspend fun identifyID(title: String, year: Int?, imdbRatingValue: Double?): Pair<Int?, String?> {
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

suspend fun fetchTmdbLogoUrl(tmdbAPI: String, apiKey: String, type: TvType, tmdbId: Int?, appLangCode: String?): String? {
    return null 
}
