/**
 * useLiquidGlass — Vue composable that turns a component root element into a
 * real WebGL liquid-glass surface via the liquidGL engine.
 *
 * The engine keeps a single shared renderer (window.__liquidGLRenderer__); the
 * first call creates it and later calls only add a lens. This composable wires
 * the lens lifecycle to the Vue component it is called from (onMounted /
 * onBeforeUnmount) and exposes setOptions() for live tuning.
 */

import { onMounted, onBeforeUnmount } from 'vue'
import { createLens, removeLens, tuneLens, nextLensId, ensureHostLayer } from '@/lib/liquidgl/engine.js'

export { LIQUID_GLASS_PRESETS } from '@/lib/liquidgl/engine.js'

/**
 * @param {import('vue').Ref<HTMLElement|null>} targetRef  the pane root element
 * @param {object} config  bag of lens options (getters are fine for reactivity)
 */
export function useLiquidGlass(targetRef, config) {
  const id = nextLensId()
  let lens = null

  onMounted(() => {
    const el = targetRef.value
    if (!el || !config || config.disabled || config.effect !== 'webgl') return
    el.classList.add('v-liquid-glass')
    ensureHostLayer(el)
    lens = createLens(el, config)
  })

  onBeforeUnmount(() => {
    if (lens) removeLens(lens)
    lens = null
  })

  // Live-tune the active lens (mirrors the engine's demo control panel).
  function setOptions(patch) {
    tuneLens(lens, patch)
  }

  return { id, setOptions }
}
