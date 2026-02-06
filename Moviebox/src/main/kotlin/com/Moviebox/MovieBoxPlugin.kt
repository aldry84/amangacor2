package com.Moviebox

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class MovieBoxPlugin: Plugin() {
    override fun load(context: Context) {
        // Mendaftarkan Provider MovieBox yang sudah kita buat
        registerMainAPI(MovieBox())
    }
}
