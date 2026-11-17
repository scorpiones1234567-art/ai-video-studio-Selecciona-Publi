package com.aivideostudio;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.widget.*;
import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends Activity {
    private final ArrayList<Uri> media = new ArrayList<>();
    private TextView mediaInfo, status;
    private EditText script;
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private static final int PICK_MEDIA = 41;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 28, 24, 24);
        root.setBackgroundColor(0xFFF4F7FB);

        TextView title = new TextView(this);
        title.setText("🎬 AI VIDEO STUDIO");
        title.setTextSize(25);
        title.setTextColor(0xFF172B4D);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 12, 0, 20);
        root.addView(title);

        TextView intro = new TextView(this);
        intro.setText("Crea la base de tus videos desde Android. Agrega imágenes o videos, escribe tu guion y escucha la narración.");
        intro.setTextSize(16);
        intro.setTextColor(0xFF334155);
        root.addView(intro);

        script = new EditText(this);
        script.setHint("Escribe o pega aquí tu guion...");
        script.setMinLines(4);
        script.setGravity(Gravity.TOP);
        script.setTextSize(16);
        root.addView(script, new LinearLayout.LayoutParams(-1, 0, 1));

        Button add = makeButton("＋ Agregar imágenes o videos");
        root.addView(add);
        add.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.setType("*/*");
            i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*", "video/mp4"});
            i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(i, PICK_MEDIA);
        });

        mediaInfo = new TextView(this);
        mediaInfo.setText("Todavía no has agregado archivos.");
        mediaInfo.setTextColor(0xFF475569);
        mediaInfo.setPadding(0, 12, 0, 12);
        root.addView(mediaInfo);

        Button speak = makeButton("🔊 Escuchar narración");
        root.addView(speak);
        speak.setOnClickListener(v -> {
            String text = script.getText().toString().trim();
            if (text.isEmpty()) status.setText("Primero escribe un guion.");
            else if (ttsReady) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "script");
            else status.setText("La voz todavía se está preparando.");
        });

        Button share = makeButton("📤 Compartir guion");
        root.addView(share);
        share.setOnClickListener(v -> {
            String text = script.getText().toString().trim();
            if (text.isEmpty()) { status.setText("Escribe algo antes de compartir."); return; }
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");
            send.putExtra(Intent.EXTRA_TEXT, text);
            startActivity(Intent.createChooser(send, "Compartir guion"));
        });

        status = new TextView(this);
        status.setText("Primera versión de prueba.");
        status.setTextColor(0xFF0F766E);
        status.setPadding(0, 18, 0, 0);
        root.addView(status);
        setContentView(root);

        tts = new TextToSpeech(this, result -> {
            if (result == TextToSpeech.SUCCESS) {
                ttsReady = true;
                tts.setLanguage(new Locale("es", "MX"));
            }
        });
    }

    private Button makeButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        return b;
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_MEDIA && resultCode == RESULT_OK && data != null) {
            if (data.getClipData() != null) {
                for (int n = 0; n < data.getClipData().getItemCount(); n++)
                    addUri(data.getClipData().getItemAt(n).getUri());
            } else if (data.getData() != null) addUri(data.getData());
            mediaInfo.setText("Archivos seleccionados: " + media.size());
            status.setText("Archivos agregados. La exportación de video se añadirá en una siguiente versión.");
        }
    }

    private void addUri(Uri uri) {
        if (!media.contains(uri)) {
            media.add(uri);
            try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); }
            catch (Exception ignored) {}
        }
    }

    @Override protected void onDestroy() {
        if (tts != null) { tts.stop(); tts.shutdown(); }
        super.onDestroy();
    }
}
