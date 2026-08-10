package com.chronie.homemoney.ui.scroll

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import java.util.ArrayDeque

/**
 * Holds the scroll-to-top action of the currently visible scroll container.
 *
 * Android exposes no public "tap the status bar to scroll to top" callback, so
 * [com.chronie.homemoney.MainActivity] watches taps in the status-bar region and
 * asks this registry to scroll the active list. Because the app shows one screen
 * at a time, the most-recently-registered handler (the foreground screen) wins;
 * a screen unregisters itself on dispose so the previous one takes over again.
 */
object ScrollToTopRegistry {

    private val handlers = ArrayDeque<() -> Unit>()

    /** Register the active screen's scroll-to-top action. */
    fun register(handler: () -> Unit) {
        handlers.addLast(handler)
    }

    /** Remove a previously registered handler (call on dispose). */
    fun unregister(handler: () -> Unit) {
        handlers.remove(handler)
    }

    /** Scroll the foreground scroll container back to its top, if any. */
    fun trigger() {
        handlers.lastOrNull()?.invoke()
    }

    /** Drop every handler — used when the registry is reset (e.g. logout). */
    fun clear() {
        handlers.clear()
    }
}

/**
 * Registers [state]'s scroll-to-top action with [ScrollToTopRegistry] for as long
 * as this composable is in the composition. A no-op when the list is already at
 * the top, so a status-bar tap never fights with normal top-of-list interaction.
 */
@Composable
fun RegisterScrollToTop(state: LazyListState) {
    val scope = rememberCoroutineScope()
    DisposableEffect(state) {
        val handler = {
            if (state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > 0) {
                scope.launch { state.animateScrollToItem(0) }
            }
        }
        ScrollToTopRegistry.register(handler)
        onDispose { ScrollToTopRegistry.unregister(handler) }
    }
}
