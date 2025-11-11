package com.AdicinemaxNew


import android.content.SharedPreferences
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.runAllAsync
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.AdicinemaxNew.AdicinemaxNewExtractor.invokeKisskhAsia

class AdicinemaxNewTest(sharedPreferences:SharedPreferences?=null) : AdicinemaxNew(sharedPreferences) {
    override var name = "AdicinemaxNew-Test"
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
