<!--
 * @file ExportButton.vue
 * @package Home Finance Tracker
 * @module Common Components
 * @description Export button component for exporting data to Excel
 * @author Developer
 * @version 1.0
-->
<template>
  <div class="export-section">
    <GlassButton type="primary" @click="handleExport" class="btn btn-primary">
        <template #icon>
          <FontAwesomeIcon :icon="['fas', 'file-export']" />
        </template>
        {{ $t('app.export') }}
      </GlassButton>
  </div>
</template>

<script setup>

/**
 * Component event definitions
 * @typedef {Object} ExportButtonEmits
 * @property {() => void} export-excel - Trigger data export event
 */
const emits = defineEmits(['export-excel']);

/**
 * Handle export button click event
 */
const handleExport = () => { emits('export-excel'); };
</script>

<style scoped>
.export-section {
  display: flex;
  justify-content: center;
}
</style>
