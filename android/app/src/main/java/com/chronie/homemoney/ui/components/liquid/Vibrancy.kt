package com.chronie.homemoney.ui.components.liquid

import top.yukonga.miuix.kmp.blur.BackdropEffectScope
import top.yukonga.miuix.kmp.blur.colorControls


/**
 * Applies a vibrancy (frosted glass) colour boost to a [BackdropEffectScope].
 *
 * This effect adjusts the backdrop's colour controls to simulate the "vibrancy"
 * effect found in Apple's UI frameworks: brightness is zeroed (content is dimmed),
 * contrast is kept neutral, and saturation is boosted to 1.5x — making the
 * blurred background content appear more vivid and colourful through the glass.
 *
 * Typically combined with a blur backdrop to create a frosted glass material.
 */
fun BackdropEffectScope.vibrancy() {
    colorControls(
        brightness = 0f,
        contrast = 1f,
        saturation = 1.5f,
    )
}