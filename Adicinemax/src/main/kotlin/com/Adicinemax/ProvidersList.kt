package com.Adicinemax

import android.os.Build
import androidx.annotation.RequiresApi
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.Adicinemax.AdicinemaxExtractor.invoke2embed
import com.Adicinemax.AdicinemaxExtractor.invoke4khdhub
import com.Adicinemax.AdicinemaxExtractor.invokeAllMovieland
import com.Adicinemax.AdicinemaxExtractor.invokeAnimes
import com.Adicinemax.AdicinemaxExtractor.invokeBollyflix
import com.Adicinemax.AdicinemaxExtractor.invokeCinemaOS
import com.Adicinemax.AdicinemaxExtractor.invokeDahmerMovies
import com.Adicinemax.AdicinemaxExtractor.invokeDotmovies
import com.Adicinemax.AdicinemaxExtractor.invokeDramadrip
import com.Adicinemax.AdicinemaxExtractor.invokeElevenmovies
import com.Adicinemax.AdicinemaxExtractor.invokeEmbedlc
import com.Adicinemax.AdicinemaxExtractor.invokeEmovies
import com.Adicinemax.AdicinemaxExtractor.invokeExtramovies
import com.Adicinemax.AdicinemaxExtractor.invokeFilm1k
import com.Adicinemax.AdicinemaxExtractor.invokeHdmovie2
import com.Adicinemax.AdicinemaxExtractor.invokeKisskh
import com.Adicinemax.AdicinemaxExtractor.invokeKisskhAsia
import com.Adicinemax.AdicinemaxExtractor.invokeMappleTv
import com.Adicinemax.AdicinemaxExtractor.invokeMoflix
import com.Adicinemax.AdicinemaxExtractor.invokeMovieBox
import com.Adicinemax.AdicinemaxExtractor.invokeMoviehubAPI
import com.Adicinemax.AdicinemaxExtractor.invokeMoviesdrive
import com.Adicinemax.AdicinemaxExtractor.invokeMoviesmod
import com.Adicinemax.AdicinemaxExtractor.invokeMultiEmbed
import com.Adicinemax.AdicinemaxExtractor.invokeMultimovies
import com.Adicinemax.AdicinemaxExtractor.invokeNepu
import com.Adicinemax.AdicinemaxExtractor.invokeNinetv
import com.Adicinemax.AdicinemaxExtractor.invokeNuvioStreams
import com.Adicinemax.AdicinemaxExtractor.invokePlaydesi
import com.Adicinemax.AdicinemaxExtractor.invokePlayer4U
import com.Adicinemax.AdicinemaxExtractor.invokePrimeSrc
import com.Adicinemax.AdicinemaxExtractor.invokeRidomovies
import com.Adicinemax.AdicinemaxExtractor.invokeRiveStream
import com.Adicinemax.AdicinemaxExtractor.invokeRogmovies
import com.Adicinemax.AdicinemaxExtractor.invokeShowflix
import com.Adicinemax.AdicinemaxExtractor.invokeSoapy
import com.Adicinemax.AdicinemaxExtractor.invokeSuperstream
import com.Adicinemax.AdicinemaxExtractor.invokeToonstream
import com.Adicinemax.AdicinemaxExtractor.invokeTopMovies
import com.Adicinemax.AdicinemaxExtractor.invokeUhdmovies
import com.Adicinemax.AdicinemaxExtractor.invokeVegamovies
import com.Adicinemax.AdicinemaxExtractor.invokeVidFast
import com.Adicinemax.AdicinemaxExtractor.invokeVidPlus
import com.Adicinemax.AdicinemaxExtractor.invokeVidSrcXyz
import com.Adicinemax.AdicinemaxExtractor.invokeVidlink
import com.Adicinemax.AdicinemaxExtractor.invokeVidnest
import com.Adicinemax.AdicinemaxExtractor.invokeVidsrccc
import com.Adicinemax.AdicinemaxExtractor.invokeVidzee
import com.Adicinemax.AdicinemaxExtractor.invokeWatch32APIHQ
import com.Adicinemax.AdicinemaxExtractor.invokeWatchsomuch
import com.Adicinemax.AdicinemaxExtractor.invokeXPrimeAPI
import com.Adicinemax.AdicinemaxExtractor.invokeZoechip
import com.Adicinemax.AdicinemaxExtractor.invokeZshow
import com.Adicinemax.AdicinemaxExtractor.invokehdhub4u
import com.Adicinemax.AdicinemaxExtractor.invokemorph
import com.Adicinemax.AdicinemaxExtractor.invokemp4hydra
import com.Adicinemax.AdicinemaxExtractor.invokevidrock

data class Provider(
    val id: String,
    val name: String,
    val invoke: suspend (
        res: Adicinemax.LinkData,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
        token: String,
        dahmerMoviesAPI: String
    ) -> Unit
)


@RequiresApi(Build.VERSION_CODES.O)
fun buildProviders(): List<Provider> {
    return listOf(
        Provider("uhdmovies", "UHD Movies (Multi)") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime) invokeUhdmovies(res.title, res.year, res.season, res.episode, callback, subtitleCallback)
        },
        Provider("anime", "All Anime Sources") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (res.isAnime) invokeAnimes(res.title, res.jpTitle, res.date, res.airedDate, res.season, res.episode, subtitleCallback, callback, res.isDub)
        },
        Provider("player4u", "Player4U") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime) invokePlayer4U(res.title, res.season, res.episode, res.year, callback)
        },

        Provider("vidsrccc", "Vidsrccc") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime) invokeVidsrccc(res.id, res.season, res.episode, callback)
        },
        Provider("topmovies", "Top Movies") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime) invokeTopMovies(res.imdbId, res.year, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("moviesmod", "MoviesMod") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime) invokeMoviesmod(res.imdbId, res.year, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("bollyflix", "Bollyflix") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime) invokeBollyflix(res.imdbId, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("watchsomuch", "WatchSoMuch") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime) invokeWatchsomuch(res.imdbId, res.season, res.episode, subtitleCallback)
        },
        Provider("ninetv", "NineTV") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime) invokeNinetv(res.id, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("ridomovies", "RidoMovies") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime) invokeRidomovies(res.id, res.imdbId, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("moviehubapi", "MovieHub API") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime) invokeMoviehubAPI(res.id, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("allmovieland", "AllMovieland") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime) invokeAllMovieland(res.imdbId, res.season, res.episode, callback)
        },
        Provider("multiembed", "MultiEmbed") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime) invokeMultiEmbed(res.imdbId, res.season, res.episode, callback)
        },
        Provider("emovies", "EMovies") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime) invokeEmovies(res.title, res.year, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("vegamovies", "VegaMovies (Multi)") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime) invokeVegamovies(res.title, res.year, res.season, res.episode, res.imdbId, subtitleCallback, callback)
        },
        Provider("extramovies", "ExtraMovies") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime) invokeExtramovies(res.imdbId, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("multimovies", "MultiMovies (Multi)") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime) invokeMultimovies(res.title, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("2embed", "2Embed") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime) invoke2embed(res.imdbId, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("zshow", "ZShow") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime) invokeZshow(res.title, res.year, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("showflix", "ShowFlix (South Indian)") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime) invokeShowflix(res.title, res.year, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("moflix", "Moflix (Multi)") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime) invokeMoflix(res.id, res.season, res.episode, callback)
        },
        Provider("zoechip", "ZoeChip") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime) invokeZoechip(res.title, res.year, res.season, res.episode, callback)
        },
        Provider("nepu", "Nepu") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime) invokeNepu(res.title, res.airedYear ?: res.year, res.season, res.episode, callback)
        },
        Provider("playdesi", "PlayDesi") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime) invokePlaydesi(res.title, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("moviesdrive", "MoviesDrive (Multi)") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            invokeMoviesdrive(res.title, res.season, res.episode, res.year, res.imdbId, subtitleCallback, callback)
        },
        Provider("watch32APIHQ", "Watch32 API HQ (English)") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime) invokeWatch32APIHQ(res.title, res.season, res.episode, res.year, subtitleCallback, callback)
        },
        Provider("primesrc", "PrimeSrc") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime) invokePrimeSrc(res.imdbId, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("film1k", "Film1k") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime) invokeFilm1k(res.title, res.season, res.year, subtitleCallback, callback)
        },
        Provider("superstream", "SuperStream") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime && res.imdbId != null) invokeSuperstream(token, res.imdbId, res.season, res.episode, callback)
        },
        Provider("vidsrcxyz", "VidSrcXyz (English)") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime) invokeVidSrcXyz(res.imdbId, res.season, res.episode, callback)
        },
        Provider("xprimeapi", "XPrime API") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime) invokeXPrimeAPI(res.title, res.year, res.imdbId, res.id, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("vidzeeapi", "Vidzee API") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime) invokeVidzee(res.id, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("4khdhub", "4kHdhub (Multi)") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            invoke4khdhub(res.title, res.year, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("elevenmovies", "ElevenMovies") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime) invokeElevenmovies(res.id, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("hdhub4u", "Hdhub4u (Multi)") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime) invokehdhub4u(res.imdbId, res.title, res.year, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("hdmovie2", "Hdmovie2") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime) invokeHdmovie2(res.title, res.year, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("dramadrip", "Dramadrip (Asian Drama)") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime)invokeDramadrip(res.imdbId, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("embedlc", "EmbedLC") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime) invokeEmbedlc(res.imdbId, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("rivestream", "RiveStream") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime) invokeRiveStream(res.id, res.season, res.episode, callback)
        },
        Provider("moviebox", "MovieBox (Multi)") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime) invokeMovieBox(res.title, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("morph", "Morph") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime) invokemorph(res.title, res.year, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("vidrock", "Vidrock") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime) invokevidrock(res.id, res.season, res.episode, callback)
        },
        Provider("soapy", "Soapy") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime) invokeSoapy(res.id, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("vidlink", "Vidlink") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime) invokeVidlink(res.id, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("mappletv", "MappleTV") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime) invokeMappleTv(res.id, res.title, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("vidnest", "Vidnest") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime) invokeVidnest(res.id, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("dotmovies", "DotMovies") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime) invokeDotmovies(res.imdbId, res.title, res.year, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("rogmovies", "RogMovies") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime) invokeRogmovies(res.imdbId, res.title, res.year, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("kisskh", "KissKH (Asian Drama)") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime) invokeKisskh(res.title, res.season, res.episode, res.lastSeason, subtitleCallback, callback)
        },
        Provider("cinemaos", "CinemaOS") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            invokeCinemaOS(res.imdbId, res.id, res.title, res.season, res.episode, res.year, callback, subtitleCallback)
        },
        Provider("dahmermovies", "DahmerMovies") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime) invokeDahmerMovies(dahmerMoviesAPI, res.title, res.year, res.season, res.episode, callback)
        },
        Provider("KisskhAsia", "KissKhAsia (Asian Drama)") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime) invokeKisskhAsia(res.id, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("mp4hydra", "MP4Hydra") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime) invokemp4hydra(res.title, res.year,res.season, res.episode, subtitleCallback, callback)
        },
        Provider("vidfast", "VidFast") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime) invokeVidFast(res.id, res.season,res.episode, callback, subtitleCallback)
        },
        Provider("vidplus", "VidPlus") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (!res.isAnime) invokeVidPlus(res.id, res.season,res.episode,  callback,subtitleCallback)
        },
        Provider("toonstream", "Toonstream (Hindi Anime)") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            if (res.isAnime || res.isCartoon) invokeToonstream(res.title, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("NuvioStreams", "NuvioStreams") { res, subtitleCallback, callback, token, dahmerMoviesAPI ->
            invokeNuvioStreams(res.imdbId, res.season,res.episode,  callback)
        },
    )
}
