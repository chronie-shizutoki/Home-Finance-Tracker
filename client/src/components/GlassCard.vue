<template>
  <div :class="['glass-card']" v-liquid-glass>
    <div v-if="title" class="glass-card-title">
      <slot name="title">{{ title }}</slot>
    </div>
    <div class="glass-card-content">
      <slot></slot>
    </div>
    <div v-if="$slots.footer" class="glass-card-footer">
      <slot name="footer"></slot>
    </div>
  </div>
</template>

<script setup>
const props = defineProps({
  title: {
    type: String,
    default: '',
  },
})
</script>

<style scoped src="../styles/components/GlassCard.css"></style>