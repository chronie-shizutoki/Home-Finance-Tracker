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
            <input
              type="range"
              v-model="zoomLevel"
              min="1"
              max="3"
              step="0.1"
              class="zoom-slider"
            />
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

<style scoped>
.avatar-upload-container {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.avatar-preview-container {
  position: relative;
  cursor: pointer;
}

.avatar-preview {
  position: relative;
  width: 100px;
  height: 100px;
}

.avatar-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: 50%;
  /* Glossy badge effect */
  box-shadow: 
    0 0 10px rgba(255, 255, 255, 0.8) inset,
    0 0 20px rgba(0, 0, 0, 0.2),
    0 4px 8px rgba(0, 0, 0, 0.15);
  border: 3px solid rgba(255, 255, 255, 0.9);
  background: linear-gradient(135deg, rgba(255,255,255,0.2) 0%, rgba(255,255,255,0) 50%, rgba(0,0,0,0.1) 100%);
  transition: all 0.3s ease;
}

.avatar-image:hover {
  transform: scale(1.05);
  box-shadow: 
    0 0 15px rgba(255, 255, 255, 0.9) inset,
    0 0 30px rgba(0, 0, 0, 0.3),
    0 6px 12px rgba(0, 0, 0, 0.2);
}

.avatar-preview {
  position: relative;
  width: 100px;
  height: 100px;
  /* Base pedestal effect */
  background: radial-gradient(circle at center, rgba(255,255,255,0.1) 0%, rgba(0,0,0,0.2) 100%);
  border-radius: 50%;
  padding: 2px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.avatar-placeholder {
  width: 100%;
  height: 100%;
  border-radius: 50%;
  background: linear-gradient(135deg, rgba(240,240,240,0.3) 0%, rgba(240,240,240,0.1) 50%, rgba(0,0,0,0.2) 100%);
  border: 3px solid rgba(217, 217, 217, 0.8);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #909399;
  font-size: 14px;
  transition: all 0.3s ease;
  box-shadow: 
    0 0 10px rgba(255, 255, 255, 0.5) inset,
    0 0 20px rgba(0, 0, 0, 0.1),
    0 2px 4px rgba(0, 0, 0, 0.1);
}

.avatar-placeholder:hover {
  background: linear-gradient(135deg, rgba(240,240,240,0.4) 0%, rgba(240,240,240,0.2) 50%, rgba(0,0,0,0.1) 100%);
  border-color: rgba(64, 158, 255, 0.8);
  transform: scale(1.05);
  box-shadow: 
    0 0 15px rgba(255, 255, 255, 0.7) inset,
    0 0 30px rgba(64, 158, 255, 0.3),
    0 4px 8px rgba(0, 0, 0, 0.15);
}

/* Cropper dialog styles */
.cropper-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

/* Dialog transition animation */
.cropper-fade-enter-active,
.cropper-fade-leave-active {
  transition: opacity 0.3s ease, visibility 0.3s ease;
}

.cropper-fade-enter-from,
.cropper-fade-leave-to {
  opacity: 0;
  visibility: hidden;
}

.cropper-container {
  /* CSS glass surface — the WebGL lens overlay previously hid the crop canvas
     and intercepted the buttons, so this dialog uses CSS glass instead. */
  background: rgba(255, 255, 255, 0.96);
  border-radius: var(--border-radius-lg, 20px);
  width: 90%;
  max-width: 500px;
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.18);
  border: 1px solid rgba(0, 0, 0, 0.08);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
}

/* Container scale animation */
.cropper-fade-enter-active .cropper-container,
.cropper-fade-leave-active .cropper-container {
  transition: transform 0.3s ease;
}

.cropper-fade-enter-from .cropper-container,
.cropper-fade-leave-to .cropper-container {
  transform: scale(0.9);
}

.cropper-fade-enter-to .cropper-container,
.cropper-fade-leave-from .cropper-container {
  transform: scale(1);
}

.cropper-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.18);
}

.cropper-header h3 {
  margin: 0;
  font-size: 18px;
  color: #303133;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.1);
}

.close-button {
  background: rgba(0, 0, 0, 0.04);
  border: 1px solid rgba(0, 0, 0, 0.08);
  font-size: 24px;
  color: #606266;
  cursor: pointer;
  padding: 0;
  width: 30px;
  height: 30px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  transition: all 0.3s ease;
}

.close-button:hover {
  background: rgba(0, 0, 0, 0.08);
  color: #303133;
  transform: rotate(90deg);
}

.cropper-content {
  padding: 20px;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.cropper-canvas {
  width: 100%;
  max-width: 400px;
  aspect-ratio: 1 / 1;
  border-radius: 8px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.1);
  cursor: move;
  background: rgba(255, 255, 255, 0.9);
  display: block;
  touch-action: none; /* Disable the browser's default touch behaviour */
}

.zoom-slider {
  margin-top: 20px;
  width: 100%;
  height: 6px;
  border-radius: 3px;
  background: rgba(255, 255, 255, 0.3);
  outline: none;
  -webkit-appearance: none;
  appearance: none;
}

.zoom-slider::-webkit-slider-thumb {
  -webkit-appearance: none;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.8);
  cursor: pointer;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.2);
}

.zoom-slider::-moz-range-thumb {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.8);
  cursor: pointer;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.2);
  border: none;
}

.cropper-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 16px 20px;
  border-top: 1px solid rgba(255, 255, 255, 0.18);
}

.cancel-button, .confirm-button {
  padding: 8px 16px;
  border-radius: 8px;
  font-size: 14px;
  cursor: pointer;
  border: 1px solid rgba(255, 255, 255, 0.18);
  transition: all 0.3s ease;
  font-weight: 500;
}

.cancel-button {
  background: rgba(0, 0, 0, 0.05);
  color: #606266;
}

.cancel-button:hover {
  background: rgba(0, 0, 0, 0.1);
  color: #303133;
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.confirm-button {
  background: rgba(64, 158, 255, 0.8);
  color: white;
  border-color: rgba(64, 158, 255, 0.5);
}

.confirm-button:hover {
  background: rgba(102, 177, 255, 0.8);
  border-color: rgba(102, 177, 255, 0.5);
  transform: translateY(-1px);
  box-shadow: 0 4px 16px rgba(64, 158, 255, 0.3);
}

/* Dark mode support */
@media (prefers-color-scheme: dark) {
  .cropper-container {
    background: rgba(30, 41, 59, 0.96);
    border-color: rgba(255, 255, 255, 0.12);
  }

  .cropper-header h3 {
    color: #e0e0e0;
    text-shadow: 0 1px 2px rgba(0, 0, 0, 0.5);
  }

  .avatar-placeholder {
    background: linear-gradient(135deg, rgba(64,64,64,0.8) 0%, rgba(48,48,48,0.6) 50%, rgba(0,0,0,0.3) 100%);
    border-color: rgba(128, 128, 128, 0.8);
    color: #a0a0a0;
    box-shadow: 
      0 0 10px rgba(255, 255, 255, 0.2) inset,
      0 0 20px rgba(0, 0, 0, 0.3),
      0 2px 4px rgba(0, 0, 0, 0.2);
  }

  .avatar-placeholder:hover {
    background: linear-gradient(135deg, rgba(80,80,80,0.8) 0%, rgba(64,64,64,0.6) 50%, rgba(0,0,0,0.2) 100%);
    border-color: rgba(64, 158, 255, 0.8);
    box-shadow: 
      0 0 15px rgba(255, 255, 255, 0.3) inset,
      0 0 30px rgba(64, 158, 255, 0.4),
      0 4px 8px rgba(0, 0, 0, 0.25);
  }

  .avatar-image {
    box-shadow: 
      0 0 10px rgba(255, 255, 255, 0.3) inset,
      0 0 20px rgba(0, 0, 0, 0.4),
      0 4px 8px rgba(0, 0, 0, 0.3);
    border: 3px solid rgba(128, 128, 128, 0.9);
    background: linear-gradient(135deg, rgba(128,128,128,0.3) 0%, rgba(128,128,128,0) 50%, rgba(0,0,0,0.2) 100%);
  }

  .avatar-image:hover {
    box-shadow: 
      0 0 15px rgba(255, 255, 255, 0.4) inset,
      0 0 30px rgba(0, 0, 0, 0.5),
      0 6px 12px rgba(0, 0, 0, 0.4);
  }

  .avatar-preview {
    background: radial-gradient(circle at center, rgba(128,128,128,0.2) 0%, rgba(0,0,0,0.3) 100%);
    box-shadow: 0 2px 4px rgba(0, 0, 0, 0.3);
  }

  .cropper-header,
  .cropper-footer {
    border-color: rgba(255, 255, 255, 0.08);
  }

  .close-button {
    background: rgba(255, 255, 255, 0.08);
    border-color: rgba(255, 255, 255, 0.12);
    color: #cbd5e1;
  }

  .close-button:hover {
    background: rgba(255, 255, 255, 0.16);
    color: #f1f5f9;
  }

  .cancel-button {
    background: rgba(255, 255, 255, 0.08);
    color: #cbd5e1;
    border-color: rgba(255, 255, 255, 0.12);
  }

  .cancel-button:hover {
    background: rgba(255, 255, 255, 0.16);
    color: #f1f5f9;
  }

  .confirm-button {
    background: rgba(64, 158, 255, 0.6);
    border-color: rgba(64, 158, 255, 0.3);
  }

  .confirm-button:hover {
    background: rgba(102, 177, 255, 0.6);
    border-color: rgba(102, 177, 255, 0.4);
  }

  .cropper-canvas {
    background: rgba(32, 32, 32, 0.9);
  }

  .zoom-slider {
    background: rgba(64, 64, 64, 0.8);
  }

  .zoom-slider::-webkit-slider-thumb {
    background: rgba(128, 128, 128, 0.8);
  }

  .zoom-slider::-moz-range-thumb {
    background: rgba(128, 128, 128, 0.8);
  }
}
</style>
