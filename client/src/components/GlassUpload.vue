<template>
  <div :class="['glass-upload-wrapper']">
    <div
      class="glass-upload-dropzone"
      v-liquid-glass
      @click="triggerFileInput"
      @dragover.prevent
      @drop="handleDrop"
    >
      <div class="glass-upload-icon">
        <slot name="icon">
          <svg xmlns="http://www.w3.org/2000/svg" width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path>
            <polyline points="17 8 12 3 7 8"></polyline>
            <line x1="12" y1="3" x2="12" y2="15"></line>
          </svg>
        </slot>
      </div>
      <div class="glass-upload-text">
        <slot name="text">点击上传</slot>
      </div>
      <input 
        ref="fileInput" 
        type="file" 
        :multiple="multiple" 
        :accept="accept" 
        class="glass-upload-input" 
        @change="handleFileChange"
      >
    </div>
    
    <!-- File list -->
    <div v-if="files.length > 0" class="glass-upload-file-list">
      <div
        v-for="(file, index) in files"
        :key="file.uid || index"
        class="glass-upload-file-item"
        v-liquid-glass
      >
        <div class="glass-upload-file-info">
          <div class="glass-upload-file-name">{{ file.name }}</div>
          <div class="glass-upload-file-size">{{ formatFileSize(file.size) }}</div>
        </div>
        <button class="glass-upload-file-remove" @click="removeFile(index)">
          <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <line x1="18" y1="6" x2="6" y2="18"></line>
            <line x1="6" y1="6" x2="18" y2="18"></line>
          </svg>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'

const props = defineProps({
  multiple: {
    type: Boolean,
    default: false
  },
  fileList: {
    type: Array,
    default: () => []
  },
  action: {
    type: String,
    default: ''
  },
  autoUpload: {
    type: Boolean,
    default: false
  },
  accept: {
    type: String,
    default: ''
  }
})

const emit = defineEmits(['file-change', 'file-remove', 'update:file-list', 'change'])

const fileInput = ref(null)
const files = ref([])

// Sync fileList prop into the files ref
watch(() => props.fileList, (newFileList) => {
  files.value = newFileList
}, { deep: true })

// Trigger the file picker
const triggerFileInput = () => {
  fileInput.value?.click()
}

// Handle file selection
const handleFileChange = (event) => {
  const selectedFiles = Array.from(event.target.files)
  if (selectedFiles.length > 0) {
    const newFiles = selectedFiles.map(file => ({
      uid: Date.now() + Math.random().toString(36).substr(2, 9),
      name: file.name,
      size: file.size,
      raw: file
    }))
    
    // Multi-select mode: append to existing list
    // Single-select mode: replace existing list
    files.value = props.multiple ? [...files.value, ...newFiles] : newFiles

    emit('file-change', files.value)
    emit('update:file-list', files.value)
    emit('change', files.value)

    // Clear input so the same file can be picked again
    event.target.value = ''
  }
}

// Handle drop
const handleDrop = (event) => {
  event.preventDefault()
  const droppedFiles = Array.from(event.dataTransfer.files)
  if (droppedFiles.length > 0) {
    const newFiles = droppedFiles.map(file => ({
      uid: Date.now() + Math.random().toString(36).substr(2, 9),
      name: file.name,
      size: file.size,
      raw: file
    }))
    
    // Multi-select mode: append to existing list
    // Single-select mode: replace existing list
    files.value = props.multiple ? [...files.value, ...newFiles] : newFiles

    emit('file-change', files.value)
    emit('update:file-list', files.value)
    emit('change', files.value)
  }
}

// Remove a file
const removeFile = (index) => {
  files.value.splice(index, 1)
  emit('file-change', files.value)
  emit('update:file-list', files.value)
  emit('file-remove', index)
}

// Format file size
const formatFileSize = (size) => {
  if (size < 1024) {
    return size + ' B'
  } else if (size < 1024 * 1024) {
    return (size / 1024).toFixed(2) + ' KB'
  } else {
    return (size / (1024 * 1024)).toFixed(2) + ' MB'
  }
}
</script>

<style scoped src="../styles/components/GlassUpload.css"></style>