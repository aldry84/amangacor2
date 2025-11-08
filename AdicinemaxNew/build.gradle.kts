plugins {
    kotlin("jvm")
}

version = 1

cloudstream {
    description = "Movies and TV Shows with TMDB metadata and VidSrc streaming"
    language = "en"
    authors = listOf("AdicinemaxNew")

    status = 1

    iconUrl = "https://www.google.com/s2/favicons?domain=vidsrc-embed.ru&sz=%size%"
    tvTypes = listOf(
        "TvSeries",
        "Movie",
        "Anime",
        "AsianDrama"
    )

    isCrossPlatform = false
}

dependencies {
    implementation("com.lagradost:cloudstream3:pre-release")
}
