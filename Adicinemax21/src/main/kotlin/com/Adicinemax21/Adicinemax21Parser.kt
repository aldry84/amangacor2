package com.Adicinemax21

import com.fasterxml.jackson.annotation.JsonProperty

// ================== IDLIX DATA CLASSES ==================
data class AesData(
    @param:JsonProperty("m") val m: String,
)

data class ResponseHash(
    @param:JsonProperty("embed_url") val embed_url: String,
    @param:JsonProperty("key") val key: String,
)

// ================== EXISTING DATA CLASSES ==================

data class AniIds(var id: Int? = null, var idMal: Int? = null)

data class TmdbDate(
    val today: String,
    val nextWeek: String,
)

data class VixsrcSource(
    val name: String,
    val url: String,
    val referer: String,
)

data class VidrockSource(
    @param:JsonProperty("resolution") val resolution: Int? = null,
    @param:JsonProperty("url") val url: String? = null,
)

data class VidrockSubtitle(
    @param:JsonProperty("label") val label: String? = null,
    @param:JsonProperty("file") val file: String? = null,
)

data class VidsrccxSource(
    @param:JsonProperty("secureUrl") val secureUrl: String? = null,
)

data class WyzieSubtitle(
    @param:JsonProperty("display") val display: String? = null,
    @param:JsonProperty("url") val url: String? = null,
)

data class VidFastSources(
    @param:JsonProperty("url") val url: String? = null,
    @param:JsonProperty("tracks") val tracks: ArrayList<Tracks>? = null,
) {
    data class Tracks(
        @param:JsonProperty("file") val file: String? = null,
        @param:JsonProperty("label") val label: String? = null,
    )
}

data class VidFastServers(
    @param:JsonProperty("name") val name: String? = null,
    @param:JsonProperty("description") val description: String? = null,
    @param:JsonProperty("data") val data: String? = null,
) {
    data class Stream(
        @param:JsonProperty("playlist") val playlist: String? = null,
    )
}

data class VidlinkSources(
    @param:JsonProperty("stream") val stream: Stream? = null,
) {
    data class Stream(
        @param:JsonProperty("playlist") val playlist: String? = null,
    )
}

data class MappleSubtitle(
    @param:JsonProperty("display") val display: String? = null,
    @param:JsonProperty("url") val url: String? = null,
)

data class MappleSources(
    @param:JsonProperty("data") val data: Data? = null,
) {
    data class Data(
        @param:JsonProperty("stream_url") val stream_url: String? = null,
    )
}

data class PrimeboxSources(
    @param:JsonProperty("streams") val streams: HashMap<String, String>? = null,
    @param:JsonProperty("subtitles") val subtitles: ArrayList<Subtitles>? = null,
) {
    data class Subtitles(
        @param:JsonProperty("file") val file: String? = null,
        @param:JsonProperty("label") val label: String? = null,
    )
}

data class RageSources(
    @param:JsonProperty("url") val url: String? = null,
)

data class VidsrcccServer(
    @param:JsonProperty("name") val name: String? = null,
    @param:JsonProperty("hash") val hash: String? = null,
)

data class VidsrcccResponse(
    @param:JsonProperty("data") val data: ArrayList<VidsrcccServer>? = arrayListOf(),
)

data class VidsrcccResult(
    @param:JsonProperty("data") val data: VidsrcccSources? = null,
)

data class VidsrcccSources(
    @param:JsonProperty("subtitles") val subtitles: ArrayList<VidsrcccSubtitles>? = arrayListOf(),
    @param:JsonProperty("source") val source: String? = null,
)

data class VidsrcccSubtitles(
    @param:JsonProperty("label") val label: String? = null,
    @param:JsonProperty("file") val file: String? = null,
)

data class UpcloudSources(
    @param:JsonProperty("file") val file: String? = null,
)

data class UpcloudResult(
    @param:JsonProperty("sources") val sources: ArrayList<UpcloudSources>? = arrayListOf(),
)

data class AniMedia(
    @param:JsonProperty("id") var id: Int? = null,
    @param:JsonProperty("idMal") var idMal: Int? = null
)

data class AniPage(@param:JsonProperty("media") var media: java.util.ArrayList<AniMedia> = arrayListOf())

data class AniData(@param:JsonProperty("Page") var Page: AniPage? = AniPage())

data class AniSearch(@param:JsonProperty("data") var data: AniData? = AniData())

data class GpressSources(
    @param:JsonProperty("src") val src: String,
    @param:JsonProperty("file") val file: String? = null,
    @param:JsonProperty("label") val label: Int? = null,
    @param:JsonProperty("max") val max: String,
)

data class KisskhEpisodes(
    @param:JsonProperty("id") val id: Int?,
    @param:JsonProperty("number") val number: Int?,
)

data class WatchsomuchTorrents(
    @param:JsonProperty("id") val id: Int? = null,
    @param:JsonProperty("movieId") val movieId: Int? = null,
    @param:JsonProperty("season") val season: Int? = null,
    @param:JsonProperty("episode") val episode: Int? = null,
)

data class WatchsomuchMovies(
    @param:JsonProperty("torrents") val torrents: ArrayList<WatchsomuchTorrents>? = arrayListOf(),
)

data class WatchsomuchSubtitles(
    @param:JsonProperty("url") val url: String? = null,
    @param:JsonProperty("label") val label: String? = null,
)

data class WatchsomuchResponses(
    @param:JsonProperty("movie") val movie: WatchsomuchMovies? = null,
)

data class WatchsomuchSubResponses(
    @param:JsonProperty("subtitles") val subtitles: ArrayList<WatchsomuchSubtitles>? = arrayListOf(),
)

data class IndexMedia(
    @param:JsonProperty("id") val id: String? = null,
    @param:JsonProperty("driveId") val driveId: String? = null,
    @param:JsonProperty("mimeType") val mimeType: String? = null,
    @param:JsonProperty("size") val size: String? = null,
    @param:JsonProperty("name") val name: String? = null,
    @param:JsonProperty("modifiedTime") val modifiedTime: String? = null,
)

data class IndexData(
    @param:JsonProperty("files") val files: ArrayList<IndexMedia>? = arrayListOf(),
)

data class IndexSearch(
    @param:JsonProperty("data") val data: IndexData? = null,
)

data class JikanExternal(
    @param:JsonProperty("name") val name: String? = null,
    @param:JsonProperty("url") val url: String? = null,
)

data class JikanData(
    @param:JsonProperty("title") val title: String? = null,
    @param:JsonProperty("external") val external: ArrayList<JikanExternal>? = arrayListOf(),
)

data class VidsrctoResult(
    @param:JsonProperty("id") val id: String? = null,
    @param:JsonProperty("title") val title: String? = null,
    @param:JsonProperty("url") val url: String? = null,
)

data class AnilistExternalLinks(
    @param:JsonProperty("id") var id: Int? = null,
    @param:JsonProperty("site") var site: String? = null,
    @param:JsonProperty("url") var url: String? = null,
    @param:JsonProperty("type") var type: String? = null,
)

data class AnilistMedia(@param:JsonProperty("externalLinks") var externalLinks: ArrayList<AnilistExternalLinks> = arrayListOf())

data class AnilistData(@param:JsonProperty("Media") var Media: AnilistMedia? = AnilistMedia())

data class MALSyncSites(
    @param:JsonProperty("Zoro") val zoro: HashMap<String?, HashMap<String, String?>>? = hashMapOf(),
    @param:JsonProperty("9anime") val nineAnime: HashMap<String?, HashMap<String, String?>>? = hashMapOf(),
)

data class MALSyncResponses(
    @param:JsonProperty("Sites") val sites: MALSyncSites? = null,
)

data class HianimeResponses(
    @param:JsonProperty("html") val html: String? = null,
    @param:JsonProperty("link") val link: String? = null,
)

data class MalSyncRes(
    @param:JsonProperty("Sites") val Sites: Map<String, Map<String, Map<String, String>>>? = null,
)

data class GokuData(
    @param:JsonProperty("link") val link: String? = null,
)

data class GokuServer(
    @param:JsonProperty("data") val data: GokuData? = GokuData(),
)

data class AllMovielandEpisodeFolder(
    @param:JsonProperty("title") val title: String? = null,
    @param:JsonProperty("id") val id: String? = null,
    @param:JsonProperty("file") val file: String? = null,
)

data class AllMovielandSeasonFolder(
    @param:JsonProperty("episode") val episode: String? = null,
    @param:JsonProperty("id") val id: String? = null,
    @param:JsonProperty("folder") val folder: ArrayList<AllMovielandEpisodeFolder>? = arrayListOf(),
)

data class AllMovielandServer(
    @param:JsonProperty("title") val title: String? = null,
    @param:JsonProperty("id") val id: String? = null,
    @param:JsonProperty("file") val file: String? = null,
    @param:JsonProperty("folder") val folder: ArrayList<AllMovielandSeasonFolder>? = arrayListOf(),
)

data class AllMovielandPlaylist(
    @param:JsonProperty("file") val file: String? = null,
    @param:JsonProperty("key") val key: String? = null,
    @param:JsonProperty("href") val href: String? = null,
)

data class DumpMedia(
    @param:JsonProperty("id") val id: String? = null,
    @param:JsonProperty("domainType") val domainType: Int? = null,
    @param:JsonProperty("name") val name: String? = null,
    @param:JsonProperty("releaseTime") val releaseTime: String? = null,
)

data class DumpQuickSearchData(
    @param:JsonProperty("searchResults") val searchResults: ArrayList<DumpMedia>? = arrayListOf(),
)

data class SubtitlingList(
    @param:JsonProperty("languageAbbr") val languageAbbr: String? = null,
    @param:JsonProperty("language") val language: String? = null,
    @param:JsonProperty("subtitlingUrl") val subtitlingUrl: String? = null,
)

data class DefinitionList(
    @param:JsonProperty("code") val code: String? = null,
    @param:JsonProperty("description") val description: String? = null,
)

data class EpisodeVo(
    @param:JsonProperty("id") val id: Int? = null,
    @param:JsonProperty("seriesNo") val seriesNo: Int? = null,
    @param:JsonProperty("definitionList") val definitionList: ArrayList<DefinitionList>? = arrayListOf(),
    @param:JsonProperty("subtitlingList") val subtitlingList: ArrayList<SubtitlingList>? = arrayListOf(),
)

data class DumpMediaDetail(
    @param:JsonProperty("episodeVo") val episodeVo: ArrayList<EpisodeVo>? = arrayListOf(),
)

data class EMovieServer(
    @param:JsonProperty("value") val value: String? = null,
)

data class EMovieSources(
    @param:JsonProperty("file") val file: String? = null,
)

data class EMovieTraks(
    @param:JsonProperty("file") val file: String? = null,
    @param:JsonProperty("label") val label: String? = null,
)

data class ShowflixResultsMovies(
    @param:JsonProperty("movieName") val movieName: String? = null,
    @param:JsonProperty("streamwish") val streamwish: String? = null,
    @param:JsonProperty("filelions") val filelions: String? = null,
    @param:JsonProperty("streamruby") val streamruby: String? = null,
)

data class ShowflixResultsSeries(
    @param:JsonProperty("seriesName") val seriesName: String? = null,
    @param:JsonProperty("streamwish") val streamwish: HashMap<String, List<String>>? = hashMapOf(),
    @param:JsonProperty("filelions") val filelions: HashMap<String, List<String>>? = hashMapOf(),
    @param:JsonProperty("streamruby") val streamruby: HashMap<String, List<String>>? = hashMapOf(),
)

data class ShowflixSearchMovies(
    @param:JsonProperty("results") val resultsMovies: ArrayList<ShowflixResultsMovies>? = arrayListOf(),
)

data class ShowflixSearchSeries(
    @param:JsonProperty("results") val resultsSeries: ArrayList<ShowflixResultsSeries>? = arrayListOf(),
)

data class SFMoviesSeriess(
    @param:JsonProperty("title") var title: String? = null,
    @param:JsonProperty("svideos") var svideos: String? = null,
)

data class SFMoviesAttributes(
    @param:JsonProperty("title") var title: String? = null,
    @param:JsonProperty("video") var video: String? = null,
    @param:JsonProperty("releaseDate") var releaseDate: String? = null,
    @param:JsonProperty("seriess") var seriess: ArrayList<ArrayList<SFMoviesSeriess>>? = arrayListOf(),
    @param:JsonProperty("contentId") var contentId: String? = null,
)

data class SFMoviesData(
    @param:JsonProperty("id") var id: Int? = null,
    @param:JsonProperty("attributes") var attributes: SFMoviesAttributes? = SFMoviesAttributes()
)

data class SFMoviesSearch(
    @param:JsonProperty("data") var data: ArrayList<SFMoviesData>? = arrayListOf(),
)

data class RidoContentable(
    @param:JsonProperty("imdbId") var imdbId: String? = null,
    @param:JsonProperty("tmdbId") var tmdbId: Int? = null,
)

data class RidoItems(
    @param:JsonProperty("slug") var slug: String? = null,
    @param:JsonProperty("contentable") var contentable: RidoContentable? = null,
)

data class RidoData(
    @param:JsonProperty("url") var url: String? = null,
    @param:JsonProperty("items") var items: ArrayList<RidoItems>? = arrayListOf(),
)

data class RidoResponses(
    @param:JsonProperty("data") var data: ArrayList<RidoData>? = arrayListOf(),
)

data class RidoSearch(
    @param:JsonProperty("data") var data: RidoData? = null,
)

data class SmashySources(
    @param:JsonProperty("sourceUrls") var sourceUrls: ArrayList<String>? = arrayListOf(),
    @param:JsonProperty("subtitleUrls") var subtitleUrls: String? = null,
)

data class AoneroomResponse(
    @param:JsonProperty("data") val data: Data? = null,
) {
    data class Data(
        @param:JsonProperty("items") val items: ArrayList<Items>? = arrayListOf(),
        @param:JsonProperty("list") val list: ArrayList<List>? = arrayListOf(),
    ) {
        data class Items(
            @param:JsonProperty("subjectId") val subjectId: String? = null,
            @param:JsonProperty("title") val title: String? = null,
            @param:JsonProperty("releaseDate") val releaseDate: String? = null,
        )

        data class List(
            @param:JsonProperty("resourceLink") val resourceLink: String? = null,
            @param:JsonProperty("extCaptions") val extCaptions: ArrayList<ExtCaptions>? = arrayListOf(),
            @param:JsonProperty("se") val se: Int? = null,
            @param:JsonProperty("ep") val ep: Int? = null,
            @param:JsonProperty("resolution") val resolution: Int? = null,
        ) {
            data class ExtCaptions(
                @param:JsonProperty("lanName") val lanName: String? = null,
                @param:JsonProperty("url") val url: String? = null,
            )
        }
    }
}

data class CinemaTvResponse(
    @param:JsonProperty("streams") val streams: HashMap<String, String>? = null,
    @param:JsonProperty("subtitles") val subtitles: ArrayList<Subtitles>? = arrayListOf(),
) {
    data class Subtitles(
        @param:JsonProperty("language") val language: String? = null,
        @param:JsonProperty("file") val file: Any? = null,
    )
}

data class NepuSearch(
    @param:JsonProperty("data") val data: ArrayList<Data>? = arrayListOf(),
) {
    data class Data(
        @param:JsonProperty("url") val url: String? = null,
        @param:JsonProperty("name") val name: String? = null,
        @param:JsonProperty("type") val type: String? = null,
    )
}

data class CinemaOsSecretKeyRequest(
    val tmdbId: String,
    val seasonId: String,
    val episodeId: String
)

data class CinemaOSReponse(
    val data: CinemaOSReponseData,
    val encrypted: Boolean,
)

data class CinemaOSReponseData(
    val encrypted: String,
    val cin: String,
    val mao: String,
    val salt: String,
)

data class Player4uLinkData(
    val name: String,
    val url: String,
)

data class AdiDewasaSearchResponse(
    @param:JsonProperty("data") val data: ArrayList<AdiDewasaItem>? = arrayListOf(),
    @param:JsonProperty("success") val success: Boolean? = null
)

data class AdiDewasaItem(
    @param:JsonProperty("name") val name: String? = null,
    @param:JsonProperty("title") val title: String? = null,
    @param:JsonProperty("slug") val slug: String? = null,
    @param:JsonProperty("image") val image: String? = null,
    @param:JsonProperty("year") val year: String? = null 
)

// ================== RIVESTREAM DATA CLASSES ==================
data class RiveStreamSource(
    val data: List<String>
)

data class RiveStreamResponse(
    val data: RiveStreamData,
)

data class RiveStreamData(
    val sources: List<RiveStreamSourceData>,
)

data class RiveStreamSourceData(
    val quality: String,
    val url: String,
    val source: String,
    val format: String,
)

// ================== ADIMOVIEBOX DATA CLASSES (NEW) ==================
data class AdimovieboxResponse(
    @param:JsonProperty("data") val data: AdimovieboxData? = null,
)

data class AdimovieboxData(
    @param:JsonProperty("items") val items: ArrayList<AdimovieboxItem>? = arrayListOf(),
    @param:JsonProperty("streams") val streams: ArrayList<AdimovieboxStreamItem>? = arrayListOf(),
    @param:JsonProperty("captions") val captions: ArrayList<AdimovieboxCaptionItem>? = arrayListOf(),
)

data class AdimovieboxItem(
    @param:JsonProperty("subjectId") val subjectId: String? = null,
    @param:JsonProperty("title") val title: String? = null,
    @param:JsonProperty("releaseDate") val releaseDate: String? = null,
    @param:JsonProperty("detailPath") val detailPath: String? = null,
)

data class AdimovieboxStreamItem(
    @param:JsonProperty("id") val id: String? = null,
    @param:JsonProperty("format") val format: String? = null,
    @param:JsonProperty("url") val url: String? = null,
    @param:JsonProperty("resolutions") val resolutions: String? = null,
)

data class AdimovieboxCaptionItem(
    @param:JsonProperty("lanName") val lanName: String? = null,
    @param:JsonProperty("url") val url: String? = null,
)
