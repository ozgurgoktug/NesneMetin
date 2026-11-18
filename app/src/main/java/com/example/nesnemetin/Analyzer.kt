package com.example.nesnemetin

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.RectF
import android.net.Uri
import com.google.android.gms.tasks.Tasks
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Det(val label: String, val score: Float, val box: RectF)

class Analyzer(private val context: Context) {

    // Düşük eşikle çalıştırıp, kullanıcı eşiğini arayüzde uyguluyoruz.
    private val detector: ObjectDetector by lazy {
        ObjectDetector.createFromOptions(
            context,
            ObjectDetector.ObjectDetectorOptions.builder()
                .setBaseOptions(BaseOptions.builder().setModelAssetPath("efficientdet_lite0.tflite").build())
                .setRunningMode(RunningMode.IMAGE)
                .setMaxResults(100)
                .setScoreThreshold(0.25f)
                .build()
        )
    }

    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    /** EXIF yönünü uygular, yazılım (ARGB_8888) bitmap döndürür. */
    suspend fun load(uri: Uri): Bitmap = withContext(Dispatchers.IO) {
        val src = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            // Çok büyük fotoğrafları küçült (bellek + hız)
            val longest = maxOf(info.size.width, info.size.height)
            if (longest > 2400) {
                val s = 2400f / longest
                decoder.setTargetSize((info.size.width * s).toInt(), (info.size.height * s).toInt())
            }
        }
    }

    suspend fun detect(bitmap: Bitmap): List<Det> = withContext(Dispatchers.Default) {
        val result = detector.detect(BitmapImageBuilder(bitmap).build())
        result.detections().mapNotNull { d ->
            val c = d.categories().firstOrNull() ?: return@mapNotNull null
            Det(c.categoryName(), c.score(), RectF(d.boundingBox()))
        }
    }

    suspend fun recognizeText(bitmap: Bitmap): String = withContext(Dispatchers.Default) {
        Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0))).text
    }
}

private val tr = mapOf(
    "person" to "kişi", "bicycle" to "bisiklet", "car" to "araba", "motorcycle" to "motosiklet",
    "airplane" to "uçak", "bus" to "otobüs", "train" to "tren", "truck" to "kamyon", "boat" to "tekne",
    "traffic light" to "trafik lambası", "fire hydrant" to "yangın musluğu", "stop sign" to "dur tabelası",
    "parking meter" to "park sayacı", "bench" to "bank", "bird" to "kuş", "cat" to "kedi", "dog" to "köpek",
    "horse" to "at", "sheep" to "koyun", "cow" to "inek", "elephant" to "fil", "bear" to "ayı",
    "zebra" to "zebra", "giraffe" to "zürafa", "backpack" to "sırt çantası", "umbrella" to "şemsiye",
    "handbag" to "el çantası", "tie" to "kravat", "suitcase" to "bavul", "frisbee" to "frizbi",
    "skis" to "kayak", "snowboard" to "snowboard", "sports ball" to "top", "kite" to "uçurtma",
    "baseball bat" to "beyzbol sopası", "baseball glove" to "beyzbol eldiveni", "skateboard" to "kaykay",
    "surfboard" to "sörf tahtası", "tennis racket" to "tenis raketi", "bottle" to "şişe",
    "wine glass" to "şarap kadehi", "cup" to "fincan", "fork" to "çatal", "knife" to "bıçak",
    "spoon" to "kaşık", "bowl" to "kase", "banana" to "muz", "apple" to "elma", "sandwich" to "sandviç",
    "orange" to "portakal", "broccoli" to "brokoli", "carrot" to "havuç", "hot dog" to "sosisli",
    "pizza" to "pizza", "donut" to "donut", "cake" to "pasta", "chair" to "sandalye", "couch" to "koltuk",
    "potted plant" to "saksı bitkisi", "bed" to "yatak", "dining table" to "yemek masası",
    "toilet" to "tuvalet", "tv" to "televizyon", "laptop" to "dizüstü", "mouse" to "fare",
    "remote" to "kumanda", "keyboard" to "klavye", "cell phone" to "telefon", "microwave" to "mikrodalga",
    "oven" to "fırın", "toaster" to "tost makinesi", "sink" to "lavabo", "refrigerator" to "buzdolabı",
    "book" to "kitap", "clock" to "saat", "vase" to "vazo", "scissors" to "makas",
    "teddy bear" to "oyuncak ayı", "hair drier" to "saç kurutma", "toothbrush" to "diş fırçası"
)

fun trLabel(en: String) = tr[en] ?: en
