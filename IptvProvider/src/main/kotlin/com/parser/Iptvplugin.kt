package com.parser

import android.content.Context
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

/**
 * Entry point CloudStream calls when it loads this .cs3 file.
 *
 * The @CloudstreamPlugin annotation is what the build scans for when generating
 * manifest.json, so the class must carry it or the packaged plugin has nothing
 * to start from.
 */
@CloudstreamPlugin
class IptvPlugin : BasePlugin() {

    override fun load() {
        // Registers the provider so it appears in CloudStream's source list.
        registerMainAPI(IptvProvider())
    }
}