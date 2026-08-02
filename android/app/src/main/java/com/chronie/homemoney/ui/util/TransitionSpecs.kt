package com.chronie.homemoney.ui.util

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally

/**
 * Shared navigation transition specifications for Compose Navigation.
 *
 * Provides four transitions:
 * - **enter** / **exit**: Forward navigation — slides in from right, fades in.
 *   Exits with a scale-down + fade-out for a card-stack feel.
 * - **popEnter** / **popExit**: Back navigation — a subtle slide from left
 *   with a slightly stronger scale-down on exit.
 *
 * All transitions use 300ms [FastOutSlowInEasing] curves for a polished feel.
 */
object TransitionSpecs {
    private const val DURATION = 300

    fun enterTransition(): EnterTransition {
        return slideInHorizontally(
            initialOffsetX = { it },
            animationSpec = tween(DURATION, easing = FastOutSlowInEasing)
        ) + fadeIn(animationSpec = tween(DURATION, easing = FastOutSlowInEasing))
    }

    fun exitTransition(): ExitTransition {
        return scaleOut(
            targetScale = 0.9f,
            animationSpec = tween(DURATION, easing = FastOutSlowInEasing)
        ) + fadeOut(animationSpec = tween(DURATION, easing = FastOutSlowInEasing))
    }

    fun popEnterTransition(): EnterTransition {
        return slideInHorizontally(
            initialOffsetX = { -it / 4 },
            animationSpec = tween(DURATION, easing = FastOutSlowInEasing)
        ) + fadeIn(animationSpec = tween(DURATION, easing = FastOutSlowInEasing))
    }

    fun popExitTransition(): ExitTransition {
        return scaleOut(
            targetScale = 0.8f,
            animationSpec = tween(DURATION, easing = FastOutSlowInEasing)
        ) + fadeOut(animationSpec = tween(DURATION, easing = FastOutSlowInEasing))
    }
}
