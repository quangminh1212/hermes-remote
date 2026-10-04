package com.hermes.bridge.updater

import android.content.Context
import java.io.File
import java.io.IOException
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Downloads a release APK into the app's private cache directory.
 *
 * The file is written to `cacheDir/updates/` and handed to
 * [UpdateInstaller], which exposes it through a FileProvider so the system
 * package installer can read it.
 */
class ApkDownloader(
    private val context: Context,
    private val client: OkHttpClient = UpdateChecker.defaultClient(),
) {

    /**
     * Download [info]'s APK. [onProgress] receives 0..100, or null when the
     * server does not report a content length.
     *
     * @return the downloaded APK file.
     * @throws IOException on network or disk failure.
     */
    fun download(
        info: UpdateInfo,
        onProgress: (Int?) -> Unit = {},
    ): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        // Wipe stale downloads so we never install an old build by accident.
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, "hermes-${info.versionName}+${info.versionCode}.apk")

        val request = Request.Builder()
            .url(info.apkUrl)
            .header("User-Agent", "HermesRemote-Updater")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("APK download failed: HTTP ${response.code}")
            }
            val body = response.body ?: throw IOException("Empty response body")
            val total = body.contentLength()

            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var read: Int
                    var written = 0L
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        written += read
                        onProgress(
                            if (total > 0) ((written * 100) / total).toInt().coerceIn(0, 100)
                            else null,
                        )
                    }
                    output.flush()
                }
            }
        }

        if (target.length() == 0L) {
            throw IOException("Downloaded APK is empty")
        }
        return target
    }
}
