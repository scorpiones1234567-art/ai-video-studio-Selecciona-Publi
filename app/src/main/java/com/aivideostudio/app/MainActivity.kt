
package com.aivideostudio.app

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.Gravity
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.*
import androidx.media3.effect.Presentation
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.math.max

@OptIn(UnstableApi::class)
class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    private lateinit var script: EditText
    private lateinit var status: TextView
    private lateinit var createButton: Button

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var currentAudioFile: File? = null

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
            text = "Escribe tu historia y crea un video vertical con escenas de texto y narración en español. No necesitas subir imágenes."
            textSize = 15f
            setTextColor(0xFFB8BCC8.toInt())
            setPadding(0, 8, 0, 16)
        }
        root.addView(info)

        script = EditText(this).apply {
            hint = "Escribe aquí tu guion..."
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF8C919E.toInt())
            gravity = Gravity.TOP
            minLines = 7
            setPadding(18, 18, 18, 18)
            setBackgroundColor(0xFF1C1F27.toInt())
        }
        root.addView(script, LinearLayout.LayoutParams(-1, 0, 1f))

        createButton = Button(this).apply {
            text = "🚀 CREAR VIDEO MP4"
            setOnClickListener { createVideo() }
        }
        root.addView(createButton)

        status = TextView(this).apply {
            text = "Preparando voz..."
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

            ttsReady = languageResult != TextToSpeech.LANG_MISSING_DATA &&
                    languageResult != TextToSpeech.LANG_NOT_SUPPORTED

            tts?.setOnUtteranceProgressListener(
                object : UtteranceProgressListener() {

                    override fun onStart(utteranceId: String) {}

                    override fun onDone(utteranceId: String) {
                        runOnUiThread {
                            val audio = currentAudioFile

                            if (audio != null && audio.exists() &&
                                audio.length() > 100
                            ) {
                                createVisualScenesAndExport(audio)
                            } else {
                                fail("No se pudo guardar la narración.")
                            }
                        }
                    }

                    override fun onError(utteranceId: String) {
                        runOnUiThread {
                            fail("Error al generar la voz.")
                        }
                    }
                }
            )

            status.text = if (ttsReady) {
                "Listo. Escribe tu guion."
            } else {
                "No está disponible la voz en español."
            }

        } else {
            status.text = "No se pudo iniciar el motor de voz."
        }
    }

    private fun createVideo() {
        val text = script.text.toString().trim()

        if (text.isEmpty()) {
            toast("Escribe un guion primero")
            return
        }

        if (!ttsReady || tts == null) {
            toast("Espera a que esté lista la voz")
            return
        }

        createButton.isEnabled = false
        status.text = "Generando narración..."

        val audioFile = File(
            cacheDir,
            "narration_${System.currentTimeMillis()}.wav"
        )

        currentAudioFile = audioFile

        val result = tts?.synthesizeToFile(
            text,
            Bundle(),
            audioFile,
            "aivideo-${System.currentTimeMillis()}"
        )

        if (result != TextToSpeech.SUCCESS) {
            fail("No se pudo iniciar la narración.")
        }
    }

    private fun splitIntoScenes(text: String): List<String> {
        val words = text.replace("\\s+".toRegex(), " ")
            .trim()
            .split(" ")

        val chunks = mutableListOf<String>()
        var current = mutableListOf<String>()

        for (word in words) {
            current.add(word)

            if (current.size >= 12 ||
                word.endsWith(".") ||
                word.endsWith("!") ||
                word.endsWith("?")
            ) {
                chunks.add(current.joinToString(" "))
                current = mutableListOf()
            }
        }

        if (current.isNotEmpty()) {
            chunks.add(current.joinToString(" "))
        }

        return chunks.filter { it.isNotBlank() }
    }

    private fun createVisualScenesAndExport(audioFile: File) {
        try {
            val scenes = splitIntoScenes(
                script.text.toString().trim()
            )

            status.text = "Creando ${scenes.size} escenas..."

            val wordCount = script.text.toString()
                .trim()
                .split("\\s+".toRegex())
                .size

            val totalMs = max(5000, wordCount * 400 + 1500)
            val perSceneMs = max(2500, totalMs / scenes.size)

            val imageFiles = scenes.mapIndexed { index, scene ->
                val imageFile = File(
                    cacheDir,
                    "scene_${System.currentTimeMillis()}_$index.png"
                )

                drawScene(
                    scene,
                    index + 1,
                    scenes.size,
                    imageFile
                )

                imageFile
            }

            exportComposition(
                imageFiles,
                perSceneMs,
                audioFile
            )

        } catch (e: Exception) {
            fail("No se pudieron preparar las escenas: ${e.message}")
        }
    }

    private fun drawScene(
        text: String,
        sceneNumber: Int,
        totalScenes: Int,
        file: File
    ) {
        val bitmap = Bitmap.createBitmap(
            1080,
            1920,
            Bitmap.Config.ARGB_8888
        )

        val canvas = Canvas(bitmap)
        canvas.drawColor(0xFF111827.toInt())

        val accent = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF38BDF8.toInt()
            strokeWidth = 12f
        }

        canvas.drawRoundRect(
            90f, 250f, 990f, 265f,
            8f, 8f, accent
        )

        val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF93C5FD.toInt()
            textSize = 38f
            typeface = Typeface.create(
                "sans-serif-medium",
                Typeface.BOLD
            )
            textAlign = Paint.Align.CENTER
        }

        canvas.drawText(
            "AI VIDEO STUDIO",
            540f,
            350f,
            smallPaint
        )

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = 72f
            typeface = Typeface.create(
                "sans-serif",
                Typeface.BOLD
            )
            textAlign = Paint.Align.CENTER
        }

        val lines = wrapText(text, textPaint, 860f)
        val lineHeight = 94f
        val startY = 960f -
                (lines.size - 1) * lineHeight / 2f

        lines.forEachIndexed { index, line ->
            canvas.drawText(
                line,
                540f,
                startY + index * lineHeight,
                textPaint
            )
        }

        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF9CA3AF.toInt()
            textSize = 34f
            textAlign = Paint.Align.CENTER
        }

        canvas.drawText(
            "ESCENA $sceneNumber / $totalScenes",
            540f,
            1660f,
            footerPaint
        )

        FileOutputStream(file).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }

        bitmap.recycle()
    }

    private fun wrapText(
        text: String,
        paint: Paint,
        maxWidth: Float
    ): List<String> {
        val lines = mutableListOf<String>()
        var line = ""

        for (word in text.split(" ")) {
            val candidate = if (line.isEmpty()) {
                word
            } else {
                "$line $word"
            }

            if (paint.measureText(candidate) > maxWidth &&
                line.isNotEmpty()
            ) {
                lines.add(line)
                line = word
            } else {
                line = candidate
            }
        }

        if (line.isNotEmpty()) {
            lines.add(line)
        }

        return lines
    }

    private fun exportComposition(
        imageFiles: List<File>,
        durationMs: Int,
        audioFile: File
    ) {
        try {
            status.text = "Montando video vertical 9:16..."

            val videoItems = imageFiles.map { file ->
                val item = MediaItem.Builder()
                    .setUri(Uri.fromFile(file))
                    .setImageDurationMs(durationMs.toLong())
                    .build()

                EditedMediaItem.Builder(item)
                    .setFrameRate(30)
                    .build()
            }

            val videoSequence =
                EditedMediaItemSequence.withVideoFrom(videoItems)

            val audioItem = EditedMediaItem.Builder(
                MediaItem.fromUri(Uri.fromFile(audioFile))
            ).build()

            val audioSequence =
                EditedMediaItemSequence.withAudioFrom(
                    listOf(audioItem)
                )

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

            val outputFile = File(
                cacheDir,
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
                        saveToGallery(outputFile)
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        runOnUiThread {
                            fail(
                                "Error al crear el video: ${exportException.message}"
                            )
                        }
                    }
                })
                .build()

            transformer.start(
                composition,
                outputFile.absolutePath
            )

        } catch (e: Exception) {
            fail("Error al montar el video: ${e.message}")
        }
    }

    private fun saveToGallery(file: File) {
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
                        Environment.DIRECTORY_MOVIES +
                                "/AI Video Studio"
                    )

                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }

                val uri = contentResolver.insert(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    values
                ) ?: throw IllegalStateException(
                    "No se pudo crear el archivo de salida"
                )

                contentResolver.openOutputStream(uri)?.use { output ->
                    file.inputStream().use { input ->
                        input.copyTo(output)
                    }
                } ?: throw IllegalStateException(
                    "No se pudo guardar el video"
                )

                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)

                contentResolver.update(uri, values, null, null)

                status.text =
                    "✅ ¡Video terminado! Está en Películas/AI Video Studio."

                createButton.isEnabled = true

            } catch (e: Exception) {
                fail(
                    "Video creado, pero no se pudo guardar: ${e.message}"
                )
            }
        }
    }

    private fun fail(message: String) {
        status.text = message
        createButton.isEnabled = true
    }

    private fun toast(message: String) {
        Toast.makeText(
            this,
            message,
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun onDestroy() {
        tts?.shutdown()
        super.onDestroy()
    }
}
