package com.phisher98

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.mvvm.safeApiCall
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

class KisskhProvider : MainAPI() {
    override var mainUrl = "https://kisskh.ovh"
    override var name = "Kisskh"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.AsianDrama, TvType.Anime)

    private val videoScriptUrl = "https://script.google.com/macros/s/AKfycbzn8B31PuDxzaMa9_CQ0VGEDasFqfzI5bXvjaIZH4DM8DNq9q6xj1ALvZNz_JT3jF0suA/exec"
    private val subScriptUrl = "https://script.google.com/macros/s/AKfycbyq6hTj0ZhlinYC6xbggtgo166tp6XaDKBCGtnYk8uOfYBUFwwxBui0sGXiu_zIFmA/exec"

    override val mainPage = mainPageOf(
        "&type=0&sub=0&country=0&status=0&order=2" to "Latest",
        "&type=0&sub=0&country=2&status=0&order=1" to "Top K-Drama",
        "&type=0&sub=0&country=1&status=0&order=1" to "Top C-Drama",
        "&type=2&sub=0&country=2&status=0&order=1" to "Movie Popular",
        "&type=1&sub=0&country=2&status=0&order=1" to "TVSeries Popular",
        "&type=3&sub=0&country=0&status=0&order=1" to "Anime Popular",
        "&type=0&sub=0&country=0&status=3&order=2" to "Upcoming"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = "$mainUrl/api/DramaList/List?page=$page${request.data}"
        val home = app.get(url).parsedSafe<Responses>()?.data?.mapNotNull { it.toSearchResponse() }
            ?: throw ErrorLoadingException("Invalid Json response")
        return newHomePageResponse(
            list = HomePageList(name = request.name, list = home, isHorizontalImages = true),
            hasNext = true
        )
    }

    private fun Media.toSearchResponse(): SearchResponse? {
        if (!settingsForProvider.enableAdult && this.label?.contains("RAW") == true) return null
        return newAnimeSearchResponse(title ?: return null, "$title/$id", TvType.TvSeries) {
            this.posterUrl = thumbnail
            addSub(episodesCount)
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        // PERBAIKAN: Menggunakan params map untuk menangani URL Encoding (spasi, simbol, dll) secara otomatis
        val url = "$mainUrl/api/DramaList/Search"
        return app.get(url, params = mapOf("q" to query, "type" to "0"), referer = "$mainUrl/")
            .parsedSafe<List<Media>>()?.mapNotNull { it.toSearchResponse() }
            ?: throw ErrorLoadingException("Invalid Json response")
    }

    private fun getTitle(str: String): String {
        return str.replace(Regex("[^a-zA-Z0-9]"), "-")
    }

    override suspend fun load(url: String): LoadResponse? {
        val id = url.split("/").last()
        val res = app.get(
            "$mainUrl/api/DramaList/Drama/$id?isq=false",
            referer = url
        ).parsedSafe<MediaDetail>() ?: throw ErrorLoadingException("Invalid Json response")

        val episodes = res.episodes?.map { eps ->
            val displayNumber = eps.number?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } ?: ""
            newEpisode(Data(res.title, eps.number?.toInt(), res.id, eps.id).toJson()) {
                this.name = "Episode $displayNumber"
            }
        } ?: throw ErrorLoadingException("No Episodes found")

        return newTvSeriesLoadResponse(
            res.title ?: return null,
            url,
            if (res.type == "Movie" || episodes.size == 1) TvType.Movie else TvType.TvSeries,
            episodes.reversed()
        ) {
            this.posterUrl = res.thumbnail
            this.year = res.releaseDate?.split("-")?.first()?.toIntOrNull()
            this.plot = res.description
            this.tags = listOfNotNull(res.country, res.status, res.type)
            this.showStatus = when (res.status) {
                "Completed" -> ShowStatus.Completed
                "Ongoing" -> ShowStatus.Ongoing
                else -> null
            }
        }
    }

    @Suppress("DEPRECATION")
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val loadData = parseJson<Data>(data)
        
        // 1. Get Video Key
        val videoKeyUrl = "$videoScriptUrl?id=${loadData.epsId}&version=2.8.10"
        val videoKey = app.get(videoKeyUrl, timeout = 10000).parsedSafe<Key>()?.key ?: ""
        
        // 2. Get Video Source
        val videoApiUrl = "$mainUrl/api/DramaList/Episode/${loadData.epsId}.png?err=false&ts=&time=&kkey=$videoKey"
        val videoReferer = "$mainUrl/Drama/${getTitle(loadData.title ?: "")}/Episode-${loadData.eps}?id=${loadData.id}&ep=${loadData.epsId}&page=0&pageSize=100"
        
        app.get(videoApiUrl, referer = videoReferer).parsedSafe<Sources>()?.let { source ->
            listOfNotNull(source.video, source.thirdParty).amap { link ->
                safeApiCall {
                    if (link.contains(".m3u8")) {
                        M3u8Helper.generateM3u8(name, link, referer = "$mainUrl/", headers = mapOf("Origin" to mainUrl))
                            .forEach(callback)
                    } else if (link.contains(".mp4")) {
                        callback.invoke(
                            newExtractorLink(name, name, link, INFER_TYPE) {
                                referer = mainUrl
                                quality = Qualities.P720.value
                            }
                        )
                    } else {
                        loadExtractor(link, "$mainUrl/", subtitleCallback, callback)
                    }
                }
            }
        }

        // 3. Get Subtitle Key
        val subKeyUrl = "$subScriptUrl?id=${loadData.epsId}&version=2.8.10"
        val subKey = app.get(subKeyUrl, timeout = 10000).parsedSafe<Key>()?.key ?: ""

        // 4. Get Subtitles
        val subApiUrl = "$mainUrl/api/Sub/${loadData.epsId}?kkey=$subKey"
        app.get(subApiUrl).text.let { res ->
            tryParseJson<List<Subtitle>>(res)?.forEach { sub ->
                val label = sub.label ?: "Unknown"
                val lang = if (label == "Indonesia") "Indonesian" else label
                if (sub.src != null) {
                    subtitleCallback.invoke(newSubtitleFile(lang, sub.src))
                }
            }
        }
        return true
    }

    override fun getVideoInterceptor(extractorLink: ExtractorLink): Interceptor {
        return Interceptor { chain ->
            val request = chain.request()
            val response = chain.proceed(request)
            if (response.request.url.toString().contains(".txt")) {
                val bodyString = response.body.string()
                val decryptedBody = try {
                    val chunks = bodyString.split(Regex("^\\d+$", RegexOption.MULTILINE))
                        .filter { it.isNotBlank() }
                        .map { it.trim() }
                    
                    chunks.mapIndexed { index, chunk ->
                        val parts = chunk.split("\n")
                        if (parts.size > 1) {
                            val header = parts.first()
                            val content = parts.drop(1).joinToString("\n")
                            val decryptedContent = content.split("\n").joinToString("\n") { line ->
                                try {
                                    SubDecryptor.decrypt(line)
                                } catch (e: Exception) {
                                    ""
                                }
                            }
                            "${index + 1}\n$header\n$decryptedContent"
                        } else ""
                    }.filter { it.isNotBlank() }.joinToString("\n\n")
                } catch (e: Exception) {
                    bodyString
                }
                
                return@Interceptor response.newBuilder()
                    .body(decryptedBody.toResponseBody(response.body.contentType()))
                    .build()
            }
            response
        }
    }

    // PERBAIKAN: Menambahkan kembali @JsonProperty untuk memastikan parsing JSON aman dari obfuscation
    data class Data(val title: String?, val eps: Int?, val id: Int?, val epsId: Int?)

    data class Sources(
        @param:JsonProperty("Video") val video: String?,
        @param:JsonProperty("ThirdParty") val thirdParty: String?
    )

    data class Subtitle(
        @param:JsonProperty("src") val src: String?,
        @param:JsonProperty("label") val label: String?
    )

    data class Responses(
        @param:JsonProperty("data") val data: ArrayList<Media>? = arrayListOf()
    )

    data class Media(
        @param:JsonProperty("episodesCount") val episodesCount: Int?,
        @param:JsonProperty("thumbnail") val thumbnail: String?,
        @param:JsonProperty("label") val label: String?,
        @param:JsonProperty("id") val id: Int?,
        @param:JsonProperty("title") val title: String?
    )

    data class Episodes(
        @param:JsonProperty("id") val id: Int?,
        @param:JsonProperty("number") val number: Double?,
        @param:JsonProperty("sub") val sub: Int?
    )

    data class MediaDetail(
        @param:JsonProperty("description") val description: String?,
        @param:JsonProperty("releaseDate") val releaseDate: String?,
        @param:JsonProperty("status") val status: String?,
        @param:JsonProperty("type") val type: String?,
        @param:JsonProperty("country") val country: String?,
        @param:JsonProperty("episodes") val episodes: ArrayList<Episodes>? = arrayListOf(),
        @param:JsonProperty("thumbnail") val thumbnail: String?,
        @param:JsonProperty("id") val id: Int?,
        @param:JsonProperty("title") val title: String?
    )

    data class Key(val key: String?)
}
