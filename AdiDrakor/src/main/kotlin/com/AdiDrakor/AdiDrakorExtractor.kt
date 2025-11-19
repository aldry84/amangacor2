package com.AdiDrakor

import android.os.Build
import androidx.annotation.RequiresApi
import com.AdiDrakor.AdiDrakor.Companion.kissKhAPI
import com.AdiDrakor.AdiDrakor.Companion.nepuAPI
import com.AdiDrakor.AdiDrakor.Companion.ridomoviesAPI
import com.AdiDrakor.AdiDrakor.Companion.showflixAPI
import com.AdiDrakor.AdiDrakor.Companion.vidsrctoAPI
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.extractors.helper.AesHelper
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.nicehttp.RequestBodyTypes
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup
import org.json.JSONObject // [PERBAIKAN] Import penting untuk KissKhAsia

object AdiDrakorExtractor : AdiDrakor() {

    // --- 1. IDLIX (UTAMA) ---
    suspend fun invokeIdlix(
        title: String? = null,
        year: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val fixTitle = title?.createSlug()
        val url = if (season == null) {
            "${AdiDrakor.idlixAPI}/movie/$fixTitle-$year"
        } else {
            "${AdiDrakor.idlixAPI}/episode/$fixTitle-season-$season-episode-$episode"
        }
        invokeWpmovies("Idlix", url, subtitleCallback, callback, encrypt = true)
    }

    // --- 2. KISSKH (Raja Drama Asia) ---
    suspend fun invokeKisskh(
        title: String? = null,
        season: Int? = null,
        episode: Int? = null,
        lastSeason: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val slug = title.createSlug() ?: return
        val type = if (season == null) "2" else "1"
        val searchResponse = app.get("$kissKhAPI/api/DramaList/Search?q=$title&type=$type", referer = "$kissKhAPI/")
        if (searchResponse.code != 200) return
        val res = tryParseJson<ArrayList<KisskhResults>>(searchResponse.text) ?: return
        
        val (id, contentTitle) = if (res.size == 1) {
            res.first().id to res.first().title
        } else {
            val data = res.find {
                val slugTitle = it.title.createSlug() ?: return@find false
                when {
                    season == null -> slugTitle == slug
                    lastSeason == 1 -> slugTitle.contains(slug)
                    else -> slugTitle.contains(slug) && it.title?.contains("Season $season", true) == true
                }
            } ?: res.find { it.title.equals(title) }
            data?.id to data?.title
        }
        
        val detailResponse = app.get("$kissKhAPI/api/DramaList/Drama/$id?isq=false", referer = "$kissKhAPI/")
        val resDetail = detailResponse.parsedSafe<KisskhDetail>() ?: return
        val epsId = if (season == null) resDetail.episodes?.first()?.id else resDetail.episodes?.find { it.number == episode }?.id ?: return
        
        // Menggunakan constant ID sederhana sebagai pengganti BuildConfig
        val kkeyResponse = app.get("https://kisskh.ovh/api/DramaList/Episode/$epsId.png?err=false&ts=&time=", timeout = 10000)
        val kkey = kkeyResponse.parsedSafe<KisskhKey>()?.key ?: ""
        
        val sourcesResponse = app.get("$kissKhAPI/api/DramaList/Episode/$epsId.png?err=false&ts=&time=&kkey=$kkey", referer = "$kissKhAPI/")
        sourcesResponse.parsedSafe<KisskhSources>()?.let { source ->
            listOf(source.video, source.thirdParty).amap { link ->
                val safeLink = link ?: return@amap
                if (safeLink.contains(".m3u8") || safeLink.contains(".mp4")) {
                    callback.invoke(newExtractorLink("Kisskh", "Kisskh", fixUrl(safeLink, kissKhAPI), INFER_TYPE) {
                        referer = kissKhAPI
                        quality = Qualities.P720.value
                        headers = mapOf("Origin" to kissKhAPI)
                    })
                }
            }
        }
        val subResponse = app.get("$kissKhAPI/api/Sub/$epsId?kkey=$kkey")
        tryParseJson<List<KisskhSubtitle>>(subResponse.text)?.forEach { sub ->
            subtitleCallback.invoke(SubtitleFile(sub.label ?: "Unknown", sub.src ?: return@forEach))
        }
    }

    // --- 3. DRAMADRIP (Spesialis Drama) ---
    suspend fun invokeDramadrip(
        imdbId: String? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val dramadripAPI = AdiDrakor.dramadripAPI
        val link = app.get("$dramadripAPI/?s=$imdbId").document.selectFirst("article > a")?.attr("href") ?: return
        val document = app.get(link).document
        
        if (season != null && episode != null) {
            val seasonLink = document.select("div.file-spoiler h2").filter { 
                "season $season ".lowercase() in it.text().trim().lowercase() 
            }.flatMap { h2 ->
                h2.nextElementSibling()?.select("a")?.mapNotNull { it.attr("href") } ?: emptyList()
            }

            seasonLink.amap { seasonUrl ->
                val episodeDoc = app.get(seasonUrl).document
                val episodeHref = episodeDoc.select("h3 > a,div.wp-block-button a")
                    .firstOrNull { it.text().contains("Episode $episode") }?.attr("href") ?: return@amap
                
                val finalUrl = if ("safelink=" in episodeHref) cinematickitBypass(episodeHref) else if ("unblockedgames" in episodeHref) bypassHrefli(episodeHref) else episodeHref
                if (finalUrl != null) loadExtractor(finalUrl, subtitleCallback, callback)
            }
        } else {
            document.select("div.file-spoiler a").amap {
                val doc = app.get(it.attr("href")).document
                doc.select("a.wp-element-button").amap { source ->
                    val rawHref = source.attr("href")
                    val finalUrl = if ("safelink=" in rawHref) cinematickitBypass(rawHref) else if ("unblockedgames" in rawHref) bypassHrefli(rawHref) else rawHref
                    if (finalUrl != null) loadExtractor(finalUrl, subtitleCallback, callback)
                }
            }
        }
    }

    // --- 4. RIDOMOVIES (Cepat & Bersih) ---
    suspend fun invokeRidomovies(
        tmdbId: Int? = null,
        imdbId: String? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val searchResponse = app.get("$ridomoviesAPI/core/api/search?q=$imdbId")
        if (searchResponse.code != 200) return
        val mediaSlug = searchResponse.parsedSafe<RidoSearch>()?.data?.items
            ?.find { it.contentable?.tmdbId == tmdbId || it.contentable?.imdbId == imdbId }?.slug ?: return

        val id = season?.let {
            val episodeUrl = "$ridomoviesAPI/tv/$mediaSlug/season-$it/episode-$episode"
            val episodeResponse = app.get(episodeUrl)
            if (episodeResponse.code != 200) return@let null
            episodeResponse.text.substringAfterLast("""postid\":\"""").substringBefore("\"")
        } ?: mediaSlug

        val url = "$ridomoviesAPI/core/api/${if (season == null) "movies" else "episodes"}/$id/videos"
        app.get(url).parsedSafe<RidoResponses>()?.data?.amap { link ->
            val iframe = Jsoup.parse(link.url ?: return@amap).select("iframe").attr("data-src")
            if (iframe.startsWith("https://closeload.top")) {
                 // Skip closeload if complex, or impl unpacker. For now, rely on others.
            } else {
                loadExtractor(iframe, "$ridomoviesAPI/", subtitleCallback, callback)
            }
        }
    }

    // --- 5. SHOWFLIX (Parse API - Cepat) ---
    suspend fun invokeShowflix(
        title: String? = null,
        year: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val classes = if (season == null) "moviesv2" else "seriesv2"
        val body = """{"where":{"name":{"${'$'}regex":"$title","${'$'}options":"i"}},"order":"-createdAt","_method":"GET","_ApplicationId":"SHOWFLIXAPPID","_JavaScriptKey":"SHOWFLIXMASTERKEY","_ClientVersion":"js3.4.1","_InstallationId":"60f6b1a7-8860-4edf-b255-6bc465b6c704"}"""
            .toRequestBody("text/plain".toMediaTypeOrNull())

        val response = app.post("https://parse.showflix.sbs/parse/classes/$classes", requestBody = body)
        if (response.code != 200) return

        val data = response.text
        val iframes = if (season == null) {
            val result = tryParseJson<ShowflixSearchMovies>(data)?.resultsMovies?.find {
                it.name.equals("$title ($year)", true) || it.name.equals(title, true)
            }
            val links = result?.embedLinks ?: return
            listOf(
                "https://embedwish.com/e/${links["streamwish"]}",
                "https://filelions.to/v/${links["filelions"]}.html",
                "https://rubyvidhub.com/embed-${links["streamruby"]}.html"
            )
        } else {
            val result = tryParseJson<ShowflixSearchSeries>(data)?.resultsSeries?.find { it.seriesName.equals(title, true) }
            val seasKey = "Season $season"
            val epIdx = episode ?: 0
            listOf(
                result?.streamwish?.get(seasKey)?.getOrNull(epIdx),
                result?.filelions?.get(seasKey)?.getOrNull(epIdx),
                result?.streamruby?.get(seasKey)?.getOrNull(epIdx)
            )
        }

        iframes.filterNotNull().amap { iframe ->
            loadExtractor(iframe, "$showflixAPI/", subtitleCallback, callback)
        }
    }

    // --- 6. NINETV (Cadangan Handal) ---
    suspend fun invokeNinetv(
        tmdbId: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val url = if (season == null) "${AdiDrakor.nineTvAPI}/movie/$tmdbId" else "${AdiDrakor.nineTvAPI}/tv/$tmdbId-$season-$episode"
        val response = app.get(url, referer = "https://pressplay.top/")
        if (response.code != 200) return
        val iframe = response.document.selectFirst("iframe")?.attr("src") ?: return
        loadExtractor(iframe, "${AdiDrakor.nineTvAPI}/", subtitleCallback, callback)
    }

    // --- 7. NEPU (Naik Daun) ---
    suspend fun invokeNepu(
        title: String? = null,
        year: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit
    ) {
        val slug = title?.createSlug() ?: return
        val headers = mapOf("X-Requested-With" to "XMLHttpRequest")
        val data = app.get("$nepuAPI/ajax/posts?q=$title", headers = headers, referer = "$nepuAPI/").parsedSafe<NepuSearch>()?.data

        val media = data?.find { it.url?.startsWith(if (season == null) "/movie/$slug-$year-" else "/serie/$slug-$year-") == true }
            ?: data?.find { it.name.equals(title, true) && it.type == if (season == null) "Movie" else "Serie" }

        val mediaUrl = media?.url ?: return
        val fullMediaUrl = if (season == null) mediaUrl else "$mediaUrl/season/$season/episode/$episode"
        val pageRes = app.get(fixUrl(fullMediaUrl, nepuAPI))
        val dataId = pageRes.document.selectFirst("a[data-embed]")?.attr("data-embed") ?: return
        val res = app.post("$nepuAPI/ajax/embed", data = mapOf("id" to dataId), referer = fullMediaUrl, headers = headers).text
        val m3u8 = "(http[^\"]+)".toRegex().find(res)?.groupValues?.get(1) ?: return

        callback.invoke(newExtractorLink("Nepu", "Nepu", url = m3u8, INFER_TYPE) {
            this.referer = "$nepuAPI/"
            this.quality = Qualities.P1080.value
        })
    }

    // --- 8. VIDSRCCC (Stabil) ---
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun invokeVidsrccc(
        id: Int? = null,
        imdbId: String? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit,
    ) {
        val url = if (season == null) "$vidsrctoAPI/v2/embed/movie/$id?autoPlay=false" else "$vidsrctoAPI/v2/embed/tv/$id/$season/$episode?autoPlay=false"
        val doc = app.get(url).text
        val regex = Regex("""var\s+(\w+)\s*=\s*(?:"([^"]*)"|(\w+));""")
        val variables = mutableMapOf<String, String>()
        regex.findAll(doc).forEach { match -> variables[match.groupValues[1]] = match.groupValues[2].ifEmpty { match.groupValues[3] } }
        
        val vvalue = variables["v"] ?: ""
        val userId = variables["userId"] ?: ""
        val movieId = variables["movieId"] ?: ""
        val movieType = variables["movieType"] ?: ""
        val vrf = generateVrfAES(movieId, userId) // Dari Utils

        val apiurl = if (season == null) {
            "$vidsrctoAPI/api/$id/servers?id=$id&type=$movieType&v=$vvalue=&vrf=$vrf&imdbId=$imdbId"
        } else {
            "$vidsrctoAPI/api/$id/servers?id=$id&type=$movieType&season=$season&episode=$episode&v=$vvalue&vrf=${vrf}&imdbId=$imdbId"
        }
        
        app.get(apiurl).parsedSafe<Vidsrcccservers>()?.data?.forEach {
            val servername = it.name
            val iframe = app.get("$vidsrctoAPI/api/source/${it.hash}").parsedSafe<Vidsrcccm3u8>()?.data?.source
            if (iframe != null && !iframe.contains(".vidbox")) {
                callback.invoke(newExtractorLink("Vidsrc", "⌜ Vidsrc ⌟ | [$servername]", iframe) {
                    this.quality = if (servername.contains("4K", true)) Qualities.P2160.value else Qualities.P1080.value
                    this.referer = vidsrctoAPI
                })
            }
        }
    }
    
    // --- 9. ZSHOW (WordPress HD) ---
    suspend fun invokeZshow(
        title: String? = null,
        year: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val fixTitle = title.createSlug()
        val url = if (season == null) {
            "${AdiDrakor.zshowAPI}/movie/$fixTitle-$year"
        } else {
            "${AdiDrakor.zshowAPI}/episode/$fixTitle-season-$season-episode-$episode"
        }
        invokeWpmovies("ZShow", url, subtitleCallback, callback, encrypt = true)
    }

    // --- 10. VIDSRCXYZ (Alternatif) ---
    suspend fun invokeVidSrcXyz(
        id: String? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit
    ) {
        val url = if (season == null) "${AdiDrakor.vidsrcxyzAPI}/embed/movie?imdb=$id" else "${AdiDrakor.vidsrcxyzAPI}/embed/tv?imdb=$id&season=$season&episode=$episode"
        val iframeUrl = app.get(url).document.select("iframe").attr("src")
        if (iframeUrl.isEmpty()) return
        val host = getBaseUrl(iframeUrl)
        
        val doc = app.get(iframeUrl).text
        val rcpUrl = Regex("""src:\s*'(.*?)'""").find(doc)?.groupValues?.get(1) ?: return
        val finalDoc = app.get(host + rcpUrl).text
        
        val m3u8Regex = Regex("""file:"(.*?)"|src:\s*'(.*?)'""")
        val m3u8 = m3u8Regex.find(finalDoc)?.groupValues?.let { it.getOrNull(1) ?: it.getOrNull(2) } ?: return
        
        if (m3u8.isNotEmpty()) {
            callback.invoke(newExtractorLink("VidsrcXYZ", "VidsrcXYZ", m3u8, ExtractorLinkType.M3U8) {
                this.referer = host
                this.quality = Qualities.P1080.value
            })
        }
    }

    // --- KISSKH ASIA (Backup) ---
    suspend fun invokeKisskhAsia(
        tmdbId: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        if (tmdbId == null) return
        val url = when {
            season != null && season > 1 -> "https://hlscdn.xyz/e/$tmdbId-$season-${episode?.toString()?.padStart(2, '0')}"
            else -> "https://hlscdn.xyz/e/$tmdbId-${episode?.toString()?.padStart(2, '0')}"
        }
        val headers = mapOf("Referer" to "https://hlscdn.xyz/", "X-Requested-With" to "XMLHttpRequest")
        val responseText = app.get(url, headers = headers).text
        val token = Regex("window\\.kaken=\"(.*?)\"").find(responseText)?.groupValues?.getOrNull(1) ?: return
        val json = JSONObject(app.post("https://hlscdn.xyz/api", headers = headers, requestBody = token.toRequestBody("text/plain".toMediaTypeOrNull())).text)
        
        val sources = json.optJSONArray("sources")
        if (sources != null) {
            for (i in 0 until sources.length()) {
                val src = sources.getJSONObject(i)
                callback.invoke(newExtractorLink("KisskhAsia", "KisskhAsia - ${src.optString("label")}", src.optString("file")) {
                    this.referer = "https://hlscdn.xyz/"
                })
            }
        }
    }

    // --- HELPER UNTUK IDLIX/ZSHOW ---
    private suspend fun invokeWpmovies(
        name: String? = null,
        url: String? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
        fixIframe: Boolean = false,
        encrypt: Boolean = false,
        hasCloudflare: Boolean = false,
        interceptor: Interceptor? = null,
    ) {
        val res = app.get(url ?: return)
        val referer = getBaseUrl(res.url)
        val document = res.document
        document.select("ul#playeroptionsul > li").map { Triple(it.attr("data-post"), it.attr("data-nume"), it.attr("data-type")) }.amap { (id, nume, type) ->
            val json = app.post(
                url = "$referer/wp-admin/admin-ajax.php",
                data = mapOf("action" to "doo_player_ajax", "post" to id, "nume" to nume, "type" to type),
                headers = mapOf("Accept" to "*/*", "X-Requested-With" to "XMLHttpRequest"),
                referer = url
            ).text
            val source = tryParseJson<ResponseHash>(json)?.let {
                when {
                    encrypt -> {
                        val meta = tryParseJson<ZShowEmbed>(it.embed_url)?.meta ?: return@amap
                        val key = generateWpKey(it.key ?: return@amap, meta)
                        AesHelper.cryptoAESHandler(it.embed_url, key.toByteArray(), false)?.replace("\"", "")?.replace("\\", "")
                    }
                    fixIframe -> Jsoup.parse(it.embed_url).select("IFRAME").attr("SRC")
                    else -> it.embed_url
                }
            } ?: return@amap
            
            if (source.startsWith("https://jeniusplay.com")) {
                Jeniusplay2().getUrl(source, "$referer/", subtitleCallback, callback)
            } else if (!source.contains("youtube")) {
                loadExtractor(source, "$referer/", subtitleCallback, callback)
            }
        }
    }
}
