package com.phisher98

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson

class KisskhProvider : MainAPI() {
    override var mainUrl = "https://kisskh.ovh"
    override var name = "Kisskh"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.AsianDrama, TvType.Anime, TvType.Movie)

    // Regex untuk memisahkan baris angka pada format SRT
    private val CHUNK_REGEX by lazy { Regex("""^\d+$""", RegexOption.MULTILINE) }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/api/DramaList/Search?q=$query&type=0"
        val response = app.get(url, referer = "$mainUrl/").text
        return tryParseJson<List<Media>>(response)?.mapNotNull { it.toSearchResponse() } 
            ?: throw ErrorLoadingException("Gagal mencari konten")
    }

    override suspend fun load(url: String): LoadResponse {
        val id = url.substringAfterLast("/")
        val cleanTitle = url.substringBeforeLast("/").replace("[^a-zA-Z0-9]".toRegex(), "-")
        
        val res = app.get(
            "$mainUrl/api/DramaList/Drama/$id?isq=false",
            referer = "$mainUrl/Drama/$cleanTitle?id=$id"
        ).parsedSafe<MediaDetail>() ?: throw ErrorLoadingException("Data drama tidak ditemukan")

        val episodes = res.episodes?.map { eps ->
            val num = eps.number ?: 0.0
            val name = if (num % 1.0 == 0.0) num.toInt().toString() else num.toString()
            newEpisode(Data(res.title, num.toInt(), res.id, eps.id).toJson()) {
                this.name = "Episode $name"
            }
        }?.reversed() ?: throw ErrorLoadingException("Episode tidak tersedia")

        return newTvSeriesLoadResponse(res.title ?: "", url, TvType.TvSeries, episodes) {
            this.posterUrl = res.thumbnail
            this.plot = res.description
            this.year = res.releaseDate?.split("-")?.firstOrNull()?.toIntOrNull()
        }
    }

    override fun getVideoInterceptor(extractorLink: ExtractorLink): Interceptor {
        return Interceptor { chain ->
            val response = chain.proceed(chain.request())
            val url = response.request.url.toString()

            if (url.contains(".txt") && response.isSuccessful) {
                val originalBody = response.body?.string() ?: ""
                val chunks = originalBody.split(CHUNK_REGEX).filter { it.isNotBlank() }
                
                val decrypted = chunks.mapIndexed { index, chunk ->
                    val lines = chunk.trim().split("\n")
                    if (lines.size < 2) return@mapIndexed ""
                    
                    val timestamp = lines.first()
                    val textContent = lines.drop(1).joinToString("\n") { line ->
                        SubDecryptor.decrypt(line) ?: "" // Kembalikan string kosong jika gagal
                    }
                    "${index + 1}\n$timestamp\n$textContent"
                }.filter { it.contains(":") }.joinToString("\n\n")

                val newBody = decrypted.toResponseBody(response.body?.contentType())
                response.newBuilder().body(newBody).build()
            } else {
                response
            }
        }
    }

    // Data Classes
    data class Data(val title: String?, val eps: Int?, val id: Int?, val epsId: Int?)
    data class Media(@JsonProperty("title") val title: String?, @JsonProperty("id") val id: Int?, @JsonProperty("thumbnail") val thumbnail: String?, @JsonProperty("label") val label: String?, @JsonProperty("episodesCount") val episodesCount: Int?)
    data class MediaDetail(@JsonProperty("title") val title: String?, @JsonProperty("id") val id: Int?, @JsonProperty("episodes") val episodes: List<Episodes>?, @JsonProperty("description") val description: String?, @JsonProperty("thumbnail") val thumbnail: String?, @JsonProperty("releaseDate") val releaseDate: String?)
    data class Episodes(@JsonProperty("id") val id: Int?, @JsonProperty("number") val number: Double?)

    private fun Media.toSearchResponse() = newAnimeSearchResponse(title ?: return null, "$title/$id", TvType.TvSeries) {
        this.posterUrl = thumbnail
        addSub(episodesCount)
    }
}
