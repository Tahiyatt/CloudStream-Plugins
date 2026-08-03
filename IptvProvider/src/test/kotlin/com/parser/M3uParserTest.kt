package com.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [M3uParser].
 *
 * These run on the JVM, not on a device. The parser has no Android or
 * CloudStream dependencies, which is what makes that possible.
 */
class M3uParserTest {

    // ---------------------------------------------------------------------
    // Fixture-based tests
    // ---------------------------------------------------------------------

    @Test
    fun `parses every usable entry and skips the rest`() {
        val channels = M3uParser.parse(loadFixture("sample-playlist.m3u"))

        // Six usable entries. Three are deliberately unusable: an #EXTINF with
        // no URL, an entry with no name at all, and a bare URL with no #EXTINF.
        assertEquals(6, channels.size)

        assertEquals(
            listOf(
                "BBC One",
                "CNN International",
                "NHK World",
                "Arte",
                "Recovered Entry",
                "Last Channel",
            ),
            channels.map { it.name },
        )
    }

    @Test
    fun `reads all attributes from a fully populated entry`() {
        val cnn = parseFixtureByName("CNN International")

        assertEquals("CNN.us", cnn.tvgId)
        assertEquals("https://example.com/cnn.png", cnn.logoUrl)
        assertEquals("US", cnn.country)
        assertEquals("English", cnn.language)
        assertEquals("https://example.com/cnn/index.m3u8", cnn.streamUrl)
    }

    /**
     * The case most naive parsers get wrong. Splitting on the first comma in the
     * line truncates the attributes and destroys the display name, and it fails
     * silently: most channels still look fine.
     */
    @Test
    fun `handles a comma inside a quoted attribute value`() {
        val cnn = parseFixtureByName("CNN International")

        assertEquals("News, Politics", cnn.groupTitle)
    }

    @Test
    fun `falls back to tvg-name when the display name is blank`() {
        val nhk = parseFixtureByName("NHK World")

        assertEquals("NHK.jp", nhk.tvgId)
        assertEquals("https://example.com/nhk/index.m3u8", nhk.streamUrl)
    }

    @Test
    fun `skips extra directives between an entry and its url`() {
        // #EXTVLCOPT and #KODIPROP sit between this entry's #EXTINF and its URL.
        // They must be ignored without discarding the pending entry.
        val arte = parseFixtureByName("Arte")

        assertEquals("https://example.com/arte/index.m3u8", arte.streamUrl)
    }

    @Test
    fun `recovers after an entry with no url`() {
        val channels = M3uParser.parse(loadFixture("sample-playlist.m3u"))

        // The orphan is dropped, but parsing continues cleanly enough that the
        // entry immediately after it is still picked up.
        assertTrue(channels.none { it.name == "Orphaned Entry" })
        assertTrue(channels.any { it.name == "Recovered Entry" })
    }

    @Test
    fun `leaves absent optional attributes null`() {
        val last = parseFixtureByName("Last Channel")

        assertNull(last.logoUrl)
        assertNull(last.country)
        assertNull(last.language)
    }

    // ---------------------------------------------------------------------
    // Inline edge cases
    // ---------------------------------------------------------------------

    @Test
    fun `returns empty list for empty input`() {
        assertTrue(M3uParser.parse("").isEmpty())
        assertTrue(M3uParser.parse("   \n\n  ").isEmpty())
    }

    @Test
    fun `returns empty list for a header with no entries`() {
        assertTrue(M3uParser.parse("#EXTM3U").isEmpty())
    }

    @Test
    fun `parses an entry with no attributes at all`() {
        val channels = M3uParser.parse(
            """
            #EXTM3U
            #EXTINF:-1,Bare Channel
            https://example.com/bare.m3u8
            """.trimIndent()
        )

        assertEquals(1, channels.size)
        assertEquals("Bare Channel", channels[0].name)
        assertNull(channels[0].tvgId)
    }

    @Test
    fun `treats attribute names case insensitively`() {
        val channels = M3uParser.parse(
            """
            #EXTINF:-1 TVG-ID="Upper.xx" Group-Title="Mixed",Case Test
            https://example.com/case.m3u8
            """.trimIndent()
        )

        assertEquals("Upper.xx", channels[0].tvgId)
        assertEquals("Mixed", channels[0].groupTitle)
    }

    @Test
    fun `tolerates windows line endings`() {
        val channels = M3uParser.parse(
            "#EXTM3U\r\n#EXTINF:-1,CRLF Channel\r\nhttps://example.com/crlf.m3u8\r\n"
        )

        assertEquals(1, channels.size)
        assertEquals("CRLF Channel", channels[0].name)
        // A stray \r left on the end would break playback in a way that is
        // genuinely painful to trace back to the parser.
        assertEquals("https://example.com/crlf.m3u8", channels[0].streamUrl)
    }

    @Test
    fun `preserves playlist order`() {
        val channels = M3uParser.parse(
            """
            #EXTINF:-1,First
            https://example.com/1.m3u8
            #EXTINF:-1,Second
            https://example.com/2.m3u8
            #EXTINF:-1,Third
            https://example.com/3.m3u8
            """.trimIndent()
        )

        assertEquals(listOf("First", "Second", "Third"), channels.map { it.name })
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private fun loadFixture(name: String): String =
        checkNotNull(javaClass.getResourceAsStream("/$name")) {
            "Fixture '$name' not found. It belongs in src/test/resources/."
        }.bufferedReader().use { it.readText() }

    private fun parseFixtureByName(name: String): Channel =
        M3uParser.parse(loadFixture("sample-playlist.m3u"))
            .firstOrNull { it.name == name }
            ?: error("No channel named '$name' was parsed.")



}

