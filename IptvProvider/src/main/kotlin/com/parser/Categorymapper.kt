package com.parser

/**
 * Normalises raw `group-title` values into a consistent set of categories.
 *
 * Playlists are inconsistent about how they label channels. The same genre may
 * appear as "News", "NEWS", or "News/Politics" depending on who maintained the
 * list, and a single channel may carry several labels at once
 * (`group-title="Classic;Movies"`).
 *
 * This class resolves those into canonical names via pattern matching. Values it
 * does not recognise are passed through unchanged rather than discarded, so the
 * plugin still produces sensible categories for playlists whose vocabulary
 * differs from the one these patterns were written against.
 *
 * Pure Kotlin with no Android or CloudStream dependencies, so it is unit
 * testable alongside [M3uParser].
 */
object CategoryMapper {

    /** Shown for channels with no usable category. Always sorted last. */
    const val OTHER = "Other"

    /**
     * Labels that qualify a genre rather than naming one.
     *
     * "Classic;Movies" describes one channel, not two. Dropping the modifier
     * folds it into the genre it accompanies instead of creating a spurious
     * "Classic" category.
     */
    private val MODIFIERS = setOf(
        "classic", "hd", "sd", "fhd", "uhd", "4k", "8k", "live", "vip", "backup",
    )

    /** Values that explicitly mean "no category". */
    private val UNDEFINED = setOf("undefined", "unknown", "none", "n/a", "other")

    /**
     * Ordered pattern list. The first match wins, so more specific patterns must
     * come before broader ones.
     */
    private val PATTERNS: List<Pair<Regex, String>> = listOf(
        Regex("""news|weather|current.?affairs""") to "News & Weather",
        Regex("""sport|football|soccer|cricket|athletic|racing|wrestl""") to "Sports",
        Regex("""kid|child|cartoon|family|youth|toon""") to "Kids & Family",
        Regex("""movie|cinema|film""") to "Movies",
        Regex("""music|song|radio""") to "Music",
        Regex("""documentar|science|nature|history|wildlife|tech|educat""") to "Documentary & Science",
        Regex("""business|financ|econom|market|legislat|politic|government|parliament""") to "Business & Politics",
        Regex("""lifestyle|travel|food|cook|home|garden|reality|fashion|health|auto""") to "Lifestyle",
        Regex("""religio|spiritual|faith|church|islam|hindu|christian|culture|shopping""") to "Specialty & Culture",
        Regex("""local|regional|broadcast|affiliate|provincial""") to "Local & Broadcast",
        Regex("""entertain|general|series|drama|comedy|sitcom|variety""") to "General Entertainment",
    )

    /** Display order. Anything absent here sorts alphabetically after these. */
    private val DISPLAY_ORDER = listOf(
        "News & Weather",
        "General Entertainment",
        "Movies",
        "Sports",
        "Kids & Family",
        "Music",
        "Lifestyle",
        "Documentary & Science",
        "Business & Politics",
        "Local & Broadcast",
        "Specialty & Culture",
    )

    /**
     * Returns the categories a `group-title` value belongs to.
     *
     * Multi-valued titles are split on `;`, modifiers are dropped, and each
     * remaining token is matched against [PATTERNS]. A channel may legitimately
     * belong to more than one category.
     *
     * Returns `[OTHER]` when [groupTitle] is null, blank, or contains nothing
     * but modifiers and undefined markers.
     */
    fun categorize(groupTitle: String?): List<String> {
        if (groupTitle.isNullOrBlank()) return listOf(OTHER)

        val categories = groupTitle
            .split(';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .filterNot { it.lowercase() in MODIFIERS }
            .filterNot { it.lowercase() in UNDEFINED }
            .map { canonicalise(it) }
            .distinct()

        return categories.ifEmpty { listOf(OTHER) }
    }

    /**
     * Maps one raw token to a canonical name, or returns it title-cased if no
     * pattern matches.
     */
    private fun canonicalise(token: String): String {
        val lower = token.lowercase()

        PATTERNS.forEach { (pattern, category) ->
            if (pattern.containsMatchIn(lower)) return category
        }

        // Unrecognised, but not meaningless. Keeping it means an unfamiliar
        // playlist still gets usable categories instead of one giant "Other".
        return token.replaceFirstChar { it.uppercase() }
    }

    /**
     * Sorts categories for display: known categories in [DISPLAY_ORDER] first,
     * then unrecognised ones alphabetically, with [OTHER] always last.
     */
    fun sortForDisplay(categories: Collection<String>): List<String> =
        categories.sortedWith(
            compareBy(
                { if (it == OTHER) 2 else if (it in DISPLAY_ORDER) 0 else 1 },
                { DISPLAY_ORDER.indexOf(it).takeIf { i -> i >= 0 } ?: 0 },
                { it },
            )
        )
}