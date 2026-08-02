package com.chronie.homemoney.ui.components.liquid

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Density
import top.yukonga.miuix.kmp.blur.Backdrop

/**
 * A backdrop that combines two [Backdrop] instances, drawing them sequentially.
 *
 * This allows layering two distinct backdrop effects (e.g., blur + lens refraction)
 * within a single composable backdrop slot. Each backdrop draws in order:
 * [first] draws first, then [second] draws on top.
 *
 * Dependencies like coordinate-dependence and residual offsets are delegated
 * to the first backdrop.
 *
 * @param first  The base backdrop, drawn first and used for offset residuals.
 * @param second The overlay backdrop, drawn on top of the first.
 */
@Stable
class CombinedBackdrop(
    val first: Backdrop,
    val second: Backdrop,
) : Backdrop {

    override val isCoordinatesDependent: Boolean = first.isCoordinatesDependent || second.isCoordinatesDependent

    override val offsetResidualX: Float get() = first.offsetResidualX
    override val offsetResidualY: Float get() = first.offsetResidualY

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
        downscaleFactor: Int,
    ) {
        with(first) { drawBackdrop(density, coordinates, layerBlock, downscaleFactor) }
        with(second) { drawBackdrop(density, coordinates, layerBlock, downscaleFactor) }
    }
}

/**
 * Remember a [CombinedBackdrop] that layers [first] and [second] backdrops together.
 * Recomposition is keyed on both backdrop instances.
 *
 * @param first  The base backdrop drawn first.
 * @param second The overlay backdrop drawn second.
 * @return A [Backdrop] that combines both effects.
 */
@Composable
fun rememberCombinedBackdrop(first: Backdrop, second: Backdrop): Backdrop = remember(first, second) { CombinedBackdrop(first, second) }