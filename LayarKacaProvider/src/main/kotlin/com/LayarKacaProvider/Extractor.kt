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
import com.lagradost.cloudstream3.utils.newExtractorLink // Wajib pakai ini sekarang

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
        // 1. Ambil HTML
        val response = app.get(url, referer = referer).text

        // 2. Cari variabel 'datas' menggunakan Regex
        val regex = Regex("""datas\s*=\s*['"]([^'"]+)['"]""")
        val match = regex.find(response)

        if (match != null) {
            val encryptedData = match.groupValues[1]

            try {
                // 3. Decode Base64
                val jsonString = base64Decode(encryptedData)

                // 4. Parse JSON (Menggunakan mapper bawaan Cloudstream agar lebih aman)
                // Kita ganti app.parseJson dengan mapper.readValue
                val data = mapper.readValue<HydraxData>(jsonString)

                // 5. Ekstrak Link
                val streamUrl = data.source ?: data.url ?: data.file

                // Pengecekan null safe
                if (!streamUrl.isNullOrEmpty()) {
                    val finalQuality = getQualityFromName(data.label)
                    
                    if (streamUrl.contains(".m3u8")) {
                        M3u8Helper.generateM3u8(
                            name,
                            streamUrl,
                            referer ?: mainUrl
                        ).forEach(callback)
                    } else {
                        // PERBAIKAN: Menggunakan newExtractorLink, bukan ExtractorLink()
                        callback(
                            newExtractorLink(
                                source = name,
                                name = name,
                                url = streamUrl,
                                referer = referer ?: mainUrl,
                                quality = finalQuality
                            )
                        )
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Fungsi bantu kecil untuk konversi label (misal "HD") ke Int quality
    private fun getQualityFromName(label: String?): Int {
        return when (label?.lowercase()) {
            "fhd", "1080p" -> Qualities.P1080.value
            "hd", "720p" -> Qualities.P720.value
            "sd", "480p" -> Qualities.P480.value
            "360p" -> Qualities.P360.value
            else -> Qualities.Unknown.value
        }
    }

    data class HydraxData(
        @JsonProperty("source") val source: String? = null,
        @JsonProperty("url") val url: String? = null,
        @JsonProperty("file") val file: String? = null,
        @JsonProperty("label") val label: String? = null
    )
}
