<template>
  <div 
    :class="['glass-switch-container']"
    :style="containerStyle"
  >
    <label
      :class="[
        'glass-switch',
        {
          'active': modelValue,
          'disabled': disabled,
          'loading': loading
        }
      ]"
      :style="switchStyle"
    >
      <input 
        type="checkbox" 
        :checked="modelValue" 
        :disabled="disabled || loading"
        @change="handleChange"
        class="glass-switch-input"
      >
      <span class="glass-switch-track">
        <span class="liquid-effect"></span>
        <span class="inner-glow"></span>
      </span>
      <span class="glass-switch-slider">
        <span class="slider-shine"></span>
        <span class="slider-reflect"></span>
      </span>
      <span class="switch-ripple" v-if="showRipple"></span>
    </label>
    <span v-if="label" class="glass-switch-label">{{ label }}</span>

    <!-- Loading spinner -->
    <div v-if="loading" class="switch-loading-overlay">
      <div class="loading-spinner"></div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  label: {
    type: String,
    default: ''
  },
  disabled: {
    type: Boolean,
    default: false
  },
  loading: {
    type: Boolean,
    default: false
  },
  size: {
    type: String,
    default: 'medium',
    validator: (value) => ['small', 'medium', 'large'].includes(value)
  },
  activeColor: {
    type: String,
    default: 'linear-gradient(135deg, #60a5fa, #3b82f6, #2563eb)'
  },
  inactiveColor: {
    type: String,
    default: 'linear-gradient(135deg, rgba(156, 163, 175, 0.3), rgba(107, 114, 128, 0.2))'
  }
})

const emit = defineEmits(['update:modelValue', 'change'])

const showRipple = ref(false)

const sizeMap = {
  small: {
    width: '46px',
    height: '24px',
    sliderSize: '18px',
    fontSize: '12px'
  },
  medium: {
    width: '58px',
    height: '30px',
    sliderSize: '24px',
    fontSize: '14px'
  },
  large: {
    width: '72px',
    height: '36px',
    sliderSize: '30px',
    fontSize: '16px'
  }
}

const switchStyle = computed(() => {
  const size = sizeMap[props.size]
  return {
    width: size.width,
    height: size.height,
    '--active-color': props.activeColor,
    '--inactive-color': props.inactiveColor,
    '--slider-size': size.sliderSize,
    '--switch-width': size.width,
    '--switch-height': size.height
  }
})

const containerStyle = computed(() => {
  return props.label ? {
    display: 'flex',
    alignItems: 'center',
    gap: '12px',
    '--label-font-size': sizeMap[props.size].fontSize
  } : {}
})

const handleChange = (event) => {
  if (!props.disabled && !props.loading) {
    const newValue = event.target.checked
    emit('update:modelValue', newValue)
    emit('change', newValue)
    
    // Trigger ripple effect
    showRipple.value = true
    setTimeout(() => {
      showRipple.value = false
    }, 600)
  }
}
</script>

<style scoped src="../styles/components/GlassSwitch.css"></style>