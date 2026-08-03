package com.example.nanohead.sensors

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.Executors

class CameraManager(private val context: Context, private val lifecycleOwner: LifecycleOwner) {
    private val TAG = "CameraManager"
    private val cameraExecutor = Executors.newSingleThreadExecutor()

    fun startCamera(onRgbFrame: (ByteArray) -> Unit) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            try {
                val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

                // Setup ImageAnalysis
                val imageAnalyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    // Requesting 320x240 resolution for TCP streaming (testing 1280 x 720 to see what would happen)
                    .setTargetResolution(android.util.Size(1280, 720)) 
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor) { image ->
                            processImageToJpeg(image, onRgbFrame)
                            // [RAW RGB OPTION - Commented Out]
                            // processImageToRgb(image, onRgbFrame)
                        }
                    }

                // Select front camera as the "Head"
                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    imageAnalyzer
                )

            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    private fun processImageToJpeg(imageProxy: ImageProxy, onJpegFrame: (ByteArray) -> Unit) {
        try {
            val bitmap = imageProxy.toBitmap()
            val stream = java.io.ByteArrayOutputStream()
            // Compress to JPEG with 60% quality (excellent balance for AI vision)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 60, stream)
            onJpegFrame(stream.toByteArray())
        } catch (e: Exception) {
            Log.e(TAG, "Error processing frame to JPEG", e)
        } finally {
            imageProxy.close()
        }
    }

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    private fun processImageToRgb(imageProxy: ImageProxy, onRgbFrame: (ByteArray) -> Unit) {
        try {
            // CameraX 1.2.0+ provides toBitmap() for YUV -> ARGB conversion
            val bitmap = imageProxy.toBitmap()
            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

            // Convert ARGB array to pure RGB byte array (dropping Alpha)
            val rgbBytes = ByteArray(width * height * 3)
            var byteIdx = 0
            for (pixel in pixels) {
                rgbBytes[byteIdx++] = ((pixel shr 16) and 0xFF).toByte() // R
                rgbBytes[byteIdx++] = ((pixel shr 8) and 0xFF).toByte()  // G
                rgbBytes[byteIdx++] = (pixel and 0xFF).toByte()          // B
            }

            // We now have pure RGB bytes! Send to callback.
            onRgbFrame(rgbBytes)

        } catch (e: Exception) {
            Log.e(TAG, "Error processing frame to RGB", e)
        } finally {
            imageProxy.close()
        }
    }

    fun stopCamera() {
        cameraExecutor.shutdown()
        try {
            val cameraProvider = ProcessCameraProvider.getInstance(context).get()
            cameraProvider.unbindAll()
        } catch (e: Exception) {
            Log.e(TAG, "Error unbinding camera", e)
        }
    }
}
