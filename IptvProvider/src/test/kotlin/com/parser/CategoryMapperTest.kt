package com.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [CategoryMapper].
 *
 * The fixture cases use the exact `group-title` values present in the iptv-org
 * Bengali playlist, so a change in behaviour against real data shows up here.
 */
class CategoryMapperTest {

    // ---------------------------------------------------------------------
    // Values observed in the real playlist
    // ---------------------------------------------------------------------

    @Test
    fun `maps the observed playlist values to canonical categories`() {
        assertEquals(listOf("News & Weather"), CategoryMapper.categorize("News"))
        assertEquals(listOf("Sports"), CategoryMapper.categorize("Sports"))
        assertEquals(listOf("Movies"), CategoryMapper.categorize("Movies"))
        assertEquals(listOf("Music"), CategoryMapper.categorize("Music"))
        assertEquals(listOf("Kids & Family"), CategoryMapper.categorize("Kids"))
        assertEquals(listOf("Kids & Family"), CategoryMapper.categorize("Family"))
        assertEquals(listOf("Lifestyle"), CategoryMapper.categorize("Travel"))
        assertEquals(listOf("Documentary & Science"), CategoryMapper.categorize("Documentary"))
        assertEquals(listOf("Business & Politics"), CategoryMapper.categorize("Business"))
        assertEquals(listOf("Business & Politics"), CategoryMapper.categorize("Legislative"))
        assertEquals(listOf("Specialty & Culture"), CategoryMapper.categorize("Religious"))
        assertEquals(listOf("Specialty & Culture"), CategoryMapper.categorize("Culture"))
        assertEquals(listOf("General Entertainment"), CategoryMapper.categorize("Entertainment"))
        assertEquals(listOf("General Entertainment"), CategoryMapper.categorize("General"))
    }

    @Test
    fun `treats undefined as other`() {
        assertEquals(listOf(CategoryMapper.OTHER), CategoryMapper.categorize("Undefined"))
    }

    // ---------------------------------------------------------------------
    // Multi-valued titles
    // ---------------------------------------------------------------------

    /**
     * "Classic;Movies" describes a single channel. Treating "Classic" as its own
     * category would split the movie channels across two rows for no reason.
     */
    @Test
    fun `folds modifiers into the genre they accompany`() {
        assertEquals(listOf("Movies"), CategoryMapper.categorize("Classic;Movies"))
        assertEquals(listOf("News & Weather"), CategoryMapper.categorize("Classic;News"))
    }

    @Test
    fun `keeps genuinely distinct categories from a multi-valued title`() {
        val result = CategoryMapper.categorize("Entertainment;Music")

        assertEquals(2, result.size)
        assertTrue(result.contains("General Entertainment"))
        assertTrue(result.contains("Music"))
    }

    @Test
    fun `collapses duplicates that map to the same category`() {
        // Both tokens resolve to Kids & Family; the channel should appear once.
        assertEquals(listOf("Kids & Family"), CategoryMapper.categorize("Kids;Family"))
    }

    @Test
    fun `returns other when a title contains only modifiers`() {
        assertEquals(listOf(CategoryMapper.OTHER), CategoryMapper.categorize("Classic;HD"))
    }

    // ---------------------------------------------------------------------
    // Robustness
    // ---------------------------------------------------------------------

    @Test
    fun `handles null and blank titles`() {
        assertEquals(listOf(CategoryMapper.OTHER), CategoryMapper.categorize(null))
        assertEquals(listOf(CategoryMapper.OTHER), CategoryMapper.categorize(""))
        assertEquals(listOf(CategoryMapper.OTHER), CategoryMapper.categorize("   "))
    }

    @Test
    fun `is case insensitive`() {
        assertEquals(listOf("News & Weather"), CategoryMapper.categorize("NEWS"))
        assertEquals(listOf("Sports"), CategoryMapper.categorize("sports"))
    }

    @Test
    fun `tolerates whitespace around separators`() {
        assertEquals(listOf("Movies"), CategoryMapper.categorize(" Classic ; Movies "))
    }

    /**
     * The plugin is meant to work with any playlist, not just the one it was
     * developed against. An unfamiliar label should become its own category
     * rather than being swept into Other.
     */
    @Test
    fun `passes unrecognised values through instead of discarding them`() {
        assertEquals(listOf("Ethnographic"), CategoryMapper.categorize("ethnographic"))
    }

    // ---------------------------------------------------------------------
    // Display ordering
    // ---------------------------------------------------------------------

    @Test
    fun `sorts known categories before unknown ones and other last`() {
        val sorted = CategoryMapper.sortForDisplay(
            listOf(CategoryMapper.OTHER, "Zebra", "Movies", "News & Weather"),
        )

        assertEquals("News & Weather", sorted[0])
        assertEquals("Movies", sorted[1])
        assertEquals("Zebra", sorted[2])
        assertEquals(CategoryMapper.OTHER, sorted[3])
    }
}