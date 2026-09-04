package com.chronie.homemoney.ui.permissions

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.chronie.homemoney.data.sync.LocalNetworkPermission

/**
 * Small contract returned by [rememberLocalNetworkPermissionRequester].
 *
 * Call [ensure] with the LAN operation to perform. If the ACCESS_LOCAL_NETWORK
 * permission is already held (or is not required on this SDK) the operation runs
 * immediately. Otherwise the runtime dialog is shown; after the user accepts,
 * [onGranted] runs, and after they refuse, [onDenied] runs - so the caller can
 * surface an explicit hint instead of failing with a silent socket timeout.
 */
interface LocalNetworkPermissionRequester {
    fun ensure(onGranted: () -> Unit)
}

/**
 * Remembers a [LocalNetworkPermissionRequester] bound to the host Activity.
 *
 * Example (inside a Composable that has a `context: Context` and a `viewModel`):
 * ```
 * val lanPermission = rememberLocalNetworkPermissionRequester(
 *     onDenied = {
 *         Toast.makeText(context, R.string.lan_permission_denied, Toast.LENGTH_LONG).show()
 *     }
 * )
 * Button(onClick = {
 *     lanPermission.ensure(onGranted = { if (viewModel.saveServerUrl(input)) onDismiss() })
 * })
 * ```
 *
 * On Android < 17 (API 37) this is a no-op wrapper: [ensure] always runs [onGranted]
 * immediately because [LocalNetworkPermission.isRequired] is false and the permission
 * does not exist.
 */
@Composable
fun rememberLocalNetworkPermissionRequester(
    onDenied: () -> Unit = {}
): LocalNetworkPermissionRequester {
    // Capture the latest context / onDenied without re-registering the activity-result launcher.
    val contextRef = rememberUpdatedState(LocalContext.current)
    val onDeniedRef = rememberUpdatedState(onDenied)
    val pendingGranted = remember { mutableStateOf<(() -> Unit)?>(null) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val action = pendingGranted.value
        pendingGranted.value = null
        if (granted) {
            action?.invoke()
        } else {
            onDeniedRef.value()
        }
    }

    return remember {
        object : LocalNetworkPermissionRequester {
            override fun ensure(onGranted: () -> Unit) {
                if (LocalNetworkPermission.isGranted(contextRef.value)) {
                    onGranted()
                } else {
                    pendingGranted.value = onGranted
                    launcher.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
                }
            }
        }
    }
}
