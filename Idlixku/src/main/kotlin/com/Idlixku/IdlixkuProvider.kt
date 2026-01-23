package com.Idlixku

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.Score
import org.jsoup.nodes.Element
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class IdlixkuProvider : MainAPI() {
    override var mainUrl = "https://tv12.idlixku.com"
    override var name = "Idlixku"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)

    data class DooplayResponse(
        @param:JsonProperty("embed_url") val embed_url: String?,
        @param:JsonProperty("type") val type: String?,
        @param:JsonProperty("key") val key: String?
    )

    data class EncryptedData(
        @param:JsonProperty("ct") val ct: String?,
        @param:JsonProperty("iv") val iv: String?,
        @param:JsonProperty("s") val s: String?
    )

    override val mainPage = mainPageOf(
        "$mainUrl/" to "Featured",
        "$mainUrl/" to "Film Terbaru",
        "$mainUrl/" to "Drama Korea",
        "$mainUrl/" to "Anime",
        "$mainUrl/" to "Serial TV",
        "$mainUrl/" to "Episode Terbaru"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        return runCatching {
            val document = app.get(request.data).document
            val sectionSelector = when (request.name) {
                "Featured" -> ".items.featured article"
                "Film Terbaru" -> "#dt-movies article"
                "Drama Korea" -> "#genre_drama-korea article"
                "Anime" -> "#genre_anime article"
                "Serial TV" -> "#dt-tvshows article"
                "Episode Terbaru" -> ".items.full article"
                else -> return null
            }
            val home = document.select(sectionSelector).mapNotNull { toSearchResult(it) }
            newHomePageResponse(request.name, home)
        }.getOrNull()
    }

    private fun toSearchResult(element: Element): SearchResponse? {
        val titleElement = element.selectFirst("h3 > a") ?: element.selectFirst(".title > a") ?: return null
        val title = titleElement.text()
        val href = titleElement.attr("href")
        val posterUrl = element.selectFirst("img")?.attr("src")
            ?.replace("/w185/", "/w500/")
            ?.replace("/w92/", "/w500/")
        val quality = element.selectFirst(".quality")?.text() ?: ""
        
        val isTvSeries = element.classNames().any { it in listOf("tvshows", "seasons", "episodes") } 
                         || href.contains("/tvseries/") || href.contains("/season/")

        return if (isTvSeries) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
                val episodeInfo = element.selectFirst(".data span")?.text()
                if (!episodeInfo.isNullOrEmpty()) addQuality(episodeInfo)
            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
                addQuality(quality)
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=$query"
        return runCatching {
            val document = app.get(url).document
            document.select("div.result-item article, .items article").mapNotNull {
                toSearchResult(it)
            }
        }.getOrElse { emptyList() }
    }

    override suspend fun load(url: String): LoadResponse? {
        return runCatching {
            val document = app.get(url).document

            val title = document.selectFirst(".data h1")?.text()?.trim() ?: return null
            val poster = document.selectFirst(".poster img")?.attr("src")?.replace("/w185/", "/w780/")
            val description = document.selectFirst(".wp-content p")?.text()?.trim() 
                ?: document.selectFirst("center p")?.text()?.trim()
            
            val ratingText = document.selectFirst(".dt_rating_vgs")?.text()?.trim()
            val ratingDouble = ratingText?.toDoubleOrNull()

            val year = document.selectFirst(".date")?.text()?.split(",")?.last()?.trim()?.toIntOrNull()
            val tags = document.select(".sgeneros a").map { it.text() }

            val isTvSeries = document.select("body").hasClass("single-tvshows") || url.contains("/tvseries/") || document.select("#seasons").isNotEmpty()

            val episodes = if (isTvSeries) {
                document.select("#seasons .se-c").flatMap { seasonElement ->
                    val seasonNum = seasonElement.selectFirst(".se-t")?.text()?.toIntOrNull() ?: 1
                    seasonElement.select("ul.episodios li").map { ep ->
                        val epImg = ep.selectFirst("img")?.attr("src")
                        val epNum = ep.selectFirst(".numerando")?.text()?.split("-")?.last()?.trim()?.toIntOrNull()
                        val epTitle = ep.selectFirst(".episodiotitle a")?.text()
                        val epUrl = ep.selectFirst(".episodiotitle a")?.attr("href") ?: ""
                        val date = ep.selectFirst(".date")?.text()

                        newEpisode(epUrl) {
                            this.name = epTitle
                            this.season = seasonNum
                            this.episode = epNum
                            this.posterUrl = epImg
                            this.addDate(date)
                        }
                    }
                }
            } else {
                listOf(newEpisode(url) {
                    this.name = title
                    this.posterUrl = poster
                })
            }

            if (isTvSeries) {
                newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                    this.posterUrl = poster
                    this.plot = description
                    this.year = year
                    this.score = Score.from10(ratingDouble)
                    this.tags = tags
                }
            } else {
                newMovieLoadResponse(title, url, TvType.Movie, episodes) {
                    this.posterUrl = poster
                    this.plot = description
                    this.year = year
                    this.score = Score.from10(ratingDouble)
                    this.tags = tags
                }
            }
        }.getOrNull()
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = runCatching { app.get(data).document }.getOrNull() ?: return false

        document.select("ul#playeroptionsul li").forEach { element ->
            val type = element.attr("data-type")
            val post = element.attr("data-post")
            val nume = element.attr("data-nume")
            val title = element.select(".title").text()

            if (nume == "trailer") return@forEach

            val formData = mapOf(
                "action" to "doo_player_ajax",
                "post" to post,
                "nume" to nume,
                "type" to type
            )

            try {
                val response = app.post(
                    "$mainUrl/wp-admin/admin-ajax.php",
                    data = formData,
                    headers = mapOf("X-Requested-With" to "XMLHttpRequest"),
                    referer = data
                )
                
                val dooplayResponse = tryParseJson<DooplayResponse>(response.text)
                var embedUrl = dooplayResponse?.embed_url
                val key = dooplayResponse?.key

                // LOGIKA DEKRIPSI (DIPERBAIKI)
                if (embedUrl != null && embedUrl.contains("\"ct\"") && !key.isNullOrEmpty()) {
                    try {
                        val cleanKey = decodeHex(key)
                        val decrypted = decryptDooplay(embedUrl, cleanKey)
                        if (decrypted != null) {
                            embedUrl = decrypted
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                if (embedUrl == null) return@forEach
                embedUrl = fixUrl(embedUrl!!)

                if (embedUrl!!.contains("<iframe")) {
                    val iframeDoc = org.jsoup.Jsoup.parse(embedUrl)
                    embedUrl = fixUrl(iframeDoc.select("iframe").attr("src"))
                }

                if (embedUrl!!.contains("jeniusplay.com")) {
                    JeniusPlayExtractor().getUrl(embedUrl!!, data, subtitleCallback, callback)
                } else {
                    loadExtractor(embedUrl!!, "IDLIX $title", subtitleCallback, callback)
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return true
    }

    // --- DECODER MANUAL YANG LEBIH AMAN ---
    private fun decodeHex(hex: String): String {
        return try {
            // Memecah berdasarkan "\x" lalu mengubah setiap bagian hex menjadi karakter
            hex.split("\\x")
                .filter { it.isNotEmpty() }
                .map { it.toInt(16).toChar() }
                .joinToString("")
        } catch (e: Exception) {
            hex // Return as is jika gagal
        }
    }

    private fun decryptDooplay(jsonString: String, key: String): String? {
        try {
            val encryptedData = parseJson<EncryptedData>(jsonString)
            val saltHex = encryptedData.s ?: return null
            val ctBase64 = encryptedData.ct ?: return null
            val ivHex = encryptedData.iv ?: return null

            val salt = hexToBytes(saltHex)
            val iv = hexToBytes(ivHex)
            val cipherText = Base64.getDecoder().decode(ctBase64)
            val pass = key.toByteArray(StandardCharsets.UTF_8)

            val derivedKey = deriveKey(pass, salt, 32)
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            val keySpec = SecretKeySpec(derivedKey, "AES")
            val ivSpec = IvParameterSpec(iv)
            
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
            val decryptedBytes = cipher.doFinal(cipherText)
            
            var result = String(decryptedBytes, StandardCharsets.UTF_8)
            if (result.startsWith("\"") && result.endsWith("\"")) {
                result = result.substring(1, result.length - 1)
            }
            return result.replace("\\/", "/")

        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun hexToBytes(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(hex[i], 16) shl 4) +
                    Character.digit(hex[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    private fun deriveKey(password: ByteArray, salt: ByteArray, keySize: Int): ByteArray {
        val digest = MessageDigest.getInstance("MD5")
        var derivedBytes = ByteArray(0)
        var lastDigest = ByteArray(0)

        while (derivedBytes.size < keySize) {
            digest.update(lastDigest)
            digest.update(password)
            digest.update(salt)
            lastDigest = digest.digest()
            derivedBytes += lastDigest
        }
        return derivedBytes.copyOfRange(0, keySize)
    }
}
