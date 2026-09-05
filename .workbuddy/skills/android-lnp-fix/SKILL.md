---
name: android-lnp-fix
description: >-
  This skill should be used when fixing local network access failures on Android 17
  (API 37, Build.VERSION_CODES.CINNAMON_BUN) caused by Local Network Protection (LNP).
  Trigger on symptoms such as a LAN sync server or self-hosted server being unreachable,
  silent TCP timeouts, UDP broadcast/discovery returning EPERM, "Local network" permission
  prompts, or "Unresolved reference" errors after adding ACCESS_LOCAL_NETWORK. It covers
  declaring android.permission.ACCESS_LOCAL_NETWORK, runtime requesting via
  ActivityResultContracts.RequestPermission, guarding inbound TCP and outbound UDP, the
  network_security_config cleartext whitelist (usesCleartextTraffic deprecation),
  USE_LOOPBACK_INTERFACE, and a clear UI on denial/revocation.
agent_created: true
---

# Android 17 Local Network Protection (LNP) Fix

## Overview

Android 17 (API 37, `Build.VERSION_CODES.CINNAMON_BUN`) introduces **Local Network Protection
(LNP)**, which by default blocks all local-network access when `targetSdk >= 37`. Apps that
sync over LAN, run an inbound TCP sync server, or send UDP discovery broadcasts silently
break. This skill provides a complete, copy-ready recipe to declare, request, and guard the
`android.permission.ACCESS_LOCAL_NETWORK` permission, plus forward-looking mitigations and a
verification checklist. Full file code lives in `references/android17-lnp-reference.md`.

## When To Use

Invoke this skill when any of the following appear:

- A LAN/self-hosted server (e.g. `192.168.x.x:port`) is unreachable on Android 17 only.
- TCP connections to the LAN silently time out; UDP `send`/`recv` returns `EPERM`.
- The app needs to run an inbound TCP server or send UDP broadcasts on the same Wi-Fi.
- User reports a "Local network" permission prompt, or LAN features are dead after a denial.
- `compileReleaseKotlin` reports `Unresolved reference: 'Manifest'` / `'Log'` after adding
  LNP code (missing imports — see Verification).
- `targetSdk` is being raised to 37 and the app uses any LAN communication.

Do **not** apply on `< Android 17`: there `ACCESS_LOCAL_NETWORK` does not exist and the
recipe degrades to a no-op (see Step 1).

## Root Cause (why it fails silently)

- LNP is **on by default at `targetSdk = 37`**. No permission = blocked.
- **TCP**: connection attempts hang until timeout (no exception you can easily catch).
- **UDP**: `send`/`recv` on a broadcast socket returns `EPERM` immediately.
- The permission is a **dangerous runtime permission** in the `NEARBY_DEVICES` group — it
  must be both **declared** in the manifest and **requested at runtime**.
- `Application` subclasses and plain Kotlin classes (e.g. a discovery service) **cannot**
  show the permission dialog — the request must originate from an `Activity`/`ComponentActivity`
  context. Callers without an Activity must *guard* (skip work) and let the Activity request.

## Diagnostic Checklist

1. Confirm `targetSdk >= 37` and `compileSdk >= 37` (so `CINNAMON_BUN` constant exists).
2. Check the manifest for `android.permission.ACCESS_LOCAL_NETWORK` — absent = broken on 17.
3. Check whether LAN work is launched from a non-Activity context (Application, service,
   repository). If so, it must be guarded, not requesting.
4. Identify every LAN entry point (save server URL, UDP discovery, inbound TCP server start).
5. Confirm `android.Manifest` and `android.util.Log` imports exist wherever used (common miss).

## The Fix Recipe

Follow these steps. Copy the exact code from `references/android17-lnp-reference.md`.

### Step 1 — Central LNP predicate
Create `LocalNetworkPermission` (object) exposing `isRequired` (`SDK_INT >= CINNAMON_BUN`)
and `isGranted(context)`. Use it everywhere instead of repeating the API-37 check. On
`< 37` it returns `true`, so `ensure { ... }` runs immediately with no dialog and no regression.

### Step 2 — Declare permissions in `AndroidManifest.xml`
Add:
```xml
<uses-permission android:name="android.permission.ACCESS_LOCAL_NETWORK" />
<uses-permission android:name="android.permission.USE_LOOPBACK_INTERFACE" />
```
Keep `USE_LOOPBACK_INTERFACE` even if unused now — it is the forward-looking cross-app
loopback permission. Replace the deprecated `android:usesCleartextTraffic="true"` with a
`networkSecurityConfig` reference (Step 3).

### Step 3 — `network_security_config.xml` cleartext whitelist
Add `res/xml/network_security_config.xml` with a `<domain-config cleartextTrafficPermitted="true">`
for the specific LAN host(s) (e.g. `192.168.10.9`). Reference it via
`android:networkSecurityConfig="@xml/network_security_config"` on `<application>`. Do **not**
flip global `cleartextTrafficPermitted` to false without a full audit — keep base-config
permissive to avoid regressions.

### Step 4 — Runtime requester for Compose screens
Create `rememberLocalNetworkPermissionRequester(onDenied = {})` wrapping
`rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission())`. Expose
`ensure(onGranted)`: runs `onGranted` immediately if granted or `< 37`; otherwise launches the
dialog and runs `onGranted` only after the user accepts. Use `rememberUpdatedState` +
a `pendingGranted` holder so the captured action stays fresh.

### Step 5 — Activity launch entry (inbound TCP server)
In the single `Activity` (`MainActivity`), register
`registerForActivityResult(ActivityResultContracts.RequestPermission())`. On grant, start the
LAN sync server; on denial, log and disable LAN features. Call this from `onCreate` (after
health check) and re-check in `onResume` so a post-install grant is picked up. **Add the
imports `android.Manifest` and `android.util.Log`** — these are the usual missing-reference
errors.

### Step 6 — Guard non-Activity starters
In `Application.onCreate()` and in the LAN sync manager's `startSyncServer()`, skip starting
the server unless `LocalNetworkPermission.isGranted(...)`. Return early **without** flipping
the "server running" flag, so it can be retried after the user grants permission. A pure
Kotlin discovery service must document (comment) that it cannot prompt and that its caller is
responsible for the permission.

### Step 7 — Wire the three LAN entry points
Wrap each with `ensure { ... }` and show a denial Toast (`R.string.lan_permission_denied`):
1. **Save self-hosted server URL** (settings + welcome dialogs) — request before `saveServerUrl`.
2. **UDP device discovery** (`searchDevices()`/`LanDiscoveryService.search()`) — request before
   the broadcast; never send if not granted.
3. **Start LAN sync server** (Activity entry, Step 5) — already covered.

Localize `lan_permission_denied` / `lan_permission_rationale` into every `strings.xml` locale
(see reference for the canonical en/zh strings).

## Known Related Issues (annotate, do NOT fix blindly)

These coexist with LNP and were intentionally left as comments in this project:
- **Concurrent full sync + giant POST**: two `SyncWorker`s may run full sync in parallel and
  still POST ~1765 `localIds` when pending changes = 0. Needs a single-flight lock + incremental/
  hash-diff sync. Flag with a comment; do not change behavior mid-LNP-fix.
- **Health check 2s timeout too aggressive**: `withTimeout(2000ms)` masks the real LNP error.
  Future fix: classify timeout type or call native `android_getnetworkblockedreason(sockFd)`
  (the JNI layer in `libsync_engine.so` already exists, low cost). Comment only.

## Verification & Common Pitfalls

- **Missing imports** (the #1 compile error): any file using `Log.d/w/e` needs
  `import android.util.Log`; any file using `Manifest.permission.*` needs
  `import android.Manifest`. `compileReleaseKotlin` aggregates all errors for the module in one
  pass, so fix every reported `Unresolved reference` before re-running.
- Build with `./gradlew :app:compileReleaseKotlin` (or `assembleDebug`). Expect only LNP-related
  errors if the surrounding code was already compiling.
- On `< Android 17` the permission path is a no-op; confirm no dialog appears and LAN still works.
- Confirm denial shows the `lan_permission_denied` Toast and LAN features stay disabled until grant.
- Re-check `onResume`: granting the permission after first launch must start the server without a
  restart.

## Repo Map (this project)

Concrete locations in Home Finance Tracker (`android/app/src/main/...`):
- Predicate: `java/com/chronie/homemoney/data/sync/LocalNetworkPermission.kt`
- Requester: `java/com/chronie/homemoney/ui/permissions/RememberLocalNetworkPermissionRequester.kt`
- Manifest: `AndroidManifest.xml`; security config: `res/xml/network_security_config.xml`
- App guard: `java/com/chronie/homemoney/HomeMoneyApplication.kt`
- Server guard: `java/com/chronie/homemoney/data/sync/LanDeviceSyncManager.kt` (`startSyncServer`)
- Activity entry: `java/com/chronie/homemoney/MainActivity.kt` (`lanPermissionLauncher`,
  `ensureLanPermissionAndStartServer`, `startLanSyncServer`)
- Screen wiring: `ui/settings/SettingsScreen.kt`, `ui/welcome/WelcomeScreen.kt`,
  `ui/sync/LanSyncScreen.kt` (`DeviceSearchDialog`)
- Deferral comments: `service/HealthCheckService.kt`, `worker/SyncWorker.kt`,
  `data/sync/SyncManagerImpl.kt`, `data/sync/discovery/LanDiscoveryService.kt`
- Strings: `res/values*/strings.xml` (`lan_permission_denied`, `lan_permission_rationale`)
