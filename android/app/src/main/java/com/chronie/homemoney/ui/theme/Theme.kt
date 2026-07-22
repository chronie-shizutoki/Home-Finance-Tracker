package com.chronie.homemoney.ui.theme

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.runtime.Stable
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

@Stable
fun dynamicColorScheme(
    keyColor: Color,
    isDark: Boolean,
    style: PaletteStyle = PaletteStyle.TonalSpot,
    contrastLevel: Double = 0.0
): ColorScheme {
    val hct = Hct.fromInt(keyColor.toArgb())
    val scheme = when (style) {
        PaletteStyle.TonalSpot -> SchemeTonalSpot(hct, isDark, contrastLevel)
        PaletteStyle.Neutral -> SchemeNeutral(hct, isDark, contrastLevel)
        PaletteStyle.Vibrant -> SchemeVibrant(hct, isDark, contrastLevel)
        PaletteStyle.Expressive -> SchemeExpressive(hct, isDark, contrastLevel)
        PaletteStyle.Rainbow -> SchemeRainbow(hct, isDark, contrastLevel)
        PaletteStyle.FruitSalad -> SchemeFruitSalad(hct, isDark, contrastLevel)
        PaletteStyle.Monochrome -> SchemeMonochrome(hct, isDark, contrastLevel)
        PaletteStyle.Fidelity -> SchemeFidelity(hct, isDark, contrastLevel)
        PaletteStyle.Content -> SchemeContent(hct, isDark, contrastLevel)
    }

    val primaryColors = derivePrimaryColors(keyColor, isDark, style)

    return ColorScheme(
        background = scheme.background.toColor(),
        error = scheme.error.toColor(),
        errorContainer = scheme.errorContainer.toColor(),
        inverseOnSurface = scheme.inverseOnSurface.toColor(),
        inversePrimary = primaryColors.inversePrimary,
        inverseSurface = scheme.inverseSurface.toColor(),
        onBackground = scheme.onBackground.toColor(),
        onError = scheme.onError.toColor(),
        onErrorContainer = scheme.onErrorContainer.toColor(),
        onPrimary = primaryColors.onPrimary,
        onPrimaryContainer = primaryColors.onPrimaryContainer,
        onSecondary = scheme.onSecondary.toColor(),
        onSecondaryContainer = scheme.onSecondaryContainer.toColor(),
        onSurface = scheme.onSurface.toColor(),
        onSurfaceVariant = scheme.onSurfaceVariant.toColor(),
        onTertiary = scheme.onTertiary.toColor(),
        onTertiaryContainer = scheme.onTertiaryContainer.toColor(),
        outline = scheme.outline.toColor(),
        outlineVariant = scheme.outlineVariant.toColor(),
        primary = primaryColors.primary,
        primaryContainer = primaryColors.primaryContainer,
        scrim = scheme.scrim.toColor(),
        secondary = scheme.secondary.toColor(),
        secondaryContainer = scheme.secondaryContainer.toColor(),
        surface = scheme.surface.toColor(),
        surfaceBright = scheme.surfaceBright.toColor(),
        surfaceContainer = scheme.surfaceContainer.toColor(),
        surfaceContainerLow = scheme.surfaceContainerLow.toColor(),
        surfaceContainerLowest = scheme.surfaceContainerLowest.toColor(),
        surfaceContainerHigh = scheme.surfaceContainerHigh.toColor(),
        surfaceContainerHighest = scheme.surfaceContainerHighest.toColor(),
        surfaceDim = scheme.surfaceDim.toColor(),
        surfaceTint = primaryColors.primary,
        surfaceVariant = scheme.surfaceVariant.toColor(),
        tertiary = scheme.tertiary.toColor(),
        tertiaryContainer = scheme.tertiaryContainer.toColor(),
        primaryFixed = primaryColors.primaryFixed,
        primaryFixedDim = primaryColors.primaryFixedDim,
        onPrimaryFixed = primaryColors.onPrimaryFixed,
        onPrimaryFixedVariant = primaryColors.onPrimaryFixedVariant,
        secondaryFixed = scheme.secondaryContainer.toColor(),
        secondaryFixedDim = scheme.secondaryContainer.toColor(),
        onSecondaryFixed = scheme.onSecondaryContainer.toColor(),
        onSecondaryFixedVariant = scheme.onSecondary.toColor(),
        tertiaryFixed = scheme.tertiaryContainer.toColor(),
        tertiaryFixedDim = scheme.tertiaryContainer.toColor(),
        onTertiaryFixed = scheme.onTertiaryContainer.toColor(),
        onTertiaryFixedVariant = scheme.onTertiary.toColor(),
    )
}

@Suppress("NOTHING_TO_INLINE")
private inline fun Int.toColor(): Color = Color(this)

data class PrimaryColors(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val primaryFixed: Color,
    val primaryFixedDim: Color,
    val onPrimaryFixed: Color,
    val onPrimaryFixedVariant: Color,
    val inversePrimary: Color
)

@Stable
fun derivePrimaryColors(
    keyColor: Color,
    isDark: Boolean,
    style: PaletteStyle = PaletteStyle.TonalSpot
): PrimaryColors {
    val hct = Hct.fromInt(keyColor.toArgb())
    val hue = hct.hue
    val chroma = hct.chroma
    val tone = hct.tone

    val adjustedChroma = when (style) {
        PaletteStyle.TonalSpot -> chroma
        PaletteStyle.Neutral -> chroma * 0.5
        PaletteStyle.Vibrant -> chroma * 1.5
        PaletteStyle.Expressive -> chroma * 1.2
        PaletteStyle.Rainbow -> chroma * 1.3
        PaletteStyle.FruitSalad -> chroma * 1.4
        PaletteStyle.Monochrome -> chroma * 0.3
        PaletteStyle.Fidelity -> chroma * 1.1
        PaletteStyle.Content -> chroma * 0.8
    }.coerceAtMost(150.0).coerceAtLeast(0.0)

    if (isDark) {
        val primaryTone = when {
            tone >= 60 -> 80.0
            tone >= 40 -> 70.0
            tone >= 20 -> 60.0
            else -> 50.0
        }
        val onPrimaryTone = 20.0
        val primaryContainerTone = 30.0
        val onPrimaryContainerTone = 90.0
        val primaryFixedTone = 90.0
        val primaryFixedDimTone = 80.0
        val onPrimaryFixedTone = 10.0
        val onPrimaryFixedVariantTone = 30.0
        val inversePrimaryTone = 40.0

        return PrimaryColors(
            primary = Hct.from(hue, adjustedChroma, primaryTone).toInt().toColor(),
            onPrimary = Hct.from(hue, adjustedChroma, onPrimaryTone).toInt().toColor(),
            primaryContainer = Hct.from(hue, adjustedChroma, primaryContainerTone).toInt().toColor(),
            onPrimaryContainer = Hct.from(hue, adjustedChroma, onPrimaryContainerTone).toInt().toColor(),
            primaryFixed = Hct.from(hue, adjustedChroma, primaryFixedTone).toInt().toColor(),
            primaryFixedDim = Hct.from(hue, adjustedChroma, primaryFixedDimTone).toInt().toColor(),
            onPrimaryFixed = Hct.from(hue, adjustedChroma, onPrimaryFixedTone).toInt().toColor(),
            onPrimaryFixedVariant = Hct.from(hue, adjustedChroma, onPrimaryFixedVariantTone).toInt().toColor(),
            inversePrimary = Hct.from(hue, adjustedChroma, inversePrimaryTone).toInt().toColor()
        )
    } else {
        val primaryTone = 40.0
        val onPrimaryTone = 100.0
        val primaryContainerTone = 90.0
        val onPrimaryContainerTone = 10.0
        val primaryFixedTone = 90.0
        val primaryFixedDimTone = 80.0
        val onPrimaryFixedTone = 10.0
        val onPrimaryFixedVariantTone = 30.0
        val inversePrimaryTone = 80.0

        return PrimaryColors(
            primary = Hct.from(hue, adjustedChroma, primaryTone).toInt().toColor(),
            onPrimary = Hct.from(hue, adjustedChroma, onPrimaryTone).toInt().toColor(),
            primaryContainer = Hct.from(hue, adjustedChroma, primaryContainerTone).toInt().toColor(),
            onPrimaryContainer = Hct.from(hue, adjustedChroma, onPrimaryContainerTone).toInt().toColor(),
            primaryFixed = Hct.from(hue, adjustedChroma, primaryFixedTone).toInt().toColor(),
            primaryFixedDim = Hct.from(hue, adjustedChroma, primaryFixedDimTone).toInt().toColor(),
            onPrimaryFixed = Hct.from(hue, adjustedChroma, onPrimaryFixedTone).toInt().toColor(),
            onPrimaryFixedVariant = Hct.from(hue, adjustedChroma, onPrimaryFixedVariantTone).toInt().toColor(),
            inversePrimary = Hct.from(hue, adjustedChroma, inversePrimaryTone).toInt().toColor()
        )
    }
}

fun createColorScheme(
    context: Context,
    darkTheme: Boolean,
    dynamicColor: Boolean,
    primaryColor: Int,
    paletteStyle: PaletteStyle
): ColorScheme {
    val userPrimaryColor = Color(primaryColor)

    return if (dynamicColor) {
        if (darkTheme) {
            dynamicDarkColorScheme(context)
        } else {
            dynamicLightColorScheme(context)
        }
    } else {
        dynamicColorScheme(
            keyColor = userPrimaryColor,
            isDark = darkTheme,
            style = paletteStyle
        )
    }
}

@Stable
fun md3SchemeToMiuixColors(md3: ColorScheme, isDark: Boolean): Colors {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = md3.primary,
        onPrimary = md3.onPrimary,
        primaryContainer = md3.primaryContainer,
        onPrimaryContainer = md3.onPrimaryContainer,
        primaryVariant = md3.tertiary,
        secondary = md3.secondary,
        secondaryContainer = md3.secondaryContainer,
        onSecondaryContainer = md3.onSecondaryContainer,
        tertiaryContainer = md3.tertiaryContainer,
        onTertiaryContainer = md3.onTertiaryContainer,
        background = md3.background,
        onBackground = md3.onBackground,
        surface = md3.surface,
        onSurface = md3.onSurface,
        surfaceVariant = md3.surfaceVariant,
        onSurfaceSecondary = md3.onSurfaceVariant,
        surfaceContainerHighest = md3.surfaceContainerHighest,
        error = md3.error,
        errorContainer = md3.errorContainer,
        onErrorContainer = md3.onErrorContainer,
        outline = md3.outline,
        dividerLine = md3.outlineVariant,
    )
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

    val md3Scheme = remember(themeSettings.value, darkTheme) {
        val settings = themeSettings.value
        if (settings.useDynamicColor || settings.primaryColor != 0) {
            createColorScheme(
                context = context,
                darkTheme = darkTheme,
                dynamicColor = settings.useDynamicColor,
                primaryColor = settings.primaryColor,
                paletteStyle = settings.paletteStyle
            )
        } else {
            dynamicColorScheme(
                keyColor = Color(0xFF2E5FA1),
                isDark = darkTheme,
                style = PaletteStyle.Expressive
            )
        }
    }

    val miuixColors = remember(themeSettings.value, darkTheme) {
        val settings = themeSettings.value
        if (settings.useDynamicColor || settings.primaryColor != 0) {
            val scheme = createColorScheme(
                context = context,
                darkTheme = darkTheme,
                dynamicColor = settings.useDynamicColor,
                primaryColor = settings.primaryColor,
                paletteStyle = settings.paletteStyle
            )
            md3SchemeToMiuixColors(scheme, darkTheme)
        } else {
            if (darkTheme) darkColorScheme() else lightColorScheme()
        }
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

    MaterialTheme(colorScheme = md3Scheme) {
        MiuixTheme(colors = miuixColors, textStyles = miuixTextStyles) {
            CompositionLocalProvider(LocalThemeSettings provides themeSettings) {
                content()
            }
        }
    }
}
