package com.hermes.bridge.updater

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.annotations.SerializedName

/**
 * The release metadata the updater cares about, regardless of source.
 *
 * [versionCode] is what decides "is this newer" — it is the monotonic integer
 * the Android package manager uses, and it is authoritative over the
 * human-readable [versionName] (which is only ever shown to the user).
 */
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val notes: String = "",
    val releaseUrl: String = "",
)

/**
 * Where the updater fetches release metadata from.
 *
 * GitHub is the default source: it exposes `GET /repos/{owner}/{repo}/releases/latest`
 * as JSON, and the APK is attached to the release as an asset. Repointing to a
 * self-hosted manifest later only means providing a different [ReleaseSource].
 */
object UpdateSource {

    /** Public GitHub repository that publishes the APKs as release assets. */
    const val GITHUB_OWNER = "quangminh1212"
    const val GITHUB_REPO = "hermes-remote"

    private const val LATEST_RELEASE_API =
        "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"

    /** Endpoint the [UpdateChecker] GETs. */
    fun latestReleaseApiUrl(): String = LATEST_RELEASE_API

    private val gson = Gson()

    /**
     * Parse GitHub's `releases/latest` JSON into an [UpdateInfo], or null when
     * the payload is malformed / has no installable APK asset.
     *
     * The versionCode is NOT sent by GitHub; it is encoded in the release tag
     * as `v<versionName>+<versionCode>` (e.g. `v1.2.0+12`) and, failing that,
     * parsed from the APK asset name `hermes-<versionName>+<versionCode>.apk`.
     */
    fun parseGithubRelease(json: String?): UpdateInfo? {
        if (json.isNullOrBlank()) return null
        return try {
            val release = gson.fromJson(json, GithubRelease::class.java) ?: return null
            val apkAsset = release.assets?.firstOrNull { it.isApk() } ?: return null

            val fromTag = parseVersion(release.tagName)
            val fromAsset = parseVersion(apkAsset.name)
            val parsed = fromTag ?: fromAsset ?: return null

            UpdateInfo(
                versionCode = parsed.second,
                versionName = parsed.first,
                apkUrl = apkAsset.browserDownloadUrl.orEmpty(),
                notes = release.body.orEmpty(),
                releaseUrl = release.htmlUrl.orEmpty(),
            ).takeIf { it.apkUrl.isNotBlank() }
        } catch (_: JsonSyntaxException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Extract (versionName, versionCode) from a tag or asset name that ends in
     * `+<code>`, e.g. `v1.2.0+12` -> ("1.2.0", 12) or
     * `hermes-1.2.0+12.apk` -> ("1.2.0", 12). Returns null when absent.
     */
    fun parseVersion(raw: String?): Pair<String, Int>? {
        if (raw.isNullOrBlank()) return null
        val core = raw.removeSuffix(".apk")
        val plus = core.lastIndexOf('+')
        if (plus <= 0 || plus == core.lastIndex) return null
        val code = core.substring(plus + 1).trim().toIntOrNull() ?: return null
        val name = core.substring(0, plus)
            .substringAfterLast('/')      // strip any path
            .removePrefix("hermes-")
            .removePrefix("v")
        if (name.isBlank()) return null
        return name to code
    }

    /** Wire model for the subset of GitHub's release JSON we consume. */
    private data class GithubRelease(
        @SerializedName("tag_name") val tagName: String? = null,
        @SerializedName("body") val body: String? = null,
        @SerializedName("html_url") val htmlUrl: String? = null,
        @SerializedName("assets") val assets: List<GithubAsset>? = null,
    )

    private data class GithubAsset(
        @SerializedName("name") val name: String? = null,
        @SerializedName("browser_download_url") val browserDownloadUrl: String? = null,
    ) {
        fun isApk(): Boolean = name?.endsWith(".apk", ignoreCase = true) == true
    }
}
