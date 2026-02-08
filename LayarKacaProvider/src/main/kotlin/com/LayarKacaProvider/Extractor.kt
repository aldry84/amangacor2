package com.LayarKacaProvider

import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode // Pastikan import ini ada
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.fasterxml.jackson.annotation.JsonProperty

class Hydrax : ExtractorApi() {
    override val name = "Hydrax"
    override val mainUrl = "https://abysscdn.com" // Domain bisa berubah, tapi logic sama
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
        // Pola: mencari kata 'datas', diikuti tanda sama dengan, lalu tanda kutip, lalu isi datanya
        val regex = Regex("""datas\s*=\s*['"]([^'"]+)['"]""")
        val match = regex.find(response)

        if (match != null) {
            val encryptedData = match.groupValues[1]

            try {
                // 3. Decode Base64 (sesuai petunjuk 'atob')
                val jsonString = base64Decode(encryptedData)
                
                // 4. Parse JSON menjadi Object
                // Cloudstream punya built-in parser, atau pakai Jackson manual
                val data = app.parseJson<HydraxData>(jsonString)

                // 5. Ekstrak Link
                // Biasanya link ada di properti 'source', 'url', atau 'file'
                val streamUrl = data.source ?: data.url ?: data.file
                
                if (!streamUrl.isNullOrEmpty()) {
                    // Cek apakah linknya .m3u8 (HLS) atau .mp4
                    if (streamUrl.contains(".m3u8")) {
                        M3u8Helper.generateM3u8(
                            name,
                            streamUrl,
                            referer ?: mainUrl
                        ).forEach(callback)
                    } else {
                        // Kalau MP4 biasa
                        callback(
                            ExtractorLink(
                                name,
                                name,
                                streamUrl,
                                referer ?: mainUrl,
                                Qualities.Unknown.value
                            )
                        )
                    }
                }

            } catch (e: Exception) {
                // Gunakan ini untuk debug jika parsing gagal
                e.printStackTrace()
                System.out.println("Hydrax Decode Error: ${e.message}")
            }
        }
    }

    // Model Data untuk menangkap hasil JSON
    // Kita buat flexible karena field-nya sering berubah nama
    data class HydraxData(
        @JsonProperty("source") val source: String? = null,
        @JsonProperty("url") val url: String? = null,
        @JsonProperty("file") val file: String? = null,
        @JsonProperty("label") val label: String? = null
    )
}
