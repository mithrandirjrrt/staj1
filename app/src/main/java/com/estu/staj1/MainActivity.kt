package com.estu.staj1

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix // YENİ: Görüntüyü döndürmek için
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.estu.staj1.databinding.ActivityMainBinding
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.vision.detector.Detection
import org.tensorflow.lite.task.vision.detector.ObjectDetector
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService

    private var objectDetector: ObjectDetector? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var cameraProvider: ProcessCameraProvider? = null // Sadece izinler için

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()

        // Modeli sadece bir kez, en başta yüklüyoruz.
        setupObjectDetector()

        // Kamerayı, arayüzün (viewFinder) hazır olmasını bekledikten sonra başlat
        binding.viewFinder.post {
            if (allPermissionsGranted()) {
                startCamera() // Kamerayı başlat
            } else {
                ActivityCompat.requestPermissions(
                    this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
                )
            }
        }
    }

    private fun setupObjectDetector() {
        try {
            val options = ObjectDetector.ObjectDetectorOptions.builder()
                .setMaxResults(5)
                .setScoreThreshold(0.5f)
                .build()

            objectDetector = ObjectDetector.createFromFileAndOptions(
                this,
                "model_object.tflite",
                options
            )
            Log.d(TAG, "TFLite modeli başarıyla yüklendi.")

        } catch (e: Exception) {
            Log.e(TAG, "TFLite modeli yüklenirken hata oluştu: ${e.message}")
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({

            this.cameraProvider = cameraProviderFuture.get()

            val cameraProvider: ProcessCameraProvider = this.cameraProvider ?: run {
                Log.e(TAG, "Kamera Sağlayıcı alınamadı.")
                return@addListener
            }

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
                }

            imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                // DEĞİŞTİ: setTargetRotation() çağrısını tamamen sildik.
                // Rotasyonu artık manuel yapacağız.
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, { imageProxy ->

                        // DEĞİŞTİ: DÖNDÜRME MANTIĞI
                        val bitmap = imageProxy.toBitmap()
                        if (bitmap != null) {

                            // 1. Görüntünün ne kadar dönük olduğunu öğren (dikeyde 90, yatayda 0)
                            val rotationDegrees = imageProxy.imageInfo.rotationDegrees.toFloat()

                            // 2. Görüntüyü döndürmek için bir Matrix (matris) oluştur
                            val matrix = Matrix().apply {
                                postRotate(rotationDegrees)
                            }

                            // 3. Orijinal bitmap'i, matrisi kullanarak DÖNDÜR
                            val rotatedBitmap = Bitmap.createBitmap(
                                bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                            )

                            // 4. Modele ve Overlay'e, artık DÜZ olan bu bitmap'i ver
                            //    (rotatedBitmap dikeyde 480x640, yatayda 640x480 olur)
                            detectObjects(rotatedBitmap)
                        }

                        // Analiz bittiğinde görüntüyü kapat
                        imageProxy.close()
                    })
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalyzer
                )

            } catch (exc: Exception) {
                Log.e(TAG, "Kamera bağlanırken hata oluştu: ${exc.message}")
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun detectObjects(bitmap: Bitmap) {
        if (objectDetector == null) {
            return
        }

        val tensorImage = TensorImage.fromBitmap(bitmap)
        var detections: List<Detection> = emptyList()
        try {
            detections = objectDetector?.detect(tensorImage) ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "TFLite çıkarım hatası: ${e.message}")
        }

        runOnUiThread {
            // Artık OverlayView'e (örn: 480x640) ve
            // modele (480x640) giden görüntü DÜZ olduğu için,
            // hem algılama hem de hizalama DOĞRU çalışacaktır.
            binding.overlayView.setResults(
                detectionResults = detections,
                imageHeight = bitmap.height,
                imageWidth = bitmap.width
            )
        }
    }

    // DEĞİŞTİ: onConfigurationChanged fonksiyonunu TAMAMEN SİLDİK.
    // Artık gerek yok, çünkü rotasyonu her karede manuel yapıyoruz.

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Kamera izni verilmedi.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        objectDetector?.close()
    }

    companion object {
        private const val TAG = "AIVisionApp"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}