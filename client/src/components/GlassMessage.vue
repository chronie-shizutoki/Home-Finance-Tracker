<template>
  <Transition
    name="glass-message-fade"
  >
    <div
      v-if="visible"
      :class="['glass-message', messageType, responsivePosition]"
      :style="{ zIndex: messageZIndex }"
      v-liquid-glass="{ reveal: 'none' }"
    >
      <div class="glass-message-content">
        <div class="glass-message-icon">
          <svg v-if="messageType === 'success'" xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"></path>
            <polyline points="22 4 12 14.01 9 11.01"></polyline>
          </svg>
          <svg v-else-if="messageType === 'warning'" xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"></path>
            <line x1="12" y1="9" x2="12" y2="13"></line>
            <line x1="12" y1="17" x2="12.01" y2="17"></line>
          </svg>
          <svg v-else-if="messageType === 'error'" xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <circle cx="12" cy="12" r="10"></circle>
            <line x1="15" y1="9" x2="9" y2="15"></line>
            <line x1="9" y1="9" x2="15" y2="15"></line>
          </svg>
          <svg v-else xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <circle cx="12" cy="12" r="10"></circle>
            <line x1="12" y1="16" x2="12" y2="12"></line>
            <line x1="12" y1="8" x2="12.01" y2="8"></line>
          </svg>
        </div>
        <div class="glass-message-text">
          {{ message }}
        </div>
        <button v-if="closable" class="glass-message-close" @click="close" v-liquid-glass>
          <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <line x1="18" y1="6" x2="6" y2="18"></line>
            <line x1="6" y1="6" x2="18" y2="18"></line>
          </svg>
        </button>
      </div>
    </div>
  </Transition>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'

const props = defineProps({
  message: {
    type: String,
    default: ''
  },
  type: {
    type: String,
    default: 'info',
    validator: (value) => ['success', 'warning', 'error', 'info'].includes(value)
  },
  duration: {
    type: Number,
    default: 3000
  },
  closable: {
    type: Boolean,
    default: false
  },
  position: {
    type: String,
    default: 'auto',
    validator: (value) => ['top-left', 'top-right', 'bottom-left', 'bottom-right', 'top', 'bottom', 'auto'].includes(value)
  },
  zIndex: {
    type: Number,
    default: 3000
  },
})

const emit = defineEmits(['close'])

const visible = ref(true)
const messageType = computed(() => props.type)
const messageZIndex = computed(() => props.zIndex)

// Responsive position: top-right on large screens, bottom-center on small screens
const responsivePosition = computed(() => {
  // Use props.position unless it is 'auto', then fall back to screen size
  if (props.position && props.position !== 'auto') {
    return props.position
  }

  // Detect screen size
  const isSmallScreen = window.innerWidth <= 768
  return isSmallScreen ? 'bottom' : 'top-right'
})

let timer = null

const close = () => {
  visible.value = false
  clearTimeout(timer)
  emit('close')
}

onMounted(() => {
  if (props.duration > 0) {
    timer = setTimeout(close, props.duration)
  }
})

onUnmounted(() => {
  clearTimeout(timer)
})
</script>

<style scoped src="../styles/components/GlassMessage.css"></style>