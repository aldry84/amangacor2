package com.Adicinemax


import android.content.SharedPreferences
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.runAllAsync
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.Adicinemax.AdicinemaxExtractor.invokeKisskhAsia

class AdicinemaxTest(sharedPreferences:SharedPreferences?=null) : Adicinemax(sharedPreferences) {
    override var name = "Adicinemax-Test"
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val res = AppUtils.parseJson<LinkData>(data)
        runAllAsync(
            {
                if (!res.isAnime) invokeKisskhAsia(
                    res.id,
                    res.season,
                    res.episode,
                    subtitleCallback,
                    callback
                )
            }
        )
        return true
    }

}
