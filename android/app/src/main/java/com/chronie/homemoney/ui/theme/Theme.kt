package com.chronie.homemoney.ui.theme

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle

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

private fun PaletteStyle.toThemePaletteStyle(): ThemePaletteStyle = ThemePaletteStyle.entries[ordinal]

/** Safely convert a stored ARGB Int to Compose Color, handling sign-extension. */
private fun Int.toComposeColor(): Color {
    val a = (this ushr 24) and 0xFF
    val r = (this ushr 16) and 0xFF
    val g = (this ushr 8) and 0xFF
    val b = this and 0xFF
    return Color(r / 255f, g / 255f, b / 255f, a / 255f)
}

private fun maybeKeyColor(primaryColor: Int): Color? {
    if (primaryColor == 0) return null
    return primaryColor.toComposeColor()
}

@Composable
fun HomeMoneyTheme(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val darkTheme = isSystemInDarkTheme()

    val themeSettings = remember {
        mutableStateOf(loadThemeSettings(context))
    }

    DisposableEffect(Unit) {
        val prefs = context.getSharedPreferences("theme_settings", Context.MODE_PRIVATE)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            themeSettings.value = loadThemeSettings(context)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    val settings = themeSettings.value
    val controller = remember(settings) {
        val (mode, keyColor) = when {
            settings.useDynamicColor -> ColorSchemeMode.MonetSystem to null
            settings.primaryColor != 0 -> ColorSchemeMode.MonetSystem to maybeKeyColor(settings.primaryColor)
            else -> ColorSchemeMode.System to null
        }
        ThemeController(
            colorSchemeMode = mode,
            keyColor = keyColor,
            paletteStyle = settings.paletteStyle.toThemePaletteStyle(),
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            window.navigationBarColor = if (darkTheme) android.graphics.Color.BLACK else android.graphics.Color.WHITE
            window.statusBarColor = if (darkTheme) android.graphics.Color.BLACK else android.graphics.Color.WHITE
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MiuixTheme(controller = controller, textStyles = miuixTextStyles) {
        CompositionLocalProvider(LocalThemeSettings provides themeSettings) {
            content()
        }
    }
}
