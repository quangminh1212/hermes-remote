package com.hermes.bridge.updater

import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Fetches the latest release from [UpdateSource] and decides whether it is
 * newer than [currentVersionCode].
 *
 * Pure networking + parsing; no Android framework types, so it is unit
 * testable with MockWebServer.
 */
class UpdateChecker(
    private val currentVersionCode: Int,
    private val endpoint: String = UpdateSource.latestReleaseApiUrl(),
    private val client: OkHttpClient = defaultClient(),
) {

    /**
     * Result of a check: either [UpdateInfo] when a strictly newer build
     * exists, or null when up to date / no parseable release.
     *
     * @throws IOException on a transport-level failure (the caller decides
     *   whether to surface or swallow it).
     */
    fun check(): UpdateInfo? {
        val request = Request.Builder()
            .url(endpoint)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "HermesRemote-Updater")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Update check failed: HTTP ${response.code}")
            }
            val body = response.body?.string()
            val info = UpdateSource.parseGithubRelease(body) ?: return null
            return if (info.versionCode > currentVersionCode) info else null
        }
    }

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
