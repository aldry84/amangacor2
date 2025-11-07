package com.Movie21

import com.lagacy.Movie21.base.TmdbProvider
import com.lagacy.Movie21.base.TmdbAPI
import com.lagacy.Movie21.base.niceUrl
import com.Movie21.extractors.VidSrcApiExtractor // Gunakan Extractor API yang baru
import com.lagacy.Movie21.TvType
import com.lagacy.Movie21.MediaItem
import com.lagacy.Movie21.ExtractorLink
import com.lagacy.Movie21.SubtitleData
import com.lagacy.Movie21.logError
import com.lagacy.Movie21.MainPageData
import com.lagacy.Movie21.HomePageList
import com.lagacy.Movie21.TvSeries
import com.lagacy.Movie21.Movie

class Movie21Provider: TmdbProvider() {
    
    // Konfigurasi Dasar
    override val tmdbApi: TmdbAPI = TmdbAPI("1cfadd9dbfc534abf6de40e1e7eaf4c7")
    override val mainName: String = "Movie21"
    override val packageName: String = "com.Movie21"
    
    // URL dasar sumber streaming (vidsrc-embed.ru untuk latest content)
    override val mainUrl: String = "https://vidsrc-embed.ru" 
    
    // GANTI DENGAN URL VERCEL API ANDA YANG BENAR
    private val VERCEL_API_URL = "https://<deployment-anda>" 
    
    override val supportedTypes: Set<TvType> = setOf(
        TvType.Movie,
        TvType.TvSeries,
    )
    
    // Inisialisasi Extractor
    private val vidSrcApi = VidSrcApiExtractor(VERCEL_API_URL)

    // Endpoint untuk mendapatkan daftar film/acara TV terbaru dari VidSrcEmbed (sesuai gambar lama)
    private fun getLatestMoviesUrl(page: Int) = "${mainUrl}/movies/latest/page-$page.json"
    private fun getLatestTvUrl(page: Int) = "${mainUrl}/tvshows/latest/page-$page.json"

    // =========================================================================
    // 🎥 PENGAMBILAN LINK STREAMING (LOAD URLS) - Menggunakan Vercel API
    // =========================================================================

    override fun loadUrls(
        mediaItem: MediaItem, 
        season: Int?, 
        episode: Int?, 
        subtitleCallback: (SubtitleData) -> Unit, 
        callback: (ExtractorLink) -> Unit
    ) {
        // TMDB ID Diteruskan sebagai String
        val tmdbId = mediaItem.id 
        
        val url: String = when (mediaItem.type) {
            TvType.Movie -> {
                // Endpoint Film: https://(deployment)/:TMDBid
                "${VERCEL_API_URL}/${tmdbId}" 
            }
            TvType.TvSeries -> {
                // Endpoint Seri: https://(deployment)/:TMDBid/:season/:episode
                if (season != null && episode != null) {
                    "${VERCEL_API_URL}/${tmdbId}/${season}/${episode}"
                } else {
                    return // Harus ada season dan episode
                }
            }
            else -> return
        }
        
        // Panggil Extractor untuk mendapatkan link streaming
        vidSrcApi.extract(url, mediaItem.niceUrl(), subtitleCallback, callback)
    }

    // =========================================================================
    // 🏠 HALAMAN UTAMA (HOME PAGE) - Menggunakan data vidsrc-embed.ru & TMDB
    // =========================================================================

    override suspend fun getMainPage(): MainPageData {
        val lists = mutableListOf<HomePageList>()
        
        // 1. Film Terbaru dari VidsrcEmbed 
        try {
            val latestMovieUrl = getLatestMoviesUrl(1)
            val jsonMovies = app.get(latestMovieUrl).parsed<LatestContentList>()
            val mediaItems = jsonMovies.results.mapNotNull { 
                // Gunakan TMDB ID untuk membuat Movie Item
                it.tmdb?.let { tmdbId -> Movie(it.title, tmdbId.toString(), null, it.poster) } 
            }
            lists.add(HomePageList("Film Terbaru (dari vidsrc-embed)", mediaItems))
        } catch (e: Exception) {
            logError(e) 
        }

        // 2. Acara TV Terbaru dari VidsrcEmbed
        try {
            val latestTvUrl = getLatestTvUrl(1)
            val jsonTv = app.get(latestTvUrl).parsed<LatestContentList>()
            val mediaItems = jsonTv.results.mapNotNull { 
                it.tmdb?.let { tmdbId -> TvSeries(it.title, tmdbId.toString(), null, it.poster) } 
            }
            lists.add(HomePageList("Acara TV Terbaru (dari vidsrc-embed)", mediaItems))
        } catch (e: Exception) {
            logError(e) 
        }
        
        // 3. Film Populer dari TMDB
        val tmdbPopularMovies = tmdbApi.getPopular(TvType.Movie, 1)
        lists.add(HomePageList("Film Populer (TMDB)", tmdbPopularMovies.results.toMediaItems()))

        // 4. Acara TV Populer dari TMDB
        val tmdbPopularTv = tmdbApi.getPopular(TvType.TvSeries, 1)
        lists.add(HomePageList("Acara TV Populer (TMDB)", tmdbPopularTv.results.toMediaItems()))

        return MainPageData(lists)
    }
}
