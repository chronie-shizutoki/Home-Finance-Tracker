package com.chronie.homemoney.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator

/**
 * Loading indicator backed by Miuix [CircularProgressIndicator].
 *
 * Miuix has no `ContainedLoadingIndicator` equivalent, so the
 * [containerVisible] flag is retained only for API compatibility with existing
 * call sites — both branches now render the same indeterminate circular
 * indicator.
 */
@Composable
fun ExpressiveLoadingIndicator(
    modifier: Modifier = Modifier,
    containerVisible: Boolean = true
) {
    CircularProgressIndicator(modifier = modifier)
}
