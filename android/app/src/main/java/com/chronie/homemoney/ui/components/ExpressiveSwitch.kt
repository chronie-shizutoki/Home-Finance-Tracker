package com.chronie.homemoney.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.basic.Switch

/**
 * Switch backed by Miuix [Switch]. The MD3 expressive thumb-content animation
 * is dropped — Miuix Switch ships its own HyperOS styling.
 *
 * Public signature is unchanged so existing call sites need no edits.
 */
@Composable
fun ExpressiveSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled
    )
}
