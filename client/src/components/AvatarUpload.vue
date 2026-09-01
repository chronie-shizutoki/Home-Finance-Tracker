<template>
  <div class="avatar-upload-container">
    <!-- Avatar preview -->
    <div class="avatar-preview-container">
      <div class="avatar-preview" @click="triggerFileInput">
        <img v-if="avatarUrl" :src="avatarUrl" alt="Avatar" class="avatar-image" />
        <div v-else v-liquid-glass class="avatar-placeholder">
          <span>{{ computedPlaceholder }}</span>
        </div>
      </div>
      <input
        type="file"
        ref="fileInput"
        accept="image/*"
        style="display: none"
        @change="handleFileChange"
      />
    </div>

    <!-- Cropper dialog -->
    <!-- Teleport to body so the fixed overlay escapes the backdrop-filter
         containing block of .user-info-card and centers on the viewport. -->
    <Teleport to="body">
      <transition name="cropper-fade">
        <div v-if="showCropper" class="cropper-overlay" @click="closeCropper">
          <div class="cropper-container" @click.stop>
          <div class="cropper-header">
            <h3>{{ $t('avatar.cropTitle') }}</h3>
            <button class="close-button" @click="closeCropper">&times;</button>
          </div>

        <div class="cropper-content">
          <canvas ref="canvas" class="cropper-canvas"></canvas>
          <div class="zoom-slider-wrapper">
            <LiquidGlassSlider
              v-model="zoomLevel"
              :min="1"
              :max="3"
              size="small"
              class="zoom-slider"
            />
          </div>
        </div>

          <div class="cropper-footer">
            <button class="cancel-button" @click="closeCropper">{{ $t('avatar.cancel') }}</button>
            <button class="confirm-button" @click="confirmCrop">{{ $t('avatar.confirm') }}</button>
          </div>
        </div>
        </div>
      </transition>
    </Teleport>
  </div>
</template>

<script setup>
import { ref, watch, computed, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { updateUserAvatar } from '@/api/membership'
import LiquidGlassSlider from '../liquid-glass/LiquidGlassSlider.vue'

const { t } = useI18n()

// Define component props
const props = defineProps({
  modelValue: {
    type: String,
    default: ''
  },
  placeholder: {
    type: String,
    default: '',
  },
  maxSize: {
    type: Number,
    default: 10 // MB
  },
  size: {
    type: Number,
    default: 200 // Avatar size
  },
  username: {
    type: String,
    required: true
  }
})

// Define component events
const emit = defineEmits(['update:modelValue', 'avatar-uploaded'])

// Component state
const avatarUrl = ref(props.modelValue)
const showCropper = ref(false)
const fileInput = ref(null)
const canvas = ref(null)
const context = ref(null)
const currentImage = ref(null)
const zoomLevel = ref(1)
const imagePosition = ref({ x: 0, y: 0 })
const isDragging = ref(false)
const lastMousePos = ref({ x: 0, y: 0 })

// Dynamically compute the placeholder, with i18n support
const computedPlaceholder = computed(() => {
  return props.placeholder || t('avatar.placeholder')
})

// Trigger the file input
const triggerFileInput = () => {
  // Reset the file input value so selecting the same file still triggers the change event
  if (fileInput.value) {
    fileInput.value.value = ''
  }
  fileInput.value?.click()
}

// Handle file selection
const handleFileChange = (event) => {
  const file = event.target.files[0]
  if (!file) return

  // Check file size
  if (file.size > props.maxSize * 1024 * 1024) {
    alert(t('avatar.error.sizeLimit', { maxSize: props.maxSize }))
    return
  }

  // Check file type
  if (!file.type.startsWith('image/')) {
    alert(t('avatar.error.invalidType'))
    return
  }

  // Read the file and show the cropper UI
  const reader = new FileReader()
  reader.onload = (e) => {
    const img = new Image()
    img.onload = () => {
      console.log('Image loaded:', img.width, 'x', img.height)
      currentImage.value = img
      showCropper.value = true
      
      // Defer execution to ensure the DOM has updated
      nextTick(() => {
        initCanvas()
        drawImage()
        console.log('Image drawn on canvas:', canvas.value?.width, 'x', canvas.value?.height)
      })
    }
    img.src = e.target.result
  }
  reader.readAsDataURL(file)
}

// Initialize the canvas
const initCanvas = () => {
  if (!canvas.value) return

  const ctx = canvas.value.getContext('2d')
  context.value = ctx

  // Keep the canvas square and match its rendered CSS width. drawImage uses
  // a single canvasSize for both dimensions and draws a centered circle mask,
  // so width and height must always be equal.
  const size = canvas.value.clientWidth || props.size
  canvas.value.width = size
  canvas.value.height = size

  // Clear the canvas
  ctx.clearRect(0, 0, size, size)

  // Add mouse event handlers
  canvas.value.addEventListener('mousedown', handleMouseDown)
  canvas.value.addEventListener('mousemove', handleMouseMove)
  canvas.value.addEventListener('mouseup', handleMouseUp)
  canvas.value.addEventListener('mouseleave', handleMouseUp)
  
  // Add touch event handlers
  canvas.value.addEventListener('touchstart', handleTouchStart)
  canvas.value.addEventListener('touchmove', handleTouchMove)
  canvas.value.addEventListener('touchend', handleTouchEnd)
  canvas.value.addEventListener('touchcancel', handleTouchCancel)
}

// Draw the image
const drawImage = () => {
  if (!canvas.value || !context.value || !currentImage.value) {
    console.log('Drawing skipped:', !canvas.value ? 'no canvas' : !context.value ? 'no context' : 'no image')
    return
  }

  const ctx = context.value
  const img = currentImage.value
  const canvasSize = canvas.value.width

  // Clear the canvas
  ctx.clearRect(0, 0, canvasSize, canvasSize)

  // Calculate the displayed image size
  const imgRatio = img.width / img.height
  let displayWidth, displayHeight

  // Ensure the image at least fills the canvas
  if (imgRatio > 1) {
    // Landscape image
    displayHeight = canvasSize
    displayWidth = displayHeight * imgRatio
  } else {
    // Portrait image
    displayWidth = canvasSize
    displayHeight = displayWidth / imgRatio
  }

  // Apply zoom
  displayWidth *= zoomLevel.value
  displayHeight *= zoomLevel.value

  // Calculate the draw position (centered)
  const x = (canvasSize - displayWidth) / 2 + imagePosition.value.x
  const y = (canvasSize - displayHeight) / 2 + imagePosition.value.y

  console.log('Drawing image:', {
    imgWidth: img.width,
    imgHeight: img.height,
    canvasSize: canvasSize,
    displayWidth: displayWidth,
    displayHeight: displayHeight,
    x: x,
    y: y,
    zoom: zoomLevel.value
  })

  // Draw the image
  ctx.drawImage(img, x, y, displayWidth, displayHeight)

  // Draw the crop mask
  ctx.save()
  ctx.globalCompositeOperation = 'destination-in'
  ctx.beginPath()
  ctx.arc(canvasSize / 2, canvasSize / 2, canvasSize / 2, 0, Math.PI * 2)
  ctx.fill()
  ctx.restore()
}

// Mouse event handlers
const handleMouseDown = (e) => {
  isDragging.value = true
  lastMousePos.value = {
    x: e.offsetX,
    y: e.offsetY
  }
}

const handleMouseMove = (e) => {
  if (!isDragging.value) return

  const deltaX = e.offsetX - lastMousePos.value.x
  const deltaY = e.offsetY - lastMousePos.value.y

  imagePosition.value = {
    x: imagePosition.value.x + deltaX,
    y: imagePosition.value.y + deltaY
  }

  lastMousePos.value = {
    x: e.offsetX,
    y: e.offsetY
  }

  drawImage()
}

const handleMouseUp = () => {
  isDragging.value = false
}

// Touch event handlers
const handleTouchStart = (e) => {
  e.preventDefault() // Prevent page scrolling
  if (e.touches.length > 0) {
    isDragging.value = true
    const touch = e.touches[0]
    const rect = canvas.value.getBoundingClientRect()
    lastMousePos.value = {
      x: touch.clientX - rect.left,
      y: touch.clientY - rect.top
    }
  }
}

const handleTouchMove = (e) => {
  e.preventDefault() // Prevent page scrolling
  if (!isDragging.value || e.touches.length === 0) return

  const touch = e.touches[0]
  const rect = canvas.value.getBoundingClientRect()
  const currentX = touch.clientX - rect.left
  const currentY = touch.clientY - rect.top

  const deltaX = currentX - lastMousePos.value.x
  const deltaY = currentY - lastMousePos.value.y

  imagePosition.value = {
    x: imagePosition.value.x + deltaX,
    y: imagePosition.value.y + deltaY
  }

  lastMousePos.value = {
    x: currentX,
    y: currentY
  }

  drawImage()
}

const handleTouchEnd = () => {
  isDragging.value = false
}

const handleTouchCancel = () => {
  isDragging.value = false
}

// Close the cropper dialog
const closeCropper = () => {
  showCropper.value = false
  zoomLevel.value = 1
  imagePosition.value = { x: 0, y: 0 }
  currentImage.value = null
  
  // Remove mouse event listeners
  if (canvas.value) {
    canvas.value.removeEventListener('mousedown', handleMouseDown)
    canvas.value.removeEventListener('mousemove', handleMouseMove)
    canvas.value.removeEventListener('mouseup', handleMouseUp)
    canvas.value.removeEventListener('mouseleave', handleMouseUp)
    
    // Remove touch event listeners
    canvas.value.removeEventListener('touchstart', handleTouchStart)
    canvas.value.removeEventListener('touchmove', handleTouchMove)
    canvas.value.removeEventListener('touchend', handleTouchEnd)
    canvas.value.removeEventListener('touchcancel', handleTouchCancel)
  }
}

// Confirm the crop
const confirmCrop = async () => {
  if (!canvas.value || !props.username) return

  // Get the cropped image data URL
  const dataUrl = canvas.value.toDataURL('image/png', 0.8)
  
  try {
    // Upload the avatar to the backend
    await updateUserAvatar(props.username, dataUrl)
    
    // Update the avatar URL
    avatarUrl.value = dataUrl
    
    // Notify the parent component
    emit('update:modelValue', dataUrl)
    emit('avatar-uploaded', dataUrl)
    
    // Close the cropper dialog
    closeCropper()
  } catch (error) {
    console.error('上传头像失败:', error)
    alert(t('avatar.error.uploadFailed'))
  }
}

// Watch for changes to modelValue
const updateAvatarUrl = (newVal) => {
  avatarUrl.value = newVal
}

watch(
  () => props.modelValue,
  (newVal) => {
    updateAvatarUrl(newVal)
  }
)

// Watch for zoom changes and redraw the image
watch(
  () => zoomLevel.value,
  () => {
    drawImage()
  }
)

// Watch for showCropper changes and redraw once the canvas is initialized
watch(
  () => showCropper.value,
  (newVal) => {
    if (newVal && currentImage.value) {
      // Ensure the canvas is already initialized
      initCanvas()
      drawImage()
    }
  }
)
</script>

<style scoped src="../styles/components/AvatarUpload.css"></style>
