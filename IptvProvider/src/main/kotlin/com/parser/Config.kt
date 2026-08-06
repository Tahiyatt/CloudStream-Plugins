package com.parser

/**
 * Plugin configuration.
 *
 * The playlist URL is hardcoded for the MVP (decision D3). Isolating it here
 * means promoting it to a user-editable setting later touches only this file.
 */
object Config {

    /**
     * Bengali-language channels from iptv-org (decision D7).
     *
     * iptv-org is a community-maintained index of publicly available broadcast
     * streams. This plugin ships no content; it only reads this playlist.
     */
    const val PLAYLIST_URL = "https://iptv-org.github.io/iptv/languages/ben.m3u"
    const val STATUS_URL ="https://raw.githubusercontent.com/Tahiyatt/CloudStream-Plugins/builds/status.json"



    /** Name shown in CloudStream's provider list. */
    const val PROVIDER_NAME = "IPTV (Bengali)"

    /** ISO 639-1 code. Must match `language` in build.gradle.kts. */
    const val PROVIDER_LANG = "en"
}