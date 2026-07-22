package com.chronie.homemoney.ui.util

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * Applies predictive back effects like corner radius and background dimming
 * using the native navigation transition.
 */
@Composable
fun Modifier.predictiveBackEffect(scope: AnimatedContentScope): Modifier {
    val transition = scope.transition
    
    // Animate corner radius when exiting (shrinking)
    val cornerRadius by transition.animateDp(
        transitionSpec = { tween(300) },
        label = "PredictiveCorner"
    ) { state ->
        if (state == EnterExitState.PostExit) 28.dp else 0.dp
    }

    // Animate a dimming mask on the background page (the one entering)
    val maskAlpha by transition.animateFloat(
        transitionSpec = { tween(300) },
        label = "PredictiveMask"
    ) { state ->
        if (state == EnterExitState.PreEnter) 0.4f else 0f
    }

    return this
        .graphicsLayer {
            shape = RoundedCornerShape(cornerRadius)
            clip = cornerRadius > 0.dp
        }
        .drawWithContent {
            drawContent()
            if (maskAlpha > 0f) {
                drawRect(color = Color.Black.copy(alpha = maskAlpha))
            }
        }
}
