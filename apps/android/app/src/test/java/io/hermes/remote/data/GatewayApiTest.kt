package io.hermes.remote.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parser tests run against payloads captured from a live gateway.
 *
 * Real bodies rather than hand-written samples on purpose: the first version of
 * this parser was written from assumptions and read the wrong nesting level and
 * the wrong key name — exactly the class of bug a made-up fixture would have
 * confirmed instead of caught.
 */
class GatewayApiTest {

    private fun fixture(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("gateway/$name")) {
            "missing test fixture gateway/$name"
        }.bufferedReader().use { it.readText() }

    // --- /health/detailed ----------------------------------------------------

    @Test
    fun parsesStatusAndVersion() {
        val info = HealthParser.parse(fixture("health_detailed.json"))
        assertEquals("degraded", info.status)
        assertEquals("0.21.5", info.version)
        assertTrue(info.isDegraded)
    }

    @Test
    fun readsChecksFromInsideReadiness() {
        val info = HealthParser.parse(fixture("health_detailed.json"))
        // `checks` is nested under `readiness`, not at the top level. Reading
        // the top level silently produced an empty map.
        assertEquals("ok", info.checks["state_db"])
        assertEquals("ok", info.checks["session_store"])
        assertEquals("degraded", info.checks["disk"])
        assertEquals("ok", info.checks["gateway"])
    }

    @Test
    fun reportsOnlyUnhealthySubsystems() {
        val info = HealthParser.parse(fixture("health_detailed.json"))
        assertEquals(listOf("disk"), info.problems)
    }

    @Test
    fun readsPlatformStatesNotStatus() {
        val info = HealthParser.parse(fixture("health_detailed.json"))
        // Platforms report `state`; looking for `status` yields nothing.
        assertTrue("expected at least one platform", info.platforms.isNotEmpty())
        assertEquals("fatal", info.platforms["whatsapp"])
        assertTrue(info.brokenPlatforms.contains("whatsapp"))
    }

    @Test
    fun readsDiskMetrics() {
        val info = HealthParser.parse(fixture("health_detailed.json"))
        assertEquals(94.6, info.diskUsedPercent!!, 0.01)
        val gb = info.freeDiskGb
        assertNotNull(gb)
        assertTrue("free disk should be a sane positive number", gb!! > 0)
    }

    @Test
    fun malformedHealthDoesNotThrow() {
        val info = HealthParser.parse("{ this is not json")
        assertTrue(info.checks.isEmpty())
        assertTrue(info.platforms.isEmpty())
        assertEquals("", info.status)
    }

    // --- /v1/toolsets --------------------------------------------------------

    @Test
    fun parsesToolsets() {
        val sets = ToolsetParser.parse(fixture("toolsets.json"))
        assertTrue("expected toolsets", sets.isNotEmpty())
        val web = sets.firstOrNull { it.name == "web" }
        assertNotNull("expected a 'web' toolset", web)
        assertTrue(web!!.enabled)
        assertTrue("web should list its tools", web.tools.isNotEmpty())
    }

    // --- /api/sessions -------------------------------------------------------

    @Test
    fun parsesSessionsNewestFirst() {
        val sessions = SessionParser.parse(fixture("sessions.json"))
        assertTrue("expected sessions", sessions.isNotEmpty())
        val times = sessions.map { it.startedAt }
        assertEquals(times.sortedDescending(), times)
    }

    @Test
    fun decodesUnicodeTitles() {
        val sessions = SessionParser.parse(fixture("sessions.json"))
        // The gateway escapes non-ASCII in titles ("Nghe r\u00f5 n"); leaving
        // the escape undecoded would show raw "\u00f5" to the user.
        assertTrue(
            "no title should still contain a raw \\u escape",
            sessions.none { it.title.contains("\\u") },
        )
    }

    @Test
    fun sessionWithNullEndedAtIsLive() {
        val sessions = SessionParser.parse(fixture("sessions.json"))
        // ended_at is JSON null for the session still open on the PC.
        val live = sessions.filter { it.endedAt == null }
        assertTrue("expected at least one live session", live.isNotEmpty())
        assertTrue(live.all { it.isLive })
    }
}
