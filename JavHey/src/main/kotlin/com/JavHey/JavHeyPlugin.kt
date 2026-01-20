package com.JavHey

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class JavHeyPlugin: Plugin() {
    override fun load(context: Context) {
        // 1. Daftarkan Provider Utama
        registerMainAPI(JavHey())

        // 2. Daftarkan Custom Extractor
        // Ini penting agar Cloudstream "kenal" dengan LeleBakarExtractor
        // dan bisa digunakan otomatis oleh sistem jika diperlukan.
        registerExtractorAPI(LeleBakarExtractor())
        
        // Nanti kalau BySebuho sudah jadi, daftarkan juga di sini:
        // registerExtractorAPI(BySebuhoExtractor())
    }
}
