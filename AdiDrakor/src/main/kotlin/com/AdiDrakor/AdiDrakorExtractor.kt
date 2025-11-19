package com.AdiDrakor

import android.os.Build
import androidx.annotation.RequiresApi
import com.lagradost.api.Log
import com.AdiDrakor.AdiDrakor.Companion.dramadripAPI
import com.AdiDrakor.AdiDrakor.Companion.nepuAPI
import com.AdiDrakor.AdiDrakor.Companion.nineTvAPI
import com.AdiDrakor.AdiDrakor.Companion.ridomoviesAPI
import com.AdiDrakor.AdiDrakor.Companion.showflixAPI
import com.AdiDrakor.AdiDrakor.Companion.vidsrctoAPI
import com.AdiDrakor.AdiDrakor.Companion.vidsrcxyzAPI
import com.AdiDrakor.AdiDrakor.Companion.zshowAPI
import com.AdiDrakor.AdiDrakor.Companion.moflixAPI
import com.AdiDrakor.AdiDrakor.Companion.emoviesAPI
import com.AdiDrakor.AdiDrakor.Companion.zoechipAPI
import com.AdiDrakor.AdiDrakor.Companion.watch32API
import com.AdiDrakor.AdiDrakor.Companion.soapyAPI
import com.AdiDrakor.AdiDrakor.Companion.multimoviesAPI
import com.AdiDrakor.AdiDrakor.Companion.extramoviesAPI
import com.AdiDrakor.AdiDrakor.Companion.allmovielandAPI
import com.AdiDrakor.AdiDrakor.Companion.mappleTvApi
import com.AdiDrakor.AdiDrakor.Companion.kissKhAPI
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.APIHolder.unixTimeMS 
import com.lagradost.cloudstream3.extractors.helper.AesHelper
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.nicehttp.RequestBodyTypes
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup
import org.json.JSONObject
import org.json.JSONArray
import java.net.URI
import kotlinx.coroutines.delay

object AdiDrakorExtractor : AdiDrakor() {

    // --- 1. IDLIX (UTAMA) ---
    suspend fun invokeIdlix(title: String?, year: Int?, season: Int?, episode: Int?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val fixTitle = title?.createSlug()
        val url = if (season == null) "${AdiDrakor.idlixAPI}/movie/$fixTitle-$year" else "${AdiDrakor.idlixAPI}/episode/$fixTitle-season-$season-episode-$episode"
        invokeWpmovies("Idlix", url, subtitleCallback, callback, encrypt = true)
    }

    // --- 2. MOFLIX ---
    suspend fun invokeMoflix(tmdbId: Int?, season: Int?, episode: Int?, callback: (ExtractorLink) -> Unit) {
        val id = (if (season == null) "tmdb|movie|$tmdbId" else "tmdb|series|$tmdbId").let { base64Encode(it.toByteArray()) }
        val loaderUrl = "$moflixAPI/api/v1/titles/$id?loader=titlePage"
        val url = if (season == null) loaderUrl else {
            val mediaId = app.get(loaderUrl, referer = "$moflixAPI/").parsedSafe<MoflixResponse>()?.title?.id
            "$moflixAPI/api/v1/titles/$mediaId/seasons/$season/episodes/$episode?loader=episodePage"
        }
        val res = app.get(url, referer = "$moflixAPI/").parsedSafe<MoflixResponse>()
        (res?.episode ?: res?.title)?.videos?.filter { it.category.equals("full", true) }?.amap { iframe ->
            val response = app.get(iframe.src ?: return@amap, referer = "$moflixAPI/")
            val host = getBaseUrl(iframe.src)
            val doc = response.document.selectFirst("script:containsData(sources:)")?.data() ?: getAndUnpack(response.text)
            val m3u8 = Regex("file:\\s*\"(.*?m3u8.*?)\"").find(doc)?.groupValues?.getOrNull(1) ?: return@amap
            callback.invoke(newExtractorLink("Moflix", "Moflix [${iframe.name}]", m3u8, INFER_TYPE) {
                this.referer = "$host/"; this.quality = iframe.quality?.filter { it.isDigit() }?.toIntOrNull() ?: Qualities.Unknown.value
            })
        }
    }

    // --- 3. EMOVIES ---
    suspend fun invokeEmovies(title: String?, year: Int?, season: Int?, episode: Int?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val slug = title?.createSlug()
        val url = if (season == null) "$emoviesAPI/watch-$slug-$year-1080p-hd-online-free/watching.html" 
                  else "$emoviesAPI/watch-$slug-season-$season-$year-1080p-hd-online-free.html"
        
        val resResponse = app.get(url)
        if (resResponse.code != 200) return
        val res = resResponse.document
        val id = if (season == null) res.selectFirst("select#selectServer option[sv=oserver]")?.attr("value") 
                 else res.select("div.le-server a").find { Regex("Episode (\\d+)").find(it.text())?.groupValues?.get(1)?.toIntOrNull() == episode }?.attr("href")
        val finalId = id?.substringAfter("id=")?.substringBefore("&") ?: return
        
        val serverResp = app.get("$emoviesAPI/ajax/v4_get_sources?s=oserver&id=$finalId&_=${unixTimeMS}", headers = mapOf("X-Requested-With" to "XMLHttpRequest"))
        val server = serverResp.parsedSafe<EMovieServer>()?.value ?: return
        val scriptResp = app.get(server, referer = "$emoviesAPI/")
        val script = scriptResp.document.selectFirst("script:containsData(sources:)")?.data() ?: return
        
        Regex("sources:\\s*\\[(.*)],").find(script)?.groupValues?.get(1)?.let { tryParseJson<List<EMovieSources>>("[$it]") }?.forEach { source ->
            M3u8Helper.generateM3u8("Emovies", source.file ?: return@forEach, "https://embed.vodstream.xyz/").forEach(callback)
        }
        Regex("tracks:\\s*\\[(.*)],").find(script)?.groupValues?.get(1)?.let { tryParseJson<List<EMovieTraks>>("[$it]") }?.forEach { track ->
             subtitleCallback.invoke(SubtitleFile(track.label ?: "", track.file ?: return@forEach))
        }
    }

    // --- 4. ZOECHIP ---
    suspend fun invokeZoechip(title: String?, year: Int?, season: Int?, episode: Int?, callback: (ExtractorLink) -> Unit) {
        val slug = title?.createSlug()
        val url = if (season == null) "$zoechipAPI/film/${title?.createSlug()}-$year" else "$zoechipAPI/episode/$slug-season-$season-episode-$episode"
        val response = app.get(url)
        val id = response.document.selectFirst("div#show_player_ajax")?.attr("movie-id") ?: return
        val postResponse = app.post("$zoechipAPI/wp-admin/admin-ajax.php", data = mapOf("action" to "lazy_player", "movieID" to id), referer = url, headers = mapOf("X-Requested-With" to "XMLHttpRequest"))
        val server = postResponse.document.selectFirst("ul.nav a:contains(Filemoon)")?.attr("data-server") ?: return
        val serverResponse = app.get(server, referer = "$zoechipAPI/")
        val script = serverResponse.document.select("script:containsData(function(p,a,c,k,e,d))").last()?.data() ?: return
        val m3u8 = Regex("file:\\s*\"(.*?m3u8.*?)\"").find(getAndUnpack(script))?.groupValues?.getOrNull(1) ?: return
        M3u8Helper.generateM3u8("Zoechip", m3u8, "${getBaseUrl(serverResponse.url)}/").forEach(callback)
    }

    // --- 5. WATCH32 ---
    suspend fun invokeWatch32(title: String?, season: Int?, episode: Int?, year: Int?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        if (title.isNullOrBlank()) return
        val searchUrl = "$watch32API/search/${title.trim().replace(" ", "-")}"
        val matchedElement = app.get(searchUrl).document.select("div.flw-item").firstOrNull { 
            it.selectFirst("h2.film-name a")?.text()?.contains(title, true) == true &&
            (season == null || it.selectFirst("span.fdi-type")?.text()?.trim()?.equals("TV", true) == true)
        }?.selectFirst("h2.film-name a") ?: return
        
        val infoId = (watch32API + matchedElement.attr("href")).substringAfterLast("-")
        if (season != null) {
            val seasonId = app.get("$watch32API/ajax/season/list/$infoId").document.select("div.dropdown-menu a").find { it.text().contains("Season $season", true) }?.attr("data-id") ?: return
            val dataId = app.get("$watch32API/ajax/season/episodes/$seasonId").document.select("li.nav-item a").find { it.text().contains("Eps $episode:", true) }?.attr("data-id") ?: return
            app.get("$watch32API/ajax/episode/servers/$dataId").document.select("li.nav-item a").forEach { source ->
                val iframeUrl = app.get("$watch32API/ajax/episode/sources/${source.attr("data-id")}").parsedSafe<Watch32>()?.link ?: return@forEach
                loadExtractor(iframeUrl, subtitleCallback, callback)
            }
        } else {
             app.get("$watch32API/ajax/episode/list/$infoId").document.select("li.nav-item a").forEach { ep ->
                 val iframeUrl = app.get("$watch32API/ajax/episode/sources/${ep.attr("data-id")}").parsedSafe<Watch32>()?.link ?: return@forEach
                 loadExtractor(iframeUrl, subtitleCallback, callback)
             }
        }
    }
    
    // --- 6. VIDZEE ---
    suspend fun invokeVidzee(id: Int?, season: Int?, episode: Int?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val keyBytes = "6966796f75736372617065796f75617265676179000000000000000000000000".chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        for (sr in 1..5) {
            try {
                val apiUrl = if (season == null) "https://player.vidzee.wtf/api/server?id=$id&sr=$sr" else "https://player.vidzee.wtf/api/server?id=$id&sr=$sr&ss=$season&ep=$episode"
                val json = JSONObject(app.get(apiUrl).text)
                val urls = json.optJSONArray("url") ?: JSONArray()
                for (i in 0 until urls.length()) {
                    val obj = urls.getJSONObject(i)
                    val encryptedLink = obj.optString("link")
                    if (encryptedLink.isNotBlank()) {
                        val finalUrl = try { decryptVidzeeUrl(encryptedLink, keyBytes) } catch (e: Exception) { encryptedLink }
                        callback.invoke(newExtractorLink("VidZee", "VidZee ${obj.optString("name")} (${obj.optString("lang")})", finalUrl, if (obj.optString("type") == "hls") ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO) {
                            referer = "https://core.vidzee.wtf/"; quality = Qualities.P1080.value
                        })
                    }
                }
                val subs = json.optJSONArray("tracks") ?: JSONArray()
                for (i in 0 until subs.length()) subtitleCallback(newSubtitleFile(subs.getJSONObject(i).optString("lang"), subs.getJSONObject(i).optString("url")))
            } catch (e: Exception) {}
        }
    }

    // --- 7. SOAPY ---
    suspend fun invokeSoapy(tmdbId: Int?, season: Int?, episode: Int?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        listOf("juliet", "romio").forEach { player ->
            val url = if (season == null) "$soapyAPI/embed/movies.php?tmdbid=$tmdbId&player=$player" else "$soapyAPI/embed/series.php?tmdbid=$tmdbId&season=$season&episode=$episode&player=$player"
            val iframe = app.get(url).document.select("iframe").attr("src")
            loadExtractor(iframe, "$soapyAPI/", subtitleCallback, callback)
        }
    }

    // --- 8. MULTIMOVIES ---
    suspend fun invokeMultimovies(title: String?, season: Int?, episode: Int?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val fixTitle = title?.createSlug()
        val url = if (season == null) "$multimoviesAPI/movies/$fixTitle" else "$multimoviesAPI/episodes/$fixTitle-${season}x${episode}"
        invokeWpmovies("Multimovies", url, subtitleCallback, callback)
    }

    // --- 9. EXTRAMOVIES ---
    suspend fun invokeExtramovies(imdbId: String?, season: Int?, episode: Int?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        app.get("$extramoviesAPI/search/$imdbId").document.select("h3 a").amap { 
            val link = it.attr("href")
            app.get(link).document.select("div.entry-content a.maxbutton-8").forEach { btn ->
                val href = btn.select("a").attr("href")
                loadExtractor(href, subtitleCallback, callback)
            }
        }
    }
    
    // --- 10. MAPPLETV ---
    suspend fun invokeMappleTv(tmdbId: Int?, title: String?, season: Int?, episode: Int?, callback: (ExtractorLink) -> Unit) {
        val fixtitle = "$tmdbId-${title?.replace(" ","-")}"
        val url = if (season == null) "$mappleTvApi/watch/movie/$fixtitle" else "$mappleTvApi/watch/tv/$season-$episode/$fixtitle"
        val headers = mapOf("next-action" to "40c2896f5f22d9d6342e5a6d8f4d8c58d69654bacd", "Referer" to mappleTvApi)
        val session = JSONObject(app.get("https://enc-dec.app/api/enc-mapple").text).optJSONObject("result")?.optString("sessionId") ?: return
        listOf("mapple","alfa").forEach {
            try {
                val payload = if(season==null) """[{"mediaId":$tmdbId,"mediaType":"movie","tv_slug":"","source":"$it","useFallbackVideo":false,"sessionId":"$session"}]""" 
                              else """[{"mediaId":$tmdbId,"mediaType":"tv","tv_slug":"$season-$episode","source":"$it","useFallbackVideo":false,"sessionId":"$session"}]"""
                val response = app.post(url, json = payload, headers = headers).text
                val streamUrl = JSONObject(response.split("\n")[1].replace("1:","")).optJSONObject("data")?.getString("stream_url") ?: return@forEach
                M3u8Helper.generateM3u8("MappleTv [$it]", streamUrl, mappleTvApi, headers = mapOf("Referer" to mappleTvApi)).forEach(callback)
            } catch (e: Exception) {}
        }
    }
    
    // --- 11. ALLMOVIELAND ---
    suspend fun invokeAllMovieland(imdbId: String?, season: Int?, episode: Int?, callback: (ExtractorLink) -> Unit) {
        try {
            val playerScript = app.get("https://allmovieland.link/player.js?v=60%20128").text
            val host = Regex("const AwsIndStreamDomain.*'(.*)';").find(playerScript)?.groupValues?.get(1) ?: return
            val resData = app.get("$host/play/$imdbId", referer = "$allmovielandAPI/").document.selectFirst("script:containsData(playlist)")?.data()?.substringAfter("{")?.substringBefore(";")?.substringBefore(")") ?: return
            val json = tryParseJson<AllMovielandPlaylist>("{$resData}") ?: return
            val headers = mapOf("X-CSRF-TOKEN" to "${json.key}")
            val jsonfile = if (json.file?.startsWith("http") == true) json.file else host + json.file
            val cleanedJson = app.get(jsonfile, headers = headers, referer = "$allmovielandAPI/").text.replace(Regex(""",\s*\/"""), "").replace(Regex(",\\s*\\[\\s*]"), "")
            
            val servers = tryParseJson<ArrayList<AllMovielandServer>>(cleanedJson)?.let { list ->
                if (season == null) list.mapNotNull { it.file?.let { file -> file to it.title.orEmpty() } }
                else list.find { it.id == season.toString() }?.folder?.find { it.episode == episode.toString() }?.folder?.mapNotNull { it.file?.let { file -> file to it.title.orEmpty() } }
            } ?: return

            servers.amap { (server, lang) ->
                val playlistUrl = app.post("$host/playlist/$server.txt", headers = headers, referer = "$allmovielandAPI/").text
                callback.invoke(newExtractorLink("AllMovieLand-$lang", "AllMovieLand-$lang", playlistUrl, ExtractorLinkType.M3U8))
            }
        } catch (e: Exception) {}
    }

    // --- EXISTING & BACKUP SOURCES ---

    suspend fun invokeDramadrip(imdbId: String?, season: Int?, episode: Int?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val link = app.get("$dramadripAPI/?s=$imdbId").document.selectFirst("article > a")?.attr("href") ?: return
        val doc = app.get(link).document
        if (season != null) {
            doc.select("div.file-spoiler h2").filter { "season $season ".lowercase() in it.text().lowercase() }.flatMap { it.nextElementSibling()?.select("a") ?: emptyList() }.amap { 
                val epHref = app.get(it.attr("href")).document.select("h3 > a").find { a -> a.text().contains("Episode $episode") }?.attr("href")
                if (epHref != null) loadExtractor(if("safelink" in epHref) cinematickitBypass(epHref)?:epHref else epHref, subtitleCallback, callback)
            }
        }
    }
    
    suspend fun invokeRidomovies(tmdbId: Int?, imdbId: String?, season: Int?, episode: Int?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val search = app.get("$ridomoviesAPI/core/api/search?q=$imdbId").parsedSafe<RidoSearch>()
        val slug = search?.data?.items?.find { it.contentable?.tmdbId == tmdbId || it.contentable?.imdbId == imdbId }?.slug ?: return
        val id = if (season != null) app.get("$ridomoviesAPI/tv/$slug/season-$season/episode-$episode").text.substringAfterLast("postid\":\"").substringBefore("\"") else slug
        app.get("$ridomoviesAPI/core/api/${if (season==null) "movies" else "episodes"}/$id/videos").parsedSafe<RidoResponses>()?.data?.forEach { 
            loadExtractor(Jsoup.parse(it.url?:"").select("iframe").attr("data-src"), "$ridomoviesAPI/", subtitleCallback, callback) 
        }
    }

    suspend fun invokeVidsrccc(id: Int?, imdbId: String?, season: Int?, episode: Int?, callback: (ExtractorLink) -> Unit) {
        val url = if (season == null) "$vidsrctoAPI/v2/embed/movie/$id" else "$vidsrctoAPI/v2/embed/tv/$id/$season/$episode"
        val doc = app.get(url).text; val v = Regex("var v = \"(.*?)\";").find(doc)?.groupValues?.get(1) ?: return
        val userId = Regex("var userId = \"(.*?)\";").find(doc)?.groupValues?.get(1) ?: ""
        val vrf = generateVrfAES(id.toString(), userId)
        val api = if (season == null) "$vidsrctoAPI/api/$id/servers?id=$id&type=movie&v=$v&vrf=$vrf&imdbId=$imdbId" else "$vidsrctoAPI/api/$id/servers?id=$id&type=tv&season=$season&episode=$episode&v=$v&vrf=$vrf&imdbId=$imdbId"
        app.get(api).parsedSafe<Vidsrcccservers>()?.data?.forEach { 
            app.get("$vidsrctoAPI/api/source/${it.hash}").parsedSafe<Vidsrcccm3u8>()?.data?.source?.let { src ->
                 callback.invoke(newExtractorLink("Vidsrc", "Vidsrc ${it.name}", src)) 
            }
        }
    }

    suspend fun invokeShowflix(title: String?, year: Int?, season: Int?, episode: Int?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val body = """{"where":{"name":{"${'$'}regex":"$title","${'$'}options":"i"}},"order":"-createdAt","_method":"GET","_ApplicationId":"SHOWFLIXAPPID","_JavaScriptKey":"SHOWFLIXMASTERKEY","_ClientVersion":"js3.4.1","_InstallationId":"60f6b1a7-8860-4edf-b255-6bc465b6c704"}""".toRequestBody("text/plain".toMediaTypeOrNull())
        val res = app.post("https://parse.showflix.sbs/parse/classes/${if(season==null)"moviesv2" else "seriesv2"}", requestBody = body).text
        val links = if(season==null) tryParseJson<ShowflixSearchMovies>(res)?.resultsMovies?.find { it.name?.contains(title?:"",true)==true }?.embedLinks?.values
                    else tryParseJson<ShowflixSearchSeries>(res)?.resultsSeries?.find { it.seriesName?.contains(title?:"",true)==true }?.streamwish?.get("Season $season")?.getOrNull(episode?:0)?.let { listOf(it) }
        links?.forEach { loadExtractor(if(it.startsWith("http")) it else "https://embedwish.com/e/$it", subtitleCallback, callback) }
    }

    suspend fun invokeNepu(title: String?, year: Int?, season: Int?, episode: Int?, callback: (ExtractorLink) -> Unit) {
        val data = app.get("$nepuAPI/ajax/posts?q=$title", headers = mapOf("X-Requested-With" to "XMLHttpRequest")).parsedSafe<NepuSearch>()?.data
        val url = data?.find { it.name.equals(title, true) }?.url ?: return
        val finalUrl = if (season == null) url else "$url/season/$season/episode/$episode"
        val id = app.get(fixUrl(finalUrl, nepuAPI)).document.selectFirst("a[data-embed]")?.attr("data-embed") ?: return
        val m3u8 = JSONObject(app.post("$nepuAPI/ajax/embed", data = mapOf("id" to id), headers = mapOf("X-Requested-With" to "XMLHttpRequest"), referer = finalUrl).text).optString("url")
        if(m3u8.isNotEmpty()) callback.invoke(newExtractorLink("Nepu", "Nepu", m3u8, INFER_TYPE))
    }
    
    suspend fun invokeNinetv(id: Int?, season: Int?, episode: Int?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val url = if (season == null) "$nineTvAPI/movie/$id" else "$nineTvAPI/tv/$id-$season-$episode"
        loadExtractor(app.get(url).document.select("iframe").attr("src"), subtitleCallback, callback)
    }

    suspend fun invokeVidSrcXyz(id: String?, season: Int?, episode: Int?, callback: (ExtractorLink) -> Unit) {
        val url = if (season == null) "$vidsrcxyzAPI/embed/movie?imdb=$id" else "$vidsrcxyzAPI/embed/tv?imdb=$id&season=$season&episode=$episode"
        val src = app.get(url).document.select("iframe").attr("src"); if(src.isEmpty()) return
        val rcp = Regex("""src:\s*'(.*?)'""").find(app.get(src).text)?.groupValues?.get(1) ?: return
        val m3u8 = Regex("""file:"(.*?)"|src:\s*'(.*?)'""").find(app.get(getBaseUrl(src)+rcp).text)?.groupValues?.let { it.getOrNull(1)?:it.getOrNull(2) }
        if(!m3u8.isNullOrEmpty()) callback.invoke(newExtractorLink("VidSrcXYZ", "VidSrcXYZ", m3u8, ExtractorLinkType.M3U8))
    }
    
    suspend fun invokeZshow(title: String?, year: Int?, season: Int?, episode: Int?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
         val url = if(season==null) "$zshowAPI/movie/${title?.createSlug()}-$year" else "$zshowAPI/episode/${title?.createSlug()}-season-$season-episode-$episode"
         invokeWpmovies("ZShow", url, subtitleCallback, callback, encrypt = true)
    }

    suspend fun invokeKisskhAsia(id: Int?, season: Int?, episode: Int?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        if (id == null) return
        val url = if (season != null && season > 1) "https://hlscdn.xyz/e/$id-$season-${episode.toString().padStart(2,'0')}" else "https://hlscdn.xyz/e/$id-${episode.toString().padStart(2,'0')}"
        val token = Regex("window\\.kaken=\"(.*?)\"").find(app.get(url, headers = mapOf("Referer" to "https://hlscdn.xyz/")).text)?.groupValues?.get(1) ?: return
        val json = JSONObject(app.post("https://hlscdn.xyz/api", headers = mapOf("Referer" to "https://hlscdn.xyz/"), requestBody = token.toRequestBody("text/plain".toMediaTypeOrNull())).text)
        val sources = json.optJSONArray("sources")
        if (sources != null) {
            for (i in 0 until sources.length()) {
                val src = sources.getJSONObject(i)
                // [PERBAIKAN PENTING] Referer dipindahkan ke dalam lambda newExtractorLink
                callback.invoke(newExtractorLink("KisskhAsia", "KisskhAsia", src.optString("file"), INFER_TYPE) {
                    this.referer = "https://hlscdn.xyz/"
                })
            }
        }
    }
    
    suspend fun invokeKisskh(title: String?, season: Int?, episode: Int?, lastSeason: Int?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val slug = title?.createSlug() ?: return
        val type = if (season == null) "2" else "1"
        val searchResponse = app.get("$kissKhAPI/api/DramaList/Search?q=$title&type=$type", referer = "$kissKhAPI/")
        if (searchResponse.code != 200) return
        val res = tryParseJson<ArrayList<KisskhResults>>(searchResponse.text) ?: return
        
        val pair = if (res.size == 1) {
            Pair(res.first().id, res.first().title)
        } else {
            val data = res.find {
                val slugTitle = it.title.createSlug() ?: return@find false
                if (season == null) slugTitle == slug else slugTitle.contains(slug) && it.title?.contains("Season $season", true) == true
            } ?: res.find { it.title.equals(title) }
            Pair(data?.id, data?.title)
        }
        
        val id = pair.first ?: return
        val detailResponse = app.get("$kissKhAPI/api/DramaList/Drama/$id?isq=false", referer = "$kissKhAPI/")
        val resDetail = detailResponse.parsedSafe<KisskhDetail>() ?: return
        val epsId = if (season == null) resDetail.episodes?.firstOrNull()?.id else resDetail.episodes?.find { it.number == episode }?.id
        if (epsId == null) return
        
        val kkey = app.get("$kissKhAPI/api/DramaList/Episode/$epsId.png?err=false&ts=&time=", timeout = 10000).parsedSafe<KisskhKey>()?.key ?: ""
        val sourcesResponse = app.get("$kissKhAPI/api/DramaList/Episode/$epsId.png?err=false&ts=&time=&kkey=$kkey", referer = "$kissKhAPI/")
        sourcesResponse.parsedSafe<KisskhSources>()?.let { source ->
            listOf(source.video, source.thirdParty).forEach { link ->
                if (link?.contains(".m3u8") == true || link?.contains(".mp4") == true) {
                    // [PERBAIKAN PENTING] Referer dipindahkan ke dalam lambda newExtractorLink
                    callback.invoke(newExtractorLink("Kisskh", "Kisskh", fixUrl(link, kissKhAPI), INFER_TYPE) {
                        this.referer = kissKhAPI
                        this.quality = Qualities.P720.value
                        this.headers = mapOf("Origin" to kissKhAPI)
                    })
                }
            }
        }
    }

    // --- HELPER ---
    private suspend fun invokeWpmovies(name: String, url: String, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit, encrypt: Boolean = false) {
        val res = app.get(url ?: return)
        val referer = getBaseUrl(res.url)
        val doc = res.document
        doc.select("ul#playeroptionsul > li").map { Triple(it.attr("data-post"), it.attr("data-nume"), it.attr("data-type")) }.amap { (id, nume, type) ->
            val json = app.post(url = "${getBaseUrl(url)}/wp-admin/admin-ajax.php", data = mapOf("action" to "doo_player_ajax", "post" to id, "nume" to nume, "type" to type), referer = url, headers = mapOf("X-Requested-With" to "XMLHttpRequest")).text
            val source = tryParseJson<ResponseHash>(json)?.embed_url ?: return@amap
            val finalUrl = if(encrypt) AesHelper.cryptoAESHandler(source, generateWpKey(tryParseJson<ResponseHash>(json)?.key?:"", tryParseJson<ZShowEmbed>(source)?.meta?:"").toByteArray(), false) else source
            val fixedUrl = if (finalUrl?.contains("youtube") == false) Jsoup.parse(finalUrl).select("iframe").attr("src").ifEmpty { finalUrl } else return@amap
            // Menggunakan contains agar lebih fleksibel mendeteksi jeniusplay
            if(fixedUrl.contains("jeniusplay", ignoreCase = true)) Jeniusplay2().getUrl(fixedUrl, "${getBaseUrl(url)}/", subtitleCallback, callback) else loadExtractor(fixedUrl, subtitleCallback, callback)
        }
    }
}
