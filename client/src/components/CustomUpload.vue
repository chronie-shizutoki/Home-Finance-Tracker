<template>
  <div class="custom-upload">
    <input
      ref="fileInput"
      type="file"
      :accept="accept"
      :multiple="multiple"
      :capture="capture"
      class="file-input"
      @change="handleFileChange"
      style="display: none;"
    />
    <slot :trigger-upload="triggerUpload"></slot>
  </div>
</template>

<script setup>
import { ref } from 'vue';

const props = defineProps({
  action: {
    type: String,
    required: true
  },
  accept: {
    type: String,
    default: ''
  },
  showFileList: {
    type: Boolean,
    default: true
  },
  multiple: {
    type: Boolean,
    default: false
  },
  capture: {
    type: String,
    default: null
  }
});

const emit = defineEmits(['success', 'error']);

const fileInput = ref(null);

const triggerUpload = (event, options = {}) => {
  // Only call preventDefault if event exists and is a valid event
  if (event && event.preventDefault) {
    event.preventDefault();
  }
  
  // Android device special handling
  if (isAndroid()) {
    // Set capture attribute based on options
    if (options.capture) {
      fileInput.value.capture = options.capture;
    } else {
      // Allow user to choose between taking a photo or selecting from the gallery
      fileInput.value.removeAttribute('capture');
    }
  }
  
  fileInput.value?.click();
};

// Detect if it's an Android device
const isAndroid = () => {
  const userAgent = navigator.userAgent.toLowerCase();
  return userAgent.includes('android');
};

const handleFileChange = async (event) => {
  const files = Array.from(event.target.files);
  if (files.length === 0) return;

  // Android device special handling
  if (isAndroid() && files.length > 1) {
    // Android device multiple file upload special handling
    console.log('Android device multiple file upload special handling');
  }

  // Upload each file individually
  for (const file of files) {
    try {
      const formData = new FormData();
      formData.append('file', file);

      const response = await fetch(props.action, {
        method: 'POST',
        body: formData
      });

      if (response.ok) {
        const data = await response.json();
        emit('success', data, file);
      } else {
        const errorData = await response.json().catch(() => ({}));
        emit('error', {
          status: response.status,
          response: errorData,
          responseText: JSON.stringify(errorData)
        }, file);
      }
    } catch (error) {
      emit('error', {
        status: 0,
        response: null,
        responseText: error.message
      }, file);
    }
  }
  // Reset file input value to allow re-selecting the same file
  event.target.value = '';
};
</script>

<style scoped>
.custom-upload {
  display: inline-block;
}

.file-input {
  position: absolute;
  opacity: 0;
  width: 0;
  height: 0;
}
</style>
