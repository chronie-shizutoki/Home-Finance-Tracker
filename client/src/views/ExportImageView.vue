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

<style scoped src="../styles/views/ExportImageView.css"></style>
