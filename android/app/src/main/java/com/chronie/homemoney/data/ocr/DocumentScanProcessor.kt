package com.chronie.homemoney.data.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.geometry.Geometry
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Document scan processor built on OpenCV.
 *
 * Turns a casual photo of a receipt/bill into a clean, deskewed "scanned
 * document" image before it is fed into the on-device multimodal LLM:
 *
 * 1. Load and normalize the bitmap to a working resolution.
 * 2. Detect the dominant document quadrilateral (Canny edges -> largest
 *    4-point convex contour) and apply a perspective warp to rectify it.
 * 3. Enhance clarity: grayscale -> CLAHE contrast normalization -> mild
 *    denoising, producing a crisp high-contrast page.
 *
 * Every stage degrades gracefully: when no document boundary is found the
 * image is only enhanced; when OpenCV is unavailable the scaled original is
 * returned. The output is always a valid ARGB_8888 bitmap.
 */
class DocumentScanProcessor(private val context: Context) {

    companion object {
        private const val TAG = "DocumentScanProcessor"

        /** Working resolution: longest edge target for the warped document. */
        private const val TARGET_MAX_SIDE = 1600

        /** Do not upscale small images beyond this factor to avoid artifacts. */
        private const val MAX_UPSCALE = 2.0f

        /** Minimum quad area relative to the full image to accept as document. */
        private const val MIN_DOCUMENT_AREA_RATIO = 0.15

        private var isInitialized = false

        /**
         * Loads the OpenCV native library once per process.
         * Returns false when OpenCV is unavailable (caller falls back to scaling only).
         */
        fun initialize(): Boolean {
            if (!isInitialized) {
                isInitialized = OpenCVLoader.initLocal()
                Log.d(TAG, "OpenCV initialized: $isInitialized")
            }
            return isInitialized
        }
    }

    /** Result of the document-scan pipeline. */
    data class ScanResult(
        /** Cleaned, deskewed page ready for the vision model. */
        val bitmap: Bitmap,
        /** True when a document boundary was detected and perspective-corrected. */
        val documentDetected: Boolean,
        /** True when OpenCV enhancement (CLAHE/denoise) was applied. */
        val enhanced: Boolean
    )

    /**
     * Runs the full document pipeline on an image [uri].
     * Returns null only when the image cannot be decoded at all.
     */
    fun processUri(uri: Uri): ScanResult? {
        val bitmap = loadBitmapFromUri(uri) ?: return null
        return processBitmap(bitmap)
    }

    /**
     * Runs the full document pipeline on an in-memory [bitmap].
     */
    fun processBitmap(bitmap: Bitmap): ScanResult {
        val scaled = normalizeSize(bitmap)

        if (!initialize()) {
            Log.d(TAG, "OpenCV not initialized, returning scaled original")
            return ScanResult(scaled, documentDetected = false, enhanced = false)
        }

        return try {
            val src = bitmapToMat(scaled)
            var working = src

            // --- Stage 1: document boundary detection + perspective correction ---
            var documentDetected = false
            val quad = detectDocumentQuad(working)
            if (quad != null) {
                val warped = warpDocument(working, quad)
                if (warped != null) {
                    working = warped
                    documentDetected = true
                    Log.d(TAG, "Document boundary detected and rectified")
                }
            }
            if (!documentDetected) {
                Log.d(TAG, "No document boundary found, skipping warp")
            }

            // --- Stage 2: clarity enhancement for the vision model ---
            val enhanced = enhanceDocument(working)

            val resultBitmap = matToBitmap(enhanced)
            if (working != src) working.release()
            if (enhanced != src && enhanced != working) enhanced.release()
            src.release()

            ScanResult(resultBitmap, documentDetected, enhanced = true)
        } catch (e: Exception) {
            Log.e(TAG, "Document scan pipeline failed, falling back to scaled original", e)
            ScanResult(scaled, documentDetected = false, enhanced = false)
        }
    }

    // ------------------------------------------------------------------
    // Stage 1: quadrilateral detection
    // ------------------------------------------------------------------

    /**
     * Finds the most plausible document quadrilateral in the image.
     * Strategy: Canny edges -> dilate to close gaps -> external contours ->
     * largest polygon approximation with exactly 4 convex corners that
     * covers a significant portion of the frame.
     */
    private fun detectDocumentQuad(src: Mat): MatOfPoint2f? {
        val gray = Mat()
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY)

        val blurred = Mat()
        Imgproc.GaussianBlur(gray, blurred, Size(5.0, 5.0), 0.0)

        val edges = Mat()
        Imgproc.Canny(blurred, edges, 75.0, 200.0)

        // Dilate so that a dashed or shadowed receipt border becomes one solid contour
        val dilated = Mat()
        Imgproc.dilate(
            edges, dilated,
            Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
        )

        val contours = ArrayList<MatOfPoint>()
        Imgproc.findContours(
            dilated, contours, Mat(),
            Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE
        )

        val imageArea = src.cols().toDouble() * src.rows().toDouble()
        var bestQuad: MatOfPoint2f? = null
        var bestArea = 0.0

        // Contours are unsorted; evaluate every candidate and keep the largest valid quad
        for (contour in contours) {
            val contour2f = MatOfPoint2f(*contour.toArray())
            val perimeter = Geometry.arcLength(contour2f, true)
            val approx = MatOfPoint2f()
            Geometry.approxPolyDP(contour2f, approx, 0.02 * perimeter, true)

            val points = approx.toArray()
            // Reject full-frame quads: the photo itself is not a detected document.
            val touchesBorder = points.all {
                it.x < 8.0 || it.y < 8.0 ||
                    it.x > src.cols() - 8.0 || it.y > src.rows() - 8.0
            }
            val valid = points.size == 4 &&
                Geometry.isContourConvex(MatOfPoint(*points)) &&
                !touchesBorder

            if (valid) {
                val area = kotlin.math.abs(Geometry.contourArea(approx))
                if (area > imageArea * MIN_DOCUMENT_AREA_RATIO && area > bestArea) {
                    bestArea = area
                    bestQuad = MatOfPoint2f(*points)
                }
            }

            contour2f.release()
            approx.release()
            contour.release()
        }

        gray.release()
        blurred.release()
        edges.release()
        dilated.release()

        return bestQuad
    }

    /**
     * Applies a perspective transform that maps [quad] onto an upright rectangle.
     * Corner order is normalized to TL, TR, BR, BL before computing the transform.
     */
    private fun warpDocument(src: Mat, quad: MatOfPoint2f): Mat? {
        return try {
            val corners = orderCorners(quad.toArray())

            // Output dimensions from the longest opposite edges, keeps aspect ratio honest
            val widthTop = hypot(corners[1].x - corners[0].x, corners[1].y - corners[0].y)
            val widthBottom = hypot(corners[2].x - corners[3].x, corners[2].y - corners[3].y)
            val heightLeft = hypot(corners[3].x - corners[0].x, corners[3].y - corners[0].y)
            val heightRight = hypot(corners[2].x - corners[1].x, corners[2].y - corners[1].y)
            val dstWidth = maxOf(widthTop, widthBottom).coerceAtLeast(1.0)
            val dstHeight = maxOf(heightLeft, heightRight).coerceAtLeast(1.0)

            val srcMat = MatOfPoint2f(*corners)
            val dstMat = MatOfPoint2f(
                Point(0.0, 0.0),
                Point(dstWidth - 1.0, 0.0),
                Point(dstWidth - 1.0, dstHeight - 1.0),
                Point(0.0, dstHeight - 1.0)
            )

            val transform = Geometry.getPerspectiveTransform(srcMat, dstMat)
            val warped = Mat()
            Imgproc.warpPerspective(src, warped, transform, Size(dstWidth, dstHeight))

            srcMat.release()
            dstMat.release()
            transform.release()
            warped
        } catch (e: Exception) {
            Log.e(TAG, "Perspective warp failed", e)
            null
        }
    }

    /**
     * Orders 4 corner points as: top-left, top-right, bottom-right, bottom-left.
     * Classic sum/diff heuristic: TL has the smallest x+y, BR the largest,
     * BL the smallest x-y and TR the largest.
     */
    private fun orderCorners(points: Array<Point>): Array<Point> {
        val bySum = points.sortedBy { it.x + it.y }
        val byDiff = points.sortedBy { it.x - it.y }
        return arrayOf(
            bySum.first(),   // top-left
            byDiff.last(),   // top-right
            bySum.last(),    // bottom-right
            byDiff.first()   // bottom-left
        )
    }

    // ------------------------------------------------------------------
    // Stage 2: clarity enhancement
    // ------------------------------------------------------------------

    /**
     * Enhances the page for OCR-grade legibility:
     * grayscale -> CLAHE adaptive contrast -> light denoising.
     * Returns an RGBA mat (so it can become an ARGB_8888 bitmap directly).
     */
    private fun enhanceDocument(src: Mat): Mat {
        val gray = Mat()
        if (src.channels() == 1) {
            src.copyTo(gray)
        } else {
            Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY)
        }

        // CLAHE evens out uneven receipt lighting (shadow of the phone hand, etc.)
        val clahe = Imgproc.createCLAHE(2.0, Size(8.0, 8.0))
        val enhanced = Mat()
        clahe.apply(gray, enhanced)

        // Mild blur removes sensor noise without smudging small print
        val denoised = Mat()
        Imgproc.GaussianBlur(enhanced, denoised, Size(3.0, 3.0), 0.0)

        val rgba = Mat()
        Imgproc.cvtColor(denoised, rgba, Imgproc.COLOR_GRAY2RGBA)

        gray.release()
        enhanced.release()
        denoised.release()
        return rgba
    }

    // ------------------------------------------------------------------
    // Bitmap <-> Mat helpers
    // ------------------------------------------------------------------

    /**
     * Scales the bitmap so the longest edge is near [TARGET_MAX_SIDE].
     * Small images are upscaled (max 2x) because the vision model downsamples
     * aggressively and tiny receipts lose their small print.
     */
    private fun normalizeSize(bitmap: Bitmap): Bitmap {
        val maxSide = maxOf(bitmap.width, bitmap.height)
        val scaleFactor = TARGET_MAX_SIDE.toFloat() / maxSide

        if (abs(scaleFactor - 1.0f) < 0.05f) return bitmap
        // Avoid extreme upscales that only interpolate pixels
        val clamped = scaleFactor.coerceIn(1f / 8f, MAX_UPSCALE)

        val newWidth = (bitmap.width * clamped).toInt().coerceAtLeast(1)
        val newHeight = (bitmap.height * clamped).toInt().coerceAtLeast(1)
        Log.d(TAG, "Scaling image from ${bitmap.width}x${bitmap.height} to ${newWidth}x${newHeight}")
        return bitmap.scale(newWidth, newHeight)
    }

    private fun loadBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading bitmap from URI", e)
            null
        }
    }

    private fun bitmapToMat(bitmap: Bitmap): Mat {
        val mat = Mat(bitmap.height, bitmap.width, CvType.CV_8UC4)
        val bitmap32 = if (bitmap.config == Bitmap.Config.ARGB_8888) bitmap
        else bitmap.copy(Bitmap.Config.ARGB_8888, true)
        Utils.bitmapToMat(bitmap32, mat)
        return mat
    }

    private fun matToBitmap(mat: Mat): Bitmap {
        val bitmap = createBitmap(mat.cols(), mat.rows())
        Utils.matToBitmap(mat, bitmap)
        return bitmap
    }
}
