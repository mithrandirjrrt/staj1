package com.estu.staj1

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import org.tensorflow.lite.task.vision.detector.Detection
import java.util.LinkedList
import java.util.Locale

/**
 * Kamera görüntüsünün üzerine nesne tespit kutularını ve etiketlerini çizmek için özel bir View sınıfı.
 */
class OverlayView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    // Tespit edilen nesnelerin bir listesini tutar
    private var results: List<Detection> = LinkedList<Detection>()
    private val rectF = RectF()

    // Görüntü boyutlarını tutar (koordinatları ölçeklemek için)
    private var imageWidth: Int = 1
    private var imageHeight: Int = 1

    // Kutu çizimi için Fırça (Paint) ayarları
    private val boxPaint = Paint().apply {
        color = ContextCompat.getColor(context, android.R.color.holo_red_dark) // Kutunun rengi
        style = Paint.Style.STROKE // İçi boş, sadece çerçeve
        strokeWidth = 8f // Çizgi kalınlığı
    }

    // Metin (etiket) çizimi için Fırça ayarları
    private val textPaint = Paint().apply {
        color = Color.WHITE // Metin rengi
        style = Paint.Style.FILL
        textSize = 50f // Metin boyutu
    }

    // Bu fonksiyon MainActivity'den çağrılacak
    fun setResults(
        detectionResults: List<Detection>,
        imageWidth: Int,
        imageHeight: Int,
    ) {
        results = detectionResults
        this.imageWidth = imageWidth
        this.imageHeight = imageHeight

        // View'i yeniden çizmeye zorla (onDraw'ı tetikler)
        invalidate()
    }

    // Bu fonksiyon View ekrana çizildiğinde otomatik olarak çalışır
    // SADECE BU FONKSİYONU GÜNCELLEYİN
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Eğer sonuç yoksa hiçbir şey çizme
        if (results.isEmpty()) {
            return
        }

        val imageAspectRatio = imageWidth.toFloat() / imageHeight.toFloat()
        val viewAspectRatio = width.toFloat() / height.toFloat()
        val scaleFactor: Float
        val offsetX: Float
        val offsetY: Float

        if (imageAspectRatio > viewAspectRatio) {
            // Image is wider than the view (letterboxing)
            scaleFactor = width.toFloat() / imageWidth
            offsetX = 0f
            offsetY = (height - imageHeight * scaleFactor) / 2
        } else {
            // Image is taller than or same as the view (pillarboxing)
            scaleFactor = height.toFloat() / imageHeight
            offsetX = (width - imageWidth * scaleFactor) / 2
            offsetY = 0f
        }


        // YENİ: Kutuyu ne kadar küçülteceğimizi belirleyen değişken
        // 1.0f = Orijinal boyut (%0 küçültme)
        // 0.9f = %10 küçült
        // 0.7f = %30 küçült (sizin istediğiniz 1 -> 0.70 oranı)
        val boxScaleFactor = 0.8f

        // Her bir tespit edilen nesne için döngü
        for (result in results) {
            val boundingBox = result.boundingBox

            // 1. Orijinal koordinatları hesapla (en boy oranını düzelterek)
            val originalLeft = (boundingBox.left * scaleFactor) + offsetX
            val originalTop = (boundingBox.top * scaleFactor) + offsetY
            val originalRight = (boundingBox.right * scaleFactor) + offsetX
            val originalBottom = (boundingBox.bottom * scaleFactor) + offsetY

            // 2. Orijinal kutunun merkezini ve boyutlarını bul
            val width = originalRight - originalLeft
            val height = originalBottom - originalTop
            val centerX = originalLeft + (width / 2f)
            val centerY = originalTop + (height / 2f)

            // 3. Yeni (küçültülmüş) genişlik ve yüksekliği hesapla
            val newWidth = width * boxScaleFactor
            val newHeight = height * boxScaleFactor

            // 4. Yeni koordinatları merkeze göre hesapla
            val newLeft = centerX - (newWidth / 2f)
            val newTop = centerY - (newHeight / 2f)
            val newRight = centerX + (newWidth / 2f)
            val newBottom = centerY + (newHeight / 2f)

            // 5. Küçültülmüş koordinatları kullanarak kutuyu çiz
            rectF.set(newLeft, newTop, newRight, newBottom)
            canvas.drawRect(rectF, boxPaint)

            // 6. Etiketi ve güven skorunu hazırla
            val category = result.categories.firstOrNull()
            val text = "${category?.label} (${String.format(Locale.US, "%.2f", category?.score)})"

            // 7. Metni, küçültülmüş kutunun sol üst köşesine yaz
            canvas.drawText(text, newLeft, newTop - 10f, textPaint)
        }
    }
}