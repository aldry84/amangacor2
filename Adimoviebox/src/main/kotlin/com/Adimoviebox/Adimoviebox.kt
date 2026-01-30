package com.Adimoviebox

import com.fasterxml.jackson.annotation.JsonProperty
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

class Adimoviebox : MainAPI() {
    // SERVER BARU (Untuk Search & Link yang lebih kuat)
    override var mainUrl = "https://api.inmoviebox.com"
    
    // SERVER LAMA (Khusus untuk memunculkan Kategori Adimoviebox)
    private val categoryApiUrl = "https://h5-api.aoneroom.com"

    override var name = "Adimoviebox"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    // --- SISTEM KEAMANAN (JAVA MURNI) ---
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

    // Signature Generator (Aman Build Cross-Platform)
    private fun generateXTrSignature(
        method: String,
        url: String,
        body: String? = null,
        useAltKey: Boolean = false
    ): String {
        val timestamp = System.currentTimeMillis()
        val uri = URI(url)
        val path = uri.path ?: ""
        
        // Sorting Query Parameter (Wajib)
        val query = if (uri.query != null && uri.query.isNotEmpty()) {
            uri.query.split("&")
                .map { 
                    val parts = it.split("=", limit = 2)
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

        val canonical = "${method.uppercase()}\n" +
                "application/json\n" +
                "application/json\n" +
                "${bodyBytes?.size ?: ""}\n" +
                "$timestamp\n" +
                "$bodyHash\n" +
                canonicalUrl

        val secret = if (useAltKey) secretKeyAlt else secretKeyDefault
        val secretBytes = base64DecodeArray(secret)
        val mac = Mac.getInstance("HmacMD5")
        mac.init(SecretKeySpec(secretBytes, "HmacMD5"))
        val signature = base64Encode(mac.doFinal(canonical.toByteArray(Charsets.UTF_8)))

        return "$timestamp|2|$signature"
    }

    // --- KATEGORI ASLI ADIMOVIEBOX ---
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
        val id = request.data
        // FIX: Menggunakan URL 'categoryApiUrl' (Aoneroom) agar kategori Adimoviebox Muncul
        // Path juga disesuaikan ke 'wefeed-h5api-bff' (milik Aoneroom/Loklok lama)
        val url = "$categoryApiUrl/wefeed-h5api-bff/ranking-list/content?id=$id&page=$page&perPage=12"

        val xClientToken = generateXClientToken()
        val xTrSignature = generateXTrSignature("GET", url)

        val headers = mapOf(
            "accept" to "application/json",
            "content-type" to "application/json",
            "x-client-token" to xClientToken,
            "x-tr-signature" to xTrSignature,
            // Header spesifik agar server lama mau merespon
            "origin" to categoryApiUrl,
            "referer" to "$categoryApiUrl/"
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
                url = subjectId, 
                type = if (subjectType == 2) TvType.TvSeries else TvType.Movie
            ) {
                this.posterUrl = coverImg
                this.score = Score.from10(item["imdbRatingValue"]?.asText())
            }
        }

        return newHomePageResponse(request.name, home)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        // Search tetap ke SERVER BARU (MovieBox) agar hasil lebih akurat
        val url = "$mainUrl/wefeed-mobile-bff/subject-api/search/v2"
        val jsonBody = """{"page": 1, "perPage": 20, "keyword": "$query"}"""
        
        val xClientToken = generateXClientToken()
        val xTrSignature = generateXTrSignature("POST", url, jsonBody)
        
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
        
        // Coba load dari SERVER BARU dulu (lebih cepat/stabil)
        // Jika gagal (mungkin ID dari kategori lama beda), fallback ke server lama
        var finalUrl = "$mainUrl/wefeed-mobile-bff/subject-api/get?subjectId=$id"
        var xClientToken = generateXClientToken()
        var xTrSignature = generateXTrSignature("GET", finalUrl)
        
        var headers = mutableMapOf(
            "accept" to "application/json",
            "content-type" to "application/json",
            "x-client-token" to xClientToken,
            "x-tr-signature" to xTrSignature,
            "x-client-info" to """{"package_name":"com.community.mbox.in","version_code":50020042,"os":"android","timezone":"Asia/Jakarta"}"""
        )

        var response = app.get(finalUrl, headers = headers)
        if (response.code != 200) {
            // FALLBACK: Jika di server baru tidak ketemu, cari di server lama (Aoneroom)
            finalUrl = "$categoryApiUrl/wefeed-h5api-bff/detail?detailPath=$id"
            xTrSignature = generateXTrSignature("GET", finalUrl)
            headers["x-tr-signature"] = xTrSignature
            headers["origin"] = categoryApiUrl
            headers["referer"] = "$categoryApiUrl/"
            response = app.get(finalUrl, headers = headers)
        }

        val mapper = jacksonObjectMapper()
        val root = try { mapper.readTree(response.body.string()) } catch(e: Exception) { throw ErrorLoadingException("Gagal memuat data") }
        
        // Handle perbedaan struktur data (Aoneroom ada 'subject', Moviebox langsung field)
        val dataRaw = root["data"] ?: throw ErrorLoadingException("Data kosong")
        val data = if (dataRaw.has("subject")) dataRaw["subject"] else dataRaw

        val title = data?.get("title")?.asText()?.substringBefore("[") ?: "Unknown"
        val description = data?.get("description")?.asText()
        val releaseDate = data?.get("releaseDate")?.asText()
        val year = releaseDate?.take(4)?.toIntOrNull()
        val coverUrl = data?.get("cover")?.get("url")?.asText()
        val subjectType = data?.get("subjectType")?.asInt() ?: 1
        val type = if (subjectType == 2) TvType.TvSeries else TvType.Movie
        val imdbRating = data?.get("imdbRatingValue")?.asText()?.toDoubleOrNull()
        
        // TMDB & Actors
        val (tmdbId, imdbId) = identifyID(title, year, imdbRating)
        val logoUrl = fetchTmdbLogoUrl(tmdbId, type)
        
        val actors = if (dataRaw.has("stars")) {
             dataRaw["stars"]?.mapNotNull { 
                 ActorData(Actor(it["name"]?.asText() ?: "", it["avatarUrl"]?.asText()), roleString = it["character"]?.asText()) 
             } ?: emptyList()
        } else {
             data?.get("staffList")?.mapNotNull { staff ->
                if (staff["staffType"]?.asInt() == 1) {
                    ActorData(Actor(staff["name"]?.asText() ?: "", staff["avatarUrl"]?.asText()), roleString = staff["character"]?.asText())
                } else null
            } ?: emptyList()
        }

        if (type == TvType.TvSeries) {
            val episodes = mutableListOf<Episode>()
            
            // Logic Episode (Cek resource/seasons atau request terpisah)
            if (dataRaw.has("resource")) {
                dataRaw["resource"]?.get("seasons")?.forEach { season ->
                    val sNum = season["se"]?.asInt() ?: 1
                    val maxEp = season["maxEp"]?.asInt() ?: 0
                    val allEp = season["allEp"]?.asText()
                    val epList = if (!allEp.isNullOrEmpty()) allEp.split(",").map { it.toInt() } else (1..maxEp).toList()
                    
                    epList.forEach { i ->
                        episodes.add(newEpisode("$id|$sNum|$i") {
                            this.name = "Episode $i"
                            this.season = sNum
                            this.episode = i
                            this.posterUrl = coverUrl
                        })
                    }
                }
            } else {
                // Moviebox style (Request terpisah)
                val seasonUrl = "$mainUrl/wefeed-mobile-bff/subject-api/season-info?subjectId=$id"
                val seasonSig = generateXTrSignature("GET", seasonUrl)
                val sHeaders = headers.toMutableMap().apply { put("x-tr-signature", seasonSig) }
                try {
                    val sResp = app.get(seasonUrl, headers = sHeaders)
                    val sRoot = mapper.readTree(sResp.body.string())
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
            }

            return newTvSeriesLoadResponse(title, url, type, episodes) {
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
            return newMovieLoadResponse(title, url, type, id) {
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
        val parts = data.split("|")
        val id = parts[0]
        val season = if (parts.size > 1) parts[1].toIntOrNull() ?: 0 else 0
        val episode = if (parts.size > 2) parts[2].toIntOrNull() ?: 0 else 0

        // Prioritas ke Server Baru (MovieBox) karena link lebih stabil
        val idList = mutableListOf<Pair<String, String>>()
        idList.add(id to "Original")

        // Cek Dubbing di Server Baru
        try {
            val dubUrl = "$mainUrl/wefeed-mobile-bff/subject-api/get?subjectId=$id"
            val dubSig = generateXTrSignature("GET", dubUrl)
            val headers = mapOf(
                "accept" to "application/json",
                "content-type" to "application/json",
                "x-client-token" to generateXClientToken(),
                "x-tr-signature" to dubSig,
                "x-client-info" to """{"package_name":"com.community.mbox.in","version_code":50020042,"os":"android","timezone":"Asia/Jakarta"}"""
            )
            val resp = app.get(dubUrl, headers = headers)
            val root = jacksonObjectMapper().readTree(resp.body.string())
            root["data"]?.get("dubs")?.forEach { dub ->
                 val dubId = dub["subjectId"]?.asText()
                 val lang = dub["lanName"]?.asText()
                 if (dubId != null && dubId != id) {
                     idList.add(dubId to (lang ?: "Dub"))
                 }
            }
        } catch(e: Exception) {}

        var found = false
        val mapper = jacksonObjectMapper()

        for ((currId, lang) in idList) {
            val playUrl = "$mainUrl/wefeed-mobile-bff/subject-api/play-info?subjectId=$currId&se=$season&ep=$episode"
            val playSig = generateXTrSignature("GET", playUrl)
            val playHeaders = mapOf(
                "accept" to "application/json",
                "content-type" to "application/json",
                "x-client-token" to generateXClientToken(),
                "x-tr-signature" to playSig,
                "x-client-info" to """{"package_name":"com.community.mbox.in","version_code":50020042,"os":"android","timezone":"Asia/Jakarta"}"""
            )
            
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
                    try {
                        val subUrl = "$mainUrl/wefeed-mobile-bff/subject-api/get-stream-captions?subjectId=$currId&streamId=$streamId"
                        val subSig = generateXTrSignature("GET", subUrl)
                        val subHeaders = playHeaders.toMutableMap().apply { put("x-tr-signature", subSig) }
                        
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

    // --- HELPER TMDB ---
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
