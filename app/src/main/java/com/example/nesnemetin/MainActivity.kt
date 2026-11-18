package com.example.nesnemetin

import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Surface(Modifier.fillMaxSize()) { App() } } }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun App() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val analyzer = remember { Analyzer(context) }

    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var dets by remember { mutableStateOf<List<Det>>(emptyList()) }
    var text by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var minScore by remember { mutableFloatStateOf(0.45f) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }

    fun process(uri: Uri) {
        scope.launch {
            busy = true
            try {
                val bmp = analyzer.load(uri)
                bitmap = bmp
                dets = analyzer.detect(bmp)
                text = analyzer.recognizeText(bmp)
            } catch (e: Exception) {
                Toast.makeText(context, "Hata: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                busy = false
            }
        }
    }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { it?.let(::process) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) cameraUri?.let(::process)
    }

    val shown = dets.filter { it.score >= minScore }
    val counts = shown.groupingBy { it.label }.eachCount().toList().sortedByDescending { it.second }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).statusBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Nesne Say & Metni Kopyala", style = MaterialTheme.typography.headlineSmall)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val f = File(context.cacheDir, "images").apply { mkdirs() }.let { File(it, "photo_${System.currentTimeMillis()}.jpg") }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
                cameraUri = uri
                camera.launch(uri)
            }) {
                Icon(Icons.Default.PhotoCamera, null); Spacer(Modifier.width(6.dp)); Text("Kamera")
            }
            OutlinedButton(onClick = {
                gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }) {
                Icon(Icons.Default.PhotoLibrary, null); Spacer(Modifier.width(6.dp)); Text("Galeri")
            }
        }

        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())

        bitmap?.let { bmp ->
            Box(Modifier.fillMaxWidth().aspectRatio(bmp.width.toFloat() / bmp.height)) {
                Image(bmp.asImageBitmap(), null, Modifier.matchParentSize())
                Canvas(Modifier.matchParentSize()) {
                    val sx = size.width / bmp.width
                    val sy = size.height / bmp.height
                    shown.forEach { d ->
                        drawRect(
                            Color(0xFFE53935),
                            topLeft = Offset(d.box.left * sx, d.box.top * sy),
                            size = Size(d.box.width() * sx, d.box.height() * sy),
                            style = Stroke(width = 3f)
                        )
                    }
                }
            }

            // --- Nesne sayımı ---
            Text("Nesne sayımı", style = MaterialTheme.typography.titleMedium)
            Text("Toplam: ${shown.size}", style = MaterialTheme.typography.bodyLarge)
            if (counts.isEmpty()) {
                Text("Nesne bulunamadı.", style = MaterialTheme.typography.bodyMedium)
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    counts.forEach { (label, n) -> AssistChip(onClick = {}, label = { Text("${trLabel(label)}: $n") }) }
                }
            }
            Text("Hassasiyet: %${(minScore * 100).toInt()} (düşük = daha çok nesne, daha çok hata)",
                style = MaterialTheme.typography.bodySmall)
            Slider(minScore, { minScore = it }, valueRange = 0.25f..0.9f)

            // --- Metin ---
            Text("Metin", style = MaterialTheme.typography.titleMedium)
            if (text.isBlank() && !busy) {
                Text("Fotoğrafta metin bulunamadı.", style = MaterialTheme.typography.bodyMedium)
            } else {
                SelectionContainer {
                    Text(text, Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyLarge)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = {
                        clipboard.setText(AnnotatedString(text))
                        Toast.makeText(context, "Kopyalandı", Toast.LENGTH_SHORT).show()
                    }) { Icon(Icons.Default.ContentCopy, null); Spacer(Modifier.width(6.dp)); Text("Kopyala") }
                    OutlinedButton(onClick = {
                        val i = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
                        context.startActivity(Intent.createChooser(i, "Paylaş"))
                    }) { Icon(Icons.Default.Share, null); Spacer(Modifier.width(6.dp)); Text("Paylaş") }
                }
            }
        } ?: Text("Bir fotoğraf çekin veya galeriden seçin.", style = MaterialTheme.typography.bodyMedium)
    }
}
