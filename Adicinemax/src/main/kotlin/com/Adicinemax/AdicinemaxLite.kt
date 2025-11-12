package com.Adicinemax

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.runAllAsync
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.Adicinemax.AdicinemaxExtractor.invoke2embed
import com.Adicinemax.AdicinemaxExtractor.invokeAllMovieland
import com.Adicinemax.AdicinemaxExtractor.invokeAnimes
import com.Adicinemax.AdicinemaxExtractor.invokeDramadrip
import com.Adicinemax.AdicinemaxExtractor.invokeElevenmovies
import com.Adicinemax.AdicinemaxExtractor.invokeEmbedlc
import com.Adicinemax.AdicinemaxExtractor.invokeEmovies
import com.Adicinemax.AdicinemaxExtractor.invokeKisskh
import com.Adicinemax.AdicinemaxExtractor.invokeKisskhAsia
import com.Adicinemax.AdicinemaxExtractor.invokeMovieBox
import com.Adicinemax.AdicinemaxExtractor.invokeNepu
import com.Adicinemax.AdicinemaxExtractor.invokeNinetv
import com.Adicinemax.AdicinemaxExtractor.invokePlayer4U
import com.Adicinemax.AdicinemaxExtractor.invokeRidomovies
import com.Adicinemax.AdicinemaxExtractor.invokeRiveStream
import com.Adicinemax.AdicinemaxExtractor.invokeShowflix
import com.Adicinemax.AdicinemaxExtractor.invokeSoapy
import com.Adicinemax.AdicinemaxExtractor.invokeAdicinemax
import com.Adicinemax.AdicinemaxExtractor.invokeSubtitleAPI
import com.Adicinemax.AdicinemaxExtractor.invokeSuperstream
import com.Adicinemax.AdicinemaxExtractor.invokeVidSrcXyz
import com.Adicinemax.AdicinemaxExtractor.invokeVidlink
import com.Adicinemax.AdicinemaxExtractor.invokeVidsrccc
import com.Adicinemax.AdicinemaxExtractor.invokeVidzee
import com.Adicinemax.AdicinemaxExtractor.invokeWatch32APIHQ
import com.Adicinemax.AdicinemaxExtractor.invokeWatchsomuch
import com.Adicinemax.AdicinemaxExtractor.invokeWyZIESUBAPI
import com.Adicinemax.AdicinemaxExtractor.invokeXPrimeAPI
import com.Adicinemax.AdicinemaxExtractor.invokeZoechip
import com.Adicinemax.AdicinemaxExtractor.invokeZshow
import com.Adicinemax.AdicinemaxExtractor.invokemorph
import com.Adicinemax.AdicinemaxExtractor.invokevidrock
import com.Adicinemax.AdicinemaxExtractor.sharedPref

class AdicinemaxLite() : Adicinemax(sharedPref) {
    override var name = "AmanGacorPlay-Ringan"

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
                if (!res.isAnime)  invokeEmbedlc(res.imdbId, res.season, res.episode,subtitleCallback, callback)
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
