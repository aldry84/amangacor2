package mytmdbprovider // 🚨 PACKAGE BARU: mytmdbprovider

import dev.shehand.cloudstream.source.Plugin

// Kelas ini adalah TITIK MASUK (entry point) ekstensi Anda
class MyTMDBProviderPlugin: Plugin() {
    override fun load() {
        // Daftarkan Source utama Anda
        registerMainPageSource(MyTMDBProvider())
    }
}
