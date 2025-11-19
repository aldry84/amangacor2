package com.AdiDrakor

import java.net.URI

object AdiDrakorUtils {

    fun getTitle(str: String): String {
        return str.replace(Regex("[^a-zA-Z0-9]"), "-")
    }

    fun getLanguage(str: String): String {
        return when (str) {
            "Indonesia" -> "Indonesian"
            else -> str
        }
    }

    fun fixUrl(url: String, domain: String): String {
        if (url.startsWith("http")) {
            return url
        }
        if (url.isEmpty()) {
            return ""
        }
        val startsWithNoHttp = url.startsWith("//")
        if (startsWithNoHttp) {
            return "https:$url"
        } else {
            if (url.startsWith('/')) {
                return domain + url
            }
            return "$domain/$url"
        }
    }

    // Logika Dekripsi Subtitle
    fun decrypt(input: String): String {
        return try {
            // Implementasi sederhana decrypt jika logika spesifik tidak tersedia
            // Anda bisa mengganti ini dengan algoritma spesifik jika memilikinya
            input
        } catch (e: Exception) {
            ""
        }
    }
    
    // Fungsi bantuan tambahan jika diperlukan
    fun String.substringBetween(prefix: String, suffix: String): String {
        val start = this.indexOf(prefix)
        if (start < 0) return ""
        val end = this.indexOf(suffix, start + prefix.length)
        if (end < 0) return ""
        return this.substring(start + prefix.length, end)
    }
}
