private fun parseTvShowItem(item: JSONObject): SearchResponse? {
    return try {
        val tmdbId = item.optString("tmdb_id", "").takeIf { it.isNotBlank() }
        val imdbId = item.optString("imdb_id", "").takeIf { it.isNotBlank() }
        var title = item.optString("title", "")?.trim()
        val posterPath = item.optString("poster", "")
        
        if (title.isNullOrEmpty() || title == "n/A") return null
        
        val posterUrl = if (posterPath.isNotBlank() && posterPath != "n/A") {
            "https://image.tmdb.org/t/p/w500$posterPath"
        } else {
            ""
        }
        
        val year = item.optString("year", "")?.take(4)?.toIntOrNull()
        val dataId = buildDataId("tv", tmdbId, imdbId)
        
        newTvSeriesSearchResponse(title, dataId, TvType.TvSeries) {
            this.posterUrl = posterUrl
            this.year = year
        }
    } catch (e: Exception) {
        null
    }
}
