/**
 * v-liquid-glass — Vue directive that upgrades any element into a real WebGL
 * liquid-glass surface driven by the vendored liquidGL engine.
 *
 * Usage:
 *   <div v-liquid-glass>                                  // default preset
 *   <div v-liquid-glass="{ preset: 'frost' }">            // named preset
 *   <div v-liquid-glass="{ tilt: true, frost: 3 }">        // custom params
 *   <button v-liquid-glass>                               // interactive too
 *   <div v-liquid-glass="{ disabled: true }">             // skip the engine
 *
 * The engine forces `pointer-events:none` on the lens HOST, but the global
 * `.v-liquid-glass` rule re-enables it (pointer-events:auto !important), so the
 * directive works equally well on containers (cards, dialogs, toasts, forms)
 * and on interactive controls (buttons, inputs, switches) — they stay clickable
 * while the pane itself refracts whatever is behind it.
 *
 * The directive manages the lens lifecycle directly (mounted/unmounted) because
 * Vue lifecycle *hooks* (onMounted/onBeforeUnmount) are unavailable here.
 */

import { createLens, removeLens, ensureHostLayer } from '@/lib/liquidgl/engine.js'

// Track the live lens per element so we can detach it on unmount.
const lensMap = new WeakMap()

function resolveConfig(binding) {
  const v = binding.value || {}
  return {
    preset: v.preset,
    refraction: v.refraction,
    aberration: v.aberration,
    bevelDepth: v.bevelDepth,
    bevelWidth: v.bevelWidth,
    frost: v.frost,
    shadow: v.shadow !== false,
    specular: v.specular !== false,
    tilt: !!v.tilt,
    tiltFactor: v.tiltFactor,
    tiltEase: v.tiltEase,
    magnify: v.magnify,
    reveal: v.reveal || 'fade',
  }
}

export const liquidGlassDirective = {
  mounted(el, binding) {
    if (binding.value && binding.value.disabled) return
    el.classList.add('v-liquid-glass')
    ensureHostLayer(el)
    const lens = createLens(el, resolveConfig(binding))
    lensMap.set(el, lens)
  },
  unmounted(el) {
    const lens = lensMap.get(el)
    if (lens) removeLens(lens)
    lensMap.delete(el)
  },
}

export default liquidGlassDirective
