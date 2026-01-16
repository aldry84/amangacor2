package com.layarKacaProvider

import android.util.Log // Import wajib untuk logging
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.extractors.Filesim

// TAG untuk mempermudah pencarian di Logcat
const val TAG = "TURBO_DEBUG"

// --- CLASS UTAMA UNTUK EMTURBOVID ---
class EmturboCustom : Turbovidhls() {
    override val name = "Emturbovid"
    override val mainUrl = "https://emturbovid.com"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        Log.e(TAG, "==================================================")
        Log.e(TAG, "1. EmturboCustom: Memulai proses untuk URL: $url")
        
        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Referer" to "https://emturbovid.com/"
        )

        try {
            Log.e(TAG, "2. EmturboCustom: Sedang mendownload halaman embed...")
            val response = app.get(url, headers = headers).text
            Log.e(TAG, "3. EmturboCustom: Download Selesai. Panjang HTML: ${response.length} karakter")
            
            // Debug: Print sedikit isi HTML untuk memastikan bukan halaman 403/404
            if (response.length > 200) {
                Log.e(TAG, "   Snippet HTML: ${response.substring(0, 200)}...")
            }

            var m3u8Url: String? = null
            
            // Regex 1
            val regex1 = """file:\s*["']([^"']+\.m3u8[^"']*)["']""".toRegex()
            m3u8Url = regex1.find(response)?.groupValues?.get(1)
            Log.e(TAG, "4. EmturboCustom: Hasil Regex 1 (file:): $m3u8Url")

            // Regex 2
            if (m3u8Url == null) {
                val regex2 = """["'](https?://[^"']+\.m3u8[^"']*)["']""".toRegex()
                m3u8Url = regex2.find(response)?.groupValues?.get(1)
                Log.e(TAG, "4. EmturboCustom: Hasil Regex 2 (generic link): $m3u8Url")
            }
            
            // Regex 3
            if (m3u8Url == null) {
                 val regex3 = """src=["']([^"']+\.m3u8[^"']*)["']""".toRegex()
                 m3u8Url = regex3.find(response)?.groupValues?.get(1)
                 Log.e(TAG, "4. EmturboCustom: Hasil Regex 3 (src=): $m3u8Url")
            }

            if (m3u8Url != null) {
                Log.e(TAG, "5. EmturboCustom: Link DITEMUKAN! Mengirim ke Turbovidhls...")
                Log.e(TAG, "   Link Asli: $m3u8Url")
                Log.e(TAG, "   Referer yg dikirim: $url") // Kita kirim URL embed sbg referer
                super.getUrl(m3u8Url, url, subtitleCallback, callback)
            } else {
                Log.e(TAG, "5. EmturboCustom: GAGAL! Link m3u8 tidak ditemukan sama sekali di halaman ini.")
                Log.e(TAG, "   Kemungkinan halaman diproteksi, server error, atau regex tidak cocok.")
            }

        } catch (e: Exception) {
            Log.e(TAG, "ERROR EmturboCustom: ${e.message}")
            e.printStackTrace()
        }
    }
}

class Co4nxtrl : Filesim() {
    override val mainUrl = "https://co4nxtrl.com"
    override val name = "Co4nxtrl"
    override val requiresReferer = true
}

class Furher : Filesim() {
    override val name = "Furher"
    override var mainUrl = "https://furher.in"
}

// --- CLASS BASE TURBOVID ---
open class Turbovidhls : ExtractorApi() {
    override val name = "Turbovid"
    override val mainUrl = "https://turbovidhls.com"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        Log.e(TAG, "--------------------------------------------------")
        Log.e(TAG, "A. Turbovidhls: Menerima URL: $url")
        
        val currentDomain = if (url.contains("emturbovid")) "https://emturbovid.com/" else "https://turbovidthis.com/"
        
        // Gunakan referer dari parameter jika ada (hasil passing dari EmturboCustom)
        val finalReferer = referer ?: currentDomain
        val finalOrigin = if (finalReferer.startsWith("http")) 
             finalReferer.substringBeforeLast("/") 
             else currentDomain.trimEnd('/')

        Log.e(TAG, "B. Turbovidhls: Menyiapkan Header:")
        Log.e(TAG, "   Origin: $finalOrigin")
        Log.e(TAG, "   Referer: $finalReferer")

        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Accept" to "*/*",
            "Origin" to finalOrigin,
            "Referer" to finalReferer
        )

        try {
            Log.e(TAG, "C. Turbovidhls: Requesting M3U8 Content...")
            val response = app.get(url, headers = headers)
            val responseText = response.text
            Log.e(TAG, "D. Turbovidhls: Response Code: ${response.code}")
            
            if (response.code != 200) {
                 Log.e(TAG, "   BAHAYA! Server menolak request (bukan 200 OK). Ini penyebab error 3001.")
            }

            // 1. Logika Nested M3U8
            if (responseText.contains("#EXT-X-STREAM-INF") && responseText.contains("http")) {
                Log.e(TAG, "E. Turbovidhls: Terdeteksi Nested M3U8 (Master Playlist).")
                val nextUrl = responseText.split('\n').firstOrNull { 
                    it.trim().startsWith("http") && it.contains(".m3u8") 
                }?.trim()
                
                Log.e(TAG, "   URL Variant ditemukan: $nextUrl")

                if (!nextUrl.isNullOrEmpty()) {
                    Log.e(TAG, "F. Turbovidhls: Mengirim Variant URL ke Player via Reflection...")
                    callback(
                        createSafeExtractorLink(
                            source = this.name,
                            name = this.name,
                            url = nextUrl,
                            referer = finalReferer,
                            isM3u8 = true, 
                            headers = headers
                        )
                    )
                    return
                }
            }

            // 2. Fallback
            Log.e(TAG, "G. Turbovidhls: Mengirim Direct URL ke Player via Reflection...")
            callback(
                createSafeExtractorLink(
                    source = this.name,
                    name = this.name,
                    url = url,
                    referer = finalReferer,
                    isM3u8 = true,
                    headers = headers
                )
            )

        } catch (e: Exception) {
            Log.e(TAG, "ERROR Turbovidhls: ${e.message}")
            e.printStackTrace()
        }
    }

    // --- FUNGSI JEMBATAN (REFLECTION) ---
    private fun createSafeExtractorLink(
        source: String,
        name: String,
        url: String,
        referer: String,
        isM3u8: Boolean,
        headers: Map<String, String>
    ): ExtractorLink {
        Log.e(TAG, "H. Reflection: Membuat ExtractorLink...")
        Log.e(TAG, "   Tipe: ${if(isM3u8) "M3U8" else "VIDEO"}")
        
        val clazz = ExtractorLink::class.java
        
        val constructor = clazz.constructors.find { it.parameterCount >= 6 } 
            ?: throw RuntimeException("Constructor ExtractorLink tidak ditemukan!")

        val type = if (isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO

        return constructor.newInstance(
            source,
            name,
            url,
            referer,
            Qualities.Unknown.value,
            type,
            headers,
            null
        ) as ExtractorLink
    }
}
