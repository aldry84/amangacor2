// AsianDrama/src/main/kotlin/com/AsianDrama/AsianDramaUtils.kt
package com.AsianDrama

import com.lagradost.cloudstream3.utils.getAndUnpack

// Utility functions that might be needed
object AsianDramaUtils {
    
    // Add any utility functions here if needed
    // Most utilities are already included in Cloudstream3
    
    fun fixUrl(url: String, domain: String): String {
        return when {
            url.startsWith("http") -> url
            url.startsWith("//") -> "https:$url"
            url.startsWith("/") -> domain + url
            else -> "$domain/$url"
        }
    }
}
