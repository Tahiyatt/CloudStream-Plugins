package com.parser

import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.HomePageList
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
 * [Channel] objects and CloudStream's response types.
 *
 * Channel data is carried between calls by serialising [Channel] to JSON and
 * using that string as the item URL, so navigation never refetches the playlist.
 */
class IptvProvider : MainAPI() {

    override var name = Config.PROVIDER_NAME
    override var mainUrl = Config.PLAYLIST_URL
    override var lang = Config.PROVIDER_LANG

    override val supportedTypes = setOf(TvType.Live)
    override val hasMainPage = true
    override val hasDownloadSupport = false

    /** Parsed playlist, cached for the lifetime of the provider instance. */
    private var cachedChannels: List<Channel>? = null

    // -----------------------------------------------------------------------
    // Playlist loading
    // -----------------------------------------------------------------------

    /**
     * Returns the parsed playlist, fetching it on first use.
     *
     * Failures are surfaced as [ErrorLoadingException] so the user sees a
     * message explaining what went wrong rather than an empty screen. Nothing
     * is cached unless the fetch succeeds and yields at least one channel, so a
     * transient network failure does not leave the provider permanently empty.
     */
    private suspend fun channels(): List<Channel> {
        cachedChannels?.let { return it }

        val body = try {
            val response = app.get(Config.PLAYLIST_URL, timeout = PLAYLIST_TIMEOUT_SECONDS)

            if (!response.isSuccessful) {
                throw ErrorLoadingException(
                    "Playlist server returned HTTP ${response.code}. " +
                            "The playlist URL may have moved or been removed."
                )
            }

            response.text
        } catch (e: ErrorLoadingException) {
            throw e
        } catch (e: Exception) {
            // Almost always no connectivity, DNS failure, or a timeout. The
            // underlying exception message is rarely meaningful to a user.
            throw ErrorLoadingException(
                "Could not reach the playlist. Check your internet connection."
            )
        }

        if (body.isBlank()) {
            throw ErrorLoadingException("The playlist is empty.")
        }

        val parsed = M3uParser.parse(body)

        if (parsed.isEmpty()) {
            // Reached the server and got content, but nothing parsed. Usually
            // means the URL points at something that is not an M3U playlist.
            throw ErrorLoadingException(
                "No channels found. The URL may not point to an M3U playlist."
            )
        }

        cachedChannels = parsed
        return parsed
    }

    // -----------------------------------------------------------------------
    // Browsing
    // -----------------------------------------------------------------------
//
//    override suspend fun getMainPage(
//        page: Int,
//        request: MainPageRequest,
//    ): HomePageResponse {
//        val items = channels().map { it.toSearchResponse() }
//
//        return newHomePageResponse(
//            name = "All Channels",
//            list = items,
//            hasNext = false,
//        )
//    }
    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest,
    ): HomePageResponse {
        val all = channels()
        val status = StatusIndex.get()

        // Suspect URLs stay in the normal rows on purpose. Only unambiguous
        // evidence pulls a channel out of the categories — a false "dead"
        // hides something that works, which is the worse error.
        val (dead, live) = all.partition { it.streamUrl in status.dead }

        // A channel can carry several group-title values, so it may legitimately
        // appear under more than one category.
        val byCategory: Map<String, List<Channel>> = live
            .flatMap { channel ->
                CategoryMapper.categorize(channel.groupTitle).map { it to channel }
            }
            .groupBy({ it.first }, { it.second })

        val lists = mutableListOf<HomePageList>()

        CategoryMapper.sortForDisplay(byCategory.keys).forEach { category ->
            val inCategory = byCategory[category].orEmpty()
            if (inCategory.isEmpty()) return@forEach

            lists += HomePageList(
                name = category,
                list = inCategory.map { it.toSearchResponse() },
                isHorizontalImages = false,
            )
        }

        // Full unfiltered list, live channels only.
        if (live.isNotEmpty()) {
            lists += HomePageList(
                name = "All Channels",
                list = live.map { it.toSearchResponse() },
                isHorizontalImages = false,
            )
        }

        // Dead channels are shown, not dropped. A stream that 404s today may
        // return next week, and a visible "Unavailable" row tells the user the
        // plugin knows — rather than looking like channels vanished at random.
        if (dead.isNotEmpty()) {
            lists += HomePageList(
                name = "Unavailable",
                list = dead.map { it.toSearchResponse() },
                isHorizontalImages = false,
            )
        }

        return newHomePageResponse(lists, hasNext = false)
    }



    override suspend fun search(query: String): List<SearchResponse> =
        channels()
            .filter { it.name.contains(query, ignoreCase = true) }
            .map { it.toSearchResponse() }

    override suspend fun load(url: String): LoadResponse {
        val channel = decodeChannel(url)

        return newLiveStreamLoadResponse(
            name = channel.name,
            url = url,
            dataUrl = url,
        ) {
            this.posterUrl = channel.logoUrl
            this.plot = channel.groupTitle
        }
    }

    // -----------------------------------------------------------------------
    // Playback
    // -----------------------------------------------------------------------

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val channel = decodeChannel(data)

        if (probe(channel.streamUrl) == Verdict.DEAD) {
            throw ErrorLoadingException(
                "${channel.name} appears to be offline. " +
                        "Community playlists often contain streams that have stopped broadcasting."
            )
        }

        callback(
            newExtractorLink(
                source = name,
                name = channel.name,
                url = channel.streamUrl,
                type = ExtractorLinkType.M3U8,
            ) {
                this.referer = ""
                this.quality = Qualities.Unknown.value
                this.headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"
                )
            }
        )

        return true
    }

    /**
     * Best-effort check for a stream that is definitively gone.
     *
     * Deliberately conservative. Many live streams reject probe requests while
     * playing correctly in the player: they require particular headers, refuse
     * non-player user agents, or answer only to a full HLS request. Treating
     * every unhappy response as "offline" would break working channels.
     *
     * So this returns true only for status codes that mean the resource is
     * genuinely absent. Timeouts, connection errors, 403s and anything else
     * ambiguous fall through and let the player decide, which is the behaviour
     * that was already working before this check existed.
     */


    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** Outcome of an on-device stream probe. */
    private enum class Verdict { DEAD, UNKNOWN }

    /**
     * Cheap on-device liveness check, run just before playback.
     *
     * Two rungs only. The full probe ladder — segment fetches, master playlist
     * resolution — belongs in CI, where bandwidth and latency are free.
     *
     * Returns [Verdict.UNKNOWN] for anything ambiguous, which means "hand it to
     * the player anyway". Timeouts, 403s and connection errors all land here:
     * many live streams reject probe requests while playing fine, so treating
     * every unhappy response as death would break working channels.
     */
    private suspend fun probe(url: String): Verdict {
        val response = try {
            app.get(url, timeout = STREAM_PROBE_TIMEOUT_SECONDS)
        } catch (e: Exception) {
            return Verdict.UNKNOWN
        }

        // Rung 1: status codes that mean the resource is genuinely gone.
        if (response.code == 404 || response.code == 410) return Verdict.DEAD

        // Rung 2: a 200 that isn't a manifest. Usually an HTML login or error
        // page served with a success code — a large share of real deaths that
        // status codes alone miss.
        val body = try {
            response.text.trimStart()
        } catch (e: Exception) {
            return Verdict.UNKNOWN
        }

        if (body.isNotEmpty() && !body.startsWith("#EXTM3U")) return Verdict.DEAD

        return Verdict.UNKNOWN
    }

    /**
     * Decodes the JSON payload carried in a CloudStream item URL.
     *
     * Fails loudly rather than returning null: a malformed payload means the
     * provider itself produced bad data, which is a bug rather than a condition
     * to recover from silently.
     */
    private fun decodeChannel(payload: String): Channel = try {
        parseJson<Channel>(payload)
    } catch (e: Exception) {
        throw ErrorLoadingException("Could not read channel details.")
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

    private companion object {
        const val PLAYLIST_TIMEOUT_SECONDS = 30L

        /** Kept short: this runs before playback and the user is waiting. */
        const val STREAM_PROBE_TIMEOUT_SECONDS = 5L

        /**
         * Only codes that unambiguously mean the resource is gone. 403 is
         * excluded on purpose: streams commonly return it to probe requests and
         * then play normally.
         */

    }
}