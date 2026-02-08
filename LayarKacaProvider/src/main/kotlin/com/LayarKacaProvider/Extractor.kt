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
                
                // Kita pakai parse manual yang aman (ignore unknown keys)
                val initialData = mapper.readValue<InitialData>(jsonString)
                val slug = initialData.slug

                if (!slug.isNullOrEmpty()) {
                    // 3. JURUS API BYPASS
                    // Kita tembak langsung ke endpoint API mereka menggunakan slug yang kita dapat.
                    // Endpoint ini sering dipakai oleh player V2 mereka.
                    val apiUrl = "$mainUrl/api/source/$slug"
                    
                    val apiHeaders = mapOf(
                        "Referer" to url,
                        "X-Requested-With" to "XMLHttpRequest",
                        "Content-Type" to "application/x-www-form-urlencoded"
                    )

                    // Kirim request POST kosong atau dengan r=referer
                    val apiResponse = app.post(
                        apiUrl, 
                        headers = apiHeaders,
                        data = mapOf("r" to (referer ?: mainUrl), "d" to "abysscdn.com")
                    ).parsedSafe<ApiResponse>()

                    // 4. Proses Hasil API
                    apiResponse?.data?.forEach { video ->
                        val streamUrl = video.file ?: video.label // Kadang link ada di label (jarang)
                        val qualityLabel = video.label ?: "Auto"
                        val qualityInt = getQualityFromName(qualityLabel)

                        if (!streamUrl.isNullOrEmpty()) {
                            if (streamUrl.contains(".m3u8")) {
                                M3u8Helper.generateM3u8(
                                    name,
                                    streamUrl,
                                    referer ?: mainUrl
                                ).forEach(callback)
                            } else {
                                callback(
                                    newExtractorLink(
                                        source = name,
                                        name = name,
                                        url = streamUrl
                                    ) {
                                        this.referer = referer ?: mainUrl
                                        this.quality = qualityInt
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

    private fun getQualityFromName(label: String?): Int {
        return when (label?.lowercase()) {
            "fhd", "1080p" -> Qualities.P1080.value
            "hd", "720p" -> Qualities.P720.value
            "sd", "480p" -> Qualities.P480.value
            "360p" -> Qualities.P360.value
            else -> Qualities.Unknown.value
        }
    }

    // Data Class untuk parsing tahap 1 (ambil slug)
    data class InitialData(
        @JsonProperty("slug") val slug: String? = null
    )

    // Data Class untuk parsing tahap 2 (hasil API)
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
