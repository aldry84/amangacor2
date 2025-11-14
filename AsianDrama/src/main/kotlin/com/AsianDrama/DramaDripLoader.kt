package com.AsianDrama

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.SubtitleFile

class DramaDripLoader {
    private val dramaDrip = DramaDrip()
    
    suspend fun loadContent(url: String): LoadResponse? {
        return dramaDrip.load(url)
    }
    
    suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return dramaDrip.loadLinks(data, isCasting, subtitleCallback, callback)
    }
}
