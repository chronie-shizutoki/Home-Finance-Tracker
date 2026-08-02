package com.chronie.homemoney.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * A circular icon button with a subtle surface background.
 *
 * Intended for use in top app bars, dialogs, and other places where
 * a floating circular action button is needed. Uses Miuix theming
 * for consistent color integration.
 *
 * @param onClick Called when the button is pressed.
 * @param modifier Modifier for the outer surface container.
 * @param enabled Whether the button responds to clicks.
 * @param size Diameter of the circular button (default: 40dp).
 * @param content The icon or content to display inside the button.
 */
@Composable
fun CircularIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 40.dp,
    content: @Composable () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = MiuixTheme.colorScheme.surfaceVariant,
        modifier = modifier
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(size)
        ) {
            Box(
                modifier = Modifier.size(size),
                contentAlignment = Alignment.Center
            ) {
                content()
            }
        }
    }
}
