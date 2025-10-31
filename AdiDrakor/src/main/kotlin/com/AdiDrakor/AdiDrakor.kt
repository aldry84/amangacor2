import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
// ... (Kode lainnya)

    override suspend fun load(url: String): LoadResponse {
        val id = url.substringAfterLast("/")

        return try {
            val document = app.get("$mainUrl/wefeed-h5-bff/web/subject/detail?subjectId=$id")
                .parsedSafe<MediaDetail>()?.data
            val subject = document?.subject
            val title = subject?.title ?: ""
            val poster = subject?.cover?.url
            val tags = subject?.genre?.split(",")?.map { it.trim() }
            val year = subject?.releaseDate?.substringBefore("-")?.toIntOrNull()

            val tvType = when (subject?.subjectType) {
                1 -> TvType.Movie
                2 -> TvType.TvSeries
                else -> {
                    logWarning("Unknown subjectType: ${subject?.subjectType}, defaulting to AsianDrama")
                    TvType.AsianDrama
                }
            }

            val description = subject?.description
            val trailer = subject?.trailer?.videoAddress?.url

            val actors = document?.stars?.mapNotNull { cast ->
                ActorData(
                    Actor(
                        cast.name ?: return@mapNotNull null,
                        cast.avatarUrl
                    ),
                    roleString = cast.character
                )
            }?.distinctBy { it.actor }

            val recommendations =
                app.get("$mainUrl/wefeed-h5-bff/web/subject/detail-rec?subjectId=$id&page=1&perPage=12")
                    .parsedSafe<Media>()?.data?.items
                    ?.filter { it.countryName?.contains("Korea", ignoreCase = true) == true }
                    ?.map { it.toSearchResponse(this) }

            if (tvType == TvType.Movie) {
                newMovieLoadResponse(title, url, TvType.Movie,
                    document?.resource?.seasons?.firstOrNull()?.allEp?.split(",")?.map { it.toInt() }
                        ?.map { ep ->
                            newEpisode(
                                LoadData(
                                    id,
                                    1,
                                    ep,
                                    subject?.detailPath
                                ).toJson()
                            ) {
                                this.episode = ep
                            }
                        } ?: emptyList()) {
                    coroutineScope { // Gunakan coroutineScope
                        this@newMovieLoadResponse.posterUrl = poster
                        this@newMovieLoadResponse.year = year
                        this@newMovieLoadResponse.plot = description
                        this@newMovieLoadResponse.tags = tags
                        this@newMovieLoadResponse.score = Score.from10(subject?.imdbRatingValue)
                        this@newMovieLoadResponse.actors = actors
                        this@newMovieLoadResponse.recommendations = recommendations
                        addTrailer(trailer, addRaw = true)
                    }
                }
            } else {
                newTvSeriesLoadResponse(title, url, TvType.TvSeries,
                    document?.resource?.seasons?.map { seasons ->
                        (if (seasons.allEp.isNullOrEmpty()) (1..seasons.maxEp!!) else seasons.allEp.split(",")
                            .map { it.toInt() })
                            .map { episode ->
                                newEpisode(
                                    LoadData(
                                        id,
                                        seasons.se,
                                        episode,
                                        subject?.detailPath
                                    ).toJson()
                                ) {
                                    this.season = seasons.se
                                    this.episode = episode
                                }
                            }
                    }?.flatten() ?: emptyList()) {
                    coroutineScope { // Gunakan coroutineScope
                        this@newTvSeriesLoadResponse.posterUrl = poster
                        this@newTvSeriesLoadResponse.year = year
                        this@newTvSeriesLoadResponse.plot = description
                        this@newTvSeriesLoadResponse.tags = tags
                        this@newTvSeriesLoadResponse.score = Score.from10(subject?.imdbRatingValue)
                        this@newTvSeriesLoadResponse.actors = actors
                        this@newTvSeriesLoadResponse.recommendations = recommendations
                        addTrailer(trailer, addRaw = true)
                    }
                }
            }
        } catch (e: Exception) {
            logError("Error loading details for URL $url: ${e.message}", e)
            throw ErrorLoadingException("Gagal memuat detail: ${e.message}")
        }
    }

// ... (Kode lainnya)
