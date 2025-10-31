// VidsrcExtractor.kt

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dev.shehand.cloudstream.extractor.ExtractorLink
import dev.shehand.cloudstream.MainApplication.Companion.app // Akses ke fungsi fetching

// Inisialisasi GSON sekali
private val GSON = Gson()

class VidsrcExtractor {
    
    private val vidsrcReferer = "https://vidsrc.me/"

    /**
     * Fungsi utama untuk mengambil tautan streaming dari URL Vidsrc.
     */
    suspend fun getLink(vidsrcUrl: String): List<ExtractorLink> {
        
        val finalLinks = mutableListOf<ExtractorLink>()
        
        // 1. Mendapatkan URL Player
        val playerUrl = extractPlayerUrl(vidsrcUrl)
        if (playerUrl.isNullOrEmpty()) return emptyList()

        // 2. Mengambil Halaman Player DENGAN REFERER (Wajib!)
        val playerPage = try {
            app.get(playerUrl, referer = vidsrcReferer).document
        } catch (e: Exception) {
            println("VidsrcExtractor: Gagal memuat halaman player: ${e.message}")
            return emptyList()
        }

        // 3. Ekstraksi JSON dari Skrip JS
        val scriptData = playerPage.select("script:containsData(sources)").html()
        val sourcesJson = extractJsonUsingRegex(scriptData, "sources")
        // val tracksJson = extractJsonUsingRegex(scriptData, "tracks") // Untuk subtitle

        // 4. Parsing Sources JSON dan Membuat ExtractorLinks
        if (sourcesJson.isNotEmpty()) {
            try {
                val listSourceType = object : TypeToken<List<SourceData>>() {}.type
                val listSources: List<SourceData> = GSON.fromJson(sourcesJson, listSourceType)

                for (source in listSources) {
                    finalLinks.add(
                        ExtractorLink(
                            name = "Vidsrc - ${source.label}", 
                            url = source.file, 
                            referer = vidsrcReferer // Wajib!
                        )
                    )
                }
            } catch (e: Exception) {
                println("VidsrcExtractor: ERROR Parsing Sources JSON: ${e.message}")
            }
        }
        
        // NOTE: Logika Subtitle (tracksJson) akan serupa, mengembalikan List<SubtitleLink>
        
        return finalLinks
    }
    
    // --- Helper Functions ---

    private fun extractPlayerUrl(vidsrcUrl: String): String? {
        return try {
            val doc = app.get(vidsrcUrl, referer = vidsrcReferer).document
            val playerFrame = doc.select("iframe#player_frame") 
            
            if (playerFrame.isEmpty()) return null
            
            val playerSrc = playerFrame.attr("src")
            if (playerSrc.startsWith("http")) playerSrc else null
            
        } catch (e: Exception) {
            null
        }
    }
    
    private fun extractJsonUsingRegex(scriptData: String, varName: String): String {
        val regex = "$varName\\s*=\\s*(\\[.*?\\])".toRegex()
        
        return try {
            val matchResult = regex.find(scriptData)
            matchResult?.groups?.get(1)?.value ?: ""
        } catch (e: Exception) {
            ""
        }
    }
}
