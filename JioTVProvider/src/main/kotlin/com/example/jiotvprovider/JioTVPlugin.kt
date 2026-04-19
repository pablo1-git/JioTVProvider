package com.example.jiotvprovider

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class JioTVPlugin : Plugin() {
    override fun load(context: Context) {
        // Register our provider so CloudStream knows about it
        registerMainAPI(JioTVProvider())
    }
}
