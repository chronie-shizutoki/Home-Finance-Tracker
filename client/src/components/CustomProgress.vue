<template>
  <div class="custom-progress" v-liquid-glass>
    <div class="progress-bar-outer">
      <div 
        class="progress-bar-inner" 
        :style="{
          width: `${percentage}%`,
          backgroundColor: color
        }"
      ></div>
    </div>
  </div>
</template>

<script setup>

const props = defineProps({
  percentage: {
    type: Number,
    default: 0,
    validator: (value) => value >= 0 && value <= 100
  },
  color: {
    type: String,
    default: '#4361ee'
  },
  strokeWidth: {
    type: Number,
    default: 12
  }
})
</script>

<style scoped src="../styles/components/CustomProgress.css"></style>