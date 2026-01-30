package com.Adimoviebox

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTMDbId
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URI
import java.net.URLEncoder
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.max

class Adimoviebox : MainAPI() {
    // KITA GUNAKAN URL MOVIEBOX KARENA LEBIH STABIL & HASIL LEBIH BANYAK
    override var mainUrl = "https://api.inmoviebox.com"
    override var name = "Adimoviebox"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    // --- KEAMANAN DARI MOVIEBOX (Diporting ke Java Murni) ---
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

    // FIX: Menggunakan java.net.URI (bukan Android URI) agar lolos Build
    private fun buildCanonicalString(
        method: String,
        accept: String?,
        contentType: String?,
        url: String,
        body: String?,
        timestamp: Long
    ): String {
        val uri = URI(url)
        val path = uri.path ?: ""
        
        // Manual Query Sorting untuk Signature yang Valid
        val query = if (uri.query != null && uri.query.isNotEmpty()) {
            uri.query.split("&")
                .map { 
                    val parts = it.split("=", limit = 2)
                    // Jangan encode ulang di sini karena raw query sudah encoded atau akan diproses nanti
                    (parts.getOrNull(0) ?: "") to (parts.getOrNull(1) ?: "")
                }
                .filter { it.first.isNotEmpty() }
                .groupBy { it.first }
                .toSortedMap()
                .flatMap { (key, values) ->
                     values.map { it.second }.map { value -> "$key=$value" }
                }
                .joinToString("&")
        } else ""
        
        val canonicalUrl = if (query.isNotEmpty()) "$path?$query" else path

        val bodyBytes = body?.toByteArray(Charsets.UTF_8)
        val bodyHash = if (bodyBytes != null) {
            val trimmed = if (bodyBytes.size > 102400) bodyBytes.copyOfRange(0, 102400) else bodyBytes
            md5(trimmed)
        } else ""

        val bodyLength = bodyBytes?.size?.toString() ?: ""
        
        // Format String Kanonikal MovieBox
        return "${method.uppercase()}\n" +
                "${accept ?: ""}\n" +
                "${contentType ?: ""}\n" +
                "$bodyLength\n" +
                "$timestamp\n" +
                "$bodyHash\n" +
                canonicalUrl
    }

    private fun generateXTrSignature(
        method: String,
        accept: String?,
        contentType: String?,
        url: String,
        body: String? = null,
        useAltKey: Boolean = false
    ): String {
        val timestamp = System.currentTimeMillis()
        val canonical = buildCanonicalString(method, accept, contentType, url, body, timestamp)
        val secret = if (useAltKey) secretKeyAlt else secretKeyDefault
        val secretBytes = base64DecodeArray(secret)

        val mac = Mac.getInstance("HmacMD5")
        mac.init(SecretKeySpec(secretBytes, "HmacMD5"))
        val signature = base64Encode(mac.doFinal(canonical.toByteArray(Charsets.UTF_8)))

        return "$timestamp|2|$signature"
    }

    // --- KATEGORI ASLI ADIMOVIEBOX (TIDAK DIUBAH) ---
    override val mainPage: List<MainPageData> = mainPageOf(
        "5283462032510044280" to "Indonesian Drama",
        "6528093688173053896" to "Indonesian Movies",
        "5848753831881965888" to "Indo Horror",
        "997144265920760504" to "Hollywood Movies",
        "4380734070238626200" to "K-Drama",
        "8624142774394406504" to "C-Drama",
        "3058742380078711608" to "Disney",
        "8449223314756747760" to "Pinoy Drama",
        "606779077307122552" to "Pinoy Movie",
        "872031290915189720" to "Bad Ending Romance" 
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val perPage = 12
        val id = request.data
        
        // Kita sesuaikan endpoint agar menerima ID kategori Adimoviebox
        // Menggunakan endpoint ranking-list yang umum
        val url = "$mainUrl/wefeed-mobile-bff/ranking-list/content?id=$id&page=$page&perPage=$perPage"

        val xClientToken = generateXClientToken()
        val xTrSignature = generateXTrSignature("GET", "application/json", "application/json", url)

        val headers = mapOf(
            "accept" to "application/json",
            "content-type" to "application/json",
            "x-client-token" to xClientToken,
            "x-tr-signature" to xTrSignature,
            "x-client-info" to """{"package_name":"com.community.mbox.in","version_code":50020042,"os":"android","timezone":"Asia/Jakarta"}"""
        )

        val response = app.get(url, headers = headers)
        
        val mapper = jacksonObjectMapper()
        val root = try { mapper.readTree(response.body.string()) } catch(e: Exception) { null }
        
        val dataNode = root?.get("data")
        val items = dataNode?.get("subjectList") ?: dataNode?.get("items") ?: return newHomePageResponse(emptyList())
        
        val home = items.mapNotNull { item ->
            val title = item["title"]?.asText() ?: return@mapNotNull null
            val subjectId = item["subjectId"]?.asText() ?: return@mapNotNull null
            val coverImg = item["cover"]?.get("url")?.asText()
            val subjectType = item["subjectType"]?.asInt() ?: 1
            
            newMovieSearchResponse(
                name = title,
                url = subjectId, // Kita kirim SubjectID langsung
                type = if (subjectType == 2) TvType.TvSeries else TvType.Movie
            ) {
                this.posterUrl = coverImg
                this.score = Score.from10(item["imdbRatingValue"]?.asText())
            }
        }

        return newHomePageResponse(request.name, home)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        // Menggunakan V2 Search dari MovieBox (Lebih akurat)
        val url = "$mainUrl/wefeed-mobile-bff/subject-api/search/v2"
        val jsonBody = """{"page": 1, "perPage": 20, "keyword": "$query"}"""
        
        val xClientToken = generateXClientToken()
        val xTrSignature = generateXTrSignature("POST", "application/json", "application/json; charset=utf-8", url, jsonBody)
        
        val headers = mapOf(
            "accept" to "application/json",
            "content-type" to "application/json",
            "x-client-token" to xClientToken,
            "x-tr-signature" to xTrSignature,
            "x-client-info" to """{"package_name":"com.community.mbox.in","version_code":50020042,"os":"android","timezone":"Asia/Jakarta"}"""
        )

        val response = app.post(url, headers = headers, requestBody = jsonBody.toRequestBody("application/json".toMediaType()))
        val mapper = jacksonObjectMapper()
        val root = mapper.readTree(response.body.string())
        
        // Parsing struktur V2 (results -> subjects)
        val results = root["data"]?.get("results") ?: return emptyList()
        val searchList = mutableListOf<SearchResponse>()
        
        for (result in results) {
            val subjects = result["subjects"] ?: continue
            for (subject in subjects) {
                val title = subject["title"]?.asText() ?: continue
                val id = subject["subjectId"]?.asText() ?: continue
                val coverImg = subject["cover"]?.get("url")?.asText()
                val type = if (subject["subjectType"]?.asInt() == 2) TvType.TvSeries else TvType.Movie
                
                searchList.add(newMovieSearchResponse(title, id, type) {
                    this.posterUrl = coverImg
                    this.score = Score.from10(subject["imdbRatingValue"]?.asText())
                })
            }
        }
        return searchList
    }

    override suspend fun load(url: String): LoadResponse {
        val id = url.substringAfterLast("subjectId=").substringAfterLast("/")
        
        val finalUrl = "$mainUrl/wefeed-mobile-bff/subject-api/get?subjectId=$id"
        val xClientToken = generateXClientToken()
        val xTrSignature = generateXTrSignature("GET", "application/json", "application/json", finalUrl)

        val headers = mapOf(
            "accept" to "application/json",
            "content-type" to "application/json",
            "x-client-token" to xClientToken,
            "x-tr-signature" to xTrSignature,
            "x-client-info" to """{"package_name":"com.community.mbox.in","version_code":50020042,"os":"android","timezone":"Asia/Jakarta"}"""
        )

        val response = app.get(finalUrl, headers = headers)
        val mapper = jacksonObjectMapper()
        val root = mapper.readTree(response.body.string())
        val data = root["data"] ?: throw ErrorLoadingException("Data not found")

        val title = data["title"]?.asText()?.substringBefore("[") ?: ""
        val description = data["description"]?.asText()
        val releaseDate = data["releaseDate"]?.asText()
        val year = releaseDate?.take(4)?.toIntOrNull()
        val coverUrl = data["cover"]?.get("url")?.asText()
        val subjectType = data["subjectType"]?.asInt() ?: 1
        val type = if (subjectType == 2) TvType.TvSeries else TvType.Movie
        val imdbRating = data["imdbRatingValue"]?.asText()?.toDoubleOrNull()
        
        // TMDB Matching (Fitur Kuat MovieBox)
        val (tmdbId, imdbId) = identifyID(title, year, imdbRating)
        val logoUrl = fetchTmdbLogoUrl(tmdbId, type)

        // Actors
        val actors = data["staffList"]?.mapNotNull { staff ->
            if (staff["staffType"]?.asInt() == 1) {
                ActorData(Actor(staff["name"]?.asText() ?: "", staff["avatarUrl"]?.asText()), roleString = staff["character"]?.asText())
            } else null
        } ?: emptyList()

        if (type == TvType.TvSeries) {
            val seasonUrl = "$mainUrl/wefeed-mobile-bff/subject-api/season-info?subjectId=$id"
            val seasonSig = generateXTrSignature("GET", "application/json", "application/json", seasonUrl)
            val seasonHeaders = headers.toMutableMap().apply { put("x-tr-signature", seasonSig) }
            val seasonResp = app.get(seasonUrl, headers = seasonHeaders)
            
            val episodes = mutableListOf<Episode>()
            try {
                val sRoot = mapper.readTree(seasonResp.body.string())
                sRoot["data"]?.get("seasons")?.forEach { season ->
                    val sNum = season["se"]?.asInt() ?: 1
                    val maxEp = season["maxEp"]?.asInt() ?: 0
                    for (i in 1..maxEp) {
                        episodes.add(newEpisode("$id|$sNum|$i") {
                            this.name = "Episode $i"
                            this.season = sNum
                            this.episode = i
                            this.posterUrl = coverUrl
                        })
                    }
                }
            } catch(e: Exception) {}

            return newTvSeriesLoadResponse(title, finalUrl, type, episodes) {
                this.posterUrl = coverUrl
                this.logoUrl = logoUrl
                this.year = year
                this.plot = description
                this.actors = actors
                this.score = Score.from10(imdbRating?.toString())
                addImdbId(imdbId)
                addTMDbId(tmdbId.toString())
            }
        } else {
            return newMovieLoadResponse(title, finalUrl, type, id) {
                this.posterUrl = coverUrl
                this.logoUrl = logoUrl
                this.year = year
                this.plot = description
                this.actors = actors
                this.score = Score.from10(imdbRating?.toString())
                addImdbId(imdbId)
                addTMDbId(tmdbId.toString())
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // Logika Multi-Audio dari MovieBox
        val parts = data.split("|")
        val id = parts[0]
        val season = if (parts.size > 1) parts[1].toIntOrNull() ?: 0 else 0
        val episode = if (parts.size > 2) parts[2].toIntOrNull() ?: 0 else 0

        val subjectUrl = "$mainUrl/wefeed-mobile-bff/subject-api/get?subjectId=$id"
        val subjectSig = generateXTrSignature("GET", "application/json", "application/json", subjectUrl)
        val headers = mapOf(
            "accept" to "application/json",
            "content-type" to "application/json",
            "x-client-token" to generateXClientToken(),
            "x-tr-signature" to subjectSig,
            "x-client-info" to """{"package_name":"com.community.mbox.in","version_code":50020042,"os":"android","timezone":"Asia/Jakarta"}"""
        )
        
        val mapper = jacksonObjectMapper()
        val idList = mutableListOf<Pair<String, String>>()
        idList.add(id to "Original")

        // Cek Dubbing
        try {
            val resp = app.get(subjectUrl, headers = headers)
            val root = mapper.readTree(resp.body.string())
            root["data"]?.get("dubs")?.forEach { dub ->
                 val dubId = dub["subjectId"]?.asText()
                 val lang = dub["lanName"]?.asText()
                 if (dubId != null && dubId != id) {
                     idList.add(dubId to (lang ?: "Dub"))
                 }
            }
        } catch(e: Exception) {}

        var found = false
        for ((currId, lang) in idList) {
            val playUrl = "$mainUrl/wefeed-mobile-bff/subject-api/play-info?subjectId=$currId&se=$season&ep=$episode"
            val playSig = generateXTrSignature("GET", "application/json", "application/json", playUrl)
            val playHeaders = headers.toMutableMap().apply { put("x-tr-signature", playSig) }
            
            try {
                val resp = app.get(playUrl, headers = playHeaders)
                val root = mapper.readTree(resp.body.string())
                val streams = root["data"]?.get("streams") ?: continue
                
                for (stream in streams) {
                    val url = stream["url"]?.asText() ?: continue
                    val res = stream["resolutions"]?.asText() ?: ""
                    val streamId = stream["id"]?.asText() ?: ""
                    
                    callback.invoke(newExtractorLink("Adimoviebox", "Adimoviebox $lang", url, INFER_TYPE) {
                        this.quality = getQualityFromName(res)
                        this.referer = mainUrl
                    })
                    found = true

                    // Subtitle
                    val subUrl = "$mainUrl/wefeed-mobile-bff/subject-api/get-stream-captions?subjectId=$currId&streamId=$streamId"
                    val subSig = generateXTrSignature("GET", "", "", subUrl)
                    val subHeaders = mapOf(
                        "x-client-token" to generateXClientToken(),
                        "x-tr-signature" to subSig,
                         "x-client-info" to """{"package_name":"com.community.mbox.in","version_code":50020042,"os":"android","timezone":"Asia/Jakarta"}"""
                    )
                    
                    try {
                        val subResp = app.get(subUrl, headers = subHeaders)
                        mapper.readTree(subResp.body.string())["data"]?.get("extCaptions")?.forEach { cap ->
                            subtitleCallback.invoke(newSubtitleFile(cap["lanName"]?.asText() ?: "Unknown", cap["url"]?.asText() ?: return@forEach))
                        }
                    } catch(e: Exception) {}
                }
            } catch(e: Exception) {}
        }
        return found
    }

    // --- HELPER UNTUK TMDB & LOGO (PERSIS MOVIEBOX TAPI JAVA NET URI) ---
    private suspend fun identifyID(title: String, year: Int?, imdbRating: Double?): Pair<Int?, String?> {
        val normTitle = title.lowercase().replace(Regex("[^a-z0-9 ]"), " ").trim()
        val url = "https://api.themoviedb.org/3/search/multi?api_key=1865f43a0549ca50d341dd9ab8b29f49&query=${URLEncoder.encode(normTitle, "UTF-8")}&page=1"
        try {
            val res = app.get(url).text
            val json = JSONObject(res)
            val results = json.optJSONArray("results") ?: return null to null
            if (results.length() > 0) {
                val item = results.getJSONObject(0)
                val id = item.optInt("id")
                // Fetch details for IMDB
                val detUrl = "https://api.themoviedb.org/3/${item.optString("media_type")}/$id?api_key=1865f43a0549ca50d341dd9ab8b29f49&append_to_response=external_ids"
                val detJson = JSONObject(app.get(detUrl).text)
                val imdb = detJson.optJSONObject("external_ids")?.optString("imdb_id")
                return id to imdb
            }
        } catch(e: Exception) {}
        return null to null
    }

    private suspend fun fetchTmdbLogoUrl(tmdbId: Int?, type: TvType): String? {
        if (tmdbId == null) return null
        val typeStr = if (type == TvType.Movie) "movie" else "tv"
        val url = "https://api.themoviedb.org/3/$typeStr/$tmdbId/images?api_key=98ae14df2b8d8f8f8136499daf79f0e0"
        try {
            val res = app.get(url).text
            val logos = JSONObject(res).optJSONArray("logos")
            if (logos != null && logos.length() > 0) {
                return "https://image.tmdb.org/t/p/w500" + logos.getJSONObject(0).optString("file_path")
            }
        } catch(e: Exception) {}
        return null
    }
}
