<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import Filter from './Filter.vue'

// Types
interface NavItem {
  id: string
  label: string
  icon?: string // SVG path content
}

// Props
const props = withDefaults(defineProps<{
  modelValue: string
  items: NavItem[]
  size?: 'small' | 'medium' | 'large' | 'XL'
  disabled?: boolean
  // Glass customization props
  specularOpacity?: number
  specularSaturation?: number
  blur?: number
  baseRefraction?: number
  color?: string
  alwaysShowGlass?: boolean
}>(), {
  modelValue: '',
  size: 'medium',
  disabled: false,
  specularOpacity: 0.4,
  specularSaturation: 10,
  blur: 0,
  baseRefraction: -0.4,
  color: '#3b82f6',
  alwaysShowGlass: false
})

// Emits
const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

// Size presets
const sizePresets = {
  small: {
    height: 42,
    itemWidth: 60,
    thumbHeight: 38,
    bezelWidth: 6,
    bazelWidthBg: 15,
    glassThickness: 100,
    fontSize: '0.5rem',
    iconSize: 16,
    thumbScale: 1.4,
    thumbScaleY: 1.2
  },
  medium: {
    height: 54,
    itemWidth: 80,
    thumbHeight: 50,
    bezelWidth: 8,
    bazelWidthBg: 30,
    glassThickness: 110,
    fontSize: '0.57rem',
    iconSize: 20,
    thumbScale: 1.3,
    thumbScaleY: 1.1
  },
  large: {
    height: 67,
    itemWidth: 100,
    thumbHeight: 62,
    bezelWidth: 13,
    bazelWidthBg: 30,
    glassThickness: 120,
    fontSize: '0.675rem',
    iconSize: 24,
    thumbScale: 1.3,
    thumbScaleY: 1.1
  },
  XL: {
    height: 80,
    itemWidth: 120,
    thumbHeight: 72,
    bezelWidth: 15,
    bazelWidthBg: 30,
    glassThickness: 160,
    fontSize: '0.8rem',
    iconSize: 28,
    thumbScale: 1.3,
    thumbScaleY: 1.25
  }
}

// Computed dimensions
const dimensions = computed(() => sizePresets[props.size])
const sliderHeight = computed(() => dimensions.value.height)
const itemWidth = computed(() => dimensions.value.itemWidth)
const sliderWidth = computed(() => itemWidth.value * props.items.length)
const thumbWidth = computed(() => itemWidth.value - 4) // Slightly smaller than item
const thumbHeight = computed(() => dimensions.value.thumbHeight)
const thumbRadius = computed(() => thumbHeight.value / 2)
const bezelWidth = computed(() => dimensions.value.bezelWidth)
const bazelWidthBg = computed(() => dimensions.value.bazelWidthBg)
const glassThickness = computed(() => dimensions.value.glassThickness)

// Generate unique filter ID
const filterId = `liquid-glass-navbar-${Math.random().toString(36).substr(2, 9)}`
const bgFilterId = `liquid-glass-navbar-bg-${Math.random().toString(36).substr(2, 9)}`

// Constants for animation
const THUMB_REST_SCALE = 1
const THUMB_ACTIVE_SCALE = dimensions.value.thumbScale
const THUMB_ACTIVE_SCALE_Y = dimensions.value.thumbScaleY

// Internal state
const internalValue = ref(props.modelValue)
const selectedIndex = computed(() => props.items.findIndex(item => item.id === internalValue.value))
const pointerDown = ref(0)
const initialPointerX = ref(0)
const initialThumbX = ref(0) // Thumb position at start of drag
const currentThumbX = ref(0) // Current visual position (px)
const isMounted = ref(false)

onMounted(() => {
  isMounted.value = true
})

// Initialize position
watch(() => props.modelValue, (newVal) => {
  internalValue.value = newVal
  const index = props.items.findIndex(item => item.id === newVal)
  // Only snap on initial load. Subsequent updates should animate via selectedIndex watch.
  if (index !== -1 && pointerDown.value === 0 && !isMounted.value) {
    targetThumbX(index)
  }
}, { immediate: true })

function targetThumbX (index: number) {
  const centerOffset = (itemWidth.value - thumbWidth.value) / 2
  const target = index * itemWidth.value + centerOffset
  currentThumbX.value = target
}

// Physics Loop
const isAnimating = ref(false)
let animationFrame: number

// Glass visibility with fast fadeout (280ms max)
const glassVisible = ref(false)
let hideGlassTimeout: ReturnType<typeof setTimeout> | null = null

// Wobble state
const wobbleScaleX = ref(1)
const wobbleScaleY = ref(1)

function updatePhysics () {
  if (pointerDown.value > 0) {
    // Reset wobble when dragging manually
    wobbleScaleX.value = lerp(wobbleScaleX.value, 1, 0.2)
    wobbleScaleY.value = lerp(wobbleScaleY.value, 1, 0.2)
    return
  }

  const index = selectedIndex.value
  const centerOffset = (itemWidth.value - thumbWidth.value) / 2
  const dest = (index === -1 ? 0 : index) * itemWidth.value + centerOffset

  // Spring-ish lerp for position
  const diff = dest - currentThumbX.value
  const newVelocity = diff * 0.5

  // Update position
  currentThumbX.value += newVelocity

  // Calculate Wobble (Squash & Stretch)
  const speed = Math.abs(newVelocity)
  const stretchFactor = 1 + Math.min(speed * 0.02, 0.5) // Factor to add to X
  const squashFactor = 1 / stretchFactor // Preserve area/volume roughly

  wobbleScaleX.value = lerp(wobbleScaleX.value, stretchFactor, 0.2)
  wobbleScaleY.value = lerp(wobbleScaleY.value, squashFactor, 0.2)

  const isSettled = Math.abs(diff) < 0.1 && Math.abs(wobbleScaleX.value - 1) < 0.01

  if (isSettled) {
    currentThumbX.value = dest
    wobbleScaleX.value = 1
    wobbleScaleY.value = 1
    isAnimating.value = false
    return
  }

  animationFrame = requestAnimationFrame(updatePhysics)
}

function lerp (start: number, end: number, t: number) {
  return start * (1 - t) + end * t
}

watch(selectedIndex, () => {
  if (pointerDown.value === 0) {
    isAnimating.value = true
    cancelAnimationFrame(animationFrame)
    updatePhysics()
  }
})

// Visual Computed Props
const isActive = computed(() => props.alwaysShowGlass || pointerDown.value > 0.5 || glassVisible.value)

const thumbScale = computed(() => {
  const base = THUMB_REST_SCALE + (THUMB_ACTIVE_SCALE - THUMB_REST_SCALE) * (isActive.value ? 1 : 0)
  return base * wobbleScaleX.value
})

const thumbScaleY = computed(() => {
  const base = THUMB_REST_SCALE + (THUMB_ACTIVE_SCALE_Y - THUMB_REST_SCALE) * (isActive.value ? 1 : 0)
  return base * wobbleScaleY.value
})

const scaleRatio = computed(() => 0.1)

// Event handlers
const handlePointerDown = (e: MouseEvent | TouchEvent) => {
  if (props.disabled) return

  const clientX = 'touches' in e ? e.touches[0].clientX : e.clientX

  pointerDown.value = 1
  initialPointerX.value = clientX
  initialThumbX.value = currentThumbX.value

  // Show glass immediately, cancel any pending hide
  if (hideGlassTimeout) clearTimeout(hideGlassTimeout)
  glassVisible.value = true

  isAnimating.value = false
  cancelAnimationFrame(animationFrame)

  // Add window listeners for drag/up
  window.addEventListener('mousemove', handlePointerMove)
  window.addEventListener('touchmove', handlePointerMove)
  window.addEventListener('mouseup', handlePointerUp)
  window.addEventListener('touchend', handlePointerUp)
}

const handlePointerMove = (e: MouseEvent | TouchEvent) => {
  if (pointerDown.value === 0) return

  const clientX = 'touches' in e
    ? e.touches[0].clientX
    : e.clientX

  const delta = clientX - initialPointerX.value
  let newPos = initialThumbX.value + delta

  // Constrain with elasticity
  const maxPos = sliderWidth.value - thumbWidth.value - (itemWidth.value - thumbWidth.value) / 2
  const minPos = (itemWidth.value - thumbWidth.value) / 2

  if (newPos < minPos) {
    const overflow = minPos - newPos
    newPos = minPos - (overflow / 3) // 1/3 damping
  }
  if (newPos > maxPos) {
    const overflow = newPos - maxPos
    newPos = maxPos + (overflow / 3)
  }

  // Manual Drag Velocity to add wobble during drag
  const velocity = newPos - currentThumbX.value
  const speed = Math.abs(velocity)
  const stretchFactor = 1 + Math.min(speed * 0.05, 0.4)
  const squashFactor = 1 / stretchFactor

  wobbleScaleX.value = lerp(wobbleScaleX.value, stretchFactor, 0.2)
  wobbleScaleY.value = lerp(wobbleScaleY.value, squashFactor, 0.2)

  currentThumbX.value = newPos
}

const handlePointerUp = (e: MouseEvent | TouchEvent) => {
  pointerDown.value = 0

  // Cleanup listeners
  window.removeEventListener('mousemove', handlePointerMove)
  window.removeEventListener('touchmove', handlePointerMove)
  window.removeEventListener('mouseup', handlePointerUp)
  window.removeEventListener('touchend', handlePointerUp)

  // Determine selection
  const thumbCenter = currentThumbX.value + thumbWidth.value / 2
  let index = Math.floor(thumbCenter / itemWidth.value)
  index = Math.max(0, Math.min(index, props.items.length - 1))

  const newItem = props.items[index]
  if (newItem && newItem.id !== internalValue.value) {
    internalValue.value = newItem.id
    emit('update:modelValue', newItem.id)
  }
  // Even if value didn't change, we need to snap back
  isAnimating.value = true
  updatePhysics()

  // Hide glass quickly (280ms) regardless of animation state
  hideGlassTimeout = setTimeout(() => {
    glassVisible.value = false
  }, 280)
}

const handleItemClick = (item: NavItem) => {
  // If clicking on an item that is NOT currently selected, just switch to it.
  if (internalValue.value !== item.id) {
    internalValue.value = item.id
    emit('update:modelValue', item.id)

    // Show glass effect briefly when clicking items
    if (hideGlassTimeout) clearTimeout(hideGlassTimeout)
    glassVisible.value = true
    hideGlassTimeout = setTimeout(() => {
      glassVisible.value = false
    }, 280)
  }
}

onUnmounted(() => {
  cancelAnimationFrame(animationFrame)
  if (hideGlassTimeout) clearTimeout(hideGlassTimeout)
  window.removeEventListener('mousemove', handlePointerMove)
  window.removeEventListener('touchmove', handlePointerMove)
  window.removeEventListener('mouseup', handlePointerUp)
  window.removeEventListener('touchend', handlePointerUp)
})
</script>

<template>
  <div
    class="navbar-root"
    :class="{ 'navbar-disabled': disabled }"
    :style="{
      transform: isActive ? 'scale(1.05)' : 'scale(1)',
      transition: 'transform 0.1s ease-out'
    }"
  >
    <div
      class="navbar-track"
      :style="{
        width: `${sliderWidth}px`,
        height: `${sliderHeight}px`,
        borderRadius: `${sliderHeight / 2}px`
      }"
    >
      <!-- Background Filter -->
      <Filter
        :id="bgFilterId"
        :width="sliderWidth"
        :height="sliderHeight"
        :radius="sliderHeight / 2"
        :bezel-width="bazelWidthBg"
        :glass-thickness="190"
        :refractive-index="1.3"
        bezel-type="convex_squircle"
        shape="pill"
        :blur="2"
        :scale-ratio="0.4"
        :specular-opacity="1"
        :specular-saturation="19"
      />

      <!-- Glass Background -->
      <div
        class="navbar-bg"
        :style="{
          borderRadius: `${sliderHeight / 2}px`,
          backdropFilter: `url(#${bgFilterId})`,
          boxShadow: '0 4px 20px rgba(0, 0, 0, 0.1)'
        }"
      ></div>

      <!-- Click Targets (Z-Index 30: Below Thumb but above background) -->
      <div class="click-targets">
        <div
          v-for="item in items"
          :key="item.id"
          class="click-target"
          :style="{ width: `${itemWidth}px` }"
          @mousedown="handleItemClick(item)"
          @touchstart.passive="handleItemClick(item)"
        ></div>
      </div>

      <!-- The Glass/White Thumb (Z-Index 40: Above Click Targets, Below Text) -->
      <div
        class="navbar-thumb"
        :style="{
          height: `${thumbHeight}px`,
          width: `${thumbWidth}px`,
          transform: `translateX(${currentThumbX}px) translateY(-50%) scale(${thumbScale}) scaleY(${thumbScaleY})`,
          top: `${sliderHeight / 2}px`,
          left: 0,
          pointerEvents: 'auto'
        }"
        @mousedown="handlePointerDown"
        @touchstart.stop="handlePointerDown"
      >
        <div class="navbar-thumb-inner">
          <Filter
            :id="filterId"
            :width="thumbWidth"
            :height="thumbHeight"
            :radius="thumbRadius"
            :bezel-width="bezelWidth"
            :glass-thickness="glassThickness"
            :refractive-index="1.5"
            bezel-type="convex_circle"
            shape="pill"
            :blur="blur"
            :scale-ratio="scaleRatio"
            :specular-opacity="specularOpacity"
            :specular-saturation="specularSaturation"
          />

          <!-- Thumb Body -->
          <div
            class="navbar-thumb-body"
            :class="{ 'navbar-thumb-solid': !isActive, 'navbar-thumb-glass': isActive }"
            :style="{
              borderRadius: `${thumbRadius}px`,
              backdropFilter: `url(#${filterId})`
            }"
          ></div>
        </div>
      </div>

      <!-- Items Layer -->
      <!-- At Rest: z-50 (Above Thumb) because Thumb is Opaque White -->
      <!-- Dragging: z-20 (Below Thumb) because Thumb is Glass and we want distortion -->
      <div
        class="navbar-items"
        :class="isActive ? 'navbar-items-below' : 'navbar-items-above'"
      >
        <div
          v-for="item in items"
          :key="item.id"
          class="navbar-item"
          :style="{
            width: `${itemWidth}px`,
            opacity: internalValue === item.id ? 1 : 0.6,
            transform: internalValue === item.id ? 'scale(1.05)' : 'scale(1)'
          }"
        >
          <div
            v-if="item.icon"
            class="navbar-icon"
            v-html="item.icon"
            :style="{
              color: internalValue === item.id ? props.color : 'white',
              width: `${dimensions.iconSize}px`,
              height: `${dimensions.iconSize}px`
            }"
          ></div>
          <span
            class="navbar-label"
            :style="{
              fontSize: dimensions.fontSize,
              color: internalValue === item.id ? props.color : 'white'
            }"
          >{{ item.label }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.navbar-root {
  display: inline-block;
  user-select: none;
  touch-action: none;
}

.navbar-disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.navbar-track {
  position: relative;
}

/*
  The track background uses --navbar-glass-bg so it stays dark enough in
  light mode (white text would otherwise disappear on a white page) while
  still rendering the SVG displacement glass effect via backdrop-filter.
*/
.navbar-bg {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  background-color: var(--navbar-glass-bg);
}

.click-targets {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  display: flex;
  z-index: 30;
}

.click-target {
  height: 100%;
  cursor: pointer;
}

.navbar-thumb {
  position: absolute;
  z-index: 40;
  cursor: pointer;
  transition: transform 0.1s ease-out;
}

.navbar-thumb-inner {
  position: relative;
  width: 100%;
  height: 100%;
}

.navbar-thumb-body {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  transition: background-color 0.1s ease, box-shadow 0.1s ease, border-color 0.1s ease;
}

/*
  Resting thumb: a solid white pill so the active coloured label is readable.
  This matches the official demo's opaque white selection capsule.
*/
.navbar-thumb-solid {
  background-color: rgba(255, 255, 255, 0.95);
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.15);
}

/*
  Active/dragging thumb: transparent glass lens with a bright outline so the
  convex distortion remains visible against the dark track background.
*/
.navbar-thumb-glass {
  background-color: rgba(255, 255, 255, 0.12);
  border: 1px solid rgba(255, 255, 255, 0.35);
  box-shadow: 0 4px 18px rgba(0, 0, 0, 0.25), inset 0 1px 0 rgba(255, 255, 255, 0.25);
}

.navbar-items {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  pointer-events: none;
  gap: 0;
}

.navbar-items-above {
  z-index: 50;
}

.navbar-items-below {
  z-index: 20;
}

.navbar-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  transition: all 0.1s ease;
}

.navbar-icon {
  margin-bottom: 4px;
  transition: color 0.1s ease;
}

.navbar-icon :deep(svg) {
  width: 100%;
  height: 100%;
  fill: none;
  stroke: currentColor;
  stroke-width: 2;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.navbar-label {
  font-weight: 500;
  line-height: 1;
  text-align: center;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  transition: color 0.1s ease;
}
</style>
