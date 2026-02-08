@CloudstreamPlugin
class LayarKacaPlugin : Plugin() {
    override fun load(context: Context) {
        // Provider Utama
        registerMainAPI(LayarKacaProvider())
        
        // Extractor Servers
        registerExtractorAPI(EmturbovidExtractor()) 
        registerExtractorAPI(P2PExtractor())        
        registerExtractorAPI(F16Extractor())        
        registerExtractorAPI(HydraxExtractor()) // <--- TAMBAHKAN BARIS INI
    }
}
