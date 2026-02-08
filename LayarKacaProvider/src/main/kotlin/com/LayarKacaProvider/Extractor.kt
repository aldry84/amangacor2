package com.LayarKacaProvider

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.mapper
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.USER_AGENT 

class Hydrax : ExtractorApi() {
    override val name = "Hydrax"
    override val mainUrl = "https://abysscdn.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        // 1. Cloudstream otomatis menangani Cloudflare di sini via WebView
        val response = app.get(url, referer = referer).text
        
        // 2. Ambil variable 'datas'
        val regex = Regex("""datas\s*=\s*['"]([^'"]+)['"]""")
        val match = regex.find(response)

        if (match != null) {
            val encryptedData = match.groupValues[1]
            try {
                // 3. Decode JSON Rahasia
                val jsonString = base64Decode(encryptedData)
                val initialData = mapper.readValue<InitialData>(jsonString)
                val slug = initialData.slug

                if (!slug.isNullOrEmpty()) {
                    val apiUrl = "$mainUrl/api/source/$slug"
                    
                    // HEADER PENYAMARAN (PENTING!)
                    val commonHeaders = mapOf(
                        "Referer" to url,
                        "Origin" to mainUrl,
                        "User-Agent" to USER_AGENT, // Pakai UA HP asli
                        "X-Requested-With" to "XMLHttpRequest",
                        "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8"
                    )

                    val postData = mapOf(
                        "r" to (referer ?: mainUrl),
                        "d" to "abysscdn.com",
                        "u" to (initialData.userId?.toString() ?: ""),
                        "h" to (initialData.md5Id?.toString() ?: "")
                    )

                    // 4. Minta Link ke API
                    val apiResponse = app.post(
                        apiUrl, 
                        headers = commonHeaders,
                        data = postData
                    ).parsedSafe<ApiResponse>()

                    // 5. Proses Hasil
                    apiResponse?.data?.forEach { video ->
                        val rawUrl = video.file ?: video.label
                        val streamUrl = decodeHexIfNeeded(rawUrl)

                        if (!streamUrl.isNullOrEmpty()) {
                            val qualityInt = getQualityFromName(video.label)
                            
                            // === JURUS KUNCI ===
                            // Header ini WAJIB dibawa sampai ke video player.
                            // Tanpa ini, server Abyss akan menolak play (Error 0x80000000)
                            val playerHeaders = mapOf(
                                "User-Agent" to USER_AGENT,
                                "Referer" to "https://abysscdn.com/", // Wajib ada slash di akhir
                                "Origin" to "https://abysscdn.com",
                                "Accept" to "*/*"
                            )

                            if (streamUrl.contains(".m3u8")) {
                                M3u8Helper.generateM3u8(
                                    name,
                                    streamUrl,
                                    "https://abysscdn.com/", // Paksa referer di sini juga
                                    headers = playerHeaders
                                ).forEach(callback)
                            } else {
                                callback(
                                    newExtractorLink(
                                        source = name,
                                        name = name,
                                        url = streamUrl
                                    ) {
                                        this.referer = "https://abysscdn.com/"
                                        this.quality = qualityInt
                                        // Header disuntikkan ke sini
                                        this.headers = playerHeaders 
                                    }
                                )
                            }
                        }
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun decodeHexIfNeeded(url: String?): String? {
        if (url == null) return null
        if (!url.startsWith("http") && url.matches(Regex("^[0-9a-fA-F]+$"))) {
            return try {
                url.chunked(2)
                    .map { it.toInt(16).toChar() }
                    .joinToString("")
            } catch (e: Exception) { url }
        }
        return url
    }

    private fun getQualityFromName(label: String?): Int {
        return when (label?.lowercase()) {
            "fhd", "1080p" -> Qualities.P1080.value
            "hd", "720p" -> Qualities.P720.value
            "sd", "480p" -> Qualities.P480.value
            "360p" -> Qualities.P360.value
            else -> Qualities.Unknown.value
        }
    }

    data class InitialData(
        @JsonProperty("slug") val slug: String? = null,
        @JsonProperty("user_id") val userId: Long? = null,
        @JsonProperty("md5_id") val md5Id: Long? = null
    )

    data class ApiResponse(
        @JsonProperty("success") val success: Boolean? = null,
        @JsonProperty("data") val data: List<VideoData>? = null
    )

    data class VideoData(
        @JsonProperty("file") val file: String? = null,
        @JsonProperty("label") val label: String? = null,
        @JsonProperty("type") val type: String? = null
    )
}
