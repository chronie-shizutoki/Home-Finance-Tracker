/**
 * engine.js — shared bridge to the `liquidGL` WebGL liquid-glass engine
 * (vendored from liquidGL-main, MIT © NaughtyDuk, v2.0.1).
 *
 * Both the Vue composable (useLiquidGlass) and the v-liquid-glass directive
 * sit on top of this module so there is a single place that knows how to
 * build lens options, create a lens, tune it live, and detach it cleanly.
 *
 * Key engine facts that shape this code:
 * - liquidGL keeps ONE shared renderer on `window.__liquidGLRenderer__`.
 *   The first `liquidGL()` call creates it (snapshot + resolution are
 *   first-call-wins and global); later calls only add a lens for the element.
 *   We therefore always pass the same global snapshot/resolution.
 * - On init the engine sets `pointer-events:none` + `backdrop-filter:none` on
 *   the lens element and paints the refraction on a shared overlay canvas
 *   (pointer-events:none, fixed). The host is re-enabled as interactive via
 *   the global `.v-liquid-glass` rule so buttons / inputs keep working.
 * - A lens instance exposes `.renderer`; the no-WebGL fallback returns the raw
 *   DOM node instead, so callers must detect `.renderer` before cleanup.
 */

import liquidGL from './liquidGL.js'

// Engine presets (from the liquidGL documentation).
export const LIQUID_GLASS_PRESETS = {
  default: { refraction: 0, bevelDepth: 0.052, bevelWidth: 0.211, frost: 2, shadow: true, specular: true },
  alien: { refraction: 0.073, bevelDepth: 0.2, bevelWidth: 0.156, frost: 2, shadow: true, specular: false },
  pulse: { refraction: 0.03, bevelDepth: 0, bevelWidth: 0.273, frost: 0, shadow: false, specular: false },
  frost: { refraction: 0, bevelDepth: 0.035, bevelWidth: 0.119, frost: 0.9, shadow: true, specular: true },
  edge: { refraction: 0.047, bevelDepth: 0.136, bevelWidth: 0.076, frost: 2, shadow: true, specular: false },
}

// Global engine settings. snapshot/resolution are shared across ALL lenses
// (first lens to mount wins); intentionally not per-instance.
export const ENGINE_SETTINGS = { snapshot: 'body', resolution: 1.5 }

// Lens options we expose for live tuning.
export const LENS_PARAMS = [
  'refraction', 'aberration', 'bevelDepth', 'bevelWidth', 'frost',
  'shadow', 'specular', 'tilt', 'tiltFactor', 'tiltEase', 'magnify', 'reveal',
]

// Promote an unlayered host to z-index 1 so it sits in front of the shared
// WebGL overlay (z-index 0). Explicit z-indexes (dialogs, messages, etc.)
// are preserved so overlays keep their stacking order.
export function ensureHostLayer(el) {
  if (!el) return
  if (window.getComputedStyle(el).zIndex === 'auto') {
    el.style.zIndex = '1'
  }
}

let _uid = 0
export function nextLensId() {
  _uid += 1
  return `lg-${_uid}-${Math.random().toString(36).slice(2, 7)}`
}

// Build the flat option object the engine expects, merging a named preset
// (if any) under explicit per-lens overrides.
export function buildLensOptions(id, cfg = {}) {
  const opts = {
    target: `#${id}`,
    snapshot: ENGINE_SETTINGS.snapshot,
    resolution: ENGINE_SETTINGS.resolution,
  }
  if (cfg.preset && LIQUID_GLASS_PRESETS[cfg.preset]) {
    Object.assign(opts, LIQUID_GLASS_PRESETS[cfg.preset])
  }
  // Refraction is disabled by default: the user wants the glass aesthetic
  // (bevel, specular, frost, shadow) without the wavy distortion effect.
  if (opts.refraction === undefined) opts.refraction = 0
  for (const k of LENS_PARAMS) {
    const v = cfg[k]
    if (v !== undefined && v !== null) opts[k] = v
  }
  return opts
}

// Create a lens for `el`. Returns the lens instance (has `.renderer`) or null
// when WebGL is unavailable or init throws — callers keep the CSS fallback.
export function createLens(el, cfg = {}) {
  if (!el) return null
  el.id = el.id || nextLensId()
  const useId = el.id
  try {
    const res = liquidGL(buildLensOptions(useId, cfg))
    return res && res.renderer ? res : null
  } catch (e) {
    console.warn('[liquidGL] init failed — keeping CSS glass', e)
    return null
  }
}

// Fully detach a lens from the shared renderer so it stops rendering and
// leaves no orphan DOM nodes / observers behind.
export function removeLens(lens) {
  if (!lens) return
  try {
    if (typeof lens.setShadow === 'function') lens.setShadow(false)
    const renderer = lens.renderer
    if (renderer && Array.isArray(renderer.lenses)) {
      const i = renderer.lenses.indexOf(lens)
      if (i !== -1) renderer.lenses.splice(i, 1)
    }
    if (lens._sizeObs && typeof lens._sizeObs.disconnect === 'function') {
      lens._sizeObs.disconnect()
    }
  } catch (e) {
    /* defensive: never let cleanup break teardown */
  }
}

// Live-tune an active lens (mirrors the engine's demo control panel).
export function tuneLens(lens, patch = {}) {
  if (!lens) return
  for (const k of LENS_PARAMS) {
    if (patch[k] !== undefined && patch[k] !== null) lens.options[k] = patch[k]
  }
  if (patch.shadow !== undefined && typeof lens.setShadow === 'function') lens.setShadow(patch.shadow)
  if (patch.tilt !== undefined && typeof lens.setTilt === 'function') lens.setTilt(patch.tilt)
}
