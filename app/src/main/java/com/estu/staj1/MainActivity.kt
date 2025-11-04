package com.estu.staj1

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
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
import com.google.mediapipe.tasks.core.ErrorListener
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService

    private var faceLandmarker: FaceLandmarker? = null
    private var handLandmarker: HandLandmarker? = null
    private var currentDetector: Int = DETECTOR_FACE
    private var cameraSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()
        setupChipListeners()

        binding.buttonFlipCamera.setOnClickListener {
            val provider = cameraProvider ?: return@setOnClickListener // Kamera henüz hazır değilse bir şey yapma

            val newSelector = if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }

            // Cihazda geçiş yapılmak istenen kameranın olup olmadığını kontrol et
            if (provider.hasCamera(newSelector)) {
                cameraSelector = newSelector // Aktif kamerayı değiştir
                binding.overlayView.isFrontCamera = (cameraSelector== CameraSelector.DEFAULT_FRONT_CAMERA)
                startCamera() // Kamerayı yeni seçiciyle yeniden başlat
            } else {
                Toast.makeText(this, "Diğer kamera bulunamadı.", Toast.LENGTH_SHORT).show()
            }
        }

        binding.viewFinder.post {
            if (allPermissionsGranted()) {
                setupFaceLandmarker()
                setupHandLandmarker()
                startCamera()
            } else {
                ActivityCompat.requestPermissions(
                    this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
                )
            }
        }
    }

    private fun setupChipListeners() {
        binding.chipGroupMode.setOnCheckedChangeListener { _, checkedId ->
            binding.overlayView.clearResults()

            if (checkedId == R.id.chipFace) {
                currentDetector = DETECTOR_FACE
                binding.textViewFaceFilters.visibility = View.VISIBLE
                binding.scrollViewFaceFilters.visibility = View.VISIBLE
            } else if (checkedId == R.id.chipHand) {
                currentDetector = DETECTOR_HAND
                binding.textViewFaceFilters.visibility = View.GONE
                binding.scrollViewFaceFilters.visibility = View.GONE
            }
        }

        val filterChipIds = mapOf(
            R.id.chipFilterEyes to "Gözler",
            R.id.chipFilterEyebrows to "Kaşlar",
            R.id.chipFilterMouth to "Ağız",
            R.id.chipFilterFaceOval to "Yüz Çevresi",
            R.id.chipFilterNose  to "Burun",
            R.id.chipFilterCheeks to "Yanaklar",
            R.id.chipFilterInnerMouth to "Ağız İçi"
        )
        val handFilterChipIds = mapOf(
            R.id.chipFilterPalm to "Avuç",
            R.id.chipFilterFingertips to "Parmak Uçları"
        )

        filterChipIds.forEach { (chipId, filterName) ->
            findViewById<com.google.android.material.chip.Chip>(chipId)
                ?.setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) binding.overlayView.faceFilters.add(filterName)
                    else binding.overlayView.faceFilters.remove(filterName)
                    binding.overlayView.invalidate()
                }
        }

        binding.textViewFaceFilters.visibility = View.VISIBLE
        binding.scrollViewFaceFilters.visibility = View.VISIBLE
        binding.textViewHandFilters.visibility = View.GONE
        binding.scrollViewHandFilters.visibility = View.GONE


    }

    private fun setupFaceLandmarker() {
        val modelName = "face_landmarker.task"
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath(modelName)
                .build()

            val options = FaceLandmarker.FaceLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setNumFaces(1)
                .setResultListener { result: FaceLandmarkerResult, image: MPImage ->
                    runOnUiThread {
                        binding.overlayView.setFaceResults(
                            faceLandmarkerResult = result,
                            imageHeight = image.height,
                            imageWidth = image.width,
                            runningMode = RunningMode.LIVE_STREAM
                        )
                    }
                }
                .setErrorListener { error ->
                    Log.e(TAG, "MediaPipe (Face) Hatası: ${error.message}",error)
                }
                .build()

            faceLandmarker = FaceLandmarker.createFromOptions(this, options)
            Log.d(TAG, "MediaPipe FaceLandmarker başarıyla yüklendi.")

        } catch (e: Exception) {
            Log.e(TAG, "MediaPipe (Face) modeli yüklenirken hata oluştu: ${e.message}")
        }
    }

    private fun setupHandLandmarker() {
        val modelName = "hand_landmarker.task"
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath(modelName)
                .build()

            val options = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setNumHands(2)
                .setResultListener { result: HandLandmarkerResult, image: MPImage ->
                    runOnUiThread {
                        binding.overlayView.setHandResults(
                            handLandmarkerResult = result,
                            imageHeight = image.height,
                            imageWidth = image.width,
                            runningMode = RunningMode.LIVE_STREAM
                        )
                    }
                }
                .setErrorListener { error ->
                    Log.e(TAG, "MediaPipe (Hand) Hatası:${error.message}",error)
                }
                .build()

            handLandmarker = HandLandmarker.createFromOptions(this, options)
            Log.d(TAG, "MediaPipe HandLandmarker başarıyla yüklendi.")

        } catch (e: Exception) {
            Log.e(TAG, "MediaPipe (Hand) modeli yüklenirken hata oluştu: ${e.message}")
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            val provider = cameraProvider ?: return@addListener

            preview = Preview.Builder()
                .setTargetRotation(binding.viewFinder.display.rotation)
                .build()
                .also {
                    it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
                }

            imageAnalyzer = ImageAnalysis.Builder()
                .setTargetRotation(binding.viewFinder.display.rotation)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        analyzeImage(imageProxy)
                    }
                }

            try {
                provider.unbindAll()
                provider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalyzer
                )
            } catch (exc: Exception) {
                Log.e(TAG, "Kamera bağlanırken hata oluştu: ${exc.message}")
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun analyzeImage(imageProxy: ImageProxy) {
        // Görüntüyü Bitmap'e dönüştür
        val bitmap = imageProxy.toBitmap()
        if (bitmap == null) {
            imageProxy.close()
            return
        }

        // Bitmap'ten MPImage oluştur (Bu import zaten vardı)
        val mpImage = BitmapImageBuilder(bitmap).build()
        val timestamp = imageProxy.imageInfo.timestamp

        when (currentDetector) {
            DETECTOR_FACE -> faceLandmarker?.detectAsync(mpImage, timestamp)
            DETECTOR_HAND -> handLandmarker?.detectAsync(mpImage, timestamp)
        }

        imageProxy.close()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                setupFaceLandmarker()
                setupHandLandmarker()
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
        faceLandmarker?.close()
        handLandmarker?.close()
    }

    companion object {
        private const val TAG = "AIVisionApp"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
        private const val DETECTOR_FACE = 0
        private const val DETECTOR_HAND = 1
    }
}
