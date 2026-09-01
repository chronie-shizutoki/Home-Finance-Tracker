<template>
  <div :class="['glass-slider-container']">
    <div v-if="label" class="glass-slider-label">{{ label }}</div>
    <div
      :class="['glass-slider', { 'disabled': disabled }]"
      :style="sliderStyle"
      v-liquid-glass
      @mousedown="handleMouseDown"
      @touchstart="handleTouchStart"
    >
      <div 
        class="glass-slider-track"
        :style="{
          width: `${progress}%`,
          backgroundColor: trackColor
        }"
      ></div>
      <div
        ref="sliderThumb"
        class="glass-slider-thumb"
        v-liquid-glass
        :style="{
          left: `${progress}%`,
          backgroundColor: thumbColor,
          transform: `translateX(-50%) scale(${isDragging ? 1.2 : 1})`
        }"
      ></div>
    </div>
    <div v-if="showValue" class="glass-slider-value">{{ modelValue }}</div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'

const props = defineProps({
  modelValue: {
    type: Number,
    default: 0
  },
  label: {
    type: String,
    default: ''
  },
  min: {
    type: Number,
    default: 0
  },
  max: {
    type: Number,
    default: 100
  },
  step: {
    type: Number,
    default: 1
  },
  disabled: {
    type: Boolean,
    default: false
  },
  showValue: {
    type: Boolean,
    default: false
  },
  trackColor: {
    type: String,
    default: '#3b82f6'
  },
  thumbColor: {
    type: String,
    default: '#3b82f6'
  },
})

const emit = defineEmits(['update:modelValue', 'change'])

const sliderThumb = ref(null)
const isDragging = ref(false)

const progress = computed(() => {
  return ((props.modelValue - props.min) / (props.max - props.min)) * 100
})

const sliderStyle = computed(() => {
  return {
    '--track-color': props.trackColor,
    '--thumb-color': props.thumbColor
  }
})

const updateValue = (clientX) => {
  if (props.disabled) return
  
  const sliderRect = sliderThumb.value.parentElement.getBoundingClientRect()
  const offsetX = clientX - sliderRect.left
  const percentage = Math.max(0, Math.min(100, (offsetX / sliderRect.width) * 100))
  
  // Compute new value
  const rawValue = (percentage / 100) * (props.max - props.min) + props.min
  // Snap to step
  const newValue = Math.round(rawValue / props.step) * props.step
  
  emit('update:modelValue', newValue)
  emit('change', newValue)
}

const handleMouseDown = (e) => {
  if (props.disabled) return
  isDragging.value = true
  updateValue(e.clientX)
}

const handleTouchStart = (e) => {
  if (props.disabled) return
  isDragging.value = true
  updateValue(e.touches[0].clientX)
}

const handleMouseMove = (e) => {
  if (isDragging.value) {
    updateValue(e.clientX)
  }
}

const handleTouchMove = (e) => {
  if (isDragging.value) {
    updateValue(e.touches[0].clientX)
  }
}

const handleMouseUp = () => {
  isDragging.value = false
}

const handleTouchEnd = () => {
  isDragging.value = false
}

onMounted(() => {
  document.addEventListener('mousemove', handleMouseMove)
  document.addEventListener('mouseup', handleMouseUp)
  document.addEventListener('touchmove', handleTouchMove)
  document.addEventListener('touchend', handleTouchEnd)
})

onUnmounted(() => {
  document.removeEventListener('mousemove', handleMouseMove)
  document.removeEventListener('mouseup', handleMouseUp)
  document.removeEventListener('touchmove', handleTouchMove)
  document.removeEventListener('touchend', handleTouchEnd)
})
</script>

<style scoped src="../styles/components/GlassSlider.css"></style>