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

/**
 * App-level palette style. Mirrors Miuix [ThemePaletteStyle] for use in settings persistence.
 */
enum class PaletteStyle {
    TonalSpot,
    Neutral,
    Vibrant,
    Expressive,
    Rainbow,
    FruitSalad,
    Monochrome,
    Fidelity,
    Content,
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

/** Map app [PaletteStyle] to Miuix [ThemePaletteStyle]. Ordinals match by design. */
private fun PaletteStyle.toThemePaletteStyle(): ThemePaletteStyle = ThemePaletteStyle.entries[ordinal]

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
            // Android system dynamic colors (Monet) — wallpaper-based
            settings.useDynamicColor -> ColorSchemeMode.MonetSystem to null
            // User-selected custom seed color
            settings.primaryColor != 0 -> ColorSchemeMode.MonetSystem to Color(settings.primaryColor)
            // Miuix default blue (#3482FF light / #277AF7 dark)
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
