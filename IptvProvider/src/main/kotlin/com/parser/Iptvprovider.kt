package com.parser

import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newLiveSearchResponse
import com.lagradost.cloudstream3.newLiveStreamLoadResponse
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink

/**
 * Exposes an M3U playlist to CloudStream as browsable live channels.
 *
 * All parsing is delegated to [M3uParser]; this class only translates between
 * [Channel] objects and CloudStream's response types. That split is what lets
 * the parsing logic be tested without a device.
 *
 * Channel data is carried between calls by serialising [Channel] to JSON and
 * using that string as the item URL. CloudStream only guarantees that this
 * string round-trips, so packing the data into it avoids re-fetching the
 * playlist on every navigation.
 */
class IptvProvider : MainAPI() {

    override var name = Config.PROVIDER_NAME
    override var mainUrl = Config.PLAYLIST_URL
    override var lang = Config.PROVIDER_LANG

    override val supportedTypes = setOf(TvType.Live)
    override val hasMainPage = true
    override val hasDownloadSupport = false

    /**
     * Parsed playlist, cached for the lifetime of the provider instance.
     *
     * Without this, every search keystroke would refetch the playlist.
     */
    private var cachedChannels: List<Channel>? = null

    private suspend fun channels(): List<Channel> {
        cachedChannels?.let { return it }

        val playlist = app.get(Config.PLAYLIST_URL).text
        val parsed = M3uParser.parse(playlist)

        cachedChannels = parsed
        return parsed
    }

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest,
    ): HomePageResponse {
        val items = channels().map { it.toSearchResponse() }

        return newHomePageResponse(
            name = "All Channels",
            list = items,
            hasNext = false,
        )
    }

    override suspend fun search(query: String): List<SearchResponse> =
        channels()
            .filter { it.name.contains(query, ignoreCase = true) }
            .map { it.toSearchResponse() }

    override suspend fun load(url: String): LoadResponse {
        val channel = parseJson<Channel>(url)

        return newLiveStreamLoadResponse(
            name = channel.name,
            url = url,
            dataUrl = url,
        ) {
            this.posterUrl = channel.logoUrl
            this.plot = channel.groupTitle
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val channel = parseJson<Channel>(data)

        callback(
            newExtractorLink(
                source = name,
                name = channel.name,
                url = channel.streamUrl,
                // Playlist entries are direct HLS endpoints, so there is no
                // scraping step here. This is why the project skips the hardest
                // part of a normal CloudStream provider.
                type = ExtractorLinkType.M3U8,
            ) {
                this.referer = ""
                this.quality = Qualities.Unknown.value
            }
        )

        return true
    }

    /** Packs a [Channel] into a CloudStream search result. */
    private fun Channel.toSearchResponse(): SearchResponse {
        val logo = logoUrl
        val payload = toJson()

        return newLiveSearchResponse(
            name = name,
            url = payload,
            type = TvType.Live,
        ) {
            this.posterUrl = logo
        }
    }
}