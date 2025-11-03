import com.lagradost.cloudstream3.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.io.IOException

// ... di dalam class Adi21 ...

    // Cache Sederhana untuk Daftar Film dan TV
    val movieCache = mutableMapOf<String, List<SearchResponse>>()
    val tvCache = mutableMapOf<String, List<SearchResponse>>()

    // Fungsi untuk Mendapatkan Daftar Film dari VidSrc
    suspend fun getVidSrcMovieList(): List<SearchResponse>? {
        val cacheKey = "movieList"
        if (movieCache.containsKey(cacheKey)) {
            return movieCache[cacheKey]
        }

        val client = OkHttpClient()
        val request = Request.Builder()
            .url("https://vidsrc.cc/api/list/movie")
            .build()

        try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: return null
            val jsonArray = JSONArray(responseBody)

            val movieList = mutableListOf<SearchResponse>()
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                val title = jsonObject.getString("title")
                val tmdbId = jsonObject.getString("tmdb_id") // Atau gunakan "imdb_id" jika tersedia

                // Buat SearchResponse
                val searchResponse = newMovieSearchResponse(
                    title = title,
                    url = tmdbId, // Gunakan ID untuk URL
                    type = TvType.Movie,
                    isDubbed = false // Sesuaikan jika ada info dubbing
                ) {
                    this.posterUrl = jsonObject.getString("poster") // Jika ada poster
                }
                movieList.add(searchResponse)
            }
            movieCache[cacheKey] = movieList // Simpan ke cache
            return movieList
        } catch (e: IOException) {
            logError("Kesalahan Jaringan saat mendapatkan daftar film dari VidSrc: ${e.message}")
            return null
        } catch (e: Exception) {
            logError("Gagal mendapatkan daftar film dari VidSrc: ${e.message}")
            return null
        }
    }

    // Fungsi untuk Mendapatkan Daftar Acara TV dari VidSrc
    suspend fun getVidSrcTvList(): List<SearchResponse>? {
        val cacheKey = "tvList"
        if (tvCache.containsKey(cacheKey)) {
            return tvCache[cacheKey]
        }

        val client = OkHttpClient()
        val request = Request.Builder()
            .url("https://vidsrc.cc/api/list/tv")
            .build()

        try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: return null
            val jsonArray = JSONArray(responseBody)

            val tvList = mutableListOf<SearchResponse>()
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                val title = jsonObject.getString("title")
                val tmdbId = jsonObject.getString("tmdb_id") // Atau gunakan "imdb_id" jika tersedia

                // Buat SearchResponse
                val searchResponse = newTvSeriesSearchResponse(
                    title = title,
                    url = tmdbId, // Gunakan ID untuk URL
                    isDubbed = false // Sesuaikan jika ada info dubbing
                ) {
                    this.posterUrl = jsonObject.getString("poster") // Jika ada poster
                }
                tvList.add(searchResponse)
            }
            tvCache[cacheKey] = tvList // Simpan ke cache
            return tvList
        } catch (e: IOException) {
            logError("Kesalahan Jaringan saat mendapatkan daftar acara TV dari VidSrc: ${e.message}")
            return null
        } catch (e: Exception) {
            logError("Gagal mendapatkan daftar acara TV dari VidSrc: ${e.message}")
            return null
        }
    }

    // Fungsi untuk Mendapatkan URL Subtitle dari Pengaturan (Contoh)
    fun getSubtitleUrlFromSettings(): String? {
        // Gunakan API CloudStream untuk mendapatkan pengaturan subtitle
        return getPref("subtitle_url")
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val loadData = parseJson<LoadData>(data)
        val tmdbId = loadData.id ?: return false // ID TMDb
        val season = loadData.season
        val episode = loadData.episode

        // Dapatkan URL subtitle dari pengaturan pengguna (contoh)
        val subtitleUrl = getSubtitleUrlFromSettings()

        try {
            // Tentukan jenis konten (film, acara TV, atau anime)
            val contentType = when {
                loadData.type == TvType.Anime -> "anime"
                season == null && episode == null -> "movie"
                else -> "tv"
            }

            // Bangun URL API VidSrc
            var vidsrcApiUrl: String? = when (contentType) {
                "anime" -> {
                    // Asumsikan episode selalu ada untuk anime
                    val animeEpisode = episode ?: 1 // Default ke episode 1 jika tidak ada
                    "https://vidsrc.cc/v2/embed/anime/$tmdbId/$animeEpisode/sub" // Selalu gunakan "sub" untuk sekarang
                }
                "tv" -> {
                    if (season != null && episode != null) {
                        "https://vidsrc.cc/v3/embed/tv/$tmdbId/$season/$episode"
                    } else if (season != null) {
                        "https://vidsrc.cc/v3/embed/tv/$tmdbId/$season" // Untuk musim saja
                    } else {
                        null // Tidak valid untuk acara TV tanpa musim
                    }
                }
                "movie" -> "https://vidsrc.cc/v3/embed/movie/$tmdbId"
                else -> null
            }

            // Pastikan URL API VidSrc valid
            val apiUrl = vidsrcApiUrl ?: run {
                logError("URL API VidSrc tidak valid untuk jenis konten ini")
                return false
            }

            // Tambahkan subtitle jika ada
            if (!subtitleUrl.isNullOrEmpty()) {
                val encodedSubtitleUrl = URLEncoder.encode(subtitleUrl, "UTF-8")
                val subtitleLabel = "Custom" // Atau dapatkan dari pengaturan pengguna
                vidsrcApiUrl += "?sub.file=$encodedSubtitleUrl&sub.label=$subtitleLabel"
            }

            // Buat klien HTTP
            val client = OkHttpClient()

            // Buat permintaan
            val request = Request.Builder()
                .url(apiUrl)
                .build()

            // Kirim permintaan dan dapatkan respons
            val response: okhttp3.Response
            try {
                response = client.newCall(request).execute()
            } catch (e: IOException) {
                logError("Kesalahan Jaringan: ${e.message}")
                return false
            }

            if (!response.isSuccessful) {
                logError("VidSrc API Error: ${response.code} ${response.message}")
                return false
            }

            val responseBody = response.body?.string() ?: return false

            // VidSrc tidak mengembalikan JSON, tetapi HTML dengan iframe
            // Kita perlu mencari URL iframe
            val iframeUrl = extractIframeUrl(responseBody)

            if (iframeUrl.isNullOrEmpty()) {
                logError("Tidak dapat menemukan URL iframe di respons VidSrc")
                return false
            }

            // Sekarang kita perlu mendapatkan tautan streaming dari URL iframe
            return extractStreamingLinksFromIframe(iframeUrl, callback)

        } catch (e: Exception) {
            logError("VidSrc API Error: ${e.message}")
            e.printStackTrace()
            return false
        }
    }

    // Fungsi untuk Mengekstrak URL iframe dari Respons HTML
    private fun extractIframeUrl(html: String): String? {
        // Gunakan regex untuk mencari URL iframe
        val regex = Regex("<iframe.*?src=\"(.*?)\".*?>")
        val matchResult = regex.find(html)
        return matchResult?.groupValues?.get(1)
    }

    // Fungsi untuk Mengekstrak Tautan Streaming dari URL iframe
    private suspend fun extractStreamingLinksFromIframe(iframeUrl: String, callback: (ExtractorLink) -> Unit): Boolean {
        try {
            // Buat klien HTTP
            val client = OkHttpClient()

            // Buat permintaan
            val request = Request.Builder()
                .url(iframeUrl)
                .build()

            // Kirim permintaan dan dapatkan respons
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                logError("Iframe Error: ${response.code} ${response.message}")
                return false
            }

            val responseBody = response.body?.string() ?: return false

            // Gunakan Jsoup untuk memproses HTML dan mencari tautan streaming
            val document = org.jsoup.Jsoup.parse(responseBody)
            val videoElements = document.select("video source") // Sesuaikan selector jika perlu

            if (videoElements.isEmpty()) {
                logError("Tidak dapat menemukan tautan streaming di iframe")
                return false
            }

            for (element in videoElements) {
                val streamUrl = element.attr("src")
                val quality = element.attr("label") ?: "Unknown"

                callback.invoke(
                    newExtractorLink(
                        name,
                        name,
                        streamUrl,
                        INFER_TYPE
                    ) {
                        this.quality = getQualityFromName(quality)
                    }
                )
            }

            return true

        } catch (e: Exception) {
            logError("Iframe Error: ${e.message}")
            e.printStackTrace()
            return false
        }
    }

    private fun logError(message: String) {
        println("Error: $message")
        log(message)
    }

    private fun logError(e: Exception) {
        println("Error: ${e.message}")
        log(e)
        e.printStackTrace()
    }
