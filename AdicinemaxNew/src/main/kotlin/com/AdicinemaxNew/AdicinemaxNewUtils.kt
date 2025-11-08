package com.AdicinemaxNew

import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.Qualities
import java.net.URLEncoder

object AdicinemaxNewUtils {
    
    /**
     * Utility functions untuk membantu operasi umum
     */
    
    fun String.createSlug(): String {
        return this.filter { it.isWhitespace() || it.isLetterOrDigit() }
            .trim()
            .replace("\\s+".toRegex(), "-")
            .lowercase()
    }
    
    fun getBaseUrl(url: String): String {
        return URI(url).let {
            "${it.scheme}://${it.host}"
        }
    }
    
    fun encodeUrlParam(param: String): String {
        return URLEncoder.encode(param, "UTF-8")
    }
    
    fun getQualityFromString(qualityStr: String): Int {
        return when {
            qualityStr.contains("4k", true) -> Qualities.P2160.value
            qualityStr.contains("1080", true) -> Qualities.P1080.value
            qualityStr.contains("720", true) -> Qualities.P720.value
            qualityStr.contains("480", true) -> Qualities.P480.value
            qualityStr.contains("360", true) -> Qualities.P360.value
            else -> getQualityFromName(qualityStr)
        }
    }
    
    fun isUrlValid(url: String): Boolean {
        return try {
            URI(url)
            true
        } catch (e: Exception) {
            false
        }
    }
}
