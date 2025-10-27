package com.Adimoviemaze

import com.lagradost.cloudstream3.*

class Adimoviemaze : MainAPI() {
    // URL utama yang Anda minta
    override var mainUrl = "https://moviemaze.cc"
    
    // Properti dasar
    override var name = "Adimoviemaze"
    override val hasMainPage = false // Diatur ke false untuk menghindari error saat tidak ada getMainPage
    override var lang = "en"
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries
    )

    // Anda bisa menambahkan logika di sini nanti:
    // override suspend fun getMainPage(...)
    // override suspend fun search(...)
    // override suspend fun load(...)
    // override suspend fun loadLinks(...)
}
