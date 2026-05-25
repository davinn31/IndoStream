package com.pusatfilm

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

open class Kotakajaib : ExtractorApi() {
    override val name = "Kotakajaib"
    override val mainUrl = "https://kotakajaib.me"
    override val requiresReferer = true

    override suspend fun getUrl(
            url: String,
            referer: String?,
            subtitleCallback: (SubtitleFile) -> Unit,
            callback: (ExtractorLink) -> Unit
    ) {
        app.get(url, referer = referer).document.select("ul#dropdown-server li a").amap {
            loadExtractor(
                    base64Decode(it.attr("data-frame")),
                    "$mainUrl/",
                    subtitleCallback,
                    callback
            )
        }
    }
}
