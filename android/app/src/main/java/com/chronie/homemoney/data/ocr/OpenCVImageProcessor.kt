package com.chronie.homemoney.data.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import org.opencv.android.OpenCVLoader
import org.opencv.core.*
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc

class OpenCVImageProcessor(private val context: Context) {

    companion object {
        private const val TAG = "OpenCVImageProcessor"
        private const val MIN_IMAGE_SIZE = 1000
        private var isInitialized = false

        fun initialize(): Boolean {
            if (!isInitialized) {
                isInitialized = OpenCVLoader.initLocal()
                Log.d(TAG, "OpenCV initialized: $isInitialized")
            }
            return isInitialized
        }
    }

    fun processImage(uri: Uri): Bitmap? {
        val bitmap = loadBitmapFromUri(uri) ?: return null
        return processBitmap(bitmap)
    }

    fun scaleBitmap(bitmap: Bitmap): Bitmap {
        return scaleIfNeeded(bitmap)
    }

    fun processBitmap(bitmap: Bitmap): Bitmap? {
        if (!initialize()) {
            Log.d(TAG, "OpenCV not initialized, returning scaled original")
            return scaleIfNeeded(bitmap)
        }

        return try {
            val scaledBitmap = scaleIfNeeded(bitmap)
            val mat = bitmapToMat(scaledBitmap)
            val processedMat = applyMildPreprocessing(mat)
            val resultBitmap = matToBitmap(processedMat)
            
            mat.release()
            processedMat.release()
            
            resultBitmap
        } catch (e: Exception) {
            Log.e(TAG, "Error processing image", e)
            scaleIfNeeded(bitmap)
        }
    }

    private fun loadBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Error loading bitmap from URI", e)
            null
        }
    }

    private fun scaleIfNeeded(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val minDim = minOf(width, height)
        
        if (minDim >= MIN_IMAGE_SIZE) {
            return bitmap
        }
        
        val scaleFactor = MIN_IMAGE_SIZE.toFloat() / minDim
        val newWidth = (width * scaleFactor).toInt()
        val newHeight = (height * scaleFactor).toInt()
        
        Log.d(TAG, "Scaling image from ${width}x${height} to ${newWidth}x${newHeight}")
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    private fun bitmapToMat(bitmap: Bitmap): Mat {
        val mat = Mat()
        val bitmap32 = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        org.opencv.android.Utils.bitmapToMat(bitmap32, mat)
        return mat
    }

    private fun matToBitmap(mat: Mat): Bitmap {
        val bitmap = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888)
        org.opencv.android.Utils.matToBitmap(mat, bitmap)
        return bitmap
    }

    private fun applyMildPreprocessing(src: Mat): Mat {
        val gray = Mat()
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY)

        val clahe = Imgproc.createCLAHE(2.0, Size(8.0, 8.0))
        val enhanced = Mat()
        clahe.apply(gray, enhanced)

        val denoised = Mat()
        Imgproc.GaussianBlur(enhanced, denoised, Size(3.0, 3.0), 0.0)

        gray.release()
        enhanced.release()

        return denoised
    }
}
