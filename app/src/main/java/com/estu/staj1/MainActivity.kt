package com.estu.staj1

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
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
import com.google.android.material.snackbar.Snackbar
import com.google.mediapipe.tasks.core.ErrorListener
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import java.io.IOException
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
    private var mouthOpenStartTime: Long = 0L
    private var isCaptureInProgress: Boolean = false
    private var latestFaceResult: FaceLandmarkerResult? = null
    @Volatile private var detectedIrisColor: String="..."
    private var latestBitmap:Bitmap?=null

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
                binding.textViewHandFilters.visibility = View.GONE
                binding.scrollViewHandFilters.visibility = View.GONE
            } else if (checkedId == R.id.chipHand) {
                currentDetector = DETECTOR_HAND
                binding.textViewFaceFilters.visibility = View.GONE
                binding.scrollViewFaceFilters.visibility = View.GONE
                binding.textViewHandFilters.visibility = View.VISIBLE
                binding.scrollViewHandFilters.visibility = View.VISIBLE
            }
        } //

        val filterChipIds = mapOf(
            R.id.chipFilterEyes to "Gözler",
            R.id.chipFilterIris to "İris",
            R.id.chipFilterEyebrows to "Kaşlar",
            R.id.chipFilterMouth to "Ağız İçi(Dil/Diş)", //
            R.id.chipFilterFaceOval to "Yüz Çevresi",
            R.id.chipFilterNose  to "Burun",
            R.id.chipFilterCheeks to "Yanaklar",

            ) //
        filterChipIds.forEach { (chipId, filterName) ->
            findViewById<com.google.android.material.chip.Chip>(chipId)
                ?.setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) binding.overlayView.faceFilters.add(filterName)
                    else binding.overlayView.faceFilters.remove(filterName)
                    binding.overlayView.invalidate()
                }
        } //

        val handFilterChipIds = mapOf(
            R.id.chipFilterPalm to "Avuç",
            R.id.chipFilterFingertips to "Parmak Uçları"
        ) //


        // DÜZELTİLMİŞ Hali (Fazladan parantezler kaldırıldı):
        handFilterChipIds.forEach { (chipId, filterName) ->
            findViewById<com.google.android.material.chip.Chip>(chipId)
                ?.setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) binding.overlayView.handFilters.add(filterName)
                    else binding.overlayView.handFilters.remove(filterName)
                    binding.overlayView.invalidate()
                }
        }
        // --- DÜZELTME SONU ---

        binding.textViewFaceFilters.visibility = View.VISIBLE
        binding.scrollViewFaceFilters.visibility = View.VISIBLE
        binding.textViewHandFilters.visibility = View.GONE
        binding.scrollViewHandFilters.visibility = View.GONE
        //
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
                    latestFaceResult = result
                    cameraExecutor.execute {
                        analyzeIrisColor(image,result)
                    }
                    runOnUiThread {
                        binding.overlayView.setFaceResults(
                            faceLandmarkerResult = result,
                            imageHeight = image.height,
                            imageWidth = image.width,
                            runningMode = RunningMode.LIVE_STREAM
                        )
                        binding.overlayView.setIrisColor(detectedIrisColor)
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
        this.latestBitmap=bitmap
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees

        // Bitmap'ten MPImage oluştur (Bu import zaten vardı)
        val mpImage = BitmapImageBuilder(bitmap).build()
        val timestamp = imageProxy.imageInfo.timestamp

        when (currentDetector) {
            DETECTOR_FACE -> faceLandmarker?.detectAsync(mpImage, timestamp)
            DETECTOR_HAND -> handLandmarker?.detectAsync(mpImage, timestamp)
        }
        if(currentDetector==DETECTOR_FACE){
            checkMouthOpenTimer(rotationDegrees)
        }

        imageProxy.close()
    }
    private fun checkMouthOpenTimer(rotationDegrees: Int) { // Parametre durabilir ama kullanmayacağız
        val isMouthCurrentlyOpen = binding.overlayView.isMouthOpen

        if (isMouthCurrentlyOpen && !isCaptureInProgress) {
            if (mouthOpenStartTime == 0L) {
                mouthOpenStartTime = System.currentTimeMillis()
                Log.d(TAG, "Ağız açıldı, süre başladı..")

            } else {
                val elapsedTime = System.currentTimeMillis() - mouthOpenStartTime
                if (elapsedTime > 2000) {
                    Log.d(TAG, "Süre doldu, Görüntü alınıyor...")
                    isCaptureInProgress = true
                    mouthOpenStartTime = 0L

                    // --- KESİN ÇÖZÜM: GÖRÜNÜMDEN BITMAP ALMA ---
                    runOnUiThread {
                        // 1. Kutunun koordinatlarını al (OverlayView nerede çiziyorsa orası)
                        val rect = binding.overlayView.getTongueRect()

                        // 2. Ekrandaki görüntüyü al (PreviewView ne gösteriyorsa o)
                        // Bu bitmap zaten dönmüş, ölçeklenmiş ve ekrana oturmuş haldedir.
                        val screenBitmap = binding.viewFinder.bitmap

                        if (rect != null && screenBitmap != null) {
                            try {
                                // 3. Koordinatları Güvenli Hale Getir (Ekran dışına taşmasın)
                                val safeLeft = rect.left.toInt().coerceIn(0, screenBitmap.width)
                                val safeTop = rect.top.toInt().coerceIn(0, screenBitmap.height)

                                val safeWidth = rect.width().toInt().coerceAtMost(screenBitmap.width - safeLeft)
                                val safeHeight = rect.height().toInt().coerceAtMost(screenBitmap.height - safeTop)

                                // 4. Kesme İşlemi (Sadece kutu içi)
                                if (safeWidth > 0 && safeHeight > 0) {
                                    val croppedBitmap = Bitmap.createBitmap(
                                        screenBitmap,
                                        safeLeft,
                                        safeTop,
                                        safeWidth,
                                        safeHeight
                                    )

                                    // 5. Kaydetmeye Gönder
                                    saveBitmap(croppedBitmap)
                                } else {
                                    isCaptureInProgress = false // Hata olursa kilidi aç
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Kırpma hatası: ${e.message}")
                                isCaptureInProgress = false
                            }
                        } else {
                            isCaptureInProgress = false
                        }
                    }
                    // -------------------------------------------
                }
            }
        } else if (!isMouthCurrentlyOpen) {
            if (mouthOpenStartTime != 0L) {
                Log.d(TAG, "Ağız kapandı, sayaç sıfırlandı.")
            }
            mouthOpenStartTime = 0L
            isCaptureInProgress = false
        }
    }

    private fun saveBitmap(finalBitmap: Bitmap) {
        val fileName = "TongueCrop_${System.currentTimeMillis()}.jpg"

        cameraExecutor.execute {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Staj1App/TongueCrops")
                }
            }

            val resolver = contentResolver
            var uri: Uri? = null

            try {
                uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { stream ->
                        finalBitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
                    }
                    // Başarılı mesajı
                    runOnUiThread {
                        Snackbar.make(binding.root, "Dil Yakalandı: $fileName", Snackbar.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Kaydetme hatası: ${e.message}")
            } finally {
                // İşlem bitince bir sonraki çekim için kilidi aç
                isCaptureInProgress = false
            }
        }
    }
    private fun classifyColor(color: Int): String{
        val red = Color.red(color)
        val green = Color.green(color)
        val blue = Color.blue(color)

        if(blue>green && blue>red&&blue>80){
            return "Mavi"
        }
        if(green>blue&&green>red&&green>80){
            return "Yeşil"
        }
        if (red>blue&&green>blue&&(kotlin.math.abs(red-green)<40)&&(red+green>100)){
            if(kotlin.math.abs(red-green)<20&&red>80) return "Kahverengi"
            if(green>red)return "Ela"
            return "Kahverengi"
        }
        return "Kahverengi/Koyu"

    }
    private fun analyzeIrisColor(image: MPImage,result: FaceLandmarkerResult) {
        if (!binding.overlayView.faceFilters.contains("İris") || result.faceLandmarks().isEmpty()) {
            detectedIrisColor = "..."
            return
        }
        try {
            val bitmap = this.latestBitmap ?: return
            val landmarks = result.faceLandmarks()[0]
            val leftIrisCenter = landmarks[476]
            var lx = leftIrisCenter.x()
            val ly = leftIrisCenter.y()
            if (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA) {
                lx = 1.0f - lx
            }
            val pixelX = (lx * bitmap.width).toInt()
            val pixelY = (ly * bitmap.height).toInt()
            if (pixelX >= 0 && pixelX < bitmap.width && pixelY >= 0 && pixelY < bitmap.height) {
                val color = bitmap.getPixel(pixelX, pixelY)
                detectedIrisColor = classifyColor(color)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Iris renk analizinde hata oluştu: ${e.message}")
            detectedIrisColor = "Hata"
        }
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
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA,)
        private const val DETECTOR_FACE = 0
        private const val DETECTOR_HAND = 1
    }
}
