package  com.parser
/**
 * Parses extended M3U (`#EXTM3U`) playlists into [Channel] objects.
 *
 * The extended M3U format pairs a metadata line with the URL line that follows it:
 *
 * ```
 * #EXTM3U
 * #EXTINF:-1 tvg-id="BBCOne.uk" tvg-logo="https://ex.com/l.png" group-title="UK",BBC One
 * https://example.com/bbcone/index.m3u8
 * ```
 *
 * The parser is intentionally forgiving. Real playlists are messy, and one bad
 * entry in a list of thousands should never fail the whole load, so malformed
 * entries are skipped rather than raising an exception.
 *
 * Has no Android or CloudStream dependencies and is safe to unit test on the JVM.
 */
object M3uParser {

    private const val EXTINF_PREFIX = "#EXTINF:"

    /** Matches `key="value"` attribute pairs on an `#EXTINF` line. */
    private val ATTRIBUTE_REGEX = Regex("""([\w-]+)="([^"]*)"""")

    /**
     * Parses [content] and returns the channels it contains, in playlist order.
     *
     * Entries missing a name or a URL are skipped. Returns an empty list for
     * empty or entirely malformed input; never throws on bad data.
     */
    fun parse(content: String): List<Channel> {
        val channels = mutableListOf<Channel>()

        // Holds the metadata from an #EXTINF line until we reach its URL line.
        var pending: ExtInf? = null

        for (rawLine in content.lineSequence()) {
            val line = rawLine.trim()

            when {
                line.isEmpty() -> continue

                line.startsWith(EXTINF_PREFIX) -> {
                    // A second #EXTINF before any URL means the previous entry
                    // had no stream. Overwriting discards it, which is correct.
                    pending = parseExtInf(line)
                }

                // Skips #EXTM3U, #EXTGRP, #EXTVLCOPT, #KODIPROP and comments.
                // These can appear between an #EXTINF and its URL, so they must
                // not clear the pending entry.
                line.startsWith("#") -> continue

                else -> {
                    // A non-comment line is the URL for the pending #EXTINF.
                    // A URL with no preceding #EXTINF has no metadata: skip it.
                    val entry = pending ?: continue
                    pending = null

                    entry.toChannel(streamUrl = line)?.let { channels += it }
                }
            }
        }

        return channels
    }

    /**
     * Splits an `#EXTINF` line into its attributes and display name.
     *
     * The line's structure is `#EXTINF:<duration> <attributes>,<display name>`.
     * Attribute values may themselves contain commas — `group-title="News, Talk"`
     * is legal — so the split uses the first comma that falls outside quotes.
     */
    private fun parseExtInf(line: String): ExtInf {
        val body = line.removePrefix(EXTINF_PREFIX)
        val separator = indexOfUnquotedComma(body)

        val metadata = if (separator >= 0) body.substring(0, separator) else body
        val displayName = if (separator >= 0) body.substring(separator + 1).trim() else ""

        val attributes = ATTRIBUTE_REGEX.findAll(metadata)
            .associate { match ->
                // Attribute names are lowercased so that TVG-ID and tvg-id both work.
                match.groupValues[1].lowercase() to match.groupValues[2].trim()
            }

        return ExtInf(attributes, displayName)
    }

    /**
     * Returns the index of the first comma outside a quoted section, or -1 if
     * there is none.
     */
    private fun indexOfUnquotedComma(text: String): Int {
        var inQuotes = false

        for (index in text.indices) {
            when (text[index]) {
                '"' -> inQuotes = !inQuotes
                ',' -> if (!inQuotes) return index
            }
        }

        return -1
    }

    /** Metadata from one `#EXTINF` line, awaiting the URL line that follows. */
    private data class ExtInf(
        val attributes: Map<String, String>,
        val displayName: String,
    ) {
        /**
         * Combines this metadata with [streamUrl], or returns null if the entry
         * is unusable (no name, or no URL).
         */
        fun toChannel(streamUrl: String): Channel? {
            if (streamUrl.isBlank()) return null

            // Some playlists leave the display name empty and only set tvg-name.
            val name = displayName.ifBlank { attributes["tvg-name"].orEmpty() }
            if (name.isBlank()) return null

            return Channel(
                name = name,
                streamUrl = streamUrl,
                tvgId = attributes["tvg-id"]?.takeIf { it.isNotBlank() },
                logoUrl = attributes["tvg-logo"]?.takeIf { it.isNotBlank() },
                groupTitle = attributes["group-title"]?.takeIf { it.isNotBlank() },
                language = attributes["tvg-language"]?.takeIf { it.isNotBlank() },
                country = attributes["tvg-country"]?.takeIf { it.isNotBlank() },
            )
        }
    }
}