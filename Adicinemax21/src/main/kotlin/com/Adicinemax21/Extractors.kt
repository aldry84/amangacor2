package com.Adicinemax21

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.cloudstream3.utils.newExtractorLink

// ==============================
// 1. JENIUSPLAY EXTRACTOR (ORIGINAL)
// ==============================

class Jeniusplay : ExtractorApi() {
    override var name = "Jeniusplay"
    override var mainUrl = "https://jeniusplay.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val document = app.get(url, referer = referer).document
        val hash = url.split("/").last().substringAfter("data=")

        // Mengambil Video M3U8
        val m3uLink = app.post(
            url = "$mainUrl/player/index.php?data=$hash&do=getVideo",
            data = mapOf("hash" to hash, "r" to "$referer"),
            referer = referer,
            headers = mapOf(
                "X-Requested-With" to "XMLHttpRequest",
                "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8"
            )
        ).parsedSafe<ResponseSource>()?.videoSource

        if (m3uLink != null) {
            callback.invoke(
                newExtractorLink(
                    name,
                    name,
                    url = m3uLink,
                    ExtractorLinkType.M3U8
                )
            )
        }

        // Mengambil Subtitle (Packed JS)
        document.select("script").map { script ->
            if (script.data().contains("eval(function(p,a,c,k,e,d)")) {
                val subData =
                    getAndUnpack(script.data()).substringAfter("\"tracks\":[").substringBefore("],")
                AppUtils.tryParseJson<List<Tracks>>("[$subData]")?.map { subtitle ->
                    subtitleCallback.invoke(
                        newSubtitleFile(
                            getLanguage(subtitle.label ?: ""),
                            subtitle.file
                        )
                    )
                }
            }
        }
    }

    private fun getLanguage(str: String): String {
        return when {
            str.contains("indonesia", true) || str
                .contains("bahasa", true) -> "Indonesian"
            else -> str
        }
    }

    data class ResponseSource(
        @JsonProperty("hls") val hls: Boolean,
        @JsonProperty("videoSource") val videoSource: String,
        @JsonProperty("securedLink") val securedLink: String?,
    )

    data class Tracks(
        @JsonProperty("kind") val kind: String?,
        @JsonProperty("file") val file: String,
        @JsonProperty("label") val label: String?,
    )
}

// ==========================================
// 2. YTDOWN EXTRACTOR (BACKDOOR FOR TRAILER)
// ==========================================
// Extractor ini meniru cara kerja curl yang Anda berikan
// untuk mendapatkan direct link googlevideo.com

class YtDownExtractor : ExtractorApi() {
    override var name = "YtDown"
    override var mainUrl = "https://ytdown.to"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        // url input contoh: "https://www.youtube.com/watch?v=TmD4c4vVclo"
        
        val proxyUrl = "$mainUrl/proxy.php"
        
        // Header sesuai dengan log curl (Chrome Mobile Android)
        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Mobile Safari/537.36",
            "Referer" to "$mainUrl/id2/",
            "Origin" to mainUrl,
            "X-Requested-With" to "XMLHttpRequest",
            "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8",
            "Accept" to "application/json, text/javascript, */*; q=0.01"
        )

        try {
            // 1. Kirim POST Data ke ytdown.to
            val response = app.post(
                proxyUrl,
                headers = headers,
                data = mapOf("url" to url) // Kirim URL Youtube asli
            ).text

            // 2. Cari link googlevideo.com dari response (Parsing Regex)
            // Kita cari pola URL direct: https://rr...googlevideo.com/videoplayback...
            // Regex ini mencari string yang dimulai dengan https, berisi googlevideo, dan diakhiri tanda kutip/spasi
            val directLinkRegex = Regex("""https:\/\/[a-zA-Z0-9-]+\.googlevideo\.com\/videoplayback\?[^"'\s]+""")
            val match = directLinkRegex.find(response)
            
            if (match != null) {
                // Bersihkan URL (kadang ada escaping backslash \/)
                val videoUrl = match.value.replace("\\/", "/")
                
                callback.invoke(
                    ExtractorLink(
                        source = this.name,
                        name = "YtDown Trailer (1080p/720p)",
                        url = videoUrl,
                        referer = mainUrl, // Kadang googlevideo butuh referer aslinya
                        quality = Qualities.Unknown.value,
                        type = INFER_TYPE // Biarkan ExoPlayer menentukan tipenya (biasanya MP4)
                    )
                )
            }
        } catch (e: Exception) {
            // Jika gagal, biarkan saja (silent fail), nanti Cloudstream coba metode lain
            e.printStackTrace()
        }
    }
}
