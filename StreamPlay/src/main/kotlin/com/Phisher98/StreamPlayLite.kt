package com.phisher98

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.runAllAsync
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.phisher98.StreamPlayExtractor.invoke2embed
import com.phisher98.StreamPlayExtractor.invokeAllMovieland
import com.phisher98.StreamPlayExtractor.invokeAnimes
import com.phisher98.StreamPlayExtractor.invokeDramadrip
import com.phisher98.StreamPlayExtractor.invokeElevenmovies
import com.phisher98.StreamPlayExtractor.invokeEmovies
import com.phisher98.StreamPlayExtractor.invokeKisskh
import com.phisher98.StreamPlayExtractor.invokeKisskhAsia
import com.phisher98.StreamPlayExtractor.invokeMovieBox
import com.phisher98.StreamPlayExtractor.invokeNepu
import com.phisher98.StreamPlayExtractor.invokeNinetv
import com.phisher98.StreamPlayExtractor.invokePlayer4U
import com.phisher98.StreamPlayExtractor.invokeRidomovies
import com.phisher98.StreamPlayExtractor.invokeRiveStream
import com.phisher98.StreamPlayExtractor.invokeShowflix
import com.phisher98.StreamPlayExtractor.invokeSoapy
import com.phisher98.StreamPlayExtractor.invokeStreamPlay
import com.phisher98.StreamPlayExtractor.invokeSubtitleAPI
import com.phisher98.StreamPlayExtractor.invokeSuperstream
import com.phisher98.StreamPlayExtractor.invokeVidSrcXyz
import com.phisher98.StreamPlayExtractor.invokeVidlink
import com.phisher98.StreamPlayExtractor.invokeVidsrccc
import com.phisher98.StreamPlayExtractor.invokeVidzee
import com.phisher98.StreamPlayExtractor.invokeWatch32APIHQ
import com.phisher98.StreamPlayExtractor.invokeWatchsomuch
import com.phisher98.StreamPlayExtractor.invokeWyZIESUBAPI
import com.phisher98.StreamPlayExtractor.invokeXPrimeAPI
import com.phisher98.StreamPlayExtractor.invokeZoechip
import com.phisher98.StreamPlayExtractor.invokeZshow
import com.phisher98.StreamPlayExtractor.invokemorph
import com.phisher98.StreamPlayExtractor.invokevidrock
import com.phisher98.StreamPlayExtractor.sharedPref

class StreamPlayLite() : StreamPlay(sharedPref) {
    override var name = "StreamPlay-Id"

    // Variabel helper
    private val apiKey = BuildConfig.TMDB_API
    
    // Filter Bahasa Asia
    private val asianLangs = "id|zh|cn|ja|ko|th"
    
    // Filter Anti-Anime (Genre ID 16 = Animation)
    private val noAnime = "&without_genres=16"

    override val mainPage = mainPageOf(
        // 1. Indonesia
        // Box Office: Diurutkan berdasarkan Vote Count (film yang paling banyak ditonton/di-rate orang Indonesia)
        "/discover/movie?api_key=$apiKey&with_original_language=id&sort_by=vote_count.desc$noAnime" to "Indonesia Box Office",
        // Prime Picks: Film Indonesia yang sedang Trending/Populer saat ini
        "/discover/movie?api_key=$apiKey&with_original_language=id&sort_by=popularity.desc$noAnime" to "Indo Prime Picks",
        
        // 2. China / Mandarin
        // Box Office: Diurutkan berdasarkan Revenue (Pendapatan Tertinggi)
        "/discover/movie?api_key=$apiKey&with_original_language=zh|cn&sort_by=revenue.desc$noAnime" to "China Box Office",
        // Hits: Yang sedang populer
        "/discover/movie?api_key=$apiKey&with_original_language=zh|cn&sort_by=popularity.desc$noAnime" to "Mandarin Hits",
        
        // 3. Japan
        // Box Office: Revenue Tertinggi (Tanpa Anime)
        "/discover/movie?api_key=$apiKey&with_original_language=ja&sort_by=revenue.desc$noAnime" to "Japan Box Office",
        // Highlights: Film Jepang dengan Rating Terbaik (Vote Average tinggi)
        "/discover/movie?api_key=$apiKey&with_original_language=ja&sort_by=vote_average.desc&vote_count.gte=100$noAnime" to "J-Movie Highlights",
        
        // 4. Thailand
        // Box Office: Vote Count terbanyak
        "/discover/movie?api_key=$apiKey&with_original_language=th&sort_by=vote_count.desc$noAnime" to "Thailand Box Office",
        // Hits: Populer saat ini
        "/discover/movie?api_key=$apiKey&with_original_language=th&sort_by=popularity.desc$noAnime" to "Thai Movie Hits",
        
        // 5. Streaming Platforms (Region ID)
        // Netflix Asia: Populer di Netflix Indonesia
        "/discover/movie?api_key=$apiKey&with_watch_providers=8&watch_region=ID&with_original_language=$asianLangs&sort_by=popularity.desc$noAnime" to "Netflix Asia Films",
        // Viu: Populer di Viu Indonesia
        "/discover/movie?api_key=$apiKey&with_watch_providers=158&watch_region=ID&sort_by=popularity.desc$noAnime" to "Viu Movie Zone",
        // WeTV: Film Mandarin/Thai populer (Pendekatan terbaik untuk WeTV)
        "/discover/movie?api_key=$apiKey&with_original_language=zh|th&sort_by=popularity.desc&page=2$noAnime" to "WeTV Movie Hub",
        // Disney+: Hotstar/Disney content Asia
        "/discover/movie?api_key=$apiKey&with_watch_providers=337|390&watch_region=ID&with_original_language=$asianLangs&sort_by=popularity.desc$noAnime" to "Disney+ Asia Movies",
        // HBO: HBO Go Asia
        "/discover/movie?api_key=$apiKey&with_watch_providers=118|119&watch_region=ID&with_original_language=$asianLangs&sort_by=popularity.desc$noAnime" to "HBO Movie Vault",
        // Prime Video: Amazon Prime Asia
        "/discover/movie?api_key=$apiKey&with_watch_providers=119&watch_region=ID&with_original_language=$asianLangs&sort_by=popularity.desc$noAnime" to "Prime Video Asia",
        
        // 6. Genres (Asian Content Only & No Anime)
        // Horror Asia
        "/discover/movie?api_key=$apiKey&with_genres=27&with_original_language=$asianLangs&sort_by=popularity.desc$noAnime" to "Asian Horror Room",
        // Thriller Asia
        "/discover/movie?api_key=$apiKey&with_genres=53&with_original_language=$asianLangs&sort_by=popularity.desc$noAnime" to "Asian Thriller Zone",
        // Comedy Asia
        "/discover/movie?api_key=$apiKey&with_genres=35&with_original_language=$asianLangs&sort_by=popularity.desc$noAnime" to "Asian Comedy Club",
        // Adventure Asia
        "/discover/movie?api_key=$apiKey&with_genres=12&with_original_language=$asianLangs&sort_by=popularity.desc$noAnime" to "Adventure Asia",
        // Romance Asia
        "/discover/movie?api_key=$apiKey&with_genres=10749&with_original_language=$asianLangs&sort_by=popularity.desc$noAnime" to "Asian Romance Line",
        // Drama Asia
        "/discover/movie?api_key=$apiKey&with_genres=18&with_original_language=$asianLangs&sort_by=popularity.desc$noAnime" to "Asian Drama Choice"
    )

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val token = sharedPref?.getString("token", null)
        val res = AppUtils.parseJson<LinkData>(data)
        runAllAsync(
            {
                if (!res.isAnime) invokeWatchsomuch(
                    res.imdbId,
                    res.season,
                    res.episode,
                    subtitleCallback
                )
            },
            {
                if (!res.isAnime) invokeNinetv(
                    res.id,
                    res.season,
                    res.episode,
                    subtitleCallback,
                    callback
                )
            },
            {
                if (res.isAnime) invokeAnimes(
                    res.title,
                    res.jpTitle,
                    res.date,
                    res.airedDate,
                    res.season,
                    res.episode,
                    subtitleCallback,
                    callback,
                    res.isDub
                )
            },
            {
                if (res.isAsian) invokeKisskh(
                    res.title,
                    res.season,
                    res.episode,
                    res.lastSeason,
                    subtitleCallback,
                    callback
                )
            },
            {
                if (!res.isAnime) invokeRidomovies(
                    res.id,
                    res.imdbId,
                    res.season,
                    res.episode,
                    subtitleCallback,
                    callback
                )
            },
            {
                if (!res.isAnime) invokeEmovies(
                    res.title,
                    res.year,
                    res.season,
                    res.episode,
                    subtitleCallback,
                    callback
                )
            },
            {
                if (!res.isAnime) invokeAllMovieland(res.imdbId, res.season, res.episode, callback)
            },
            {
                if (!res.isAnime) invoke2embed(
                    res.imdbId,
                    res.season,
                    res.episode,
                    subtitleCallback,
                    callback
                )
            },
            {
                if (!res.isAsian && !res.isBollywood &&!res.isAnime) invokeZshow(
                    res.title,
                    res.year,
                    res.season,
                    res.episode,
                    subtitleCallback,
                    callback
                )
            },
            {
                if (!res.isAnime) invokeShowflix(
                    res.title,
                    res.year,
                    res.season,
                    res.episode,
                    subtitleCallback,
                    callback
                )
            },
            {
                if (!res.isAnime) invokeZoechip(
                    res.title,
                    res.year,
                    res.season,
                    res.episode,
                    callback
                )
            },
            {
                if (!res.isAnime) invokeNepu(
                    res.title,
                    res.airedYear ?: res.year,
                    res.season,
                    res.episode,
                    callback
                )
            },
            {
                if (!res.isAnime) invokeWatch32APIHQ(
                    res.title,
                    res.season,
                    res.episode,
                    res.year,
                    subtitleCallback,
                    callback
                )
            },
            {
                if (!res.isAnime) invokeVidsrccc(
                    res.id,
                    res.season,
                    res.episode,
                    callback
                )
            },
            {
                invokeRiveStream(
                    res.id,
                    res.season,
                    res.episode,
                    callback
                )

            },
            {
                invokeSuperstream(
                    token,
                    res.imdbId,
                    res.season,
                    res.episode,
                    callback
                )
            },
            {
                if (settingsForProvider.enableAdult) {
                    if (!res.isAnime) invokePlayer4U(
                        res.title,
                        res.season,
                        res.episode,
                        res.year,
                        callback
                    )
                }
            },
            {
                invokeStreamPlay(
                    res.id,
                    res.season,
                    res.episode,
                    subtitleCallback,
                    callback
                )
            },
            {
                if (!res.isAnime) invokeVidSrcXyz(
                    res.imdbId,
                    res.season,
                    res.episode,
                    callback
                )
            },
            {
                if (!res.isAnime) invokeElevenmovies(
                    res.id,
                    res.season,
                    res.episode,
                    subtitleCallback,
                    callback
                )
            },
            {
                if (!res.isAnime) invokeXPrimeAPI(
                    res.title,
                    res.year,
                    res.imdbId,
                    res.id,
                    res.season,
                    res.episode,
                    subtitleCallback,
                    callback
                )
            },
            {
                if (!res.isAnime) invokeVidzee(
                    res.id,
                    res.season,
                    res.episode,
                    subtitleCallback,
                    callback
                )
            },
            {
                if (!res.isAnime) invokeDramadrip(res.imdbId, res.season, res.episode, subtitleCallback, callback)
            },
            {
                if (!res.isAnime) invokeMovieBox(res.title, res.season, res.episode, subtitleCallback, callback)
            },
            {
                if (!res.isAnime) invokemorph(res.title,res.year, res.season, res.episode, subtitleCallback, callback)
            },
            {
                if (!res.isAnime) invokevidrock(res.id, res.season, res.episode, callback)
            },
            {
                if (!res.isAnime) invokeSoapy(res.id, res.season, res.episode, subtitleCallback,callback)
            },
            {
                if (!res.isAnime) invokeVidlink(res.id, res.season, res.episode, subtitleCallback,callback)
            },
            {
                if (!res.isAnime) invokeKisskhAsia(
                    res.id,
                    res.season,
                    res.episode,
                    subtitleCallback,
                    callback
                )
            },

            //Subtitles Invokes
            {
                invokeSubtitleAPI(
                    res.imdbId,
                    res.season,
                    res.episode,
                    subtitleCallback
                )
            },
            {
                invokeWyZIESUBAPI(
                    res.imdbId,
                    res.season,
                    res.episode,
                    subtitleCallback,
                )
            },
        )
        return true
    }

}
