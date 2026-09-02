package com.mgafk.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VersionFetcherRoomVersionTest {

    @Test
    fun `reads the version from a room page head`() {
        val html = """
            <!doctype html>
            <html lang="en" translate="no">
            <head>
              <script type="module" crossorigin src="/version/1063/assets/polyfills-lGNsezwW.js"></script>
              <script type="module" crossorigin src="/version/1063/assets/index-BkHXQ1Db.js"></script>
              <link rel="icon" href="/version/1063/app_icon.webp">
            </head>
        """.trimIndent()

        assertEquals("1063", VersionFetcher.parseRoomVersion(html))
    }

    @Test
    fun `accepts the older hash version format`() {
        val html = """<script src="/version/db34dc9/assets/index-BkHXQ1Db.js"></script>"""

        assertEquals("db34dc9", VersionFetcher.parseRoomVersion(html))
    }

    @Test
    fun `ignores non-asset version references`() {
        val html = """<link rel="icon" href="/version/9999/app_icon.webp">"""

        assertNull(VersionFetcher.parseRoomVersion(html))
    }

    @Test
    fun `returns null when the page carries no version`() {
        assertNull(VersionFetcher.parseRoomVersion("<html><body>nope</body></html>"))
    }
}
