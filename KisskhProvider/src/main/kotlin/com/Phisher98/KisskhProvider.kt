package com.phisher98

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import okhttp3.Interceptor
import okhttp3.ResponseBody.Companion.toResponseBody

class KisskhProvider : MainAPI() {
    override var mainUrl = "https://kisskh.ovh"
    override var name = "Kisskh"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.AsianDrama, TvType.Anime, TvType.Movie)

    override val mainPage = mainPageOf(
        "&type=0&sub=0&country=0&status=0&order=2" to "Latest",
        "&type=0&sub=0&country=2&status=0&order=1" to "Top K-Drama",
        "&type=3&sub=0&country=0&status=0&order=1" to "Anime Popular"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val data = app.get("$mainUrl/api/DramaList/List?page=$page${request.data}").parsedSafe<Responses>()
        val home = data?.data?.mapNotNull { it.toSearchResponse() } ?: throw ErrorLoadingException("Gagal load data")
        return newHomePageResponse(request.name, home)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return app.get("$mainUrl/api/DramaList/Search?q=$query&type=0").parsedSafe<List<Media>>()
            ?.mapNotNull { it.toSearchResponse() } ?: emptyList()
    }

    override suspend fun load(url: String): LoadResponse {
        val id = url.substringAfterLast("/")
        val res = app.get("$mainUrl/api/DramaList/Drama/$id?isq=false").parsedSafe<MediaDetail>() 
            ?: throw ErrorLoadingException("Drama tidak ditemukan")

        val episodes = res.episodes?.map { eps ->
            val isInt = (eps.number ?: 0.0) % 1.0 == 0.0
            val name = if (isInt) eps.number?.toInt().toString() else eps.number.toString()
            newEpisode(Data(res.title, eps.number?.toInt(), res.id, eps.id).toJson()) {
                this.name = "Episode $name"
            }
        }?.reversed() ?: emptyList()

        return newTvSeriesLoadResponse(res.title ?: "", url, TvType.TvSeries, episodes) {
            this.posterUrl = res.thumbnail
            this.plot = res.description
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val loadData = AppUtils.parseJson<Data>(data)
        
        [span_7](start_span)// Ambil link video[span_7](end_span)
        val kkey = app.get("${BuildConfig.KissKh}${loadData.epsId}&version=2.8.10").parsedSafe<Key>()?.key ?: ""
        val sources = app.get("$mainUrl/api/DramaList/Episode/${loadData.epsId}.png?kkey=$kkey").parsedSafe<Sources>()
        
        sources?.run {
            listOfNotNull(video, thirdParty).forEach { link ->
                if (link.contains(".m3u8")) {
                    M3u8Helper.generateM3u8(name, link, mainUrl).forEach(callback)
                }
            }
        }

        [span_8](start_span)// Ambil link subtitle[span_8](end_span)
        val skey = app.get("${BuildConfig.KisskhSub}${loadData.epsId}&version=2.8.10").parsedSafe<Key>()?.key ?: ""
        app.get("$mainUrl/api/Sub/${loadData.epsId}?kkey=$skey").parsedSafe<List<Subtitle>>()?.forEach { sub ->
            subtitleCallback.invoke(SubtitleFile(sub.label ?: "Unknown", sub.src ?: ""))
        }
        return true
    }

    override fun getVideoInterceptor(extractorLink: ExtractorLink): Interceptor {
        return Interceptor { chain ->
            val res = chain.proceed(chain.request())
            if (res.request.url.toString().contains(".txt")) {
                val body = res.body?.string() ?: ""
                val decrypted = body.split(Regex("""^\d+$""", RegexOption.MULTILINE))
                    .filter { it.isNotBlank() }
                    .mapIndexed { i, chunk ->
                        val parts = chunk.trim().split("\n")
                        val time = parts.getOrNull(0) ?: ""
                        val text = parts.drop(1).joinToString("\n") { SubDecryptor.decrypt(it) ?: "" }
                        "${i + 1}\n$time\n$text"
                    }.joinToString("\n\n")
                res.newBuilder().body(decrypted.toResponseBody(res.body?.contentType())).build()
            } else res
        }
    }

    // Data Models
    data class Data(val title: String?, val eps: Int?, val id: Int?, val epsId: Int?)
    data class Key(val key: String)
    data class Responses(val data: List<Media>?)
    data class Media(val title: String?, val id: Int?, val thumbnail: String?, val episodesCount: Int?)
    data class MediaDetail(val title: String?, val id: Int?, val episodes: List<Episodes>?, val description: String?, val thumbnail: String?, val releaseDate: String?)
    data class Episodes(val id: Int?, val number: Double?)
    data class Sources(@JsonProperty("Video") val video: String?, @JsonProperty("ThirdParty") val thirdParty: String?)
    data class Subtitle(val src: String?, val label: String?)

    private fun Media.toSearchResponse() = newAnimeSearchResponse(title ?: "", "$title/$id", TvType.TvSeries) {
        this.posterUrl = thumbnail
        addSub(episodesCount)
    }
}
