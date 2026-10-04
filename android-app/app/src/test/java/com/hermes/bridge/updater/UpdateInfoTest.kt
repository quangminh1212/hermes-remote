package com.hermes.bridge.updater

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateInfoTest {

    @Test
    fun `parses versionName and versionCode from a plus-suffixed tag`() {
        assertEquals("1.2.0" to 12, UpdateSource.parseVersion("v1.2.0+12"))
        assertEquals("1.2.0" to 12, UpdateSource.parseVersion("hermes-1.2.0+12"))
        assertEquals("2.0.0" to 7, UpdateSource.parseVersion("hermes-2.0.0+7.apk"))
    }

    @Test
    fun `rejects tags without an explicit versionCode`() {
        assertNull(UpdateSource.parseVersion("v1.2.0"))
        assertNull(UpdateSource.parseVersion("1.2.0"))
        assertNull(UpdateSource.parseVersion("v1.2.0+"))
        assertNull(UpdateSource.parseVersion(""))
        assertNull(UpdateSource.parseVersion(null))
    }

    private val releaseJson = """
        {
          "tag_name": "v1.3.0+13",
          "body": "Fixes the quick-action row layout.",
          "html_url": "https://github.com/quangminh1212/hermes-remote/releases/tag/v1.3.0%2B13",
          "assets": [
            {"name": "notes.txt", "browser_download_url": "https://example.com/notes.txt"},
            {"name": "hermes-1.3.0+13.apk", "browser_download_url": "https://example.com/hermes-1.3.0+13.apk"}
          ]
        }
    """.trimIndent()

    @Test
    fun `parses the apk asset and version from a github release`() {
        val info = UpdateSource.parseGithubRelease(releaseJson)!!
        assertEquals(13, info.versionCode)
        assertEquals("1.3.0", info.versionName)
        assertEquals("https://example.com/hermes-1.3.0+13.apk", info.apkUrl)
        assertTrue(info.notes.contains("quick-action"))
    }

    @Test
    fun `falls back to the asset name when the tag has no versionCode`() {
        val json = """
            {
              "tag_name": "v1.3.0",
              "assets": [
                {"name": "hermes-1.3.0+13.apk", "browser_download_url": "https://example.com/a.apk"}
              ]
            }
        """.trimIndent()
        val info = UpdateSource.parseGithubRelease(json)!!
        assertEquals(13, info.versionCode)
        assertEquals("1.3.0", info.versionName)
    }

    @Test
    fun `returns null when there is no installable apk`() {
        val json = """
            {"tag_name": "v1.3.0+13", "assets": [
              {"name": "source.zip", "browser_download_url": "https://example.com/s.zip"}
            ]}
        """.trimIndent()
        assertNull(UpdateSource.parseGithubRelease(json))
    }

    @Test
    fun `returns null on malformed or empty json`() {
        assertNull(UpdateSource.parseGithubRelease(null))
        assertNull(UpdateSource.parseGithubRelease(""))
        assertNull(UpdateSource.parseGithubRelease("not json at all"))
        assertNull(UpdateSource.parseGithubRelease("{}"))
    }
}
