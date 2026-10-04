package com.hermes.bridge.updater

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class UpdateCheckerTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun release(versionCode: Int, name: String = "1.0.0") = """
        {
          "tag_name": "v$name+$versionCode",
          "assets": [
            {"name": "hermes-$name+$versionCode.apk",
             "browser_download_url": "https://example.com/hermes-$name+$versionCode.apk"}
          ]
        }
    """.trimIndent()

    @Test
    fun `returns info when the release is strictly newer`() {
        server.enqueue(MockResponse().setBody(release(versionCode = 5)))
        val checker = UpdateChecker(
            currentVersionCode = 4,
            endpoint = server.url("/latest").toString(),
        )
        val info = checker.check()!!
        assertEquals(5, info.versionCode)
    }

    @Test
    fun `returns null when the release is the same version`() {
        server.enqueue(MockResponse().setBody(release(versionCode = 4)))
        val checker = UpdateChecker(4, server.url("/latest").toString())
        assertNull(checker.check())
    }

    @Test
    fun `returns null when the release is older`() {
        server.enqueue(MockResponse().setBody(release(versionCode = 3)))
        val checker = UpdateChecker(4, server.url("/latest").toString())
        assertNull(checker.check())
    }

    @Test
    fun `sends a github-friendly user agent`() {
        server.enqueue(MockResponse().setBody(release(versionCode = 9)))
        val checker = UpdateChecker(1, server.url("/latest").toString())
        checker.check()
        val recorded = server.takeRequest()
        assertEquals("HermesRemote-Updater", recorded.getHeader("User-Agent"))
        assertEquals("application/vnd.github+json", recorded.getHeader("Accept"))
    }

    @Test(expected = java.io.IOException::class)
    fun `throws on a non-2xx response`() {
        server.enqueue(MockResponse().setResponseCode(403).setBody("rate limited"))
        val checker = UpdateChecker(1, server.url("/latest").toString())
        checker.check()
    }

    @Test
    fun `returns null on a body with no apk asset`() {
        server.enqueue(MockResponse().setBody("""{"tag_name":"v1.0.0+9","assets":[]}"""))
        val checker = UpdateChecker(1, server.url("/latest").toString())
        assertNull(checker.check())
    }
}
