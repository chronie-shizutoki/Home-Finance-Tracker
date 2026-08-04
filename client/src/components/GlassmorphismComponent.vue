<template>
  <div
    :id="paneId"
    ref="rootRef"
    :class="['glassmorphism-component', type, { 'is-webgl': useWebGL }]"
    :style="rootStyle"
  >
    <slot />
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { useLiquidGlass } from '@/composables/useLiquidGlass.js'

const props = defineProps({
  type: {
    type: String,
    default: 'base', // base | button | input | card | dialog
  },
  width: { type: String, default: 'auto' },
  height: { type: String, default: 'auto' },
  borderRadius: { type: String, default: '12px' },
  padding: { type: String, default: '16px' },

  // Rendering mode. 'webgl' drives the liquidGL engine (the engine owns all
  // refraction — no hand-written CSS glass is used anywhere). 'css' skips the
  // engine entirely and renders a plain translucent pane (legacy fallback).
  effect: { type: String, default: 'webgl' }, // 'webgl' | 'css'
  disabled: { type: Boolean, default: false }, // force-disable the engine

  // ---- liquidGL lens parameters (live-tunable) ----
  preset: { type: String, default: '' }, // '', default, alien, pulse, frost, edge
  refraction: { type: Number, default: 0 },
  aberration: { type: Number, default: 0 },
  bevelDepth: { type: Number, default: 0.06 },
  bevelWidth: { type: Number, default: 0.2 },
  frost: { type: Number, default: 1.5 },
  shadow: { type: Boolean, default: true },
  specular: { type: Boolean, default: true },
  tilt: { type: Boolean, default: false },
  tiltFactor: { type: Number, default: 5 },
  tiltEase: { type: Number, default: 400 },
  magnify: { type: Number, default: 1 },
  reveal: { type: String, default: 'fade' }, // 'fade' | 'none'

  // Stacking: lens canvas is placed at (effectiveZ - 1), so the pane must sit
  // above the content it should refract. Keep all panes on a similar level.
  zIndex: { type: Number, default: 10 },
})

const rootRef = ref(null)
const useWebGL = computed(() => props.effect === 'webgl' && !props.disabled)

const { id: paneId, setOptions } = useLiquidGlass(rootRef, {
  get effect() { return props.effect },
  get disabled() { return props.disabled },
  get preset() { return props.preset || undefined },
  get refraction() { return props.refraction },
  get aberration() { return props.aberration },
  get bevelDepth() { return props.bevelDepth },
  get bevelWidth() { return props.bevelWidth },
  get frost() { return props.frost },
  get shadow() { return props.shadow },
  get specular() { return props.specular },
  get tilt() { return props.tilt },
  get tiltFactor() { return props.tiltFactor },
  get tiltEase() { return props.tiltEase },
  get magnify() { return props.magnify },
  get reveal() { return props.reveal },
})

const rootStyle = computed(() => ({
  width: props.width,
  height: props.height,
  borderRadius: props.borderRadius,
  padding: props.padding,
  zIndex: props.zIndex,
}))

// Push prop changes into the live lens (same mechanism as the engine demo GUI).
watch(
  () => [
    props.refraction, props.aberration, props.bevelDepth, props.bevelWidth,
    props.frost, props.shadow, props.specular, props.tilt, props.tiltFactor,
    props.tiltEase, props.magnify, props.reveal, props.preset,
  ],
  () => {
    setOptions({
      refraction: props.refraction,
      aberration: props.aberration,
      bevelDepth: props.bevelDepth,
      bevelWidth: props.bevelWidth,
      frost: props.frost,
      shadow: props.shadow,
      specular: props.specular,
      tilt: props.tilt,
      tiltFactor: props.tiltFactor,
      tiltEase: props.tiltEase,
      magnify: props.magnify,
      reveal: props.reveal,
    })
  },
)
</script>

<style scoped>
.glassmorphism-component {
  position: relative;
  overflow: hidden;
  transition: all 0.3s ease;
  border: 1px solid rgba(255, 255, 255, 0.2);
  box-shadow: 0 8px 32px rgba(31, 38, 135, 0.1);
}

/* ---- WebGL mode ----
 * The liquidGL engine paints the refraction on a shared overlay canvas and
 * forces pointer-events:none on this pane, so interactive children must opt
 * back in. The engine captures the pane's background-color as the tint. */
.glassmorphism-component.is-webgl > * {
  pointer-events: auto;
}

/* Base styles */
.glassmorphism-component.base {
  background: rgba(255, 255, 255, 0.7);
}

/* Button styles */
.glassmorphism-component.button {
  background: rgba(255, 255, 255, 0.8);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-weight: 500;
  gap: 8px;
}

.glassmorphism-component.button:hover {
  background: rgba(255, 255, 255, 0.9);
  box-shadow: 0 12px 48px rgba(31, 38, 135, 0.15);
  transform: translateY(-2px);
}

.glassmorphism-component.button:active {
  transform: translateY(0);
}

/* Input styles */
.glassmorphism-component.input {
  background: rgba(255, 255, 255, 0.8);
}

.glassmorphism-component.input:focus-within {
  border-color: rgba(255, 255, 255, 0.4);
  box-shadow: 0 0 0 3px rgba(255, 255, 255, 0.1);
}

/* Card styles */
.glassmorphism-component.card {
  background: rgba(255, 255, 255, 0.7);
}

/* Dialog styles */
.glassmorphism-component.dialog {
  background: rgba(255, 255, 255, 0.9);
}

/* Dark theme */
@media (prefers-color-scheme: dark) {
  .glassmorphism-component {
    border-color: rgba(255, 255, 255, 0.1);
    box-shadow: 0 8px 32px rgba(0, 0, 0, 0.2);
  }

  .glassmorphism-component.base,
  .glassmorphism-component.card,
  .glassmorphism-component.dialog {
    background: rgba(26, 32, 44, 0.7);
  }

  .glassmorphism-component.button {
    background: rgba(26, 32, 44, 0.8);
  }

  .glassmorphism-component.button:hover {
    background: rgba(26, 32, 44, 0.9);
    box-shadow: 0 12px 48px rgba(0, 0, 0, 0.3);
  }

  .glassmorphism-component.input {
    background: rgba(26, 32, 44, 0.8);
  }

  .glassmorphism-component.input:focus-within {
    border-color: rgba(255, 255, 255, 0.2);
    box-shadow: 0 0 0 3px rgba(255, 255, 255, 0.05);
  }
}
</style>
