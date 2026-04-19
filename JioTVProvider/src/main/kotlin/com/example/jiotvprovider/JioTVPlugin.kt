package com.example.jiotvprovider

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class JioTVPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(JioTVProvider())
    }
}
