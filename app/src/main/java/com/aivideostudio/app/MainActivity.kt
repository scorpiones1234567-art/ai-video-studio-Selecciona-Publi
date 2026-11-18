
package com.aivideostudio.app

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
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
import kotlin.math.sin

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
            text = "🎬 STICKMAN VIDEO STUDIO"
            textSize = 25f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
        }
        root.addView(title, LinearLayout.LayoutParams(-1, 70))

        val info = TextView(this).apply {
            text = "Escribe tu historia y crea un video animado con personajes Stickman, escenarios y narración en español."
            textSize = 15f
            setTextColor(0xFFB8BCC8.toInt())
            setPadding(0, 8, 0, 16)
        }
        root.addView(info)

        script = EditText(this).apply {
            hint = "Ejemplo: Un Stickman caminaba por el bosque y encontró un tesoro..."
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF8C919E.toInt())
            gravity = Gravity.TOP
            minLines = 7
            setPadding(18, 18, 18, 18)
            setBackgroundColor(0xFF1C1F27.toInt())
        }
        root.addView(script, LinearLayout.LayoutParams(-1, 0, 1f))

        createButton = Button(this).apply {
            text = "🚀 CREAR VIDEO STICKMAN"
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

            ttsReady =
                languageResult != TextToSpeech.LANG_MISSING_DATA &&
                languageResult != TextToSpeech.LANG_NOT_SUPPORTED

            tts?.setOnUtteranceProgressListener(
                object : UtteranceProgressListener() {

                    override fun onStart(utteranceId: String) {}

                    override fun onDone(utteranceId: String) {
                        runOnUiThread {
                            val audio = currentAudioFile

                            if (audio != null &&
                                audio.exists() &&
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
                "Listo. Escribe tu historia."
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
            toast("Escribe una historia primero")
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
            "stickman-${System.currentTimeMillis()}"
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

            status.text = "Dibujando personajes Stickman..."

            val wordCount = script.text.toString()
                .trim()
                .split("\\s+".toRegex())
                .size

            val totalMs = max(5000, wordCount * 400 + 1500)
            val perSceneMs = max(2500, totalMs / scenes.size)

            val frameCount = 8
            val frameDuration = max(250, perSceneMs / frameCount)

            val imageFiles = mutableListOf<File>()

            scenes.forEachIndexed { sceneIndex, scene ->
                for (frame in 0 until frameCount) {
                    val imageFile = File(
                        cacheDir,
                        "stickman_${System.currentTimeMillis()}_${sceneIndex}_$frame.png"
                    )

                    drawStickmanFrame(
                        scene,
                        sceneIndex + 1,
                        scenes.size,
                        frame,
                        imageFile
                    )

                    imageFiles.add(imageFile)
                }
            }

            exportComposition(
                imageFiles,
                frameDuration,
                audioFile
            )

        } catch (e: Exception) {
            fail("No se pudieron preparar las escenas: ${e.message}")
        }
    }

    private fun drawStickmanFrame(
        scene: String,
        sceneNumber: Int,
        totalScenes: Int,
        frame: Int,
        file: File
    ) {
        val bitmap = Bitmap.createBitmap(
            1080,
            1920,
            Bitmap.Config.ARGB_8888
        )

        val canvas = Canvas(bitmap)
        val lower = scene.lowercase(Locale.ROOT)

        canvas.drawColor(0xFF9AD9F5.toInt())

        val sun = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFD166.toInt()
        }
        canvas.drawCircle(880f, 260f, 105f, sun)

        val cloud = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
        }

        canvas.drawCircle(210f, 300f, 65f, cloud)
        canvas.drawCircle(280f, 270f, 85f, cloud)
        canvas.drawCircle(355f, 305f, 60f, cloud)

        val hill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF79B978.toInt()
        }

        canvas.drawOval(-180f, 1120f, 650f, 1580f, hill)
        canvas.drawOval(400f, 1080f, 1300f, 1600f, hill)

        val ground = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF4D8B54.toInt()
        }

        canvas.drawRect(0f, 1450f, 1080f, 1920f, ground)

        val dark = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF20242C.toInt()
            strokeWidth = 22f
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }

        // Bosque
        if (listOf("bosque", "árbol", "arbol", "selva")
            .any { lower.contains(it) }
        ) {
            for (x in listOf(120f, 900f)) {
                canvas.drawLine(x, 1450f, x, 650f, dark)

                val leaves = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFF28734A.toInt()
                    style = Paint.Style.FILL
                }

                canvas.drawCircle(x, 600f, 180f, leaves)
            }
        }

        // Casa
        if (listOf("casa", "hogar", "puerta")
            .any { lower.contains(it) }
        ) {
            val house = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFFFE0A3.toInt()
            }

            canvas.drawRect(690f, 900f, 1000f, 1450f, house)

            val roof = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFB85C38.toInt()
            }

            val path = Path().apply {
                moveTo(650f, 920f)
                lineTo(845f, 700f)
                lineTo(1040f, 920f)
                close()
            }

            canvas.drawPath(path, roof)
        }

        // Tesoro
        if (listOf("tesoro", "oro", "moneda")
            .any { lower.contains(it) }
        ) {
            val gold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFFFD700.toInt()
            }

            canvas.drawRoundRect(
                690f, 1260f, 900f, 1410f,
                24f, 24f, gold
            )

            canvas.drawCircle(790f, 1210f, 72f, gold)
        }

        val running = listOf(
            "corre", "corrió", "correr",
            "persigue", "escapa", "huye"
        ).any { lower.contains(it) }

        val jumping = listOf(
            "salta", "saltó", "brinca", "vuela"
        ).any { lower.contains(it) }

        val fighting = listOf(
            "pelea", "pelear", "lucha",
            "golpea", "monstruo", "ataca"
        ).any { lower.contains(it) }

        val phase = frame / 7f

        val bob = if (jumping) {
            -180f * kotlin.math.abs(
                sin(phase * Math.PI)
            ).toFloat()
        } else {
            0f
        }

        val stride = if (running || fighting) {
            sin(frame * 0.9).toFloat() * 95f
        } else {
            sin(frame * 0.45).toFloat() * 24f
        }

        val moveX = if (running) frame * 24f else 0f
        val cx = (360f + moveX).coerceAtMost(700f)
        val cy = 1180f + bob

        // Dibuja un personaje Stickman
        fun stickPerson(
            x: Float,
            y: Float,
            scale: Float,
            arm: Float,
            leg: Float,
            color: Int
        ) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                strokeWidth = 24f * scale
                strokeCap = Paint.Cap.ROUND
                style = Paint.Style.STROKE
            }

            val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                style = Paint.Style.FILL
            }

            // Cabeza
            canvas.drawCircle(
                x,
                y - 235f * scale,
                72f * scale,
                p
            )

            // Cuerpo
            canvas.drawLine(
                x, y - 160f * scale,
                x, y + 70f * scale, p
            )

            // Brazos
            canvas.drawLine(
                x, y - 90f * scale,
                x - 105f * scale + arm,
                y + 15f * scale, p
            )

            canvas.drawLine(
                x, y - 90f * scale,
                x + 105f * scale + arm,
                y + 15f * scale, p
            )

            // Piernas
            canvas.drawLine(
                x, y + 70f * scale,
                x - 95f * scale + leg,
                y + 235f * scale, p
            )

            canvas.drawLine(
                x, y + 70f * scale,
                x + 95f * scale - leg,
                y + 235f * scale, p
            )

            // Ojos
            canvas.drawCircle(
                x - 24f * scale,
                y - 250f * scale,
                8f * scale,
                fill
            )

            canvas.drawCircle(
                x + 24f * scale,
                y - 250f * scale,
                8f * scale,
                fill
            )
        }

        stickPerson(
            cx, cy, 1.15f,
            stride, -stride,
            0xFF17191F.toInt()
        )

        // Segundo personaje si hay pelea o monstruo
        if (fighting) {
            stickPerson(
                790f, 1190f, 0.95f,
                -stride, stride,
                0xFF8B2635.toInt()
            )
        }

        // Texto de la escena
        val caption = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF17212B.toInt()
            textSize = 42f
            typeface = Typeface.create(
                "sans-serif-medium",
                Typeface.BOLD
            )
            textAlign = Paint.Align.CENTER
        }

        val lines = wrapText(scene, caption, 900f)

        val box = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xEEFFFFFF.toInt()
        }

        canvas.drawRoundRect(
            55f, 70f, 1025f, 430f,
            35f, 35f, box
        )

        val lineHeight = 58f
        val startY = 220f -
            (lines.size - 1) * lineHeight / 2f

        lines.take(5).forEachIndexed { index, line ->
            canvas.drawText(
                line,
                540f,
                startY + index * lineHeight,
                caption
            )
        }

        val footer = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = 34f
            textAlign = Paint.Align.CENTER
            setShadowLayer(
                5f, 2f, 2f,
                0xFF000000.toInt()
            )
        }

        canvas.drawText(
            "STICKMAN • ESCENA $sceneNumber / $totalScenes",
            540f,
            1810f,
            footer
        )

        FileOutputStream(file).use {
            bitmap.compress(
                Bitmap.CompressFormat.PNG,
                90,
                it
            )
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
            status.text = "Montando video Stickman..."

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
                "stickman_${System.currentTimeMillis()}.mp4"
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
                                "Error al crear video: ${exportException.message}"
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
                        "Stickman_${System.currentTimeMillis()}.mp4"
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

                    put(
                        MediaStore.Video.Media.IS_PENDING,
                        1
                    )
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
                values.put(
                    MediaStore.Video.Media.IS_PENDING,
                    0
                )

                contentResolver.update(uri, values, null, null)

                status.text =
                    "✅ Video terminado. Está en Películas/AI Video Studio."

                createButton.isEnabled = true

            } catch (e: Exception) {
                fail(
                    "No se pudo guardar el video: ${e.message}"
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
