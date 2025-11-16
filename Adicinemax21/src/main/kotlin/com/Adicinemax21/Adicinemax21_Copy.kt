package com.Adicinemax21

import android.os.Build
import androidx.annotation.RequiresApi
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType

// --- IMPORT SEMUA DARI PACKAGE com.phisher98 ---
import com.phisher98.*
import com.phisher98.StreamPlay.LinkData
import com.phisher98.StreamPlayExtractor // Diperlukan untuk mengakses inner object/function

// Menjamin API baru dapat mem-parse semua model data dari StreamPlay dan mengakses utilitas penting.

// == Salinan Model Data (StreamPlayParser.kt) ==

data class Provider(
    val id: String,
    val name: String,
    val invoke: suspend (
        res: LinkData, // Menggunakan LinkData dari com.phisher98.StreamPlay
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
        token: String,
        dahmerMoviesAPI: String
    ) -> Unit
)

// Menggunakan typealias ke kelas phisher98 yang sudah di-import di atas.
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
typealias StreamPlayAnimeKaiSearchResult = StreamPlayExtractor.AnimeKaiSearchResult
typealias StreamPlayCrunchyrollToken = CrunchyrollToken
typealias StreamPlayCrunchyrollVersions = CrunchyrollVersions
typealias StreamPlayCrunchyrollData = CrunchyrollData
typealias StreamPlayCrunchyrollResponses = CrunchyrollResponses
typealias StreamPlayCrunchyrollSourcesResponses = CrunchyrollSourcesResponses
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
    get() = com.phisher98.createSlug(this)

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
