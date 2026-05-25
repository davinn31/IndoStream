package com.gomov

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class GomovPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Gomov())
        registerExtractorAPI(Chillx())
        registerExtractorAPI(Watchx())
        registerExtractorAPI(Boosterx())
        registerExtractorAPI(Dhtpre())
        registerExtractorAPI(Filelions())
        registerExtractorAPI(LocalVidHidePro())
        registerExtractorAPI(LocalJWPlayer())
    }
}
