<template>
  <transition name="glass-dialog" @after-enter="afterEnter" @after-leave="afterLeave">
    <div v-if="visible" class="glass-dialog-overlay" @click.self="handleClose">
      <div :class="['glass-dialog']" :style="dialogStyle">
        <div class="glass-dialog-header">
          <h3 class="glass-dialog-title">{{ title }}</h3>
          <button class="glass-dialog-close" @click="handleClose">
            <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <line x1="18" y1="6" x2="6" y2="18"></line>
              <line x1="6" y1="6" x2="18" y2="18"></line>
            </svg>
          </button>
        </div>
        <div class="glass-dialog-body">
          <slot></slot>
        </div>
        <div v-if="$slots.footer" class="glass-dialog-footer">
          <slot name="footer"></slot>
        </div>
      </div>
    </div>
  </transition>
</template>

<script setup>
import { computed, watch } from 'vue'

const props = defineProps({
  visible: {
    type: Boolean,
    default: false
  },
  title: {
    type: String,
    default: ''
  },
  width: {
    type: String,
    default: '50%'
  },
  animationDuration: {
    type: Number,
    default: 300
  },
  preventScroll: {
    type: Boolean,
    default: true
  },
  zIndex: {
    type: Number,
    default: 1000
  }
})

const emit = defineEmits(['update:visible', 'close', 'opened', 'closed'])

const dialogStyle = computed(() => ({
  width: props.width,
  '--animation-duration': `${props.animationDuration}ms`
}))

let originalOverflow = ''

watch(() => props.visible, (newVal) => {
  if (props.preventScroll) {
    if (newVal) {
      // Prevent background scroll while open
      originalOverflow = document.body.style.overflow
      document.body.style.overflow = 'hidden'
    } else {
      // Restore background scroll on close
      setTimeout(() => {
        document.body.style.overflow = originalOverflow
      }, props.animationDuration)
    }
  }
})

const handleClose = () => {
  emit('update:visible', false)
  emit('close')
}

const afterEnter = () => {
  emit('opened')
}

const afterLeave = () => {
  emit('closed')
}
</script>

<style scoped src="../styles/components/GlassDialog.css"></style>