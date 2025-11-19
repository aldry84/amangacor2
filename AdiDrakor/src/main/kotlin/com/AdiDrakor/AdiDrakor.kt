package com.AdiDrakor

import com.AdiDrakor.AdiDrakorUtils.decrypt
import com.AdiDrakor.AdiDrakorUtils.fixUrl
import com.AdiDrakor.AdiDrakorUtils.getLanguage
import com.AdiDrakor.AdiDrakorUtils.getTitle
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

class AdiDrakor : MainAPI() {
    override var mainUrl = "https://kisskh.ovh"
    override var name = "AdiDrakor"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.AsianDrama,
        TvType.Movie,
        TvType.TvSeries
    )

    // 8 Kategori Khusus Korea (country=2)
    override val mainPage = mainPageOf(
        "&type=0&sub=0&country=2&status=0&order=2" to "Latest Korean Uploads",
        "&type=1&sub=0&country=2&status=0&order=1" to "Popular K-Drama",
        "&type=2&sub=0&country=2&status=0&order=1" to "Popular K-Movies",
        "&type=2&sub=0&country=2&status=0&order=2" to "Latest K-Movies",
        "&type=1&sub=0&country=2&status=0&order=2" to "Latest K-Series",
        "&type=0&sub=0&country=2&status=1&order=1" to "Ongoing K-Drama",
        "&type=0&sub=0&country=2&status=2&order=1" to "Completed K-Drama",
        "&type=0&sub=0&country=2&status=3&order=1" to "Upcoming Korean"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val home = app.get("$mainUrl/api/DramaList/List?page=$page${request.data}")
            .parsedSafe<Responses>()?.data
            ?.mapNotNull { media ->
                media.toSearchResponse()
            } ?: throw ErrorLoadingException("Invalid Json reponse")
        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = true
            ),
            hasNext = true
        )
    }

    private fun Media.toSearchResponse(): SearchResponse? {
        if (!settingsForProvider.enableAdult && this.label?.contains("RAW") == true) {
            // Skip RAW entries when adult is disabled
            return null
        }

        return newAnimeSearchResponse(
            title ?: return null,
            "$title/$id",
            TvType.AsianDrama,
        ) {
            this.posterUrl = thumbnail
            addSub(episodesCount)
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        // Mencari khusus konten Korea (country=2) jika memungkinkan, namun API search biasanya global
        // Kita filter manual atau biarkan global
        val searchResponse =
            app.get("$mainUrl/api/DramaList/Search?q=$query&type=0", referer = "$mainUrl/").text
        
        return tryParseJson<ArrayList<Media>>(searchResponse)
            ?.filter { it.title != null } // Filter dasar
            ?.mapNotNull { media ->
                media.toSearchResponse()
            } ?: throw ErrorLoadingException("Invalid Json reponse")
    }

    override suspend fun load(url: String): LoadResponse? {
        val split = url.split("/")
        val idStr = split.last()
        val titleStr = split.first() // Hati-hati jika struktur URL berubah

        val res = app.get(
            "$mainUrl/api/DramaList/Drama/$idStr?isq=false",
            referer = "$mainUrl/Drama/${getTitle(titleStr)}?id=$idStr"
        ).parsedSafe<MediaDetail>()
            ?: throw ErrorLoadingException("Invalid Json reponse")

        val episodes = res.episodes?.map { eps ->
            val displayNumber = eps.number?.let { num ->
                if (num % 1.0 == 0.0) num.toInt().toString() else num.toString()
            } ?: ""

            newEpisode(Data(res.title, eps.number?.toInt(), res.id, eps.id).toJson()) {
                this.name = "Episode $displayNumber"
                this.episode = eps.number?.toInt()
            }
        } ?: throw ErrorLoadingException("No Episode")

        return newTvSeriesLoadResponse(
            res.title ?: return null,
            url,
            if (res.type == "Movie" || episodes.size == 1) TvType.Movie else TvType.AsianDrama,
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

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // Menggunakan hardcoded value jika BuildConfig tidak tersedia, atau Anda bisa membuatnya di file Utils
        val kisskhAPI = "https://kisskh.ovh/api/DramaList/Episode/" 
        val kisskhSub = "https://kisskh.ovh/api/Sub/"
        
        val loadData = parseJson<Data>(data)
        
        // Mendapatkan key (mocking logic jika key dinamis berubah, sesuaikan dengan logic terbaru)
        // Di sini kita gunakan parameter dummy jika endpoint version berubah
        val kkey = try {
            app.get("$kisskhAPI${loadData.epsId}&version=2.8.10", timeout = 10000).parsedSafe<Key>()?.key ?: ""
        } catch (e: Exception) { "" }

        app.get(
            "$mainUrl/api/DramaList/Episode/${loadData.epsId}.png?err=false&ts=&time=&kkey=$kkey",
            referer = "$mainUrl/Drama/${getTitle(loadData.title ?: "")}/Episode-${loadData.eps}?id=${loadData.id}&ep=${loadData.epsId}&page=0&pageSize=100"
        ).parsedSafe<Sources>()?.let { source ->
            listOf(source.video, source.thirdParty).amap { link ->
                safeApiCall {
                    val fixedLink = fixUrl(link ?: return@safeApiCall, mainUrl)
                    if (fixedLink.contains(".m3u8")) {
                        M3u8Helper.generateM3u8(
                            this.name,
                            fixedLink,
                            referer = "$mainUrl/",
                            headers = mapOf("Origin" to mainUrl)
                        ).forEach(callback)
                    } else if (fixedLink.contains("mp4")) {
                        callback.invoke(
                            newExtractorLink(
                                this.name,
                                this.name,
                                url = fixedLink,
                                INFER_TYPE
                            ) {
                                this.referer = mainUrl
                                this.quality = Qualities.P720.value
                            }
                        )
                    } else {
                        loadExtractor(
                            fixedLink.substringBefore("=http"),
                            "$mainUrl/",
                            subtitleCallback,
                            callback
                        )
                    }
                }
            }
        }

        val kkey1 = try {
            app.get("$kisskhSub${loadData.epsId}&version=2.8.10", timeout = 10000).parsedSafe<Key>()?.key ?: ""
        } catch (e: Exception) { "" }

        app.get("$mainUrl/api/Sub/${loadData.epsId}?kkey=$kkey1").text.let { res ->
            tryParseJson<List<Subtitle>>(res)?.map { sub ->
                val label = getLanguage(sub.label ?: return@map)
                subtitleCallback.invoke(
                    newSubtitleFile(label, sub.src ?: "")
                )
            }
        }

        return true
    }

    private val CHUNK_REGEX1 by lazy { Regex("^\\d+$", RegexOption.MULTILINE) }

    override fun getVideoInterceptor(extractorLink: ExtractorLink): Interceptor {
        return object : Interceptor {
            override fun intercept(chain: Interceptor.Chain): Response {
                val request = chain.request()
                    .newBuilder()
                    .build()
                val response = chain.proceed(request)
                if (response.request.url.toString().contains(".txt")) {
                    val responseBody = response.body.string()
                    val chunks = responseBody.split(CHUNK_REGEX1)
                        .filter(String::isNotBlank)
                        .map(String::trim)
                    
                    val decrypted = chunks.mapIndexed { index, chunk ->
                        if (chunk.isBlank()) return@mapIndexed ""
                        val parts = chunk.split("\n")
                        if (parts.isEmpty()) return@mapIndexed ""

                        val header = parts.first()
                        val text = parts.drop(1)
                        val d = text.joinToString("\n") { line ->
                            try {
                                decrypt(line) // Menggunakan fungsi decrypt dari AdiDrakorUtils
                            } catch (e: Exception) {
                                "DECRYPT_ERROR:${e.message}"
                            }
                        }
                        listOf(index + 1, header, d).joinToString("\n")
                    }.filter { it.isNotEmpty() }
                        .joinToString("\n\n")
                    
                    val newBody = decrypted.toResponseBody(response.body.contentType())
                    return response.newBuilder()
                        .body(newBody)
                        .build()
                }
                return response
            }
        }
    }
}
