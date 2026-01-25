package com.phisher98

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.mvvm.safeApiCall
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.util.ArrayList

class KisskhProvider : MainAPI() {
    override var mainUrl = "https://kisskh.ovh"
    override var name = "Kisskh"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.AsianDrama, TvType.Anime)

    private val kisskhApiUrl = "https://script.google.com/macros/s/AKfycbzn8B31PuDxzaMa9_CQ0VGEDasFqfzI5bXvjaIZH4DM8DNq9q6xj1ALvZNz_JT3jF0suA/exec?id="
    private val kisskhSubUrl = "https://script.google.com/macros/s/AKfycbyq6hTj0ZhlinYC6xbggtgo166tp6XaDKBCGtnYk8uOfYBUFwwxBui0sGXiu_zIFmA/exec?id="

    override val mainPage = mainPageOf(
        "&type=0&sub=0&country=0&status=0&order=2" to "Latest",
        "&type=0&sub=0&country=2&status=0&order=1" to "Top K-Drama",
        "&type=3&sub=0&country=0&status=0&order=1" to "Anime Popular"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val home = app.get("$mainUrl/api/DramaList/List?page=$page${request.data}")
            .parsedSafe<Responses>()?.data
            ?.mapNotNull { it.toSearchResponse() } ?: throw ErrorLoadingException("Invalid Json response")
        return newHomePageResponse(HomePageList(request.name, home, isHorizontalImages = true), true)
    }

    private fun Media.toSearchResponse(): SearchResponse? {
        if (!settingsForProvider.enableAdult && this.label?.contains("RAW") == true) return null
        return newAnimeSearchResponse(title ?: return null, "$title/$id", TvType.TvSeries) {
            this.posterUrl = thumbnail
            addSub(episodesCount)
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = app.get("$mainUrl/api/DramaList/Search?q=$query&type=0", referer = "$mainUrl/").text
        return tryParseJson<ArrayList<Media>>(searchResponse)?.mapNotNull { it.toSearchResponse() } ?: emptyList()
    }

    private fun getTitle(str: String): String {
        return str.replace(Regex("[^a-zA-Z0-9]"), "-")
    }

    override suspend fun load(url: String): LoadResponse? {
        val idParts = url.split("/")
        val res = app.get("$mainUrl/api/DramaList/Drama/${idParts.last()}?isq=false").parsedSafe<MediaDetail>() 
            ?: throw ErrorLoadingException("Invalid Json response")

        val episodes = res.episodes?.map { eps ->
            val displayNumber = if (eps.number != null && eps.number % 1.0 == 0.0) eps.number.toInt().toString() else eps.number.toString()
            newEpisode(Data(res.title, eps.number?.toInt(), res.id, eps.id).toJson()) {
                this.name = "Episode $displayNumber"
            }
        } ?: throw ErrorLoadingException("No Episode")

        return newTvSeriesLoadResponse(res.title ?: return null, url, TvType.TvSeries, episodes.reversed()) {
            this.posterUrl = res.thumbnail
            this.plot = res.description
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val loadData = parseJson<Data>(data)
        val kkey = app.get("$kisskhApiUrl${loadData.epsId}&version=2.8.10").parsedSafe<Key>()?.key ?: ""
        
        app.get("$mainUrl/api/DramaList/Episode/${loadData.epsId}.png?err=false&kkey=$kkey", referer = "$mainUrl/").parsedSafe<Sources>()?.let { source ->
            listOfNotNull(source.video, source.thirdParty).amap { link ->
                safeApiCall {
                    if (link.contains(".m3u8")) {
                        M3u8Helper.generateM3u8(this.name, link, referer = "$mainUrl/").forEach(callback)
                    } else if (link.contains("mp4")) {
                        callback.invoke(newExtractorLink(this.name, this.name, link, INFER_TYPE))
                    }
                }
            }
        }

        val kkeySub = app.get("$kisskhSubUrl${loadData.epsId}&version=2.8.10").parsedSafe<Key>()?.key ?: ""
        app.get("$mainUrl/api/Sub/${loadData.epsId}?kkey=$kkeySub").parsedSafe<List<Subtitle>>()?.forEach { sub ->
            if (sub.src != null) {
                subtitleCallback.invoke(newSubtitleFile(sub.label ?: "Unknown", sub.src))
            }
        }

        return true
    }

    private val CHUNK_REGEX1 by lazy { Regex("^\\d+$", RegexOption.MULTILINE) }

    override fun getVideoInterceptor(extractorLink: ExtractorLink): Interceptor {
        return Interceptor { chain ->
            val response = chain.proceed(chain.request())
            val url = response.request.url.toString()
            if (url.contains(".txt")) {
                val responseBody = response.body.string()
                val chunks = responseBody.split(CHUNK_REGEX1).filter { it.isNotBlank() }.map { it.trim() }
                val decrypted = chunks.mapIndexed { index, chunk ->
                    val parts = chunk.split("\n")
                    if (parts.size < 2) return@mapIndexed ""
                    val header = parts.first()
                    val d = parts.drop(1).joinToString("\n") { line ->
                        try { decrypt(line) } catch (e: Exception) { line }
                    }
                    "${index + 1}\n$header\n$d"
                }.joinToString("\n\n")
                return@Interceptor response.newBuilder().body(decrypted.toResponseBody(response.body.contentType())).build()
            }
            response
        }
    }

    data class Data(val title: String?, val eps: Int?, val id: Int?, val epsId: Int?)
    data class Sources(@JsonProperty("Video") val video: String?, @JsonProperty("ThirdParty") val thirdParty: String?)
    data class Subtitle(@JsonProperty("src") val src: String?, @JsonProperty("label") val label: String?)
    data class Responses(@JsonProperty("data") val data: ArrayList<Media>? = arrayListOf())
    data class Media(@JsonProperty("episodesCount") val episodesCount: Int?, @JsonProperty("thumbnail") val thumbnail: String?, @JsonProperty("label") val label: String?, @JsonProperty("id") val id: Int?, @JsonProperty("title") val title: String?)
    data class MediaDetail(@JsonProperty("description") val description: String?, val episodes: ArrayList<Episodes>? = arrayListOf(), val thumbnail: String?, val id: Int?, val title: String?)
    data class Episodes(val id: Int?, val number: Double?)
    data class Key(val key: String)
}
