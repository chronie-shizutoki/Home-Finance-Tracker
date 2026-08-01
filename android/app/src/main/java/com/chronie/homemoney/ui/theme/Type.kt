package com.chronie.homemoney.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.theme.defaultTextStyles

/**
 * Miuix text styles with custom overrides for body1, title3, and footnote2.
 *
 * App usage to Miuix textStyle mapping:
 *   bodyMedium -> body2, titleMedium -> body1, bodySmall -> footnote1,
 *   titleLarge -> title3, bodyLarge -> body1, titleSmall -> body2,
 *   labelLarge -> body2, headlineSmall -> title2, labelSmall -> footnote2,
 *   headlineLarge -> title1, headlineMedium -> title2.
 */
val miuixTextStyles = defaultTextStyles(
    body1 = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    title3 = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    footnote2 = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)
