<script setup lang="ts">
import { ref, onMounted, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { library } from '@fortawesome/fontawesome-svg-core'
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome'
import { faDownload, faCheck, faTimes } from '@fortawesome/free-solid-svg-icons'

library.add(faDownload, faCheck, faTimes)

const { t } = useI18n()

// API base address (same origin as the page)
const API_BASE_URL = window.location.origin + '/api'

const canvasEl = ref<HTMLCanvasElement | null>(null)
const loading = ref(true)
const rendered = ref(false)
const error = ref('')

const toast = ref<{ show: boolean; type: 'success' | 'error'; msg: string }>({
  show: false,
  type: 'success',
  msg: ''
})

let toastTimer: ReturnType<typeof setTimeout> | null = null

function padZero(num: number): string {
  return num.toString().padStart(2, '0')
}

function showToast(type: 'success' | 'error', msg: string) {
  toast.value = { show: true, type, msg }
  if (toastTimer) clearTimeout(toastTimer)
  toastTimer = setTimeout(() => {
    toast.value.show = false
  }, 3000)
}

// Draw the monthly expense data onto a (visible) canvas as a clean table
function drawDataOnCanvas(data: Record<string, any>[], currentMonth: string) {
  const canvas = canvasEl.value
  if (!canvas) {
    console.error('[ExportImageView] Canvas ref is not available yet')
    return
  }
  const ctx = canvas.getContext('2d')
  if (!ctx) return

  // High-DPI scaling for a crisp image
  const dpr = window.devicePixelRatio || 1
  const scaleFactor = 2
  const totalScale = dpr * scaleFactor

  const columns = [
    t('expense.type'),
    t('expense.amount'),
    t('expense.date'),
    t('expense.remark')
  ]

  function calculateColumnWidths() {
    ctx!.font = 'bold 16px sans-serif'
    const widths = columns.map(col => ctx!.measureText(col).width + 30)
    ctx!.font = '14px sans-serif'
    data.forEach(item => {
      widths[0] = Math.max(widths[0], ctx!.measureText(item[t('expense.type')]).width + 30)
      widths[1] = Math.max(widths[1], ctx!.measureText(String(item[t('expense.amount')])).width + 30)
      widths[2] = Math.max(widths[2], ctx!.measureText(item[t('expense.date')]).width + 30)
      widths[3] = Math.max(widths[3], 400)
    })
    return widths
  }

  const columnWidths = calculateColumnWidths()
  const totalWidth = columnWidths.reduce((s, w) => s + w, 0) + 100
  const width = Math.max(totalWidth, 1000)

  // Estimate content height
  let contentHeight = 150
  const lineHeight = 22
  data.forEach(item => {
    const remarkText = item[t('expense.remark')] || ''
    const maxWidth = columnWidths[3]
    let tempLine = ''
    let tempHeight = 0
    for (let i = 0; i < remarkText.length; i++) {
      tempLine += remarkText[i]
      const metrics = ctx.measureText(tempLine)
      if (metrics.width > maxWidth) {
        tempHeight += lineHeight
        tempLine = remarkText[i]
      }
    }
    if (tempLine.length > 0) tempHeight += lineHeight
    contentHeight += tempHeight > lineHeight ? tempHeight : 30
  })
  const height = Math.max(400, contentHeight + 100)

  canvas.width = width * totalScale
  canvas.height = height * totalScale
  ctx.scale(totalScale, totalScale)
  canvas.style.width = width + 'px'
  canvas.style.height = height + 'px'

  // Page background
  ctx.fillStyle = '#f8f5f0'
  ctx.fillRect(0, 0, width, height)

  // Subtle paper texture
  ctx.save()
  ctx.globalAlpha = 0.03
  ctx.fillStyle = '#000000'
  for (let i = 0; i < 200; i++) {
    ctx.fillRect(Math.random() * width, Math.random() * height, Math.random() * 2, Math.random() * 2)
  }
  ctx.restore()

  // Title
  ctx.fillStyle = '#333333'
  ctx.font = 'bold 24px sans-serif'
  const title = currentMonth + t('app.export')
  const titleWidth = ctx.measureText(title).width
  ctx.fillText(title, (width - titleWidth) / 2, 60)

  ctx.strokeStyle = '#e0e0e0'
  ctx.lineWidth = 2
  ctx.beginPath()
  ctx.moveTo((width - titleWidth) / 2 - 20, 70)
  ctx.lineTo((width + titleWidth) / 2 + 20, 70)
  ctx.stroke()

  // Header row
  ctx.save()
  ctx.font = 'bold 16px sans-serif'
  const headerY = 100
  const headerHeight = 40
  ctx.fillStyle = '#f0f0f0'
  ctx.fillRect(50, headerY, width - 100, headerHeight)
  ctx.fillStyle = '#333333'
  let x = 50
  for (let i = 0; i < columns.length; i++) {
    ctx.fillText(columns[i], x, headerY + headerHeight * 0.6)
    x += columnWidths[i] + 20
  }
  ctx.strokeStyle = '#e0e0e0'
  ctx.lineWidth = 1
  ctx.beginPath()
  ctx.moveTo(50, headerY + headerHeight)
  ctx.lineTo(width - 50, headerY + headerHeight)
  ctx.stroke()
  ctx.restore()

  // Data rows
  ctx.font = '14px sans-serif'
  let y = 150
  data.forEach((item, index) => {
    if (index % 2 === 0) {
      ctx.fillStyle = '#ffffff'
      ctx.fillRect(50, y - 20, width - 100, 40)
    }
    x = 50

    ctx.fillStyle = '#333333'
    ctx.fillText(item[t('expense.type')], x, y)
    x += columnWidths[0] + 20

    ctx.font = 'bold 14px sans-serif'
    ctx.fillStyle = '#e74c3c'
    ctx.textAlign = 'right'
    ctx.fillText(String(item[t('expense.amount')]), x + columnWidths[1], y)
    ctx.textAlign = 'left'
    ctx.font = '14px sans-serif'
    ctx.fillStyle = '#333333'
    x += columnWidths[1] + 20

    ctx.fillStyle = '#666666'
    ctx.fillText(item[t('expense.date')], x, y)
    ctx.fillStyle = '#333333'
    x += columnWidths[2] + 20

    // Remark with character-level wrapping
    const remarkText = item[t('expense.remark')] || ''
    const maxWidth = columnWidths[3]
    let currentY = y
    let currentLine = ''
    for (let i = 0; i < remarkText.length; i++) {
      currentLine += remarkText[i]
      const metrics = ctx.measureText(currentLine)
      if (metrics.width > maxWidth) {
        const lastSpace = currentLine.lastIndexOf(' ')
        if (lastSpace > 0) {
          ctx.fillText(currentLine.substring(0, lastSpace), x, currentY)
          currentLine = currentLine.substring(lastSpace + 1)
        } else if (currentLine.length > 1) {
          ctx.fillText(currentLine.substring(0, currentLine.length - 1), x, currentY)
          currentLine = remarkText[i]
        }
        currentY += lineHeight
      }
    }
    if (currentLine.length > 0) ctx.fillText(currentLine, x, currentY)

    y = currentY > y ? currentY + lineHeight : y + 30
  })

  ctx.strokeStyle = '#e0e0e0'
  ctx.lineWidth = 1
  ctx.beginPath()
  ctx.moveTo(50, y + 20)
  ctx.lineTo(width - 50, y + 20)
  ctx.stroke()
}

async function loadData() {
  loading.value = true
  rendered.value = false
  error.value = ''
  try {
    const now = new Date()
    const startOfMonth = new Date(now.getFullYear(), now.getMonth(), 1)
    const endOfMonth = new Date(now.getFullYear(), now.getMonth() + 1, 0)
    const currentMonth = now.toLocaleDateString(undefined, { year: 'numeric', month: 'long' })

    const response = await fetch(`${API_BASE_URL}/expenses?limit=10000`)
    if (!response.ok) throw new Error('Failed to fetch expenses')

    const result = await response.json()
    const raw = Array.isArray(result.data) ? result.data : []
    const filtered = raw.filter((e: any) => {
      if (!e || (!e.date && !e.time)) return false
      const d = new Date(e.date || e.time)
      return d >= startOfMonth && d <= endOfMonth
    })
filtered.sort((a: any, b: any) => new Date(b.date || b.time).getTime() - new Date(a.date || a.time).getTime())

    const typeKey = t('expense.type')
    const amountKey = t('expense.amount')
    const dateKey = t('expense.date')
    const remarkKey = t('expense.remark')

    const exportData = filtered.map((e: any) => ({
      [typeKey]: e.type,
      [amountKey]: e.amount,
      [dateKey]: new Date(e.date || e.time).toLocaleDateString(undefined, {
        year: 'numeric', month: '2-digit', day: '2-digit'
      }),
      [remarkKey]: e.remark || ''
    }))

    if (exportData.length === 0) {
      error.value = t('expense.noDataTitle')
      loading.value = false
      rendered.value = false
      return
    }

    loading.value = false
    // Wait for Vue to swap the loading state and render the canvas element
    await nextTick()
    drawDataOnCanvas(exportData, currentMonth)
    rendered.value = true
  } catch (err) {
    console.error('Export image load failed:', err)
    error.value = t('common.loadFailed')
    loading.value = false
    rendered.value = false
  }
}

function handleSave() {
  const canvas = canvasEl.value
  if (!canvas || !rendered.value) return
  const now = new Date()
  const monthStr = `${now.getFullYear()}${padZero(now.getMonth() + 1)}`
  const link = document.createElement('a')
  link.download = `pic_export_HomeFinance_${monthStr}.png`
  link.href = canvas.toDataURL('image/png')
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  showToast('success', t('export.monthData'))
}

onMounted(loadData)
</script>

<template>
  <div class="export-page">
    <!-- Header with the save button pinned to the top-right -->
    <header class="export-header glass-bar">
      <h1 class="export-title">{{ t('export.monthData') }}</h1>
      <button
        class="save-btn"
        :class="{ 'is-busy': loading }"
        @click="handleSave"
        :disabled="!rendered || loading"
      >
        <font-awesome-icon :icon="['fas', 'download']" />
        <span>{{ loading ? t('app.loading') : t('app.export') }}</span>
      </button>
    </header>

    <!-- Body: a CSS-glass card holding the rendered table -->
    <main class="export-body">
      <div class="table-card">
        <div v-show="loading" class="state-box">
          <div class="spinner"></div>
          <p>{{ t('app.loading') }}</p>
        </div>
        <div v-show="error && !loading" class="state-box">
          <font-awesome-icon :icon="['fas', 'times']" class="state-icon error" />
          <p>{{ error }}</p>
        </div>
        <div v-show="!loading && !error" class="canvas-wrap">
          <canvas ref="canvasEl"></canvas>
        </div>
      </div>
    </main>

    <!-- Toast -->
    <transition name="toast-fade">
      <div v-if="toast.show" class="toast" :class="toast.type">
        <font-awesome-icon
          :icon="['fas', toast.type === 'success' ? 'check' : 'times']"
        />
        <span>{{ toast.msg }}</span>
      </div>
    </transition>
  </div>
</template>

<style scoped>
.export-page {
  min-height: 100vh;
  width: 100%;
  padding: 1.5rem;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

/* Top bar */
.export-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.75rem 1.25rem;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border: 1px solid rgba(255, 255, 255, 0.5);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.08);
}

@media (prefers-color-scheme: dark) {
  .export-header {
    background: rgba(30, 41, 59, 0.92);
    border-color: rgba(255, 255, 255, 0.1);
  }
}

.export-title {
  margin: 0;
  font-size: 1.15rem;
  font-weight: 600;
  color: #1e293b;
}

@media (prefers-color-scheme: dark) {
  .export-title {
    color: #e2e8f0;
  }
}

/* Save button */
.save-btn {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.6rem 1.25rem;
  border: 1px solid rgba(59, 130, 246, 0.4);
  border-radius: 12px;
  background: rgba(59, 130, 246, 0.92);
  color: #ffffff;
  font-size: 0.95rem;
  font-weight: 600;
  cursor: pointer;
  transition: transform 0.15s ease, box-shadow 0.15s ease, opacity 0.15s ease;
  box-shadow: 0 4px 14px rgba(59, 130, 246, 0.35);
}

.save-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 6px 18px rgba(59, 130, 246, 0.45);
}

.save-btn:active:not(:disabled) {
  transform: translateY(0);
}

.save-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
  box-shadow: none;
}

/* Body card holding the table */
.export-body {
  flex: 1;
  border-radius: 20px;
  padding: 1.25rem;
  display: flex;
  min-height: 0;
  background: rgba(255, 255, 255, 0.72);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border: 1px solid rgba(255, 255, 255, 0.6);
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.12);
}

@media (prefers-color-scheme: dark) {
  .export-body {
    background: rgba(30, 41, 59, 0.72);
    border-color: rgba(255, 255, 255, 0.1);
  }
}

.table-card {
  flex: 1;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border: 1px solid rgba(255, 255, 255, 0.6);
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.12);
  padding: 1.25rem;
  overflow: auto;
  display: flex;
  align-items: flex-start;
  justify-content: center;
}

@media (prefers-color-scheme: dark) {
  .table-card {
    background: rgba(30, 41, 59, 0.92);
    border-color: rgba(255, 255, 255, 0.1);
  }
}

.canvas-wrap {
  display: inline-block;
  max-width: 100%;
}

.canvas-wrap canvas {
  display: block;
  max-width: 100%;
  height: auto !important;
  border-radius: 8px;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);
}

/* Loading / error states */
.state-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.75rem;
  width: 100%;
  min-height: 240px;
  color: #64748b;
}

.state-icon {
  font-size: 2rem;
}

.state-icon.error {
  color: #ef4444;
}

.spinner {
  width: 36px;
  height: 36px;
  border: 4px solid rgba(59, 130, 246, 0.25);
  border-top-color: #3b82f6;
  border-radius: 50%;
  animation: export-spin 0.8s linear infinite;
}

@keyframes export-spin {
  to { transform: rotate(360deg); }
}

/* Toast */
.toast {
  position: fixed;
  bottom: 1.5rem;
  right: 1.5rem;
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.85rem 1.25rem;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border: 1px solid rgba(255, 255, 255, 0.5);
  box-shadow: 0 12px 30px rgba(0, 0, 0, 0.2);
  color: #1e293b;
  z-index: 50;
  font-weight: 500;
}

.toast.success { color: #15803d; }
.toast.error { color: #dc2626; }

@media (prefers-color-scheme: dark) {
  .toast {
    background: rgba(30, 41, 59, 0.9);
    border-color: rgba(255, 255, 255, 0.12);
    color: #e2e8f0;
  }
}

.toast-fade-enter-active,
.toast-fade-leave-active {
  transition: all 0.3s ease;
}

.toast-fade-enter-from,
.toast-fade-leave-to {
  opacity: 0;
  transform: translateY(1.5rem);
}
</style>
