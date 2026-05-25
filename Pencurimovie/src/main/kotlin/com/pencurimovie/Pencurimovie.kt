package com.pencurimovie

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class Pencurimovie : MainAPI() {
    override var mainUrl = "https://ww73.pencurimovie.bond"
    override var name = "Pencurimovie"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.Anime, TvType.Cartoon)

    override val mainPage =
            mainPageOf(
                    "movies" to "Latest Movies",
                    "series" to "TV Series",
                    "most-rating" to "Most Rating Movies",
                    "top-imdb" to "Top IMDB Movies",
                    "country/malaysia" to "Malaysia Movies",
                    "country/indonesia" to "Indonesia Movies",
                    "country/india" to "India Movies",
                    "country/japan" to "Japan Movies",
                    "country/thailand" to "Thailand Movies",
                    "country/china" to "China Movies",
            )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("$mainUrl/${request.data}/page/$page").document
        val home = document.select("div.ml-item").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("a")?.attr("oldtitle")?.substringBefore("(") ?: return null
        val href = fixUrl(this.selectFirst("a")!!.attr("href"))
        val posterUrl = fixUrlNull(this.selectFirst("a img")?.attr("data-original"))
        val quality = getQualityFromString(this.select("span.mli-quality").text())
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
            addQuality(quality.toString())
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("${mainUrl}?s=$query").document
        return document.select("div.ml-item").mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val title =
                document.selectFirst("div.mvic-desc h3")
                        ?.text()
                        ?.trim()
                        ?.substringBefore("(")
                        ?: ""
        val poster = document.selectFirst("meta[property=og:image]")?.attr("content")
        val description = document.selectFirst("div.desc p.f-desc")?.text()?.trim()
        val tvtag = if (url.contains("series")) TvType.TvSeries else TvType.Movie
        val trailer = document.selectFirst("meta[itemprop=embedUrl]")?.attr("content")
        val genre = document.select("div.mvic-info p:contains(Genre) a").map { it.text() }
        val actors = document.select("div.mvic-info p:contains(Actors) a").map { it.text() }
        val year =
                document.selectFirst("div.mvic-info p:contains(Release) a")
                        ?.text()
                        ?.toIntOrNull()
        val recommendation = document.select("div.ml-item").mapNotNull { it.toSearchResult() }
        
        return if (tvtag == TvType.TvSeries) {
            val episodes = mutableListOf<Episode>()
            document.select("div.tvseason").forEach { info ->
                val season =
                        info.selectFirst("strong")?.text()?.substringAfter("Season")?.trim()?.toIntOrNull()
                info.select("div.les-content a").forEach {
                    val name = it.text().substringAfter("-").trim()
                    val href = it.attr("href")
                    val rawepisode =
                            it.text()
                                    .substringAfter("Episode")
                                    .substringBefore("-")
                                    .trim()
                                    .toIntOrNull()
                    episodes.add(
                            newEpisode(href){
								this.episode = rawepisode
								this.name = name
								this.season = season
							}
                    )
                }
            }

            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = description
                this.tags = genre
                this.year = year
                addTrailer(trailer)
                addActors(actors)
                this.recommendations = recommendation
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = description
                this.tags = genre
                this.year = year
                addTrailer(trailer)
                addActors(actors)
                this.recommendations = recommendation
            }
        }
    }

    override suspend fun loadLinks(
            data: String,
            isCasting: Boolean,
            subtitleCallback: (SubtitleFile) -> Unit,
            callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        document.select("div.movieplay iframe").amap {
            val href = it.attr("data-src")
            loadExtractor(href, subtitleCallback, callback)
        }
        return true
    }
}
