package com.chronie.homemoney.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Linear progress indicator backed by Miuix [LinearProgressIndicator].
 *
 * The MD3 `LinearWavyProgressIndicator` had no direct Miuix equivalent, so the
 * wavy amplitude parameter is dropped. Both public overloads are preserved so
 * existing call sites are unchanged.
 */
@Composable
fun ExpressiveLinearProgressIndicator(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MiuixTheme.colorScheme.primary,
    trackColor: Color = MiuixTheme.colorScheme.surfaceVariant
) {
    LinearProgressIndicator(
        modifier = modifier,
        progress = progress.coerceIn(0f, 1f),
        colors = ProgressIndicatorDefaults.progressIndicatorColors(
            foregroundColor = color,
            backgroundColor = trackColor
        )
    )
}

@Composable
fun ExpressiveLinearProgressIndicator(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = MiuixTheme.colorScheme.primary,
    trackColor: Color = MiuixTheme.colorScheme.surfaceVariant
) {
    ExpressiveLinearProgressIndicator(
        progress = progress(),
        modifier = modifier,
        color = color,
        trackColor = trackColor
    )
}
