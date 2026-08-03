package com.parser

/**
 * A single playable channel parsed from an M3U playlist.
 *
 * This class deliberately has no Android or CloudStream dependencies so that the
 * parser can be unit tested on the JVM without an emulator or device.
 *
 * @property name Display name shown to the user. Taken from the text after the
 *   comma on the `#EXTINF` line, falling back to `tvg-name` when that is blank.
 * @property streamUrl Direct URL to the stream, usually an HLS `.m3u8` endpoint.
 * @property tvgId EPG identifier (`tvg-id`), if the playlist supplies one.
 * @property logoUrl Channel logo (`tvg-logo`), if present.
 * @property groupTitle Category the playlist assigns (`group-title`), e.g. "News".
 * @property language Language code (`tvg-language`), if present.
 * @property country Country code (`tvg-country`), if present.
 */
data class Channel(
    val name: String,
    val streamUrl: String,
    val tvgId: String? = null,
    val logoUrl: String? = null,
    val groupTitle: String? = null,
    val language: String? = null,
    val country: String? = null,
)