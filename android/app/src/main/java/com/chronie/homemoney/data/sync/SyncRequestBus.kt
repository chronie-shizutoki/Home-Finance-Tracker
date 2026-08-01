package com.chronie.homemoney.data.sync

import com.chronie.homemoney.domain.sync.SyncRequestInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.CompletableFuture

/**
 * App-wide carrier for an incoming LAN sync confirmation.
 *
 * The v2 responder ([com.chronie.homemoney.data.sync.engine.SyncResponder]) asks for the
 * user's permission through [com.chronie.homemoney.data.sync.LanDeviceSyncManager.PromptingSyncAuthorizer]
 * from a native worker thread. The original wiring only installed a callback while the
 * Settings / LAN-sync screen was in the foreground, so a request that arrived on any other
 * screen was silently refused - which is exactly the "B does nothing, A fails" symptom.
 *
 * This bus decouples the prompt from whatever screen is showing. [post] is called from the
 * responder thread, the root composable ([com.chronie.homemoney.MainActivity] -> [HomeMoneyApp])
 * observes [request] and renders the same [com.chronie.homemoney.ui.sync.LanSyncScreenKt.IncomingSyncRequestDialog]
 * it would have on the sync screen, and [resolve] is called from the dialog buttons. Because
 * it is a plain object (not scoped to a ViewModel), it is reachable from both the manager and
 * the app root regardless of navigation state.
 */
object SyncRequestBus {

    private val _request = MutableStateFlow<SyncRequestInfo?>(null)
    val request = _request.asStateFlow()

    @Volatile
    private var pending: CompletableFuture<Boolean>? = null

    /**
     * Publishes an incoming request and returns a future completed by [resolve].
     * Called on a native worker thread; safe because [MutableStateFlow] accepts updates from
     * any thread and the future is only completed from the UI thread.
     */
    fun post(info: SyncRequestInfo): CompletableFuture<Boolean> {
        val future = CompletableFuture<Boolean>()
        pending = future
        _request.value = info
        return future
    }

    /** Called from the confirmation dialog's Accept/Reject buttons (main thread). */
    fun resolve(accepted: Boolean) {
        pending?.complete(accepted)
        pending = null
        _request.value = null
    }

    /** Drops an in-flight request without a decision (e.g. on timeout). */
    fun cancel() {
        pending?.cancel(true)
        pending = null
        _request.value = null
    }
}
