<template>
  <Transition
    name="fade"
  >
    <div v-if="message" v-liquid-glass :class="['message-tip', type, responsivePosition]">
      {{ message }}
    </div>
  </Transition>
</template>

<script setup>
import { watch, onUnmounted, computed } from 'vue';


// Declare component props using defineProps
const props = defineProps({
  // Message content, string type
  message: String,
  // Message type, either 'success' or 'error', defaults to 'success'
  type: {
    type: String,
    default: 'success',
    validator: val => ['success', 'error'].includes(val)
  },
  // Position, can be 'top-left', 'top-right', 'bottom-left', 'bottom-right', 'top', 'bottom', 'auto', defaults to 'auto'
  position: {
    type: String,
    default: 'auto',
    validator: val => ['top-left', 'top-right', 'bottom-left', 'bottom-right', 'top', 'bottom', 'auto'].includes(val)
  }
});

// Responsive position calculation: top-right on large screens, bottom-center on small screens
const responsivePosition = computed(() => {
  // Use props.position by default; fall back to screen-size detection if not specified
  if (props.position && props.position !== 'auto') {
    return props.position
  }
  
  // Detect screen size
  const isSmallScreen = window.innerWidth <= 768
  return isSmallScreen ? 'bottom' : 'top-right'
});

// Declare events the component can emit using defineEmits
// The 'update:message' event notifies the parent component to update the message prop
const emit = defineEmits(['update:message']);

// Used to store the timer ID
let timer = null;

// Watch for changes to props.message
watch(() => props.message, (newVal) => {
  // If there is new message content
  if (newVal) {
    // Log a message
    // If an old timer exists, clear it first to avoid duplicate triggers
    if (timer) {
      clearTimeout(timer);
    }
    // Set a new timer to clear the message after 3 seconds
    timer = setTimeout(() => {
      // Emit the 'update:message' event to set the message content to an empty string
      emit('update:message', '');
      // After the timer fires, reset timer to null
      timer = null;
    }, 3000);
  } else {
    // If message becomes empty, immediately clear any running timer
    if (timer) {
      clearTimeout(timer);
      timer = null;
    }
  }
}, { immediate: true }); // immediate: true ensures the watch also runs once during initialization if message already has a value

// Clear the timer when the component is unmounted
onUnmounted(() => {
  if (timer) clearTimeout(timer);
});
</script>

<style scoped>
/* Base message-tip styles. The glass blur is produced by the WebGL engine
   via v-liquid-glass; only the tint colour and framing live here. */
.message-tip {
  position: fixed; /* Floats above the page; offsets come from the position classes */
  padding: 14px 24px;
  border-radius: 16px;
  font-size: 15px;
  font-weight: 450;
  z-index: 9999;
  border: 1px solid rgba(255, 255, 255, 0.25);
  box-shadow: 
    inset 0 1px 0 rgba(255, 255, 255, 0.4),
    0 8px 32px rgba(31, 38, 135, 0.15),
    0 0 0 1px rgba(255, 255, 255, 0.1);
  background: rgba(255, 255, 255, 0.85);
  transition: all 0.35s cubic-bezier(0.4, 0, 0.2, 1);
  text-align: center;
  max-width: 90vw;
  min-width: 220px;
  box-sizing: border-box;
  word-wrap: break-word;
  letter-spacing: 0.2px;
}

/* Light-mode success message styles */
.message-tip.success {
  color: #16a34a; /* Dark green text */
  border-left: 4px solid rgba(34, 197, 94, 0.6);
  box-shadow: 
    inset 0 1px 0 rgba(255, 255, 255, 0.4),
    0 8px 32px rgba(34, 197, 94, 0.15),
    0 0 0 1px rgba(34, 197, 94, 0.1);
}

/* Light-mode error message styles */
.message-tip.error {
  color: #dc2626; /* Dark red text */
  border-left: 4px solid rgba(239, 68, 68, 0.6);
  box-shadow: 
    inset 0 1px 0 rgba(255, 255, 255, 0.4),
    0 8px 32px rgba(239, 68, 68, 0.15),
    0 0 0 1px rgba(239, 68, 68, 0.1);
}

/* Dark mode support */
@media (prefers-color-scheme: dark) {
  .message-tip {
    background: rgba(30, 41, 59, 0.85);
    border-color: rgba(255, 255, 255, 0.12);
    box-shadow: 
      inset 0 1px 0 rgba(255, 255, 255, 0.1),
      0 8px 32px rgba(0, 0, 0, 0.25),
      0 0 0 1px rgba(255, 255, 255, 0.05);
  }

  /* Dark-mode success message styles */
  .message-tip.success {
    color: #34d399;
    border-left: 4px solid rgba(34, 197, 94, 0.7);
    box-shadow: 
      inset 0 1px 0 rgba(255, 255, 255, 0.1),
      0 8px 32px rgba(34, 197, 94, 0.12),
      0 0 0 1px rgba(34, 197, 94, 0.15);
  }

  /* Dark-mode error message styles */
  .message-tip.error {
    color: #fca5a5;
    border-left: 4px solid rgba(239, 68, 68, 0.7);
    box-shadow: 
      inset 0 1px 0 rgba(255, 255, 255, 0.1),
      0 8px 32px rgba(239, 68, 68, 0.12),
      0 0 0 1px rgba(239, 68, 68, 0.15);
  }
}

/* Message-tip transition effect */
.fade-enter-active,
.fade-leave-active {
  transition: all 0.35s cubic-bezier(0.4, 0, 0.2, 1);
}

/* Apply different animations based on position */
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
  scale: 0.9;
}

/* Top-position animation */
.message-tip.top {
  .fade-enter-from,
  .fade-leave-to {
    transform: translateX(-50%) translateY(-30px) scale(0.9);
  }
}

.message-tip.top-right,
.message-tip.top-left {
  .fade-enter-from,
  .fade-leave-to {
    transform: translateY(-30px) scale(0.9);
  }
}

/* Bottom-position animation */
.message-tip.bottom {
  .fade-enter-from,
  .fade-leave-to {
    transform: translateX(-50%) translateY(30px) scale(0.9);
  }
}

.message-tip.bottom-right,
.message-tip.bottom-left {
  .fade-enter-from,
  .fade-leave-to {
    transform: translateY(30px) scale(0.9);
  }
}

/* Hover effect */
.message-tip:hover {
  box-shadow: 
    inset 0 1px 0 rgba(255, 255, 255, 0.4),
    0 12px 40px rgba(31, 38, 135, 0.2),
    0 0 0 1px rgba(255, 255, 255, 0.1);
}

.message-tip.top:hover {
  transform: translateX(-50%) translateY(-2px);
}

.message-tip.top-right:hover,
.message-tip.top-left:hover {
  transform: translateY(-2px);
}

.message-tip.bottom:hover {
  transform: translateX(-50%) translateY(2px);
}

.message-tip.bottom-right:hover,
.message-tip.bottom-left:hover {
  transform: translateY(2px);
}

/* Position styles */
.message-tip.top {
  top: 20px;
  left: 50%;
  transform: translateX(-50%);
}

.message-tip.top-right {
  top: 20px;
  right: 20px;
  transform: none;
}

.message-tip.top-left {
  top: 20px;
  left: 20px;
  transform: none;
}

.message-tip.bottom {
  bottom: 20px;
  left: 50%;
  transform: translateX(-50%);
}

.message-tip.bottom-right {
  bottom: 20px;
  right: 20px;
  transform: none;
}

.message-tip.bottom-left {
  bottom: 20px;
  left: 20px;
  transform: none;
}

/* Responsive adjustments */
@media (max-width: 480px) {
  .message-tip {
    max-width: calc(100vw - 40px);
    border-radius: 14px;
    padding: 12px 20px;
  }
}
</style>
