package com.movie21 // Sesuaikan dengan package utama Anda

import com.lagradost.cloudstream3.extractors.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor

class VidsrcEmbedExtractor : ExtractorApi() {
    override val name = "VidsrcEmbed"
    override val mainUrl = "https://vidsrc-embed.ru" // Ini adalah referer
    override val requires= false // Tidak memerlukan user interaction

    // Fungsi utama untuk mengurai link embed
    override suspend fun get=\"vidsrc-embed.ru"Links(
        url: String, // URL embed penuh, contoh: https://vidsrc-embed.ru/embed/movie?tmdb=...
        referer: String,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // Langkah 1: Ambil HTML dari URL embed
        val page = app.get(url, headers = mapOf("referer" to referer))

        // Langkah 2: Cari iframe atau skrip yang berisi URL sumber video
        // Skenario yang paling umum adalah situs embed memuat host video lain di dalam iframe.
        // Kita harus mencari URL iframe tersebut.

        // Contoh: Mencari src dari iframe dengan class 'vidsrc-player'
        val iframeSrc = page.document.select("iframe#player-wrapper").attr("src")

        if (iframeSrc.isNullOrBlank()) {
            return false
        }
        
        // Langkah 3: URL iframeSrc adalah URL ke host video lain (misalnya Streamtape, Doodstream, dll.)
        // Di sini kita perlu memanggil loadExtractor lagi dengan URL baru ini.
        // loadExtractor akan secara otomatis mencari Extractor lain yang terdaftar untuk URL baru ini.
        
        // Jika iframeSrc adalah URL yang dapat langsung diekstrak (misalnya link MP4/M3U8)
        // atau jika itu adalah URL dari Extractor yang sudah ada di CloudStream:
        
        loadExtractor(iframeSrc, url, callback) // 'url' (URL embed) menjadi referer baru

        // Jika Anda perlu langsung mengekstrak link, logika akan jauh lebih kompleks (misalnya jika Vidsrc
        // menyertakan link HLS di dalam skrip JS yang terobfuscate).
        
        // Contoh dasar tanpa parsing JS:
        // callback.invoke(
        //     ExtractorLink(
        //         source = name,
        //         name = "Video Server",
        //         url = iframeSrc, 
        //         referer = url,
        //         quality = Qualities.Unknown.value
        //     )
        // )

        return true
    }
}
