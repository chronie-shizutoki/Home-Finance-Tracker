package com.chronie.homemoney.data.sync

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Central helper for Android 17 (API 37, Build.VERSION_CODES.CINNAMON_BUN)
 * Local Network Protection (LNP).
 *
 * What LNP changes:
 *   On Android 17, when an app targets API 37, the system blocks ALL local-network
 *   access - both LAN TCP connections (inbound + outbound) and UDP broadcasts - unless
 *   the app holds the ACCESS_LOCAL_NETWORK permission. The block is silent: TCP attempts
 *   simply time out and UDP sends fail with EPERM, so without the permission the user only
 *   sees "cannot connect" with no explanation. That is exactly the Android-16-works /
 *   Android-17-fails symptom reported for the 192.168.10.9 LAN sync server.
 *
 * This object answers two questions and never launches a dialog itself:
 *   - [isRequired]: is LNP in effect on this device/SDK? (false below API 37)
 *   - [isGranted]: does the calling context already hold the permission?
 *
 * Requesting the permission must happen from an Activity. For Compose use
 * [com.chronie.homemoney.ui.permissions.rememberLocalNetworkPermissionRequester];
 * for the launch-time entry point see MainActivity.ensureLanPermissionAndStartServer().
 */
object LocalNetworkPermission {

    /**
     * True when the running device enforces Local Network Protection, i.e. Android 17+
     * (CINNAMON_BUN / API 37). Below that the permission does not exist and LAN access is
     * unrestricted, so callers can treat the permission as always granted.
     */
    val isRequired: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN

    /**
     * True when LAN access is currently allowed: either LNP is not in effect, or the
     * ACCESS_LOCAL_NETWORK permission is already granted to this context.
     */
    fun isGranted(context: Context): Boolean =
        !isRequired || ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_LOCAL_NETWORK
        ) == PackageManager.PERMISSION_GRANTED
}
