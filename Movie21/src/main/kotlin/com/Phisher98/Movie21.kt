package com.movie21

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.json.JSONObject
import java.net.URLEncoder

class Movie21 : MainAPI() {
    // ====== Konfigurasi dasar ======
    override var name = "Movie21"
    override var mainUrl = "https://api.themoviedb.org/3"
    private val streamApi = "https://vidsrc.cc"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    private val apiKey = "1cfadd9dbfc534abf6de40e1e7eaf4c7"
    private val bearerToken =
        "Bearer eyJhbGciOiJIUzI1NiJ9.eyJhdWQiOiIxY2ZhZGQ5ZGJmYzUzNGFiZjZkZTQwZTFlN2VhZjRjNyIsIm5iZiI6MTc1OTA1OTI1Mi4xMjUsInN1YiI6IjY4ZDkxZDM0Y2MyMjM5MDUxZjM4YmQwYiIsInNjb3BlcyI6WyJhcGlfcmVhZCJdLCJ2ZXJzaW9uIjoxfQ.NcFqD1OvlG9r9WEmWh2UnDs3FqP_TtYQLS_MBnzy_VQ"

    // ====== Halaman utama ======
    override suspend fun getMainPage(): HomePageResponse {
        val sections = listOf(
            "Film Populer" to "$mainUrl/movie/popular?api_key=$apiKey&language=id-ID&page=1",
            "Serial Populer" to "$mainUrl/tv/popular?api_key=$apiKey&language=id-ID&page=1",
            "Film Terbaik" to "$mainUrl/movie/top_rated?api_key=$apiKey&language=id-ID&page=1"
        )

        val homeList = sections.mapNotNull { (title, url) ->
            // Perbaikan: Tentukan tipe konten
            val isTv = title.contains("Serial")
            
            val json = app.get(url).text
            val results = JSONObject(json).getJSONArray("results")
            
            val items = (0 until results.length()).mapNotNull { i ->
                val obj = results.getJSONObject(i)
                val id = obj.getInt("id")
                // Gunakan 'title' untuk movie dan 'name' untuk tv
                val name = obj.optString("title", obj.optString("name", "")) 
                val poster = "https://image.tmdb.org/t/p/w500" + obj.optString("poster_path", "")
                
                // Gunakan tipe yang benar
                val tvType = if (isTv) TvType.TvSeries else TvType.Movie
                
                newMovieSearchResponse(name, "$id|$tvType", tvType) {
                    this.posterUrl = poster
                    // Penanganan tanggal rilis untuk movie dan tv series
                    this.year = obj.optString("release_date", obj.optString("first_air_date", "")).take(4).toIntOrNull()
                }
            }
            HomePageList(title, items)
        }

        return HomePageResponse(homeList)
    }

    // ====== Pencarian ======
    override suspend fun search(query: String): List<SearchResponse> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "$mainUrl/search/multi?query=$encoded&api_key=$apiKey&language=id-ID&page=1"
        val json = app.get(url).text
        val results = JSONObject(json).getJSONArray("results")

        return (0 until results.length()).mapNotNull { i ->
            val obj = results.getJSONObject(i)
            val id = obj.optInt("id")
            val typeStr = obj.optString("media_type", "movie")
            if (id == 0) return@mapNotNull null // Skip item tanpa ID atau type yang tidak relevan

            val tvType = if (typeStr == "tv") TvType.TvSeries else TvType.Movie
            val poster = "https://image.tmdb.org/t/p/w500" + obj.optString("poster_path", "")
            val title = obj.optString("title", obj.optString("name", ""))
            
            newMovieSearchResponse(title, "$id|$typeStr", tvType) {
                this.posterUrl = poster
                // Perbaikan: Tambahkan tahun untuk pencarian
                this.year = obj.optString("release_date", obj.optString("first_air_date", "")).take(4).toIntOrNull()
            }
        }
    }

    // ====== Detail & Link Streaming ======
    override suspend fun load(url: String): LoadResponse {
        val (idStr, typeStr) = if (url.contains("|")) url.split("|") else listOf(url, "movie")
        val type = if (typeStr == "tv") "tv" else "movie"
        val apiUrl = "$mainUrl/$type/$idStr?api_key=$apiKey&language=id-ID"

        val json = app.get(apiUrl).text
        val obj = JSONObject(json)

        val title = obj.optString("title", obj.optString("name", ""))
        val poster = "https://image.tmdb.org/t/p/w500" + obj.optString("poster_path", "")
        val overview = obj.optString("overview", "")
        val year = obj.optString("release_date", obj.optString("first_air_date", "")).take(4).toIntOrNull()
        
        // Vidsrc menggunakan IMDb ID untuk movie, dan ID TMDB + Season/Episode untuk TV
        val imdbId = obj.optString("imdb_id", "")

        // Perbaikan Kritis: Pisahkan logika untuk Movie dan TvSeries
        return if (type == "tv") {
            // Logika untuk Serial TV (TvSeries)
            val seasonsArray = obj.optJSONArray("seasons")
            val seasons = if (seasonsArray != null) {
                (0 until seasonsArray.length()).mapNotNull { i ->
                    val seasonObj = seasonsArray.getJSONObject(i)
                    val seasonNumber = seasonObj.optInt("season_number")
                    val episodeCount = seasonObj.optInt("episode_count")
                    
                    // Kita hanya tertarik pada Season 1 ke atas (bukan Specials/Season 0)
                    if (seasonNumber >= 1 && episodeCount > 0) { 
                        val episodes = (1..episodeCount).map { episodeNumber ->
                            Episode(
                                // Format data untuk loadLinks: "TMDB_ID|SeasonNum|EpisodeNum"
                                data = "$idStr|$seasonNumber|$episodeNumber", 
                                name = "Eps $episodeNumber"
                            )
                        }
                        
                        TvSeason(
                            name = seasonObj.optString("name", "Musim $seasonNumber"),
                            season = seasonNumber,
                            episodes = episodes
                        )
                    } else null
                }
            } else listOf()

            newTvSeriesLoadResponse(title, url, TvType.TvSeries, seasons) {
                this.posterUrl = poster
                this.plot = overview
                this.year = year
            }
        } else {
            // Logika untuk Film (Movie)
            val finalStreamUrl = when {
                imdbId.isNotEmpty() -> "$streamApi/embed/movie/$imdbId"
                else -> {
                    // Fallback jika tidak ada IMDb ID, menggunakan judul (kurang disarankan, tapi dipertahankan)
                    val encodedTitle = URLEncoder.encode(title, "UTF-8")
                    "$streamApi/embed/movie?title=$encodedTitle"
                }
            }
            
            // Mengembalikan MovieLoadResponse
            newMovieLoadResponse(title, finalStreamUrl, TvType.Movie, finalStreamUrl) {
                this.posterUrl = poster
                this.plot = overview
                this.year = year
            }
        }
    }

    // Perbaikan Kritis: Fungsi untuk mendapatkan link streaming (Vidsrc)
    override suspend fun loadLinks(
        data: String,
        isTrailer: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (isTrailer) return false

        val streamUrl: String
        
        if (data.contains("|")) {
            // Serial TV: data format "TMDB_ID|SeasonNum|EpisodeNum"
            val parts = data.split("|")
            if (parts.size != 3) return false
            val (idStr, seasonStr, episodeStr) = parts
            streamUrl = "$streamApi/embed/tv/$idStr-$seasonStr-$episodeStr"
        } else {
            // Film: data adalah URL streaming yang sudah dibuat di load()
            streamUrl = data
        }

        // Panggil loadExtractor untuk memproses link Vidsrc
        // Asumsi Vidsrc Extractor sudah terdaftar atau menggunakan internal logic
        // Di sini Anda perlu memastikan bahwa loadExtractor dapat menangani Vidsrc
        loadExtractor(streamUrl, streamUrl, subtitleCallback, callback)
        return true
    }
}
