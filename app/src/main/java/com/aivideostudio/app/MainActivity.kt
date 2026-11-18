
package com.aivideostudio.app

import android.content.ContentValues
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.widget.*
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.*
import androidx.media3.effect.Presentation
import java.util.Locale

@OptIn(UnstableApi::class)
class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    private lateinit var script: EditText
    private lateinit var status: TextView
    private lateinit var createButton: Button

    private var tts: TextToSpeech? = null
    private val assets = mutableListOf<Uri>()

    private val picker =
        registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
            if (uris.isNotEmpty()) {
                assets.clear()
                assets.addAll(uris)
                status.text = "${assets.size} archivo(s) listos."
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        buildUi()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 28, 28, 28)
            setBackgroundColor(0xFF101218.toInt())
        }

        val title = TextView(this).apply {
            text = "🎬 AI Video Studio"
            textSize = 28f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
        }

        root.addView(title, LinearLayout.LayoutParams(-1, 70))

        val info = TextView(this).apply {
            text = "Crea Shorts 9:16 con tus imágenes y videos. La narración se genera en el teléfono."
            textSize = 15f
            setTextColor(0xFFB8BCC8.toInt())
            setPadding(0, 8, 0, 16)
        }

        root.addView(info)

        script = EditText(this).apply {
            hint = "Escribe aquí tu guion...\n\nEjemplo: Un pequeño gato descubre Marte y comienza una aventura."
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF8C919E.toInt())
            gravity = Gravity.TOP
            minLines = 7
            setPadding(18, 18, 18, 18)
            setBackgroundColor(0xFF1C1F27.toInt())
        }

        root.addView(script, LinearLayout.LayoutParams(-1, 0, 1f))

        val add = Button(this).apply {
            text = "📁 Agregar imágenes / MP4"
            setOnClickListener {
                picker.launch("*/*")
            }
        }

        root.addView(add)

        createButton = Button(this).apply {
            text = "🚀 CREAR VIDEO"
            setOnClickListener {
                createVideo()
            }
        }

        root.addView(createButton)

        status = TextView(this).apply {
            text = "Listo."
            textSize = 14f
            setTextColor(0xFF8DE1A6.toInt())
            setPadding(0, 12, 0, 0)
        }

        root.addView(status)

        setContentView(root)
    }

    override fun onInit(result: Int) {
        if (result == TextToSpeech.SUCCESS) {
            val languageResult = tts?.setLanguage(Locale("es", "MX"))

            if (languageResult == TextToSpeech.LANG_MISSING_DATA ||
                languageResult == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                status.text = "Idioma español no disponible."
            }
        }
    }

    private fun createVideo() {
        val text = script.text.toString().trim()

        if (text.isEmpty()) {
            toast("Escribe un guion primero")
            return
        }

        if (assets.isEmpty()) {
            toast("Agrega al menos una imagen o un MP4")
            return
        }

        createButton.isEnabled = false
        status.text = "Generando narración..."

        val audioFile = java.io.File(cacheDir, "narration.wav")

        val params = Bundle()

        val result = tts?.synthesizeToFile(
            text,
            params,
            audioFile,
            "aivideo-narration"
        )

        if (result != TextToSpeech.SUCCESS) {
            status.text = "No se pudo iniciar la narración."
            createButton.isEnabled = true
            return
        }

        Handler(Looper.getMainLooper()).postDelayed({

            if (!audioFile.exists() || audioFile.length() < 100) {
                status.text = "No se pudo generar la voz."
                createButton.isEnabled = true
                return@postDelayed
            }

            exportComposition(audioFile)

        }, 3000)
    }

    private fun exportComposition(audioFile: java.io.File) {
        try {
            status.text = "Montando video 9:16..."

            val videoItems = assets.map { uri ->

                val mime = contentResolver.getType(uri) ?: ""

                if (mime.startsWith("image/")) {

                    val image = MediaItem.Builder()
                        .setUri(uri)
                        .setImageDurationMs(4000)
                        .build()

                    EditedMediaItem.Builder(image)
                        .setFrameRate(30)
                        .build()

                } else {

                    val item = MediaItem.Builder()
                        .setUri(uri)
                        .build()

                    EditedMediaItem.Builder(item)
                        .build()
                }
            }

            // CORRECCIÓN: crear secuencia de video mediante método oficial.
            val videoSequence =
                EditedMediaItemSequence.withVideoFrom(videoItems)

            val audioItem = EditedMediaItem.Builder(
                MediaItem.fromUri(Uri.fromFile(audioFile))
            ).build()

            // CORRECCIÓN: crear secuencia de audio mediante método oficial.
            val audioSequence =
                EditedMediaItemSequence.withAudioFrom(listOf(audioItem))

            val effects = Effects(
                emptyList(),
                listOf(
                    Presentation.createForWidthAndHeight(
                        1080,
                        1920,
                        Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP
                    )
                )
            )

            val composition = Composition.Builder(
                listOf(videoSequence, audioSequence)
            )
                .setEffects(effects)
                .build()

            val outputPath = cacheDir.resolve(
                "final_${System.currentTimeMillis()}.mp4"
            )

            val transformer = Transformer.Builder(this)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(object : Transformer.Listener {

                    override fun onCompleted(
                        composition: Composition,
                        exportResult: ExportResult
                    ) {
                        saveToGallery(outputPath)
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        runOnUiThread {
                            status.text =
                                "Error al crear video: ${exportException.message}"

                            createButton.isEnabled = true
                        }
                    }

                })
                .build()

            transformer.start(composition, outputPath.absolutePath)

        } catch (e: Exception) {

            status.text = "Error: ${e.message}"
            createButton.isEnabled = true
        }
    }

    private fun saveToGallery(file: java.io.File) {
        runOnUiThread {

            try {
                val values = ContentValues().apply {
                    put(
                        MediaStore.Video.Media.DISPLAY_NAME,
                        "AI_Video_Studio_${System.currentTimeMillis()}.mp4"
                    )

                    put(
                        MediaStore.Video.Media.MIME_TYPE,
                        "video/mp4"
                    )

                    put(
                        MediaStore.Video.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_MOVIES + "/AI Video Studio"
                    )

                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }

                val uri = contentResolver.insert(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    values
                ) ?: throw IllegalStateException(
                    "No se pudo crear el archivo de salida"
                )

                contentResolver.openOutputStream(uri)?.use { out ->
                    file.inputStream().use { input ->
                        input.copyTo(out)
                    }
                } ?: throw IllegalStateException(
                    "No se pudo guardar el video"
                )

                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)

                contentResolver.update(uri, values, null, null)

                status.text =
                    "✅ Video terminado. Está en Películas/AI Video Studio"

                createButton.isEnabled = true

            } catch (e: Exception) {

                status.text =
                    "Video creado, pero no se pudo guardar: ${e.message}"

                createButton.isEnabled = true
            }
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        tts?.shutdown()
        super.onDestroy()
    }
}
