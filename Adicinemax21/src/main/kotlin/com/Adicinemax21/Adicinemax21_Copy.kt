package com.Adicinemax21

import android.os.Build
import androidx.annotation.RequiresApi
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.phisher98.AniIds
import com.phisher98.AniSearch
import com.phisher98.AniData
import com.phisher98.AniPage
import com.phisher98.AniMedia
import com.phisher98.TmdbDate
import com.phisher98.decrypthex
import com.phisher98.fixUrlPath
import com.phisher98.getEpisodeSlug
import com.phisher98.getLanguage
import com.phisher98.getSeason
import com.phisher98.getTitleSlug
import com.phisher98.isUpcoming
import com.phisher98.matchingIndex
import com.phisher98.toHex
import com.phisher98.hexStringToByteArray2
import com.phisher98.customEncode
import com.phisher98.padData
import com.phisher98.derivePbkdf2Key
import com.phisher98.unpadData
import com.phisher98.hasHost
import com.phisher98.generateKeyIv
import com.phisher98.KeyIvResult
import com.phisher98.hexStringToByteArray
import com.phisher98.parseCinemaOSSources
import com.phisher98.cinemaOSGenerateHash
import com.phisher98.cinemaOSDecryptResponse
import com.phisher98.CinemaOsSecretKeyRequest
import com.phisher98.CinemaOSReponseData
import com.phisher98.WatchsomuchSubResponses
import com.phisher98.WatchsomuchResponses
import com.phisher98.WatchsomuchSubtitles
import com.phisher98.WatchsomuchMovies
import com.phisher98.WatchsomuchTorrents
import com.phisher98.KisskhEpisodes
import com.phisher98.ResponseHash
import com.phisher98.GpressSources
import com.phisher98.IndexSearch
import com.phisher98.IndexData
import com.phisher98.IndexMedia
import com.phisher98.AllMovielandPlaylist
import com.phisher98.AllMovielandServer
import com.phisher98.AllMovielandSeasonFolder
import com.phisher98.AllMovielandEpisodeFolder
import com.phisher98.GokuServer
import com.phisher98.GokuData
import com.phisher98.MalSyncRes
import com.phisher98.HianimeResponses
import com.phisher98.MALSyncResponses
import com.phisher98.MALSyncSites
import com.phisher98.JikanData
import com.phisher98.JikanExternal
import com.phisher98.JikanResponse
import com.phisher98.NepuSearch
import com.phisher98.RidoSearch
import com.phisher98.RidoResponses
import com.phisher98.RidoData
import com.phisher98.RidoItems
import com.phisher98.RidoContentable
import com.phisher98.SFMoviesSearch
import com.phisher98.SFMoviesData
import com.phisher98.SFMoviesAttributes
import com.phisher98.SFMoviesSeriess
import com.phisher98.ShowflixSearchSeries
import com.phisher98.ShowflixSearchMovies
import com.phisher98.ShowflixResultsSeries
import com.phisher98.ShowflixResultsMovies
import com.phisher98.EMovieTraks
import com.phisher98.EMovieSources
import com.phisher98.EMovieServer
import com.phisher98.DumpMediaDetail
import com.phisher98.EpisodeVo
import com.phisher98.DefinitionList
import com.phisher98.SubtitlingList
import com.phisher98.DumpQuickSearchData
import com.phisher98.DumpMedia
import com.phisher98.MoflixResponse
import com.phisher98.UiraCaption
import com.phisher98.UiraStream
import com.phisher98.UiraResponse
import com.phisher98.ExternalSources
import com.phisher98.ExternalSourcesWrapper
import com.phisher98.ExternalResponse
import com.phisher98.FileList
import com.phisher98.DData
import com.phisher98.ER
import com.phisher98.SmashyData
import com.phisher98.SmashyRoot
import com.phisher98.VidsrctoSubtitles
import com.phisher98.VidsrctoResponse
import com.phisher98.VidsrctoResult
import com.phisher98.Media
import com.phisher98.MetaEpisode
import com.phisher98.MetaAnimeData
import com.phisher98.ImageData
import com.phisher98.AnimeKaiM3U8
import com.phisher98.AnimekaiTrack
import com.phisher98.AnimekaiSource
import com.phisher98.VideoData
import com.phisher98.AnimeKaiResponse
import com.phisher98.MiroTV
import com.phisher98.StremplayAPI
import com.phisher98.StremplayFields
import com.phisher98.StremplayLinks
import com.phisher98.StremplayArrayValue
import com.phisher98.StremplayValue
import com.phisher98.StremplayMapValue
import com.phisher98.StremplayFields2
import com.phisher98.StremplayHref
import com.phisher98.StremplayQuality
import com.phisher98.StremplaySource
import com.phisher98.Player4uLinkData
import com.phisher98.m3u8KAA
import com.phisher98.SubtitleKAA
import com.phisher98.EncryptedKAA
import com.phisher98.ServersResKAA
import com.phisher98.ServerKAA
import com.phisher98.EpisoderesponseKAA
import com.phisher98.ThumbnailKAA
import com.phisher98.Xprime
import com.phisher98.XprimeStreams
import com.phisher98.XprimeSubtitle
import com.phisher98.AkIframe
import com.phisher98.AnichiStream
import com.phisher98.PortData
import com.phisher98.AnichiSubtitles
import com.phisher98.AnichiLinks
import com.phisher98.Headers
import com.phisher98.AnichiVideoApiResponse
import com.phisher98.ElevenmoviesServerEntry
import com.phisher98.ElevenmoviesStreamResponse
import com.phisher98.ElevenmoviesSubtitle
import com.phisher98.Elevenmoviesjson
import com.phisher98.DomainsParser
import com.phisher98.XprimeServers
import com.phisher98.XprimeServer1
import com.phisher98.XprimeStream
import com.phisher98.XprimePrimeSubs
import com.phisher98.Cinemeta
import com.phisher98.cinemetaMeta
import com.phisher98.Oxxfile
import com.phisher98.DriveLink
import com.phisher98.Metadata
import com.phisher98.Beamup
import com.phisher98.BeamupMeta
import com.phisher98.CinemetaRes
import com.phisher98.VidfastServerData
import com.phisher98.VidFastkey
import com.phisher98.VidFastkeyHeaders
import com.phisher98.Watch32
import com.phisher98.PrimewireClass
import com.phisher98.Morph
import com.phisher98.MorphDaum
import com.phisher98.VidlinkStream
import com.phisher98.VidlinkCaption
import com.phisher98.Vidnest
import com.phisher98.VidnestStream
import com.phisher98.VidnestHeaders
import com.phisher98.PrimeSrcServerList
import com.phisher98.PrimeSrcServer
import com.phisher98.MP4Hydra
import com.phisher98.Playlist
import com.phisher98.Sub
import com.phisher98.Servers
import com.phisher98.VidFastServer
import com.phisher98.NuvioStreams
import com.phisher98.NuvioStreamsStream
import com.phisher98.NuvioStreamsBehaviorHints
import com.phisher98.GeoResponse
import com.phisher98.decryptVidzeeUrl
import com.phisher98.generateMagnetLink
import com.phisher98.StreamPlay.LinkData
import java.net.URI
import java.net.URL
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

// Menjamin API baru dapat mem-parse semua model data dari StreamPlay dan mengakses utilitas penting.

// == Salinan Model Data (StreamPlayParser.kt) ==

data class Provider(
    val id: String,
    val name: String,
    val invoke: suspend (
        res: LinkData,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
        token: String,
        dahmerMoviesAPI: String
    ) -> Unit
)

// Menggunakan kembali model dari StreamPlayParser.kt
typealias StreamPlayIndexMedia = IndexMedia
typealias StreamPlayAniIds = AniIds
typealias StreamPlayAniSearch = AniSearch
typealias StreamPlayAniData = AniData
typealias StreamPlayAniPage = AniPage
typealias StreamPlayAniMedia = AniMedia
typealias StreamPlayVidsrcccServer = VidsrcccServer
typealias StreamPlayVidsrcccResponse = VidsrcccResponse
typealias StreamPlayVidsrcccResult = VidsrcccResult
typealias StreamPlayVidsrcccSources = VidsrcccSources
typealias StreamPlayVidsrcccSubtitles = VidsrcccSubtitles
typealias StreamPlayResponseHash = ResponseHash
typealias StreamPlayMoflixResponse = MoflixResponse
typealias StreamPlayKisskhEpisodes = KisskhEpisodes
typealias StreamPlayWatchsomuchTorrents = WatchsomuchTorrents
typealias StreamPlayWatchsomuchMovies = WatchsomuchMovies
typealias StreamPlayWatchsomuchResponses = WatchsomuchResponses
typealias StreamPlayWatchsomuchSubtitles = WatchsomuchSubtitles
typealias StreamPlayWatchsomuchSubResponses = WatchsomuchSubResponses
typealias StreamPlayRidoSearch = RidoSearch
typealias StreamPlayRidoResponses = RidoResponses
typealias StreamPlayRidoData = RidoData
typealias StreamPlayRidoItems = RidoItems
typealias StreamPlayRidoContentable = RidoContentable
typealias StreamPlayAllMovielandPlaylist = AllMovielandPlaylist
typealias StreamPlayAllMovielandServer = AllMovielandServer
typealias StreamPlayAllMovielandSeasonFolder = AllMovielandSeasonFolder
typealias StreamPlayAllMovielandEpisodeFolder = AllMovielandEpisodeFolder
typealias StreamPlayEMovieServer = EMovieServer
typealias StreamPlayEMovieSources = EMovieSources
typealias StreamPlayEMovieTraks = EMovieTraks
typealias StreamPlayShowflixSearchMovies = ShowflixSearchMovies
typealias StreamPlayShowflixSearchSeries = ShowflixSearchSeries
typealias StreamPlayShowflixResultsMovies = ShowflixResultsMovies
typealias StreamPlayShowflixResultsSeries = ShowflixResultsSeries
typealias StreamPlayNepuSearch = NepuSearch
typealias StreamPlayXprimeServers = XprimeServers
typealias StreamPlayXprimeServer1 = XprimeServer1
typealias StreamPlayXprimeStream = XprimeStream
typealias StreamPlayXprimePrimeSubs = XprimePrimeSubs
typealias StreamPlayMetaEpisode = MetaEpisode
typealias StreamPlayMetaAnimeData = MetaAnimeData
typealias StreamPlayAnimeKaiSearchResult = com.phisher98.StreamPlayExtractor.AnimeKaiSearchResult // Ganti dari tipe lokal
typealias StreamPlayCrunchyrollToken = com.phisher98.CrunchyrollToken
typealias StreamPlayCrunchyrollVersions = com.phisher98.CrunchyrollVersions
typealias StreamPlayCrunchyrollData = com.phisher98.CrunchyrollData
typealias StreamPlayCrunchyrollResponses = com.phisher98.CrunchyrollResponses
typealias StreamPlayCrunchyrollSourcesResponses = com.phisher98.CrunchyrollSourcesResponses
typealias StreamPlayPrimeSrcServerList = PrimeSrcServerList
typealias StreamPlayPrimeSrcServer = PrimeSrcServer
typealias StreamPlayVidnest = Vidnest
typealias StreamPlayVidnestStream = VidnestStream
typealias StreamPlayVidnestHeaders = VidnestHeaders
typealias StreamPlayNuvioStreams = NuvioStreams
typealias StreamPlayNuvioStreamsStream = NuvioStreamsStream
typealias StreamPlayNuvioStreamsBehaviorHints = NuvioStreamsBehaviorHints
typealias StreamPlayVidFastServer = VidFastServer
typealias StreamPlayKeyIvResult = KeyIvResult
typealias StreamPlayCinemaOsSecretKeyRequest = CinemaOsSecretKeyRequest
typealias StreamPlayCinemaOSReponseData = CinemaOSReponseData

// == Salinan Utilitas (StreamPlayUtils.kt) ==

// Fungsi umum
val String?.createSlug: String?
    get() = this?.filter { it.isWhitespace() || it.isLetterOrDigit() }
        ?.trim()
        ?.replace("\\s+".toRegex(), "-")
        ?.lowercase()

// Fungsi-fungsi yang harus ada (disalin dari StreamPlayUtils.kt)
val mimeTypeCopy = arrayOf(
    "video/x-matroska",
    "video/mp4",
    "video/x-msvideo"
)

fun getIndexQuality(str: String?): Int = com.phisher98.getIndexQuality(str)

fun getIndexQualityTags(str: String?, fullTag: Boolean = false): String = com.phisher98.getIndexQualityTags(str, fullTag)

fun getIndexQuery(
    title: String? = null,
    year: Int? = null,
    season: Int? = null,
    episode: Int? = null
): String = com.phisher98.getIndexQuery(title, year, season, episode)

fun searchIndex(
    title: String? = null,
    season: Int? = null,
    episode: Int? = null,
    year: Int? = null,
    response: String,
    isTrimmed: Boolean = true,
): List<IndexMedia>? = com.phisher98.searchIndex(title, season, episode, year, response, isTrimmed)

fun getEpisodeSlug(
    season: Int? = null,
    episode: Int? = null,
): Pair<String, String> = com.phisher98.getEpisodeSlug(season, episode)

fun getTitleSlug(title: String? = null): Pair<String?, String?> = com.phisher98.getTitleSlug(title)

fun bytesToGigaBytes(number: Double): Double = com.phisher98.bytesToGigaBytes(number)

fun getKisskhTitle(str: String?): String? = com.phisher98.getKisskhTitle(str)

fun getBaseUrl(url: String): String = com.phisher98.getBaseUrl(url)

fun fixUrl(url: String, domain: String): String = com.phisher98.fixUrl(url, domain)

fun generateWpKey(r: String, m: String): String = com.phisher98.generateWpKey(r, m)

fun decryptVidzeeUrlCopy(encrypted: String, key: ByteArray): String = com.phisher98.decryptVidzeeUrl(encrypted, key)

@RequiresApi(Build.VERSION_CODES.O)
fun generateVrfAESCopy(movieId: String, userId: String): String = com.phisher98.generateVrfAES(movieId, userId)

fun hexStringToByteArray2Copy(hex: String): ByteArray = com.phisher98.hexStringToByteArray2(hex)

fun padDataCopy(data: ByteArray, blockSize: Int): ByteArray = com.phisher98.padData(data, blockSize)

fun customEncodeCopy(input: ByteArray): String = com.phisher98.customEncode(input)

fun parseServersCopy(jsonString: String): List<VidFastServer> = com.phisher98.parseServers(jsonString)

fun derivePbkdf2KeyCopy(
    password: String,
    salt: ByteArray,
    iterations: Int,
    keyLength: Int
): ByteArray = com.phisher98.derivePbkdf2Key(password, salt, iterations, keyLength)

fun unpadDataCopy(data: ByteArray): ByteArray = com.phisher98.unpadData(data)

fun hasHostCopy(url: String): Boolean = com.phisher98.hasHost(url)

fun generateKeyIvCopy(keySize: Int = 32): KeyIvResult = com.phisher98.generateKeyIv(keySize)

fun hexStringToByteArrayCopy(hex: String): ByteArray = com.phisher98.hexStringToByteArray(hex)

fun parseCinemaOSSourcesCopy(jsonString: String): List<Map<String, String>> = com.phisher98.parseCinemaOSSources(jsonString)

fun cinemaOSGenerateHashCopy(t: CinemaOsSecretKeyRequest,isSeries: Boolean): String = com.phisher98.cinemaOSGenerateHash(t, isSeries)

fun cinemaOSDecryptResponseCopy(e: CinemaOSReponseData?): Any = com.phisher98.cinemaOSDecryptResponse(e)

@RequiresApi(Build.VERSION_CODES.O)
suspend fun convertTmdbToAnimeIdCopy(
    title: String?,
    date: String?,
    airedDate: String?,
    type: TvType
): AniIds = com.phisher98.convertTmdbToAnimeId(title, date, airedDate, type)

suspend fun tmdbToAnimeIdCopy(title: String?, year: Int?, season: String?, type: TvType): AniIds = com.phisher98.tmdbToAnimeId(title, year, season, type)

fun getLanguageCopy(code: String): String = com.phisher98.getLanguage(code)

fun getPlayer4UQualityCopy(quality: String): Int = com.phisher98.getPlayer4UQuality(quality)

fun extractPlayer4uLinksCopy(document: org.jsoup.nodes.Document,season:Int?,episode:Int?,title:String,year:Int?): List<Player4uLinkData> = com.phisher98.StreamPlayExtractor.extractPlayer4uLinks(document, season, episode, title, year)

// == Salinan Logika Pemanggil Extractor (StreamPlayExtractor.kt) ==

// Definisi ulang untuk ExtractorLinkType agar bisa diakses
val INFER_TYPE_COPY = ExtractorLinkType.values().firstOrNull { it.name == "INFER_TYPE" } ?: ExtractorLinkType.STREAM

// Definisi ulang fungsi pemanggil eksekusi
@Suppress("NOTHING_TO_INLINE")
inline suspend fun loadCustomExtractorCopy(
    name: String? = null,
    url: String,
    referer: String? = null,
    noinline subtitleCallback: (SubtitleFile) -> Unit,
    noinline callback: (ExtractorLink) -> Unit,
    quality: Int? = null,
) = com.phisher98.StreamPlayExtractor.loadCustomExtractor(name, url, referer, subtitleCallback, callback, quality)

@Suppress("NOTHING_TO_INLINE")
inline suspend fun loadSourceNameExtractorCopy(
    source: String,
    url: String,
    referer: String? = null,
    noinline subtitleCallback: (SubtitleFile) -> Unit,
    noinline callback: (ExtractorLink) -> Unit,
    quality: Int? = null,
    size: String = ""
) = com.phisher98.StreamPlayExtractor.loadSourceNameExtractor(source, url, referer, subtitleCallback, callback, quality, size)

@Suppress("NOTHING_TO_INLINE")
inline suspend fun loadCustomTagExtractorCopy(
    tag: String? = null,
    url: String,
    referer: String? = null,
    noinline subtitleCallback: (SubtitleFile) -> Unit,
    noinline callback: (ExtractorLink) -> Unit,
    quality: Int? = null,
) = com.phisher98.StreamPlayExtractor.loadCustomTagExtractor(tag, url, referer, subtitleCallback, callback, quality)

@Suppress("NOTHING_TO_INLINE")
inline suspend fun generateMagnetLinkCopy(url: String, hash: String?): String = com.phisher98.StreamPlayTorrent.generateMagnetLink(url, hash)

