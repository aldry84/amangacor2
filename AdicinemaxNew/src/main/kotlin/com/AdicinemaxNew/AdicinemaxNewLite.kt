package com.AdicinemaxNew

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.runAllAsync
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.AdicinemaxNew.AdicinemaxNewExtractor.invoke2embed
import com.AdicinemaxNew.AdicinemaxNewExtractor.invokeAllMovieland
import com.AdicinemaxNew.AdicinemaxNewExtractor.invokeAnimes
import com.AdicinemaxNew.AdicinemaxNewExtractor.invokeDramadrip
import com.AdicinemaxNew.AdicinemaxNewExtractor.invokeElevenmovies
import com.AdicinemaxNew.AdicinemaxNewExtractor.invokeEmovies
import com.AdicinemaxNew.AdicinemaxNewExtractor.invokeKisskh
import com.AdicinemaxNew.AdicinemaxNewExtractor.invokeKisskhAsia
import com.AdicinemaxNew.AdicinemaxNewExtractor.invokeMovieBox
import com.AdicinemaxNew.AdicinemaxNewExtractor.invokeNepu
import com.AdicinemaxNew.AdicinemaxNewExtractor.invokeNinetv
import com.AdicinemaxNew.AdicinemaxNewExtractor.invokePlayer4U
import com.AdicinemaxNew.AdicinemaxNewExtractor.invokeRidomovies
import com.AdicinemaxNew.AdicinemaxNewExtractor.invokeRiveStream
import com.AdicinemaxNew.AdicinemaxNewExtractor.invokeShowflix
import com.AdicinemaxNew.AdicinemaxNewExtractor.invokeSoapy
import com.AdicinemaxNew.AdicinemaxNewExtractor.invokeStreamPlay
import com.AdicinemaxNew.AdicinemaxNewExtractor.invokeSubtitleAPI
import com.AdicinemaxNew.AdicinemaxNewExtractor.invokeSuperstream
import com.AdicinemaxNew.AdicinemaxNewExtractor.invokeVidSrcXyz
import com.AdicinemaxNew.AdicinemaxNewExtractor.invokeVidlink
import com.AdicinemaxNew.AdicinemaxNewExtractor.invokeVidsrccc
import com.AdicinemaxNew.AdicinemaxNewExtractor.invokeVidzee
import com.AdicinemaxNew.AdicinemaxNewExtractor.invokeWatch32APIHQ
import com.AdicinemaxNew.AdicinemaxNewExtractor.invokeWatchsomuch
import com.AdicinemaxNew.AdicinemaxNewExtractor.invokeWyZIESUBAPI
import com.AdicinemaxNew.AdicinemaxNewExtractor.invokeXPrimeAPI
import com.AdicinemaxNew.AdicinemaxNewExtractor.invokeZoechip
import com.AdicinemaxNew.AdicinemaxNewExtractor.invokeZshow
import com.AdicinemaxNew.AdicinemaxNewExtractor.invokemorph
import com.AdicinemaxNew.AdicinemaxNewExtractor.invokevidrock
import com.AdicinemaxNew.AdicinemaxNewExtractor.sharedPref

class AdicinemaxNewLite() : AdicinemaxNew(sharedPref) {
    override var name = "AdicinemaxNew-Lite"

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
