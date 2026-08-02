package com.chronie.homemoney.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Outlined button built on Miuix [Surface].
 *
 * Miuix has no native OutlinedButton; this emulates one with a transparent
 * [Surface], a [BorderStroke] and centered [Row] content, matching the sizing
 * conventions of [top.yukonga.miuix.kmp.basic.Button].
 */
@Composable
fun OutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentColor: Color = MiuixTheme.colorScheme.primary,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier
            .clickable(onClick = onClick, enabled = enabled)
            .border(BorderStroke(1.dp, contentColor), shape = RoundedCornerShape(16.dp))
            .defaultMinSize(minWidth = ButtonDefaults.MinWidth, minHeight = ButtonDefaults.MinHeight)
            .padding(ButtonDefaults.InsideMargin),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}
