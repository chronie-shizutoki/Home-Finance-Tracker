package com.chronie.homemoney.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.kyant.m3color.hct.Hct
import com.kyant.m3color.scheme.SchemeContent
import com.kyant.m3color.scheme.SchemeExpressive
import com.kyant.m3color.scheme.SchemeFidelity
import com.kyant.m3color.scheme.SchemeFruitSalad
import com.kyant.m3color.scheme.SchemeMonochrome
import com.kyant.m3color.scheme.SchemeNeutral
import com.kyant.m3color.scheme.SchemeRainbow
import com.kyant.m3color.scheme.SchemeTonalSpot
import com.kyant.m3color.scheme.SchemeVibrant
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

/**
 * Available palette styles for the Material 3 dynamic color system.
 *
 * Each style produces a different color scheme from the same key color,
 * ranging from subtle (Monochrome) to highly saturated (Rainbow).
 * The style is persisted in SharedPreferences and can be changed in Settings.
 */
enum class PaletteStyle {
    TonalSpot, Neutral, Vibrant, Expressive, Rainbow,
    FruitSalad, Monochrome, Fidelity, Content,
}

val LocalThemeSettings = staticCompositionLocalOf<MutableState<ThemeSettings>> {
    error("No ThemeSettings provided")
}

class ThemeSettings(
    val useDynamicColor: Boolean,
    val primaryColor: Int,
    val paletteStyle: PaletteStyle
)

fun loadThemeSettings(context: Context): ThemeSettings {
    val prefs: SharedPreferences = context.getSharedPreferences("theme_settings", Context.MODE_PRIVATE)
    val useDynamicColor = prefs.getBoolean("use_dynamic_color", true)
    val primaryColor = prefs.getInt("primary_color", 0)
    val paletteStyleValue = prefs.getInt("palette_style", PaletteStyle.Expressive.ordinal)
    val paletteStyle = PaletteStyle.entries.toTypedArray().getOrElse(paletteStyleValue) { PaletteStyle.Expressive }
    return ThemeSettings(useDynamicColor, primaryColor, paletteStyle)
}

// ── m3color → Miuix Colors ──

@Suppress("NOTHING_TO_INLINE")
private inline fun Int.toColor(): Color = Color(this)

private data class PrimaryColors(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
)

@Stable
private fun derivePrimary(keyColor: Color, isDark: Boolean, style: PaletteStyle): PrimaryColors {
    val hct = Hct.fromInt(keyColor.toArgb())
    val hue = hct.hue
    val chroma = hct.chroma.coerceAtMost(150.0).coerceAtLeast(0.0)
    val adjustedChroma = (chroma * when (style) {
        PaletteStyle.TonalSpot -> 1.0; PaletteStyle.Neutral -> 0.5
        PaletteStyle.Vibrant -> 1.5; PaletteStyle.Expressive -> 1.2
        PaletteStyle.Rainbow -> 1.3; PaletteStyle.FruitSalad -> 1.4
        PaletteStyle.Monochrome -> 0.3; PaletteStyle.Fidelity -> 1.1
        PaletteStyle.Content -> 0.8
        else -> 1.0
    }).coerceAtMost(150.0).coerceAtLeast(0.0)

    return if (isDark) PrimaryColors(
        primary = Hct.from(hue, adjustedChroma, 80.0).toInt().toColor(),
        onPrimary = Hct.from(hue, adjustedChroma, 20.0).toInt().toColor(),
        primaryContainer = Hct.from(hue, adjustedChroma, 30.0).toInt().toColor(),
        onPrimaryContainer = Hct.from(hue, adjustedChroma, 90.0).toInt().toColor(),
    ) else PrimaryColors(
        primary = Hct.from(hue, adjustedChroma, 40.0).toInt().toColor(),
        onPrimary = Hct.from(hue, adjustedChroma, 100.0).toInt().toColor(),
        primaryContainer = Hct.from(hue, adjustedChroma, 90.0).toInt().toColor(),
        onPrimaryContainer = Hct.from(hue, adjustedChroma, 10.0).toInt().toColor(),
    )
}

@Stable
private fun m3colorToMiuix(keyColor: Color, isDark: Boolean, style: PaletteStyle): Colors {
    val hct = Hct.fromInt(keyColor.toArgb())
    val scheme = when (style) {
        PaletteStyle.TonalSpot -> SchemeTonalSpot(hct, isDark, 0.0)
        PaletteStyle.Neutral -> SchemeNeutral(hct, isDark, 0.0)
        PaletteStyle.Vibrant -> SchemeVibrant(hct, isDark, 0.0)
        PaletteStyle.Expressive -> SchemeExpressive(hct, isDark, 0.0)
        PaletteStyle.Rainbow -> SchemeRainbow(hct, isDark, 0.0)
        PaletteStyle.FruitSalad -> SchemeFruitSalad(hct, isDark, 0.0)
        PaletteStyle.Monochrome -> SchemeMonochrome(hct, isDark, 0.0)
        PaletteStyle.Fidelity -> SchemeFidelity(hct, isDark, 0.0)
        PaletteStyle.Content -> SchemeContent(hct, isDark, 0.0)
    }
    val prim = derivePrimary(keyColor, isDark, style)
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = prim.primary,
        onPrimary = prim.onPrimary,
        primaryContainer = prim.primaryContainer,
        onPrimaryContainer = prim.onPrimaryContainer,
        primaryVariant = scheme.tertiary.toColor(),
        secondary = scheme.secondary.toColor(),
        secondaryContainer = scheme.secondaryContainer.toColor(),
        onSecondaryContainer = scheme.onSecondaryContainer.toColor(),
        tertiaryContainer = scheme.tertiaryContainer.toColor(),
        onTertiaryContainer = scheme.onTertiaryContainer.toColor(),
        background = scheme.background.toColor(),
        onBackground = scheme.onBackground.toColor(),
        surface = scheme.surface.toColor(),
        onSurface = scheme.onSurface.toColor(),
        surfaceVariant = scheme.surfaceVariant.toColor(),
        onSurfaceSecondary = scheme.onSurfaceVariant.toColor(),
        surfaceContainerHighest = scheme.surfaceContainerHighest.toColor(),
        error = scheme.error.toColor(),
        errorContainer = scheme.errorContainer.toColor(),
        onErrorContainer = scheme.onErrorContainer.toColor(),
        outline = scheme.outline.toColor(),
        dividerLine = scheme.outlineVariant.toColor(),
    )
}

// ── Theme ──

/**
 * The main theme composable for the HomeMoney app.
 *
 * Supports two modes:
 * 1. **Monet System** — Material You dynamic color from the device wallpaper
 *    (when [ThemeSettings.useDynamicColor] is true).
 * 2. **Custom** — User-selected key color with one of 9 palette styles,
 *    rendered via the m3color library's HCT color space.
 *
 * Theme changes are reactive: a SharedPreferences listener detects
 * setting changes and recomposes immediately without restart.
 */
@Composable
fun HomeMoneyTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val darkTheme = isSystemInDarkTheme()

    val themeSettings = remember { mutableStateOf(loadThemeSettings(context)) }

    DisposableEffect(Unit) {
        val prefs = context.getSharedPreferences("theme_settings", Context.MODE_PRIVATE)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            themeSettings.value = loadThemeSettings(context)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    val settings = themeSettings.value
    val isCustom = !settings.useDynamicColor && settings.primaryColor != 0

    // Only control bar icon colours; scrims are transparent (set in MainActivity)
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val insets = WindowCompat.getInsetsController(
                (view.context as android.app.Activity).window, view
            )
            insets.isAppearanceLightStatusBars = !darkTheme
            insets.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    val controller = remember(settings) {
        ThemeController(
            colorSchemeMode = if (settings.useDynamicColor) {
                ColorSchemeMode.MonetSystem
            } else {
                ColorSchemeMode.System
            },
            // Default theme: pure white / #242424 page background instead of
            // Miuix stock #F7F7F7 / pure black. Ignored in Monet and custom-seed modes.
            // The following 2 lines align the default mode's background color with the 
            // navigation and status bars. 
            // However, this may cause the page background to blend with card and other 
            // element backgrounds. 
            // If contrast between the page background and child elements (such as cards) 
            // is needed in the future, consider these approaches:
            // 1. Adjust card background colors so they have sufficient contrast against 
            // the page background.
            // 2. Adjust the navigation and status bar colors to remain consistent with 
            // the page background.
            lightColors = lightColorScheme(surface = Color(0xFFFFFFFF)),
            darkColors = darkColorScheme(surface = Color(0xFF242424)),
        )
    }

    // Outer: provides LocalColorSchemeMode for components like FloatingBottomBar
    MiuixTheme(controller = controller, textStyles = miuixTextStyles) {
        if (isCustom) {
            val customColors = remember(settings, darkTheme) {
                m3colorToMiuix(
                    keyColor = Color(settings.primaryColor),
                    isDark = darkTheme,
                    style = settings.paletteStyle
                )
            }
            MiuixTheme(colors = customColors, textStyles = miuixTextStyles) {
                CompositionLocalProvider(LocalThemeSettings provides themeSettings) {
                    content()
                }
            }
        } else {
            CompositionLocalProvider(LocalThemeSettings provides themeSettings) {
                content()
            }
        }
    }
}
