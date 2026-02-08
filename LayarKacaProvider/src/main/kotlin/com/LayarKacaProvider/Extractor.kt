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
        val response = app.get(url, referer = referer).text
        
        // 1. Ambil variable 'datas'
        val regex = Regex("""datas\s*=\s*['"]([^'"]+)['"]""")
        val match = regex.find(response)

        if (match != null) {
            val encryptedData = match.groupValues[1]
            try {
                // 2. Decode Base64 untuk mendapatkan SLUG
                val jsonString = base64Decode(encryptedData)
                val initialData = mapper.readValue<InitialData>(jsonString)
                val slug = initialData.slug

                if (!slug.isNullOrEmpty()) {
                    // 3. API Call Resmi (Pura-pura jadi player V2)
                    val apiUrl = "$mainUrl/api/source/$slug"
                    
                    val apiHeaders = mapOf(
                        "Referer" to url,
                        "X-Requested-With" to "XMLHttpRequest",
                        "Origin" to mainUrl,
                        "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8",
                        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                    )

                    // Kirim credential lengkap (User ID & Hash) biar server percaya 100%
                    val postData = mapOf(
                        "r" to (referer ?: mainUrl),
                        "d" to "abysscdn.com",
                        "u" to (initialData.userId?.toString() ?: ""),
                        "h" to (initialData.md5Id?.toString() ?: "")
                    )

                    val apiResponse = app.post(
                        apiUrl, 
                        headers = apiHeaders,
                        data = postData
                    ).parsedSafe<ApiResponse>()

                    // 4. Proses Hasil & INJEKSI HEADER (PENTING!)
                    apiResponse?.data?.forEach { video ->
                        val rawUrl = video.file ?: video.label
                        val streamUrl = decodeHexIfNeeded(rawUrl)

                        if (!streamUrl.isNullOrEmpty()) {
                            val qualityInt = getQualityFromName(video.label)
                            
                            // INI KUNCINYA: Kita buat map header khusus untuk video ini
                            // Agar saat player memutar, dia membawa 'surat jalan' ini.
                            val videoHeaders = mapOf(
                                "Referer" to mainUrl,
                                "Origin" to mainUrl,
                                "User-Agent" to (apiHeaders["User-Agent"] ?: "")
                            )

                            if (streamUrl.contains(".m3u8")) {
                                M3u8Helper.generateM3u8(
                                    name,
                                    streamUrl,
                                    referer ?: mainUrl,
                                    headers = videoHeaders // Masukkan header di sini
                                ).forEach(callback)
                            } else {
                                callback(
                                    newExtractorLink(
                                        source = name,
                                        name = name,
                                        url = streamUrl
                                    ) {
                                        this.referer = mainUrl
                                        this.quality = qualityInt
                                        this.headers = videoHeaders // DAN DI SINI! (Fix setDataSource failed)
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

    // Dekoder Hex (Jaga-jaga server iseng kirim hex)
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
