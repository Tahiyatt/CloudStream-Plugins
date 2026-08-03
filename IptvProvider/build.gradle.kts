// Plugin version. Bump this on each release you want CloudStream to offer as
// an update. Must be an integer, not a semver string.
version = 1

cloudstream {
    description = "Generic IPTV provider. Loads channels from any M3U/M3U8 playlist URL."
    authors = listOf("Tahiyatt")

    /**
     * Status codes the app understands:
     * 0 = down, 1 = ok, 2 = slow, 3 = beta-only
     */
    status = 1

    // Must match the TvType your provider declares in supportedTypes.
    // If "Live" is disabled in the app's content-type filter, the plugin hides.
    tvTypes = listOf("Live")

    // REQUIRED. A missing or mismatched language means the plugin never appears
    // in the extensions list, with no error to explain why.
    language = "en"

//    iconUrl = "https://www.google.com/s2/favicons?domain=iptv-org.github.io&sz=%size%"
}

dependencies {
    // JUnit 4 is the default for Android library modules. These tests run on the
    // JVM via testDebugUnitTest, so no emulator or device is involved.
    testImplementation("junit:junit:4.13.2")
}