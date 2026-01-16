package com.layarKacaProvider

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
// Import Filesim
import com.lagradost.cloudstream3.extractors.Filesim

class Co4nxtrl : Filesim() {
    override val mainUrl = "https://co4nxtrl.com"
    override val name = "Co4nxtrl"
    override val requiresReferer = true
}

class Furher : Filesim() {
    override val name = "Furher"
    override var mainUrl = "https://furher.in"
}

class Turbovidhls : ExtractorApi() {
    override val name = "Turbovid"
    override val mainUrl = "https://turbovidhls.com"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36",
            "Accept" to "*/*"
        )

        try {
            val response = app.get(url, headers = headers)
            val responseText = response.text

            // Logika untuk Nested M3U8
            if (responseText.contains("#EXT-X-STREAM-INF") && responseText.contains("http")) {
                val nextUrl = responseText.split('\n').firstOrNull { 
                    it.trim().startsWith("http") && it.contains(".m3u8") 
                }?.trim()

                if (!nextUrl.isNullOrEmpty()) {
                    // Panggil fungsi Reflection kita
                    callback(
                        createSafeExtractorLink(
                            source = this.name,
                            name = this.name,
                            url = nextUrl,
                            referer = "https://turbovidthis.com/",
                            isM3u8 = true,
                            headers = headers
                        )
                    )
                    return
                }
            }

            // Fallback ke URL asli
            callback(
                createSafeExtractorLink(
                    source = this.name,
                    name = this.name,
                    url = url,
                    referer = "https://turbovidthis.com/",
                    isM3u8 = true,
                    headers = headers
                )
            )

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // --- FUNGSI JEMBATAN (REFLECTION) ---
    // Kita menggunakan Java Reflection untuk memanggil Constructor secara paksa
    // Compiler tidak akan bisa memblokir ini.
    private fun createSafeExtractorLink(
        source: String,
        name: String,
        url: String,
        referer: String,
        isM3u8: Boolean,
        headers: Map<String, String>
    ): ExtractorLink {
        // Ambil kelas ExtractorLink
        val clazz = ExtractorLink::class.java
        
        // Cari constructor yang memiliki parameter terbanyak (Constructor utama)
        // Kita bypass pemeriksaan compiler di sini
        val constructor = clazz.constructors.find { it.parameterCount >= 6 } 
            ?: throw RuntimeException("Constructor ExtractorLink tidak ditemukan!")

        // Buat instance baru secara manual
        // Urutan parameter sesuai pesan error: 
        // source, name, url, referer, quality, isM3u8, headers, extractorData
        return constructor.newInstance(
            source,
            name,
            url,
            referer,
            Qualities.Unknown.value, // quality
            isM3u8,                  // isM3u8
            headers,                 // headers
            null                     // extractorData (String?)
        ) as ExtractorLink
    }
}
