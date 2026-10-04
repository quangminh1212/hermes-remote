package com.hermes.bridge.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

/**
 * Hands a downloaded APK to the system package installer.
 *
 * On Android 8+ (our minSdk is 26) an app may only trigger the install
 * confirmation dialog if it holds the `REQUEST_INSTALL_PACKAGES` permission
 * AND the user has toggled "Allow from this source" for this app. This class
 * checks that and can send the user to the right settings screen.
 */
object UpdateInstaller {

    /** True when this app is permitted to launch the install prompt. */
    fun canInstall(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    /**
     * Open the system "install unknown apps" settings page for this app, so
     * the user can grant the permission we cannot request ourselves.
     */
    fun openInstallPermissionSettings(context: Context) {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).setData(
                Uri.parse("package:${context.packageName}"),
            )
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS)
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /**
     * Launch the package installer for a downloaded [apk]. Returns false when
     * the app lacks the install permission (call
     * [openInstallPermissionSettings] instead).
     */
    fun install(context: Context, apk: File): Boolean {
        if (!canInstall(context)) return false

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return true
    }
}
