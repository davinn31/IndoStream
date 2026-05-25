package com.nodrakorid

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addScore
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import java.net.URI
import org.jsoup.nodes.Element

class Nodrakorid : MainAPI() {

    override var mainUrl = "https://nodrakorid.ink"
    private var directUrl: String? = null
    override var name = "Nodrakorid"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes = setOf(TvType.TvSeries, TvType.AsianDrama)

    override val mainPage =
            mainPageOf(
                    "category/drama-korea/page/%d/" to "Drama Korea",
                    "category/drama-china/page/%d/" to "Drama China",
                    "category/drama-jepang/page/%d/" to "Drama Jepang",
                    "category/drama-thailand/page/%d/" to "Drama Thailand",
                    "category/film-korea/page/%d/" to "Film Korea",
            )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val data = request.data.format(page)
        val document = app.get("$mainUrl/$data").document
        val home = document.select("article.item").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("h2.entry-title > a")?.text()?.trim() ?: return null
        val href = fixUrl(this.selectFirst("a")!!.attr("href"))
        val posterUrl = fixUrlNull(this.selectFirst("a > img")?.getImageAttr())
        return newAnimeSearchResponse(title, href, TvType.TvSeries) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("${mainUrl}?s=$query&post_type[]=post&post_type[]=tv").document
        val results = document.select("article.item").mapNotNull { it.toSearchResult() }
        return results
    }

    override suspend fun load(url: String): LoadResponse {
        val fetch = app.get(url)
        directUrl = getBaseUrl(fetch.url)
        val document = fetch.document

        val title =
                document.selectFirst("h1.entry-title")
                        ?.text()
                        ?.substringBefore("Season")
                        ?.substringBefore("Episode")
                        ?.trim()
                        .toString()
        val poster = fixUrlNull(document.selectFirst("figure.pull-left > img")?.getImageAttr())
        val tags =
                document.select("div.gmr-moviedata strong:contains(Genre:) > a").map { it.text() }

        val year =
                document.select("div.gmr-moviedata strong:contains(Year:) > a")
                        .text()
                        .trim()
                        .toIntOrNull()
        val description = document.selectFirst("div[itemprop=description] > p")?.text()?.trim()
        val trailer = document.selectFirst("ul.gmr-player-nav li a.gmr-trailer-popup")?.attr("href")
        val rating =
                document.selectFirst("div.gmr-meta-rating > span[itemprop=ratingValue]")
                        ?.text()
        val actors =
                document.select("div.gmr-moviedata").last()?.select("span[itemprop=actors]")?.map {
                    it.select("a").text()
                }

        val episodes =
                document.select("div.vid-episodes a, div.gmr-listseries a")
                        .mapNotNull { eps ->
                            val href = fixUrl(eps.attr("href"))
                            val name = eps.text()
                            val episode =
                                    name.split(" ")
                                            .lastOrNull()
                                            ?.filter { it.isDigit() }
                                            ?.toIntOrNull()
                            val season =
                                    name.split(" ")
                                            .firstOrNull()
                                            ?.filter { it.isDigit() }
                                            ?.toIntOrNull()
                            newEpisode(href) {
                                this.name = name
                                this.episode = episode
                                this.season = if (name.contains(" ")) season else null
                            }
                        }
                        .filter { it.episode != null }
        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
            this.posterUrl = poster
            this.year = year
            this.plot = description
            this.tags = tags
            addScore(rating)
            addActors(actors)
            addTrailer(trailer)
        }
    }

    override suspend fun loadLinks(
            data: String,
            isCasting: Boolean,
            subtitleCallback: (SubtitleFile) -> Unit,
            callback: (ExtractorLink) -> Unit
    ): Boolean {

        val document = app.get(data).document
        val id = document.selectFirst("div#muvipro_player_content_id")?.attr("data-id")

        if (id.isNullOrEmpty()) {
            document.select("ul#muvipro-player-tabs > li > a").amap { ele ->
                val iframe =
                        app.get(fixUrl(ele.attr("href")))
                                .document
                                .selectFirst("div.gmr-embed-responsive iframe")
                                .getIframeAttr()
                                ?.let { httpsify(it) }
                                ?: return@amap

                loadExtractor(iframe, "$directUrl/", subtitleCallback, callback)
            }
        } else {
            document.select("div.tab-content-ajax").amap { ele ->
                val server =
                        app.post(
                                        "$directUrl/wp-admin/admin-ajax.php",
                                        data =
                                                mapOf(
                                                        "action" to "muvipro_player_content",
                                                        "tab" to ele.attr("id"),
                                                        "post_id" to "$id"
                                                )
                                )
                                .document
                                .select("iframe")
                                .attr("src")
                                .let { httpsify(it) }

                loadExtractor(server, "$directUrl/", subtitleCallback, callback)
            }
        }

        return true
    }

    private fun Element.getImageAttr(): String {
        return when {
            this.hasAttr("data-src") -> this.attr("abs:data-src")
            this.hasAttr("data-lazy-src") -> this.attr("abs:data-lazy-src")
            this.hasAttr("srcset") -> this.attr("abs:srcset").substringBefore(" ")
            else -> this.attr("abs:src")
        }
    }

    private fun Element?.getIframeAttr(): String? {
        return this?.attr("data-litespeed-src").takeIf { it?.isNotEmpty() == true }
                ?: this?.attr("src")
    }

    private fun getBaseUrl(url: String): String {
        return URI(url).let { "${it.scheme}://${it.host}" }
    }
}
