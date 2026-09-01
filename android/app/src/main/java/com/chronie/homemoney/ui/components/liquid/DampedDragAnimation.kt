package com.chronie.homemoney.ui.components.liquid

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.MutatorMutex
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * A drag animation system that provides damped spring-based motion for a 1D value
 * along with simultaneous press-scale and velocity tracking.
 *
 * Designed for interactive sliders or draggable UI elements, this class manages
 * multiple [Animatable] instances orchestrated by a single [CoroutineScope]:
 * - [value] follows drag input with a damped spring, bounded by [valueRange].
 * - [velocity] tracks the smoothed drag velocity for inertial feel.
 * - [pressProgress] interpolates between unpressed (0) and pressed (1) visual states.
 * - [scaleX] / [scaleY] animate between [initialScale] and [pressedScale] for a
 *   subtle press-release scale response.
 *
 * The [modifier] property attaches a [pointerInput] that feeds drag events into
 * the animation system automatically.
 *
 * @param animationScope     CoroutineScope that owns all animation coroutines.
 * @param initialValue       Starting value within [valueRange].
 * @param valueRange         Allowed range for the animated value.
 * @param visibilityThreshold Animation threshold for value convergence.
 * @param initialScale       Base scale when not pressed.
 * @param pressedScale       Scale when the user is actively dragging/pressing.
 * @param canDrag            Predicate tested against each pointer position to allow/prevent drag.
 * @param onDragStarted      Called when a drag gesture begins, with the touch-down position.
 * @param onDragStopped      Called when a drag gesture ends (release or cancel).
 * @param onDrag             Called on each drag delta, receiving the layout size and drag amount.
 */
class DampedDragAnimation(
    private val animationScope: CoroutineScope,
    val initialValue: Float,
    val valueRange: ClosedRange<Float>,
    val visibilityThreshold: Float,
    val initialScale: Float,
    val pressedScale: Float,
    val canDrag: (Offset) -> Boolean = { true },
    val onDragStarted: DampedDragAnimation.(position: Offset) -> Unit,
    val onDragStopped: DampedDragAnimation.() -> Unit,
    val onDrag: DampedDragAnimation.(size: IntSize, dragAmount: Offset) -> Unit,
) {

    private val valueAnimationSpec =
        spring(1f, 1000f, visibilityThreshold)
    private val velocityAnimationSpec =
        spring(0.5f, 300f, visibilityThreshold * 10f)
    private val pressProgressAnimationSpec =
        spring(1f, 1000f, 0.001f)
    private val scaleXAnimationSpec =
        spring(0.6f, 250f, 0.001f)
    private val scaleYAnimationSpec =
        spring(0.7f, 250f, 0.001f)

    private val valueAnimation =
        Animatable(initialValue, visibilityThreshold)
    private val velocityAnimation =
        Animatable(0f, 5f)
    private val pressProgressAnimation =
        Animatable(0f, 0.001f)
    private val scaleXAnimation =
        Animatable(initialScale, 0.001f)
    private val scaleYAnimation =
        Animatable(initialScale, 0.001f)

    private val mutatorMutex = MutatorMutex()

    private val velocityTracker = VelocityTracker()

    /** The current animated value, clamped within [valueRange]. */
    val value: Float get() = valueAnimation.value
    /** The target value the animation is moving toward. */
    val targetValue: Float get() = valueAnimation.targetValue
    /** Press progress from 0 (released) to 1 (fully pressed). */
    val pressProgress: Float get() = pressProgressAnimation.value
    /** Horizontal scale factor, animated between [initialScale] and [pressedScale]. */
    val scaleX: Float get() = scaleXAnimation.value
    /** Vertical scale factor, animated between [initialScale] and [pressedScale]. */
    val scaleY: Float get() = scaleYAnimation.value
    /** Smoothed velocity of the animated value (normalized to the value range). */
    val velocity: Float get() = velocityAnimation.value

    /** Modifier that attaches drag gesture input to drive this animation. */
    val modifier: Modifier = Modifier.pointerInput(Unit) {
        inspectDragGestures(
            onDragStart = { down ->
                onDragStarted(down.position)
                press()
            },
            onDragEnd = {
                onDragStopped()
                release()
            },
            onDragCancel = {
                onDragStopped()
                release()
            }
        ) { change, dragAmount ->
            val position = change.position
            val previousPosition = change.previousPosition

            val isInside = canDrag(position)
            val wasInside = canDrag(previousPosition)

            if (isInside && wasInside) {
                onDrag(size, dragAmount)
            }
        }
    }

    /** Triggers the press animation: advances [pressProgress] to 1 and scales to [pressedScale]. */
    fun press() {
        velocityTracker.resetTracking()
        animationScope.launch {
            launch { pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec) }
            launch { scaleXAnimation.animateTo(pressedScale, scaleXAnimationSpec) }
            launch { scaleYAnimation.animateTo(pressedScale, scaleYAnimationSpec) }
        }
    }

    /**
     * Triggers the release animation: waits for [value] to settle, then animates
     * [pressProgress] back to 0 and scale back to [initialScale].
     */
    fun release() {
        animationScope.launch {
            awaitFrame()
            if (value != targetValue) {
                val threshold = (valueRange.endInclusive - valueRange.start) * 0.025f
                snapshotFlow { valueAnimation.value }.first { abs(it - valueAnimation.targetValue) < threshold }
            }
            launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
            launch { scaleXAnimation.animateTo(initialScale, scaleXAnimationSpec) }
            launch { scaleYAnimation.animateTo(initialScale, scaleYAnimationSpec) }
        }
    }

    /**
     * Directly sets the target value, animating from the current value to [value]
     * (clamped to [valueRange]) while tracking velocity.
     *
     * @param value The desired target value.
     */
    fun updateValue(value: Float) {
        val targetValue = value.coerceIn(valueRange)
        animationScope.launch {
            launch { valueAnimation.animateTo(targetValue, valueAnimationSpec) { updateVelocity() } }
        }
    }

    /**
     * Animates to [value] with a press-release cycle: presses in, animates the value,
     * waits for velocity to settle, then releases. Uses [MutatorMutex] to serialize
     * concurrent calls.
     *
     * @param value The desired target value (clamped to [valueRange]).
     */
    fun animateToValue(value: Float) {
        animationScope.launch {
            mutatorMutex.mutate {
                press()
                val targetValue = value.coerceIn(valueRange)
                launch { valueAnimation.animateTo(targetValue, valueAnimationSpec) }
                if (velocity != 0f) {
                    launch { velocityAnimation.animateTo(0f, velocityAnimationSpec) }
                }
                release()
            }
        }
    }

    private fun updateVelocity() {
        velocityTracker.addPosition(
            System.currentTimeMillis(),
            Offset(value, 0f)
        )
        val targetVelocity = velocityTracker.calculateVelocity().x / (valueRange.endInclusive - valueRange.start)
        animationScope.launch { velocityAnimation.animateTo(targetVelocity, velocityAnimationSpec) }
    }
}