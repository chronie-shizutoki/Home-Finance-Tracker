# Android 17 LNP — Full Code Reference

Copy-ready code for every file touched by the LNP fix. All comments are in English per project
convention. Paths are relative to `android/app/src/main/`.

---

## 1. `java/com/chronie/homemoney/data/sync/LocalNetworkPermission.kt` (new)

```kotlin
package com.chronie.homemoney.data.sync

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Central predicate for Android 17 (API 37) Local Network Protection (LNP).
 * On Android < 17 [isRequired] is false and [isGranted] always returns true, so callers
 * degrade to a no-op with no dialog and no regression.
 */
object LocalNetworkPermission {
    val isRequired: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN

    fun isGranted(context: Context): Boolean =
        !isRequired || ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_LOCAL_NETWORK
        ) == PackageManager.PERMISSION_GRANTED
}
```

---

## 2. `java/com/chronie/homemoney/ui/permissions/RememberLocalNetworkPermissionRequester.kt` (new)

```kotlin
package com.chronie.homemoney.ui.permissions

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.chronie.homemoney.data.sync.LocalNetworkPermission

interface LocalNetworkPermissionRequester {
    /** Run [onGranted] immediately if permitted (or < Android 17); else prompt first. */
    fun ensure(onGranted: () -> Unit)
}

@Composable
fun rememberLocalNetworkPermissionRequester(
    onDenied: () -> Unit = {}
): LocalNetworkPermissionRequester {
    val context = LocalContext.current
    val currentOnDenied by rememberUpdatedState(onDenied)
    // Holds the action to run after the user accepts, so it is not lost across recomposition.
    var pendingGranted by remember { mutableStateOf<(() -> Unit)?>(null) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val action = pendingGranted
        pendingGranted = null
        if (granted) action?.invoke() else currentOnDenied()
    }

    return remember {
        object : LocalNetworkPermissionRequester {
            override fun ensure(onGranted: () -> Unit) {
                if (LocalNetworkPermission.isGranted(context)) {
                    onGranted()
                } else {
                    pendingGranted = onGranted
                    launcher.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
                }
            }
        }
    }
}
```

---

## 3. `res/xml/network_security_config.xml` (new)

```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <!-- Keep base-config permissive to avoid regressing existing cleartext usage. -->
    <base-config cleartextTrafficPermitted="true" />
    <!-- Android 17 forward-looking: explicit cleartext whitelist for the LAN sync host. -->
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="false">192.168.10.9</domain>
    </domain-config>
</network-security-config>
```

---

## 4. `AndroidManifest.xml` (edit)

Add the two permissions (after `READ_MEDIA_VISUAL_USER_SELECTED` or any existing block):

```xml
<uses-permission android:name="android.permission.ACCESS_LOCAL_NETWORK" />
<uses-permission android:name="android.permission.USE_LOOPBACK_INTERFACE" />
```

On `<application>`, add the security-config reference (keep `usesCleartextTraffic` if already
present, or replace it — `usesCleartextTraffic` is on the deprecation path):

```xml
<application
    ...
    android:networkSecurityConfig="@xml/network_security_config"
    ... >
```

---

## 5. `java/com/chronie/homemoney/HomeMoneyApplication.kt` (edit)

```kotlin
import com.chronie.homemoney.data.sync.LocalNetworkPermission
...
// In onCreate(), where the LAN server used to start unconditionally:
appScope.launch {
    if (LocalNetworkPermission.isGranted(applicationContext)) {
        createDeviceSyncManager() // starts the LAN sync server
    } else {
        // Application class cannot show a permission dialog. MainActivity requests the
        // permission on launch and starts the server after the user grants it.
        Log.w(TAG, "LAN sync server deferred: ACCESS_LOCAL_NETWORK not granted (Android 17 LNP)")
    }
}
```

---

## 6. `java/com/chronie/homemoney/data/sync/LanDeviceSyncManager.kt` (edit)

```kotlin
import com.chronie.homemoney.data.sync.LocalNetworkPermission
...
fun startSyncServer() {
    if (!LocalNetworkPermission.isGranted(context)) {
        // LNP blocks inbound TCP on Android 17 without the permission. Return early WITHOUT
        // flipping isServerRunning, so the server can be retried after the user grants.
        Log.w(TAG, "startSyncServer skipped: ACCESS_LOCAL_NETWORK not granted (Android 17 LNP)")
        return
    }
    if (!isServerRunning.compareAndSet(false, true)) return
    // ... existing server bootstrap ...
}
```

---

## 7. `java/com/chronie/homemoney/MainActivity.kt` (edit)

Imports (the usual missing-reference errors — add both):

```kotlin
import android.Manifest
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import com.chronie.homemoney.data.sync.DeviceSyncManagerFactory
import com.chronie.homemoney.data.sync.LocalNetworkPermission
```

Field + launcher:

```kotlin
@Inject lateinit var deviceSyncManagerFactory: DeviceSyncManagerFactory

private val lanPermissionLauncher = registerForActivityResult(
    ActivityResultContracts.RequestPermission()
) { granted ->
    if (granted) startLanSyncServer()
    else Log.w("MainActivity", "ACCESS_LOCAL_NETWORK denied; LAN sync/discovery disabled until granted")
}
```

In `onCreate` (after `healthCheckService.start()`):

```kotlin
ensureLanPermissionAndStartServer()
```

Helper methods:

```kotlin
private fun ensureLanPermissionAndStartServer() {
    if (LocalNetworkPermission.isGranted(this)) startLanSyncServer()
    else lanPermissionLauncher.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
}

private fun startLanSyncServer() {
    try {
        deviceSyncManagerFactory.createDeviceSyncManager()
        Log.d("MainActivity", "LAN sync server started after permission grant")
    } catch (e: Exception) {
        Log.e("MainActivity", "Failed to start LAN sync server", e)
    }
}
```

Re-check in `onResume` so a post-install grant starts the server without an app restart.

---

## 8. Screen wiring (three LAN entry points)

### 8a. `ui/settings/SettingsScreen.kt` — `ServerConfigSection` save button

```kotlin
import com.chronie.homemoney.ui.permissions.rememberLocalNetworkPermissionRequester
...
val lanPermissionRequester = rememberLocalNetworkPermissionRequester(
    onDenied = {
        Toast.makeText(context, R.string.lan_permission_denied, Toast.LENGTH_LONG).show()
    }
)
...
CircularIconButton(
    onClick = {
        lanPermissionRequester.ensure {
            if (viewModel.saveServerUrl(input)) {
                viewModel.clearServerTestState()
                onDismiss()
            }
        }
    },
    enabled = input.isNotBlank()
) { /* icon */ }
```

### 8b. `ui/welcome/WelcomeScreen.kt` — `WelcomeServerConfigDialog` save button

Same pattern, wrapping `viewModel.saveServerUrl()`.

### 8c. `ui/sync/LanSyncScreen.kt` — `DeviceSearchDialog` (UDP discovery)

```kotlin
val lanPermissionRequester = rememberLocalNetworkPermissionRequester(
    onDenied = {
        Toast.makeText(context, R.string.lan_permission_denied, Toast.LENGTH_LONG).show()
        isSearching = false
    }
)
// Request BEFORE any UDP broadcast; never send if not granted.
var searchStarted by remember { mutableStateOf(false) }
LaunchedEffect(Unit) { lanPermissionRequester.ensure { searchStarted = true } }

LaunchedEffect(searchStarted) {
    if (!searchStarted) return@LaunchedEffect
    val startTime = System.currentTimeMillis()
    val progressJob = coroutineScope.launch { /* existing progress loop */ }
    viewModel.searchDevices().collect { device ->
        discoveredDevices = discoveredDevices.filterNot { it.deviceId == device.deviceId } + device
    }
    progressJob.cancel()
    isSearching = false
    searchProgress = 1f
}
// Keep the existing 30s timeout LaunchedEffect separate.
```

---

## 9. Strings (localize to every locale)

Default `res/values/strings.xml`:

```xml
<!-- Local Network Protection (Android 17 / API 37) -->
<string name="lan_permission_denied">Local network access is blocked. Grant "Local network" permission so the app can reach your LAN sync server and discover nearby devices.</string>
<string name="lan_permission_rationale">We need local network access to sync with the server and nearby devices on the same Wi-Fi.</string>
```

`res/values-zh-rCN/strings.xml`:

```xml
<string name="lan_permission_denied">局域网访问已被拦截。请授予"本地网络"权限，应用才能连接你的局域网同步服务器并发现同 Wi-Fi 下的设备。</string>
<string name="lan_permission_rationale">此应用需要局域网访问权限，以便与服务器及同网络下的设备进行同步。</string>
```

Localize `lan_permission_denied` + `lan_permission_rationale` into **all** `strings.xml` locales
(en default, zh-rCN, zh-rHK, zh-rMO, zh-rTW, zh-rSG, ja-rJP, ko-rKR, th-rTH, vi-rVN,
in-rID, ms-rMY). Insert before `</resources>` with the same `<!-- Local Network Protection ... -->`
comment.

---

## 10. Deferral comments (annotate, do NOT change behavior)

### `service/HealthCheckService.kt`

```kotlin
// DEFERRED FIX (Android 17 LNP): HEALTH_CHECK_TIMEOUT = 2000L is too aggressive and masks the
// real LNP block error (a silent TCP timeout looks like a merely slow server). Future: classify
// the timeout type or call native android_getnetworkblockedreason(sockFd) via the libsync_engine.so
// JNI layer to surface "blocked by Local Network Protection" precisely.
private const val HEALTH_CHECK_TIMEOUT = 2000L
```
```kotlin
// See HEALTH_CHECK_TIMEOUT note: 2s hides LNP failures behind a generic timeout.
val response = withTimeout(HEALTH_CHECK_TIMEOUT.milliseconds) { /* ... */ }
```

### `worker/SyncWorker.kt`

```kotlin
// DEFERRED FIX: two SyncWorker instances may run performFullSync() concurrently, and even when
// pending changes = 0 this still POSTs ~1765 localIds (a giant duplicate payload). Add a
// single-flight lock and switch to incremental/hash-diff sync to avoid amplifying failures on
// slow networks. Do NOT change this behavior as part of the LNP fix.
val syncResult = syncManager.performFullSync()
```

### `data/sync/SyncManagerImpl.kt`

```kotlin
override suspend fun performFullSync(): Result<SyncResult> {
    // Caller MUST have requested ACCESS_LOCAL_NETWORK first (Android 17 LNP); otherwise LAN calls
    // silently time out. See DEFERRED FIX note in SyncWorker for the concurrent full-sync +
    // 1765-localId POST problem.
    ...
}
```
```kotlin
override fun getDeviceSyncManager(): DeviceSyncManager {
    // Caller must hold the LAN permission before using the returned manager on Android 17.
    return deviceSyncManagerFactory.createDeviceSyncManager()
}
```

### `data/sync/discovery/LanDiscoveryService.kt`

```kotlin
fun search(
    timeoutMs: Long = DEFAULT_SEARCH_TIMEOUT_MS,
    queryBursts: Int = DEFAULT_QUERY_BURSTS,
    burstIntervalMs: Long = DEFAULT_BURST_INTERVAL_MS
): Flow<DiscoveredDevice> = channelFlow {
    // DEFERRED: this sends a UDP broadcast that REQUIRES android.permission.ACCESS_LOCAL_NETWORK
    // on Android 17 (LNP). This class has no Activity context and cannot prompt; the caller
    // (DeviceSearchDialog) must ensure the permission is granted before invoking search().
    ...
}
```
