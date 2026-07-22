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

// Color scheme style enum
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

// Map our PaletteStyle to Miuix ThemePaletteStyle (1:1 correspondence)
fun PaletteStyle.toMiuixPaletteStyle(): ThemePaletteStyle = when (this) {
    PaletteStyle.TonalSpot -> ThemePaletteStyle.TonalSpot
    PaletteStyle.Neutral -> ThemePaletteStyle.Neutral
    PaletteStyle.Vibrant -> ThemePaletteStyle.Vibrant
    PaletteStyle.Expressive -> ThemePaletteStyle.Expressive
    PaletteStyle.Rainbow -> ThemePaletteStyle.Rainbow
    PaletteStyle.FruitSalad -> ThemePaletteStyle.FruitSalad
    PaletteStyle.Monochrome -> ThemePaletteStyle.Monochrome
    PaletteStyle.Fidelity -> ThemePaletteStyle.Fidelity
    PaletteStyle.Content -> ThemePaletteStyle.Content
}

// Theme settings local context
val LocalThemeSettings = staticCompositionLocalOf<MutableState<ThemeSettings>> {
    error("No ThemeSettings provided")
}

// Theme settings data class
class ThemeSettings(
    val useDynamicColor: Boolean,
    val primaryColor: Int,
    val paletteStyle: PaletteStyle
)

// Load theme settings from SharedPreferences
fun loadThemeSettings(context: Context): ThemeSettings {
    val prefs: SharedPreferences = context.getSharedPreferences("theme_settings", Context.MODE_PRIVATE)
    val useDynamicColor = prefs.getBoolean("use_dynamic_color", true)
    val primaryColor = prefs.getInt("primary_color", 0xFF6750A4.toInt()) // 默认紫色
    val paletteStyleValue = prefs.getInt("palette_style", PaletteStyle.Expressive.ordinal)
    val paletteStyle = PaletteStyle.entries.toTypedArray().getOrElse(paletteStyleValue) { PaletteStyle.Expressive }
    return ThemeSettings(useDynamicColor, primaryColor, paletteStyle)
}

@Composable
fun HomeMoneyTheme(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    // Get system theme mode in real-time, ensure it responds to system theme changes
    val darkTheme = isSystemInDarkTheme()

    // Create observable theme settings
    val themeSettings = remember {
        mutableStateOf(loadThemeSettings(context))
    }

    // Listen for SharedPreferences changes to update theme settings in real-time
    DisposableEffect(Unit) {
        val prefs = context.getSharedPreferences("theme_settings", Context.MODE_PRIVATE)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            themeSettings.value = loadThemeSettings(context)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)

        // Clean up listener
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    // Build Miuix ThemeController:
    //  - useDynamicColor=true  → Monet with keyColor=null (system wallpaper colors)
    //  - useDynamicColor=false → Monet with keyColor=user's primaryColor (seed-based generation)
    // ColorSchemeMode.MonetSystem follows the system light/dark setting and enables
    // Miuix's built-in Monet color generation (replaces kyant0/m3color + material3).
    val controller = remember(themeSettings.value) {
        ThemeController(
            colorSchemeMode = ColorSchemeMode.MonetSystem,
            keyColor = if (themeSettings.value.useDynamicColor) null else Color(themeSettings.value.primaryColor),
            paletteStyle = themeSettings.value.paletteStyle.toMiuixPaletteStyle(),
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            // Set navigation bar color to basic white/black based on theme mode
            window.navigationBarColor = if (darkTheme) android.graphics.Color.BLACK else android.graphics.Color.WHITE
            // Set status bar color to basic white/black based on theme mode, ignore on high versions of Android
            window.statusBarColor = if (darkTheme) android.graphics.Color.BLACK else android.graphics.Color.WHITE
            // Ensure navigation bar icon colors are correct
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MiuixTheme(controller = controller, textStyles = miuixTextStyles) {
        CompositionLocalProvider(LocalThemeSettings provides themeSettings) {
            content()
        }
    }
}
