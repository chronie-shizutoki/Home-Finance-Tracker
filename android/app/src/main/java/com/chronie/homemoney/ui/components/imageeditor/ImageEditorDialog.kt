package com.chronie.homemoney.ui.components.imageeditor

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.BitmapImage
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.chronie.homemoney.R
import com.chronie.homemoney.ui.components.CircularIconButton
import com.chronie.homemoney.ui.components.ExpressiveLoadingIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Crop frame shape of the editor. */
enum class CropShape { SQUARE, CIRCLE }

private enum class DragMode { NONE, MOVE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

/**
 * Full-screen image editor dialog built with Compose (miuix + coil).
 *
 * Features:
 * - Crop with a draggable / resizable frame (square or circle)
 * - Rotate in 90-degree steps
 * - Eraser that paints strokes in white, brush size adjustable via slider
 *
 * @param uri Source image uri. When null the dialog is hidden.
 * @param cropShape Shape of the crop frame. CIRCLE forces a 1:1 aspect ratio.
 * @param enableEraser Whether the white-paint eraser tool is available.
 * @param maxResultSize Longest edge of the result bitmap.
 * @param onDismiss Called when the user cancels editing.
 * @param onConfirm Called with the final cropped bitmap.
 */
@Composable
fun ImageEditorDialog(
    uri: Uri?,
    cropShape: CropShape = CropShape.SQUARE,
    enableEraser: Boolean = true,
    maxResultSize: Int = 1080,
    onDismiss: () -> Unit,
    onConfirm: (Bitmap) -> Unit
) {
    if (uri == null) return
    val context = LocalContext.current
    val density = LocalDensity.current

    var editBitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
    var baseBitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
    var bitmapVersion by remember(uri) { mutableIntStateOf(0) }
    // Crop rect normalized to image coordinates (0..1)
    var cropRect by remember(uri) { mutableStateOf<Rect?>(null) }
    var eraserMode by remember(uri) { mutableStateOf(false) }
    var brushSizeDp by remember { mutableFloatStateOf(24f) }
    var brushCursor by remember { mutableStateOf<Offset?>(null) }

    // Load bitmap with coil
    LaunchedEffect(uri) {
        try {
            val request = ImageRequest.Builder(context)
                .data(uri)
                .size(2048)
                .allowHardware(false)
                .build()
            val result = SingletonImageLoader.get(context).execute(request)
            val image = (result as? SuccessResult)?.image
            val bmp = (image as? BitmapImage)?.bitmap ?: image?.toBitmap()
            if (bmp == null) {
                Toast.makeText(context, context.getString(R.string.image_editor_load_failed), Toast.LENGTH_SHORT).show()
                onDismiss()
            } else {
                val mutable = bmp.copy(Bitmap.Config.ARGB_8888, true)
                baseBitmap = mutable
                editBitmap = mutable.copy(Bitmap.Config.ARGB_8888, true)
                cropRect = defaultCropRect(mutable, cropShape)
                bitmapVersion++
            }
        } catch (_: Exception) {
            Toast.makeText(context, context.getString(R.string.image_editor_load_failed), Toast.LENGTH_SHORT).show()
            onDismiss()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MiuixTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
                // Top bar
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularIconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = context.getString(R.string.cancel), tint = MiuixTheme.colorScheme.onBackground)
                    }
                    Text(
                        text = context.getString(R.string.image_editor_title),
                        style = MiuixTheme.textStyles.title3,
                        modifier = Modifier.weight(1f),
                        color = MiuixTheme.colorScheme.onBackground
                    )
                    CircularIconButton(
                        onClick = {
                            val bmp = editBitmap
                            val rect = cropRect
                            if (bmp != null && rect != null) {
                                try {
                                    onConfirm(cropResult(bmp, rect, maxResultSize))
                                } catch (e: Exception) {
                                    Toast.makeText(context, context.getString(R.string.crop_image_failed, e.message ?: ""), Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                }
                            }
                        }
                    ) {
                        Icon(Icons.Default.Check, contentDescription = context.getString(R.string.confirm), tint = MiuixTheme.colorScheme.primary)
                    }
                }

                // Editor canvas area
                Box(modifier = Modifier.fillMaxWidth().weight(1f).padding(8.dp)) {
                    val bmp = editBitmap
                    if (bmp == null) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            ExpressiveLoadingIndicator(containerVisible = false)
                        }
                    } else {
                        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                            val boxWidthPx = with(density) { maxWidth.toPx() }
                            val boxHeightPx = with(density) { maxHeight.toPx() }
                            val scale = min(boxWidthPx / bmp.width, boxHeightPx / bmp.height)
                            val dispW = bmp.width * scale
                            val dispH = bmp.height * scale
                            val imageRect = Rect(
                                Offset((boxWidthPx - dispW) / 2f, (boxHeightPx - dispH) / 2f),
                                androidx.compose.ui.geometry.Size(dispW, dispH)
                            )
                            val handleRadiusPx = with(density) { 24.dp.toPx() }
                            val minCropPx = with(density) { 48.dp.toPx() }
                            val brushPx = with(density) { brushSizeDp.dp.toPx() }

                            // Eraser stroke painting helpers (bitmap coordinates)
                            val erasePaint = remember(bmp) {
                                Paint().apply {
                                    color = android.graphics.Color.WHITE
                                    style = Paint.Style.STROKE
                                    strokeCap = Paint.Cap.ROUND
                                    strokeJoin = Paint.Join.ROUND
                                    isAntiAlias = true
                                }
                            }
                            val dotPaint = remember(bmp) {
                                Paint().apply {
                                    color = android.graphics.Color.WHITE
                                    style = Paint.Style.FILL
                                    isAntiAlias = true
                                }
                            }

                            fun toBitmapCoords(p: Offset): Offset {
                                return Offset(
                                    ((p.x - imageRect.left) / scale).coerceIn(0f, bmp.width.toFloat()),
                                    ((p.y - imageRect.top) / scale).coerceIn(0f, bmp.height.toFloat())
                                )
                            }

                            fun eraseDot(p: Offset) {
                                val bp = toBitmapCoords(p)
                                android.graphics.Canvas(bmp).drawCircle(bp.x, bp.y, brushPx / scale / 2f, dotPaint)
                                bitmapVersion++
                            }

                            fun eraseLine(from: Offset, to: Offset) {
                                val f = toBitmapCoords(from)
                                val t = toBitmapCoords(to)
                                erasePaint.strokeWidth = brushPx / scale
                                android.graphics.Canvas(bmp).drawLine(f.x, f.y, t.x, t.y, erasePaint)
                                bitmapVersion++
                            }

                            // Crop drag state
                            var dragMode by remember(bmp) { mutableStateOf(DragMode.NONE) }
                            var lastPoint by remember { mutableStateOf(Offset.Zero) }

                            fun cropDisplayRect(): Rect {
                                val c = cropRect ?: defaultCropRect(bmp, cropShape)
                                return Rect(
                                    imageRect.left + c.left * dispW,
                                    imageRect.top + c.top * dispH,
                                    imageRect.left + c.right * dispW,
                                    imageRect.top + c.bottom * dispH
                                )
                            }

                            fun storeCropRect(r: Rect) {
                                cropRect = Rect(
                                    ((r.left - imageRect.left) / dispW).coerceIn(0f, 1f),
                                    ((r.top - imageRect.top) / dispH).coerceIn(0f, 1f),
                                    ((r.right - imageRect.left) / dispW).coerceIn(0f, 1f),
                                    ((r.bottom - imageRect.top) / dispH).coerceIn(0f, 1f)
                                )
                            }

                            Canvas(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(bmp, eraserMode) {
                                        if (eraserMode) {
                                            detectTapGestures(onTap = { pos ->
                                                if (imageRect.contains(pos)) eraseDot(pos)
                                            })
                                        }
                                    }
                                    .pointerInput(bmp, eraserMode, cropShape) {
                                        detectDragGestures(
                                            onDragStart = { pos ->
                                                lastPoint = pos
                                                if (eraserMode) {
                                                    brushCursor = pos
                                                    if (imageRect.contains(pos)) eraseDot(pos)
                                                } else {
                                                    val r = cropDisplayRect()
                                                    dragMode = when {
                                                        (pos - Offset(r.left, r.top)).getDistance() < handleRadiusPx -> DragMode.TOP_LEFT
                                                        (pos - Offset(r.right, r.top)).getDistance() < handleRadiusPx -> DragMode.TOP_RIGHT
                                                        (pos - Offset(r.left, r.bottom)).getDistance() < handleRadiusPx -> DragMode.BOTTOM_LEFT
                                                        (pos - Offset(r.right, r.bottom)).getDistance() < handleRadiusPx -> DragMode.BOTTOM_RIGHT
                                                        r.contains(pos) -> DragMode.MOVE
                                                        else -> DragMode.NONE
                                                    }
                                                }
                                            },
                                            onDragEnd = {
                                                dragMode = DragMode.NONE
                                                brushCursor = null
                                            },
                                            onDragCancel = {
                                                dragMode = DragMode.NONE
                                                brushCursor = null
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                val pos = change.position
                                                if (eraserMode) {
                                                    if (imageRect.contains(pos) || imageRect.contains(lastPoint)) {
                                                        eraseLine(lastPoint, pos)
                                                    }
                                                    brushCursor = pos
                                                    lastPoint = pos
                                                } else {
                                                    val r = cropDisplayRect()
                                                    val updated = when (dragMode) {
                                                        DragMode.MOVE -> {
                                                            val dx = dragAmount.x.coerceIn(imageRect.left - r.left, imageRect.right - r.right)
                                                            val dy = dragAmount.y.coerceIn(imageRect.top - r.top, imageRect.bottom - r.bottom)
                                                            r.translate(dx, dy)
                                                        }
                                                        DragMode.TOP_LEFT -> resizeCrop(r, pos, imageRect, minCropPx, cropShape, fixedX = r.right, fixedY = r.bottom)
                                                        DragMode.TOP_RIGHT -> resizeCrop(r, pos, imageRect, minCropPx, cropShape, fixedX = r.left, fixedY = r.bottom)
                                                        DragMode.BOTTOM_LEFT -> resizeCrop(r, pos, imageRect, minCropPx, cropShape, fixedX = r.right, fixedY = r.top)
                                                        DragMode.BOTTOM_RIGHT -> resizeCrop(r, pos, imageRect, minCropPx, cropShape, fixedX = r.left, fixedY = r.top)
                                                        DragMode.NONE -> r
                                                    }
                                                    storeCropRect(updated)
                                                    lastPoint = pos
                                                }
                                            }
                                        )
                                    }
                            ) {
                                // Force redraw when strokes are painted
                                @Suppress("UNUSED_EXPRESSION")
                                bitmapVersion

                                drawImage(
                                    image = bmp.asImageBitmap(),
                                    dstOffset = IntOffset(imageRect.left.roundToInt(), imageRect.top.roundToInt()),
                                    dstSize = IntSize(dispW.roundToInt(), dispH.roundToInt())
                                )

                                val r = cropDisplayRect()
                                // Dim outside of the crop area
                                val dimPath = Path().apply {
                                    fillType = PathFillType.EvenOdd
                                    addRect(Rect(0f, 0f, size.width, size.height))
                                    if (cropShape == CropShape.CIRCLE) addOval(r) else addRect(r)
                                }
                                drawPath(dimPath, Color.Black.copy(alpha = 0.55f))

                                // Crop frame
                                val frameStroke = Stroke(width = 2.dp.toPx())
                                if (cropShape == CropShape.CIRCLE) {
                                    drawOval(Color.White, topLeft = r.topLeft, size = r.size, style = frameStroke)
                                } else {
                                    drawRect(Color.White, topLeft = r.topLeft, size = r.size, style = frameStroke)
                                    // Rule-of-thirds grid
                                    val gw = r.width / 3f
                                    val gh = r.height / 3f
                                    val gridColor = Color.White.copy(alpha = 0.4f)
                                    for (i in 1..2) {
                                        drawLine(gridColor, Offset(r.left + gw * i, r.top), Offset(r.left + gw * i, r.bottom), 1.dp.toPx())
                                        drawLine(gridColor, Offset(r.left, r.top + gh * i), Offset(r.right, r.top + gh * i), 1.dp.toPx())
                                    }
                                }

                                // Corner handles
                                if (!eraserMode) {
                                    val handleR = 6.dp.toPx()
                                    listOf(
                                        Offset(r.left, r.top), Offset(r.right, r.top),
                                        Offset(r.left, r.bottom), Offset(r.right, r.bottom)
                                    ).forEach { drawCircle(Color.White, radius = handleR, center = it) }
                                }

                                // Brush cursor preview
                                if (eraserMode) {
                                    brushCursor?.let {
                                        drawCircle(Color.White.copy(alpha = 0.9f), radius = brushPx / 2f, center = it, style = Stroke(width = 2.dp.toPx()))
                                    }
                                }
                            }
                        }
                    }
                }

                // Bottom controls
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                    if (enableEraser && eraserMode) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = context.getString(R.string.image_editor_brush_size),
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceSecondary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Slider(
                                brushSizeDp,
                                { brushSizeDp = it },
                                modifier = Modifier.weight(1f),
                                valueRange = 8f..64f
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Rotate 90 degrees clockwise
                        EditorToolButton(
                            icon = { Icon(Icons.Default.RotateRight, contentDescription = null, tint = MiuixTheme.colorScheme.onBackground) },
                            label = context.getString(R.string.image_editor_rotate),
                            onClick = {
                                editBitmap?.let { current ->
                                    val matrix = Matrix().apply { postRotate(90f) }
                                    editBitmap = Bitmap.createBitmap(current, 0, 0, current.width, current.height, matrix, true)
                                    editBitmap?.let { cropRect = defaultCropRect(it, cropShape) }
                                    bitmapVersion++
                                }
                            }
                        )
                        if (enableEraser) {
                            EditorToolButton(
                                icon = {
                                    Icon(
                                        Icons.Default.AutoFixHigh,
                                        contentDescription = null,
                                        tint = if (eraserMode) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onBackground
                                    )
                                },
                                label = context.getString(R.string.image_editor_eraser),
                                highlighted = eraserMode,
                                onClick = { eraserMode = !eraserMode }
                            )
                        }
                        // Reset all edits
                        EditorToolButton(
                            icon = { Icon(Icons.Default.Restore, contentDescription = null, tint = MiuixTheme.colorScheme.onBackground) },
                            label = context.getString(R.string.image_editor_reset),
                            onClick = {
                                baseBitmap?.let { base ->
                                    editBitmap = base.copy(Bitmap.Config.ARGB_8888, true)
                                    editBitmap?.let { cropRect = defaultCropRect(it, cropShape) }
                                    bitmapVersion++
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorToolButton(
    icon: @Composable () -> Unit,
    label: String,
    highlighted: Boolean = false,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularIconButton(onClick = onClick) { icon() }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote1,
            color = if (highlighted) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceSecondary
        )
    }
}

/** Default crop rect in normalized image coordinates. */
private fun defaultCropRect(bmp: Bitmap, shape: CropShape): Rect {
    return if (shape == CropShape.CIRCLE) {
        val side = 0.9f * min(bmp.width, bmp.height)
        val l = (bmp.width - side) / 2f / bmp.width
        val t = (bmp.height - side) / 2f / bmp.height
        Rect(l, t, l + side / bmp.width, t + side / bmp.height)
    } else {
        Rect(0.05f, 0.05f, 0.95f, 0.95f)
    }
}

/**
 * Resize the crop rect from a dragged corner while keeping it inside [bounds].
 * For CIRCLE the rect is kept square, anchored at the fixed corner.
 */
private fun resizeCrop(
    current: Rect,
    pos: Offset,
    bounds: Rect,
    minSize: Float,
    shape: CropShape,
    fixedX: Float,
    fixedY: Float
): Rect {
    val px = pos.x.coerceIn(bounds.left, bounds.right)
    val py = pos.y.coerceIn(bounds.top, bounds.bottom)
    var left = min(fixedX, px)
    var right = max(fixedX, px)
    var top = min(fixedY, py)
    var bottom = max(fixedY, py)

    if (right - left < minSize) {
        if (px < fixedX) left = right - minSize else right = left + minSize
    }
    if (bottom - top < minSize) {
        if (py < fixedY) top = bottom - minSize else bottom = top + minSize
    }

    if (shape == CropShape.CIRCLE) {
        // Keep square, anchored at the fixed corner
        var side = min(right - left, bottom - top)
        // Also clamp so the square stays within bounds relative to the fixed corner
        val maxW = if (px < fixedX) fixedX - bounds.left else bounds.right - fixedX
        val maxH = if (py < fixedY) fixedY - bounds.top else bounds.bottom - fixedY
        side = min(side, min(maxW, maxH)).coerceAtLeast(minSize)
        left = if (px < fixedX) fixedX - side else fixedX
        right = left + side
        top = if (py < fixedY) fixedY - side else fixedY
        bottom = top + side
    }

    return Rect(
        left.coerceIn(bounds.left, bounds.right),
        top.coerceIn(bounds.top, bounds.bottom),
        right.coerceIn(bounds.left, bounds.right),
        bottom.coerceIn(bounds.top, bounds.bottom)
    )
}

/** Crop [bmp] by the normalized [rect] and scale down to [maxResultSize]. */
private fun cropResult(bmp: Bitmap, rect: Rect, maxResultSize: Int): Bitmap {
    val x = (rect.left * bmp.width).roundToInt().coerceIn(0, bmp.width - 1)
    val y = (rect.top * bmp.height).roundToInt().coerceIn(0, bmp.height - 1)
    val w = (rect.width * bmp.width).roundToInt().coerceIn(1, bmp.width - x)
    val h = (rect.height * bmp.height).roundToInt().coerceIn(1, bmp.height - y)
    var out = Bitmap.createBitmap(bmp, x, y, w, h)
    val longest = max(out.width, out.height)
    if (longest > maxResultSize) {
        val ratio = maxResultSize.toFloat() / longest
        out = Bitmap.createScaledBitmap(
            out,
            (out.width * ratio).roundToInt().coerceAtLeast(1),
            (out.height * ratio).roundToInt().coerceAtLeast(1),
            true
        )
    }
    return out
}

/**
 * Compress [bitmap] into a byte array whose size does not exceed [maxBytes].
 *
 * PNG ignores the quality parameter, so only downscaling can shrink its output.
 * For lossy formats (JPEG / WEBP) the quality is lowered first; if the result is
 * still too large the bitmap is downscaled step by step until it fits.
 */
fun compressBitmapToBytes(
    bitmap: Bitmap,
    maxBytes: Int,
    format: Bitmap.CompressFormat,
    quality: Int = 90
): ByteArray {
    var q = quality.coerceIn(1, 100)
    var scale = 1f
    var result: ByteArray
    while (true) {
        val target = if (scale >= 1f) bitmap else Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true
        )
        val out = java.io.ByteArrayOutputStream()
        target.compress(format, if (format == Bitmap.CompressFormat.PNG) 100 else q, out)
        result = out.toByteArray()
        if (result.size <= maxBytes) break
        if (format != Bitmap.CompressFormat.PNG && q > 30) {
            q -= 10
        } else if (scale > 0.2f) {
            scale -= 0.1f
        } else {
            break
        }
    }
    return result
}
