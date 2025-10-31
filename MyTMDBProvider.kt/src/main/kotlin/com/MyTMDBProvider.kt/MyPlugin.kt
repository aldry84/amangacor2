// MyPlugin.kt

import dev.shehand.cloudstream.source.Plugin

class MyPlugin: Plugin() {
    override fun load() {
        // Daftarkan Source utama Anda agar dapat diakses oleh CloudStream
        registerMainPageSource(MyTMDBProvider())
    }
}
