<template>
  <form
    :class="['glass-form']"
    @submit.prevent="handleSubmit"
  >
    <slot></slot>
  </form>
</template>

<script setup>
const props = defineProps({
  model: {
    type: Object,
    default: () => ({})
  },
})

const emit = defineEmits(['submit'])

const handleSubmit = () => {
  emit('submit')
}
</script>

<style scoped src="../styles/components/GlassForm.css"></style>