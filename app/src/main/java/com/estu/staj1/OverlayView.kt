package com.estu.staj1

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.Log
import android.view.View
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import kotlin.math.max
import kotlin.math.min

/**
 * GÜNCELLENDİ (Rotasyon Düzeltmesi):
 * - Telefon dikey (Portrait) moddayken 90 derece dönük olan koordinatları düzeltir.
 * - Ön kamera (Front) için X-ekseni aynalaması yapar.
 */
class OverlayView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    // --- DOĞRU İNDEKSLER (468 model için) ---
    // (Bu kısım aynı, dokunmayın)
    private val FACE_OVAL_INDICES = listOf(
        10, 338, 297, 332, 284, 251, 389, 356, 454, 323, 361, 288, 397, 365, 379,
        378, 400, 377, 152, 148, 176, 149, 150, 136, 172, 58, 132, 93, 234, 127,
        162, 21, 54, 103, 67, 109
    )
    private val LIPS_INDICES = listOf(
        61, 146, 91, 181, 84, 17, 314, 405, 321, 375, 291, 308, 324, 318, 402, 317,
        14, 87, 178, 88, 95, 185, 40, 39, 37, 0, 267, 269, 270, 409, 415, 310,
        311, 312, 13, 82, 81, 42, 183, 78
    )
    private val LEFT_EYE_INDICES = listOf(
        362, 382, 381, 380, 374, 373, 390, 249, 263, 466, 388, 387, 386, 385, 384, 398
    )
    private val RIGHT_EYE_INDICES = listOf(
        33, 7, 163, 144, 145, 153, 154, 155, 133, 173, 157, 158, 159, 160, 161, 246
    )
    private val LEFT_EYEBROW_INDICES = listOf(
        336, 296, 334, 293, 300, 276, 283, 282, 295, 285
    )
    private val RIGHT_EYEBROW_INDICES = listOf(
        70, 63, 105, 66, 107, 55, 65, 52, 53, 46
    )
    private val NOSE_INDICES = listOf(
       1,2,4,5,48,278,115,344
    )
    private val LIPS_INNER_INDICES = listOf(
        78, 95, 88, 178, 87, 14, 317, 402, 318, 324, 308, 415, 409, 270, 269, 267,
        0, 37, 39, 40, 185
    )
    private val LEFT_CHEEK_INDICES = listOf(
        132, 117, 118, 119,120, 205, 147, 213
    )
    private val RIGHT_CHEEK_INDICES = listOf(
        361,346,347,348,349,425,376,433
    )
    private val FINGERTIPS_INDICES=listOf(
        4,8,12,16,20
    )
    private val PALM_INDICES =listOf(
        0,1,2,5,9,13,17
    )


    private var handResults: HandLandmarkerResult? = null
    private var faceResults: FaceLandmarkerResult? = null

    val faceFilters = mutableSetOf<String>()
    val handFilters = mutableSetOf<String>()


    private var scaleFactor: Float = 1f
    private var imageWidth: Int = 1
    private var imageHeight: Int = 1
    private var runningMode: RunningMode = RunningMode.IMAGE

    // YENİ: Rotasyon ve Aynalama bayrakları
    private var isPortraitMode: Boolean = false
    var isFrontCamera: Boolean = false // MainActivity tarafından ayarlanacak

    private val debugTextPaint = Paint().apply {
        color = Color.RED
        textSize = 60f // Okunması kolay olsun
        style = Paint.Style.FILL
    }
    // Hesaplanan oranı saklamak için
    private var mouthAspectRatio: Float = 0f
    // --- YENİ EKLENEN KOD SONU ---

    // Boya stilleri (Rengi Siyan ve Sarı olarak değiştirmiştik)
    private val handBoxPaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }
    private val handTextPaint = Paint().apply {
        color = Color.WHITE
        textSize = 40f
    }
    private val faceBoxPaint = Paint().apply {
        color = Color.CYAN // Belirgin renk
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }
    private val faceTextPaint = Paint().apply {
        color = Color.YELLOW // Belirgin renk
        textSize = 40f
    }

    fun setHandResults(
        handLandmarkerResult: HandLandmarkerResult,
        imageHeight: Int,
        imageWidth: Int,
        runningMode: RunningMode = RunningMode.IMAGE
    ) {
        handResults = handLandmarkerResult
        faceResults = null
        updateScaling(imageHeight, imageWidth, runningMode)
    }

    fun setFaceResults(
        faceLandmarkerResult: FaceLandmarkerResult,
        imageHeight: Int,
        imageWidth: Int,
        runningMode: RunningMode = RunningMode.IMAGE
    ) {
        faceResults = faceLandmarkerResult
        handResults = null
        updateScaling(imageHeight, imageWidth, runningMode)
    }

    fun clearResults() {
        handResults = null
        faceResults = null
        invalidate()
    }

    // GÜNCELLENDİ: Rotasyonu hesaba katan ölçekleme
    private fun updateScaling(imageHeight: Int, imageWidth: Int, runningMode: RunningMode) {
        this.imageHeight = imageHeight
        this.imageWidth = imageWidth
        this.runningMode = runningMode

        // Cihazın dikeyde (Portrait) olup olmadığını ve görüntünün yatay (Landscape) olup olmadığını kontrol et
        // (Görünüm Dikey) && (Görüntü Yatay)
        val viewAspectRatio = if (width > 0 && height > 0) width.toFloat() / height.toFloat() else 0f
        val imageAspectRatio = if (imageWidth > 0 && imageHeight > 0) imageWidth.toFloat() / imageHeight.toFloat() else 0f

        isPortraitMode = (imageAspectRatio > 1.0f && viewAspectRatio < 1.0f)

        // Ölçekleme faktörünü hesapla
        if (isPortraitMode) {
            // Dikey Mod: Görüntü boyutlarını 90 derece döndürerek hesapla
            val scaleFactorX = width.toFloat() / imageHeight.toFloat()
            val scaleFactorY = height.toFloat() / imageWidth.toFloat()
            scaleFactor = max(scaleFactorX, scaleFactorY)
        } else {
            // Yatay Mod (veya her ikisi de aynı yönelimde)
            scaleFactor = if (imageAspectRatio > viewAspectRatio) {
                height.toFloat() / imageHeight.toFloat()
            } else {
                width.toFloat() / imageWidth.toFloat()
            }
        }
        invalidate()
    }

    // GÜNCELLENDİ: Rotasyonu hesaba katan merkezleme ofseti
    private fun calculateOffset(): Pair<Float, Float> {
        return if (runningMode == RunningMode.LIVE_STREAM) {
            val postScaleWidth: Float
            val postScaleHeight: Float

            if (isPortraitMode) {
                // Görüntü 90 derece döndü, boyutları ters al
                postScaleWidth = imageHeight * scaleFactor
                postScaleHeight = imageWidth * scaleFactor
            } else {
                // Normal
                postScaleWidth = imageWidth * scaleFactor
                postScaleHeight = imageHeight * scaleFactor
            }

            ((width - postScaleWidth) / 2f) to ((height - postScaleHeight) / 2f)
        } else {
            0f to 0f
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        handResults?.let { drawHandBoxes(canvas, it) }
        faceResults?.let { drawFaceBoxes(canvas, it) }
        if(faceResults != null){
            canvas.drawText(
                "Oran:%.2f.".format(mouthAspectRatio),
                80f,
                120f,
                debugTextPaint
            )
        }
    }

    // --- Çizim Fonksiyonları ---
    // (Aşağıdaki fonksiyonlar, en alttaki getBoundingBoxForLandmarks
    // fonksiyonunu çağırdığı için otomatik olarak düzeltilmiş koordinatları alacaklar)

    private fun drawHandBoxes(canvas: Canvas, result: HandLandmarkerResult) {
        val (offsetX, offsetY) = calculateOffset()
        try {
            // Filtreler boşsa hiçbir şey çizme
            if (handFilters.isEmpty()) return

            val rawLandmarksList = safeCall(result, "landmarks") ?: return
            val lists = rawLandmarksList as? Iterable<*>

            lists?.forEach { singleHand ->
                val landmarks = toNormalizedList(singleHand)
                if (landmarks.isEmpty()) return@forEach

                try {
                    // "Avuç" filtresi aktifse çiz
                    if (handFilters.contains("Avuç")) {
                        val palm = mapLandmarks(landmarks, PALM_INDICES)
                        if (palm.isNotEmpty()) {
                            val r = getBoundingBoxForLandmarks(palm, offsetX, offsetY)
                            canvas.drawRect(r, handBoxPaint)
                            canvas.drawText("Avuç", r.left, r.top - 10f, handTextPaint)
                        }
                    }

                    // "Parmak Uçları" filtresi aktifse çiz
                    if (handFilters.contains("Parmak Uçları")) {
                        val fingertips = mapLandmarks(landmarks, FINGERTIPS_INDICES)
                        if (fingertips.isNotEmpty()) {
                            val r = getBoundingBoxForLandmarks(fingertips, offsetX, offsetY)
                            canvas.drawRect(r, handBoxPaint)
                            canvas.drawText("Uçlar", r.left, r.top - 10f, handTextPaint)
                        }
                    }

                } catch (e: Exception) {
                    Log.e("OverlayView", "El filtresi çizim hatası: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e("OverlayView", "drawHandBoxes hata: ${e.message}")
        }
    }

    private fun drawFaceBoxes(canvas: Canvas, result: FaceLandmarkerResult) {
        val (offsetX, offsetY) = calculateOffset()
        try {
            val rawFaceLandmarksContainer = safeCall(result, "faceLandmarks") ?: safeCall(result, "landmarks") ?: return
            val faceItems = rawFaceLandmarksContainer as? Iterable<*> ?: return

            for (faceItem in faceItems) {
                if (faceFilters.isEmpty()) continue

                val landmarks = toNormalizedList(faceItem)
                if (landmarks.isEmpty()) continue

                // --- AĞIZ ORANI HESAPLAMASI (DÜZELTİLDİ) ---
                var isMouthOpen = false
                try {
                    // Değişken isimleri daha anlaşılır hale getirildi
                    val topLip = getXY(landmarks[0]) // Üst dudak merkezi (DIŞ)
                    val bottomLip = getXY(landmarks[17]) // Alt dudak merkezi (DIŞ)
                    val leftCorner = getXY(landmarks[61]) // Sol köşe
                    val rightCorner = getXY(landmarks[291]) // Sağ köşe

                    val verticalDist = kotlin.math.abs(topLip.second - bottomLip.second)
                    val horizontalDist = kotlin.math.abs(leftCorner.first - rightCorner.first)

                    if (horizontalDist > 0) {
                        val ratio = verticalDist / horizontalDist
                        mouthAspectRatio = ratio // Debug yazısı için oranı kaydet

                        // HATA DÜZELTMESİ: Eşik değeri 0.35f'den 0.20f'ye düşürüldü.
                        // Sol üstteki "Oran:" yazısına bakarak bu eşiği kendinize göre ayarlayın.
                        if (ratio > 0.60f) {
                            isMouthOpen = true
                        } else {
                            isMouthOpen = false
                        }
                    }

                } catch (e: Exception) {
                    Log.e("OverlayView", "Ağız oranı hesaplanamadı: ${e.message}")
                }
                // --- ORAN HESAPLAMA SONU ---


                try {
                    // --- ÇİZİM BLOKLARI (KUTU HATASI DÜZELTİLDİ) ---

                    if (faceFilters.contains("Gözler")) {
                        val leftEye = mapLandmarks(landmarks, LEFT_EYE_INDICES) //
                        val rightEye = mapLandmarks(landmarks, RIGHT_EYE_INDICES) //
                        if (leftEye.isNotEmpty()) {
                            val r = getBoundingBoxForLandmarks(leftEye, offsetX, offsetY)
                            canvas.drawRect(r, faceBoxPaint)
                            canvas.drawText("Sol Göz", r.left, r.top - 10f, faceTextPaint)
                        }
                        if (rightEye.isNotEmpty()) {
                            val r2 = getBoundingBoxForLandmarks(rightEye, offsetX, offsetY)
                            canvas.drawRect(r2, faceBoxPaint)
                            canvas.drawText("Sağ Göz", r2.left, r2.top - 10f, faceTextPaint)
                        }
                    }

                    if (faceFilters.contains("Kaşlar")) {
                        val leftBrow = mapLandmarks(landmarks, LEFT_EYEBROW_INDICES) //
                        val rightBrow = mapLandmarks(landmarks, RIGHT_EYEBROW_INDICES) //
                        if (leftBrow.isNotEmpty()) {
                            val r = getBoundingBoxForLandmarks(leftBrow, offsetX, offsetY)
                            canvas.drawRect(r, faceBoxPaint)
                            canvas.drawText("Sol Kaş", r.left, r.top - 10f, faceTextPaint)
                        }
                        if (rightBrow.isNotEmpty()) {
                            val r2 = getBoundingBoxForLandmarks(rightBrow, offsetX, offsetY)
                            canvas.drawRect(r2, faceBoxPaint)
                            canvas.drawText("Sağ Kaş", r2.left, r2.top - 10f, faceTextPaint)
                        }
                    }

                    if (faceFilters.contains("Ağız")) {
                        val mouth = mapLandmarks(landmarks, LIPS_INDICES) //
                        if (mouth.isNotEmpty()) {
                            val r = getBoundingBoxForLandmarks(mouth, offsetX, offsetY)
                            canvas.drawRect(r, faceBoxPaint)
                            val text = if (isMouthOpen) "Ağız (AÇIK)" else "Ağız" // Metin güncellendi
                            canvas.drawText(text, r.left, r.top - 10f, faceTextPaint)
                        }
                    }

                    // --- KOPYALA-YAPIŞTIR HATASI BURADAYDI (DÜZELTİLDİ) ---
                    if (faceFilters.contains("Yüz Çevresi")) {
                        // HATA: Burası LIPS_INDICES idi.
                        // DÜZELTME: FACE_OVAL_INDICES olarak değiştirildi.
                        val oval = mapLandmarks(landmarks, FACE_OVAL_INDICES)
                        if (oval.isNotEmpty()) {
                            val r = getBoundingBoxForLandmarks(oval, offsetX, offsetY)
                            canvas.drawRect(r, faceBoxPaint)
                            val text = "Yüz Çevresi" // Metin düzeltildi
                            canvas.drawText(text, r.left, r.top - 10f, faceTextPaint)
                        }
                    }
                    // --- HATA DÜZELTMESİ SONU ---

                    if (faceFilters.contains("Burun")) {
                        val nose = mapLandmarks(landmarks, NOSE_INDICES) //
                        if (nose.isNotEmpty()) {
                            val r = getBoundingBoxForLandmarks(nose, offsetX, offsetY)
                            canvas.drawRect(r, faceBoxPaint)
                            canvas.drawText("Burun", r.left, r.top - 10f, faceTextPaint)
                        }
                    }

                    // MainActivity'de "Ağız içi" yerine "Ağız İçi" (büyük İ) kullandık,
                    // burayı da onunla eşleşecek şekilde güncelledim.
                    if (faceFilters.contains("Ağız İçi")) {
                        val innerMouth = mapLandmarks(landmarks, LIPS_INNER_INDICES) //
                        if (innerMouth.isNotEmpty()) {
                            val r = getBoundingBoxForLandmarks(innerMouth, offsetX, offsetY)
                            canvas.drawRect(r, faceBoxPaint)
                            val text = if (isMouthOpen) "Ağız İçi (AÇIK)" else "Ağız İçi"
                            canvas.drawText(text, r.left, r.top - 10f, faceTextPaint)
                        }
                    }

                    if (faceFilters.contains("Yanaklar")) {
                        val leftCheek = mapLandmarks(landmarks, LEFT_CHEEK_INDICES) //
                        val rightCheek = mapLandmarks(landmarks, RIGHT_CHEEK_INDICES) //
                        if (leftCheek.isNotEmpty()) {
                            val r = getBoundingBoxForLandmarks(leftCheek, offsetX, offsetY)
                            canvas.drawRect(r, faceBoxPaint)
                            canvas.drawText("Sol Yanak", r.left, r.top - 10f, faceTextPaint)
                        }
                        if (rightCheek.isNotEmpty()) {
                            val r2 = getBoundingBoxForLandmarks(rightCheek, offsetX, offsetY)
                            canvas.drawRect(r2, faceBoxPaint)
                            canvas.drawText("Sağ Yanak", r2.left, r2.top - 10f, faceTextPaint)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("OverlayView", "filtreli çizim içi hata: ${e.message}")
                }
            }

        } catch (e: Exception) {
            Log.e("OverlayView", "drawFaceBoxes hata: ${e.message}")
        }
    }

    // --- Reflection (Senin kodun) - DEĞİŞİKLİK YOK ---
    // (Bu kısımlar aynı)
    private fun toNormalizedList(obj: Any?): List<Any> {
        if (obj == null) return emptyList()
        if (obj is Iterable<*>) {
            return obj.filterNotNull()
        }
        val tryNames = listOf("getLandmarkList", "getLandmark", "landmarkList", "landmark")
        for (name in tryNames) {
            try {
                val m = obj.javaClass.methods.firstOrNull { it.name == name || it.name.equals(name, ignoreCase = true) }
                if (m != null) {
                    val res = m.invoke(obj)
                    if (res is Iterable<*>) return res.filterNotNull()
                    if (res is Array<*>) return res.filterNotNull()
                }
            } catch (_: Exception) { /* ignore */ }
        }
        return listOf(obj)
    }

    private fun safeCall(obj: Any, methodName: String): Any? {
        return try {
            val m = obj.javaClass.methods.firstOrNull { it.name.equals(methodName, ignoreCase = true) || it.name == "get${methodName.capitalize()}" }
            m?.invoke(obj)
        } catch (e: Exception) {
            null
        }
    }

    private fun mapLandmarks(all: List<Any>, indices: List<Int>): List<Any> {
        return indices
            .filter { it >= 0 && it < all.size }
            .map { idx -> all[idx] }
    }

    private fun getXY(landmark: Any): Pair<Float, Float> {
        try {
            val mx = landmark.javaClass.getMethod("getX")
            val my = landmark.javaClass.getMethod("getY")
            val xv = mx.invoke(landmark) as? Number
            val yv = my.invoke(landmark) as? Number
            if (xv != null && yv != null) return xv.toFloat() to yv.toFloat()
        } catch (_: Exception) { /* ignore */ }

        try {
            val mx = landmark.javaClass.getMethod("x")
            val my = landmark.javaClass.getMethod("y")
            val xv = mx.invoke(landmark) as? Number
            val yv = my.invoke(landmark) as? Number
            if (xv != null && yv != null) return xv.toFloat() to yv.toFloat()
        } catch (_: Exception) { /* ignore */ }

        try {
            val fx = landmark.javaClass.getDeclaredField("x")
            val fy = landmark.javaClass.getDeclaredField("y")
            fx.isAccessible = true
            fy.isAccessible = true
            val xv = fx.get(landmark) as? Number
            val yv = fy.get(landmark) as? Number
            if (xv != null && yv != null) return xv.toFloat() to yv.toFloat()
        } catch (_: Exception) { /* ignore */ }

        return 0f to 0f
    }
    // --- Reflection Sonu ---


    // GÜNCELLENDİ: Asıl sihrin yapıldığı yer.
    // Koordinatları rotasyona ve aynalamaya göre hesaplar.
    private fun getBoundingBoxForLandmarks(
        landmarks: List<Any>,
        offsetX: Float,
        offsetY: Float
    ): RectF {
        if (landmarks.isEmpty()) return RectF(0f, 0f, 0f, 0f)

        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE

        for (landmark in landmarks) {
            var (lx, ly) = getXY(landmark) //

            // Adım 1: Ön kamera ise X eksenini ters çevir (Aynalama)
            if (isFrontCamera) {
                lx = 1.0f - lx
            }

            // Adım 2: Dikey mod ise koordinatları 90 derece döndür (Rotasyon)
            if (isPortraitMode) {
                // lx (görüntü X) -> ekran Y olur
                // ly (görüntü Y) -> ekran X olur (ters çevrilmiş)

                val x = (1.0f - ly) * (imageHeight * scaleFactor) + offsetX
                val y = lx * (imageWidth * scaleFactor) + offsetY

                minX = min(minX, x)
                minY = min(minY, y)
                maxX = max(maxX, x)
                maxY = max(maxY, y)

            } else {
                // Normal (yatay) mod,
                val x = lx * imageWidth * scaleFactor + offsetX
                val y = ly * imageHeight * scaleFactor + offsetY

                minX = min(minX, x)
                minY = min(minY, y)
                maxX = max(maxX, x)
                maxY = max(maxY, y)
            }
        }

        if (minX > maxX || minY > maxY) return RectF(0f, 0f, 0f, 0f)
        return RectF(minX, minY, maxX, maxY)
    }
}