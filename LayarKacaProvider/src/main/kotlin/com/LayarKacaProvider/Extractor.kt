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
        
        // 1. Ambil DATA MENTAH (datas)
        val regex = Regex("""datas\s*=\s*['"]([^'"]+)['"]""")
        val match = regex.find(response)

        if (match != null) {
            val encryptedData = match.groupValues[1]
            try {
                // 2. Decode Base64
                val jsonString = base64Decode(encryptedData)
                
                // 3. Ambil Kunci Rahasia (slug, id, user_id)
                // Kita pakai mapper agar tipe datanya aman
                val initialData = mapper.readValue<InitialData>(jsonString)
                val slug = initialData.slug

                if (!slug.isNullOrEmpty()) {
                    // --- JURUS HACKER: OTENTIKASI RESMI ---
                    // Kita tidak menebak API, kita menggunakan jalur standar player V2
                    // Endpoint ini menerima parameter yang ada di 'datas'
                    
                    val apiUrl = "$mainUrl/api/source/$slug"
                    
                    // Header wajib agar dikira browser
                    val apiHeaders = mapOf(
                        "Referer" to url,
                        "X-Requested-With" to "XMLHttpRequest",
                        "Origin" to mainUrl,
                        "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8"
                    )

                    // Payload (Isi Surat)
                    // Kita kirimkan semua 'credential' yang kita temukan di dump
                    val postData = mapOf(
                        "r" to (referer ?: mainUrl), // Referer asli
                        "d" to "abysscdn.com",       // Domain
                        "u" to (initialData.userId?.toString() ?: ""), // User ID dari dump
                        "h" to (initialData.md5Id?.toString() ?: "")   // Hash ID dari dump
                    )

                    // TEMBAK!
                    val apiResponse = app.post(
                        apiUrl, 
                        headers = apiHeaders,
                        data = postData
                    ).parsedSafe<ApiResponse>()

                    // 4. Panen Hasil
                    apiResponse?.data?.forEach { video ->
                        val streamUrl = video.file ?: video.label
                        // Decryption layer kedua (kadang file-nya masih di-encode Hex/Base64)
                        val finalUrl = decodeHexIfNeeded(streamUrl)

                        if (!finalUrl.isNullOrEmpty()) {
                            val qualityInt = getQualityFromName(video.label)
                            
                            if (finalUrl.contains(".m3u8")) {
                                M3u8Helper.generateM3u8(
                                    name,
                                    finalUrl,
                                    referer ?: mainUrl
                                ).forEach(callback)
                            } else {
                                callback(
                                    newExtractorLink(
                                        source = name,
                                        name = name,
                                        url = finalUrl
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

    // Fungsi bantu: Kadang Hydrax mengirim URL dalam format Hex (contoh: 68747470...)
    private fun decodeHexIfNeeded(url: String?): String? {
        if (url == null) return null
        // Ciri hex: panjang, genap, hanya angka 0-9 dan huruf a-f, tidak ada http
        if (!url.startsWith("http") && url.matches(Regex("^[0-9a-fA-F]+$"))) {
            return try {
                url.chunked(2)
                    .map { it.toInt(16).toChar() }
                    .joinToString("")
            } catch (e: Exception) {
                url // Kalau gagal decode, kembalikan aslinya
            }
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

    // Data Class sesuai dump JSON kamu
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
