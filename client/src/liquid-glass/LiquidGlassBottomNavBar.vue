<script setup lang="ts">
import { ref, computed, watch } from 'vue'

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
  color?: string
  alwaysShowGlass?: boolean
}>(), {
  modelValue: '',
  size: 'medium',
  disabled: false,
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
    height: 38,
    itemWidth: 56,
    fontSize: '0.6rem',
    iconSize: 14,
    padding: 4
  },
  medium: {
    height: 46,
    itemWidth: 72,
    fontSize: '0.7rem',
    iconSize: 18,
    padding: 4
  },
  large: {
    height: 56,
    itemWidth: 92,
    fontSize: '0.8rem',
    iconSize: 22,
    padding: 5
  },
  XL: {
    height: 68,
    itemWidth: 112,
    fontSize: '0.95rem',
    iconSize: 26,
    padding: 6
  }
}

const dimensions = computed(() => sizePresets[props.size])
const sliderHeight = computed(() => dimensions.value.height)
const itemWidth = computed(() => dimensions.value.itemWidth)
const sliderWidth = computed(() => itemWidth.value * props.items.length)
const selectedIndex = computed(() => props.items.findIndex(item => item.id === props.modelValue))

const activeIndicatorX = computed(() => {
  const index = selectedIndex.value
  return index >= 0 ? index * itemWidth.value : 0
})

const handleItemClick = (item: NavItem) => {
  if (props.disabled || item.id === props.modelValue) return
  emit('update:modelValue', item.id)
}
</script>

<template>
  <div
    class="lg-navbar"
    :class="{ 'lg-disabled': disabled }"
    :style="{
      width: `${sliderWidth}px`,
      height: `${sliderHeight}px`,
      borderRadius: `${sliderHeight / 2}px`,
      padding: `${dimensions.padding}px`
    }"
  >
    <!-- Active indicator pill -->
    <div
      class="lg-active-pill"
      :style="{
        width: `${itemWidth - dimensions.padding * 2}px`,
        height: `${sliderHeight - dimensions.padding * 2}px`,
        borderRadius: `${(sliderHeight - dimensions.padding * 2) / 2}px`,
        transform: `translateX(${activeIndicatorX}px) translateY(-50%)`,
        backgroundColor: color
      }"
    ></div>

    <!-- Items -->
    <div class="lg-items">
      <button
        v-for="item in items"
        :key="item.id"
        type="button"
        class="lg-item"
        :class="{ 'lg-item-active': modelValue === item.id }"
        :style="{ width: `${itemWidth}px` }"
        @click="handleItemClick(item)"
        :disabled="disabled"
      >
        <div
          v-if="item.icon"
          class="lg-icon"
          v-html="item.icon"
          :style="{ width: `${dimensions.iconSize}px`, height: `${dimensions.iconSize}px` }"
        ></div>
        <span
          class="lg-label"
          :style="{ fontSize: dimensions.fontSize }"
        >{{ item.label }}</span>
      </button>
    </div>
  </div>
</template>

<style scoped>
.lg-navbar {
  position: relative;
  display: inline-flex;
  align-items: center;
  /* Dark translucent glass works in both light and dark modes */
  background: rgba(15, 23, 42, 0.65);
  backdrop-filter: blur(12px) saturate(150%);
  -webkit-backdrop-filter: blur(12px) saturate(150%);
  border: 1px solid rgba(255, 255, 255, 0.12);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.08),
    0 4px 20px rgba(0, 0, 0, 0.2);
  box-sizing: border-box;
  user-select: none;
  transition: opacity 0.2s ease;
}

.lg-navbar.lg-disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.lg-active-pill {
  position: absolute;
  top: 50%;
  left: 0;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.25);
  transition: transform 0.25s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.lg-items {
  position: relative;
  display: flex;
  align-items: center;
  width: 100%;
  height: 100%;
  z-index: 1;
}

.lg-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  padding: 0;
  margin: 0;
  border: none;
  background: transparent;
  color: rgba(255, 255, 255, 0.65);
  cursor: pointer;
  transition: color 0.2s ease;
}

.lg-item:hover:not(:disabled) {
  color: rgba(255, 255, 255, 0.9);
}

.lg-item.lg-item-active {
  color: #ffffff;
}

.lg-item:disabled {
  cursor: not-allowed;
}

.lg-icon {
  margin-bottom: 2px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.lg-icon :deep(svg) {
  width: 100%;
  height: 100%;
}

.lg-label {
  font-weight: 500;
  line-height: 1;
  text-align: center;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* Light mode: keep the dark pill so it remains readable on light backgrounds */
@media (prefers-color-scheme: light) {
  .lg-navbar {
    background: rgba(15, 23, 42, 0.72);
    border-color: rgba(255, 255, 255, 0.18);
  }
}
</style>
