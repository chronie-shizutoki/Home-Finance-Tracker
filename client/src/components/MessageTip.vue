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

<style scoped src="../styles/components/MessageTip.css"></style>
