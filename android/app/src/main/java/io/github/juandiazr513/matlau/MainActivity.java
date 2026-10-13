package io.github.juandiazr513.matlau;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/**
 * MatLau para Android: muestra la app web incluida en el APK (funciona sin internet),
 * usa el motor de voz de Android y permite guardar/restaurar la copia de seguridad.
 */
public class MainActivity extends Activity {

    private static final String HOST = "appassets.androidplatform.net";
    private static final String START = "https://" + HOST + "/index.html";
    private static final int REQ_OPEN = 1;
    private static final int REQ_SAVE = 2;

    private WebView web;
    private TextToSpeech tts;
    private volatile boolean ttsReady = false;
    private volatile String voiceName = "";
    private ValueCallback<Uri[]> fileCallback;
    private String pendingSave;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setTextZoom(100);

        web.addJavascriptInterface(new Bridge(), "MatLauAndroid");
        web.setWebViewClient(new LocalClient());
        web.setWebChromeClient(new ChromeClient());
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);

        initTts();

        if (savedInstanceState != null) {
            web.restoreState(savedInstanceState);
        } else {
            web.loadUrl(START);
        }
    }

    /* ---------- servir los archivos de la app desde el APK ---------- */
    private class LocalClient extends WebViewClient {
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            Uri u = request.getUrl();
            if (!HOST.equals(u.getHost())) return null;
            String path = u.getPath();
            if (path == null || path.equals("/") || path.isEmpty()) path = "/index.html";
            String assetPath = "www" + path;
            try {
                InputStream in = getAssets().open(assetPath);
                String mime = mimeFor(path);
                String enc = mime.startsWith("text/") || mime.contains("json") || mime.contains("javascript") ? "utf-8" : null;
                return new WebResourceResponse(mime, enc, in);
            } catch (Exception e) {
                WebResourceResponse r = new WebResourceResponse("text/plain", "utf-8", null);
                r.setStatusCodeAndReasonPhrase(404, "Not Found");
                return r;
            }
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Uri u = request.getUrl();
            if (HOST.equals(u.getHost())) return false;
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, u));
            } catch (ActivityNotFoundException ignored) { }
            return true;
        }
    }

    private static String mimeFor(String path) {
        String p = path.toLowerCase(Locale.ROOT);
        if (p.endsWith(".html")) return "text/html";
        if (p.endsWith(".js")) return "application/javascript";
        if (p.endsWith(".css")) return "text/css";
        if (p.endsWith(".json") || p.endsWith(".webmanifest")) return "application/json";
        if (p.endsWith(".png")) return "image/png";
        if (p.endsWith(".svg")) return "image/svg+xml";
        if (p.endsWith(".woff2")) return "font/woff2";
        return "application/octet-stream";
    }

    /* ---------- elegir archivo (restaurar copia) ---------- */
    private class ChromeClient extends WebChromeClient {
        @Override
        public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
            if (fileCallback != null) fileCallback.onReceiveValue(null);
            fileCallback = callback;
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            try {
                startActivityForResult(Intent.createChooser(i, "Elegir copia de MatLau"), REQ_OPEN);
            } catch (ActivityNotFoundException e) {
                fileCallback = null;
                return false;
            }
            return true;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_OPEN) {
            Uri[] result = null;
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                result = new Uri[]{data.getData()};
            }
            if (fileCallback != null) fileCallback.onReceiveValue(result);
            fileCallback = null;
        } else if (requestCode == REQ_SAVE) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null && pendingSave != null) {
                try (OutputStream out = getContentResolver().openOutputStream(data.getData())) {
                    if (out != null) out.write(pendingSave.getBytes(StandardCharsets.UTF_8));
                    Toast.makeText(this, "Copia guardada", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(this, "No se pudo guardar la copia", Toast.LENGTH_LONG).show();
                }
            }
            pendingSave = null;
        }
    }

    /* ---------- voz ---------- */
    private void initTts() {
        tts = new TextToSpeech(this, status -> {
            if (status != TextToSpeech.SUCCESS) return;
            Locale[] prefs = {new Locale("es", "CL"), new Locale("es", "US"), new Locale("es", "MX"), new Locale("es", "ES"), new Locale("es")};
            for (Locale l : prefs) {
                int r = tts.setLanguage(l);
                if (r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED) {
                    ttsReady = true;
                    break;
                }
            }
            if (!ttsReady) return;
            pickBestVoice();
            Voice v = tts.getVoice();
            voiceName = v != null ? v.getName() + " (" + v.getLocale().toLanguageTag() + ")" : "Voz en español";
            runOnUiThread(() -> web.evaluateJavascript("window.__ttsReady&&window.__ttsReady()", null));
        });
    }

    private void pickBestVoice() {
        try {
            Set<Voice> voices = tts.getVoices();
            if (voices == null) return;
            Voice best = null;
            int bestScore = -1;
            for (Voice v : voices) {
                Locale l = v.getLocale();
                if (!"es".equals(l.getLanguage())) continue;
                Set<String> feats = v.getFeatures();
                if (feats != null && feats.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)) continue;
                int score = v.getQuality() / 100;            // calidad del motor (100-500)
                String c = l.getCountry();
                if ("CL".equals(c)) score += 4;
                else if ("US".equals(c) || "MX".equals(c) || "419".equals(c)) score += 3;
                else if ("ES".equals(c)) score += 1;
                if (v.isNetworkConnectionRequired()) score -= 2;  // preferir voces que funcionan sin internet
                if (score > bestScore) { bestScore = score; best = v; }
            }
            if (best != null) tts.setVoice(best);
        } catch (Exception ignored) { }
    }

    private class Bridge {
        @JavascriptInterface
        public boolean ttsReady() { return ttsReady; }

        @JavascriptInterface
        public String voiceName() { return voiceName; }

        @JavascriptInterface
        public void speak(String text, float rate) {
            if (!ttsReady || text == null) return;
            tts.setSpeechRate(Math.max(0.5f, Math.min(1.6f, rate)));
            tts.setPitch(1.05f);
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "matlau");
        }

        @JavascriptInterface
        public void stop() { if (tts != null) tts.stop(); }

        @JavascriptInterface
        public void openTtsSettings() {
            runOnUiThread(() -> {
                try {
                    Intent i = new Intent("com.android.settings.TTS_SETTINGS");
                    i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(i);
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Abra Ajustes › Accesibilidad › Salida de texto a voz", Toast.LENGTH_LONG).show();
                }
            });
        }

        @JavascriptInterface
        public void reloadVoice() {
            runOnUiThread(() -> {
                if (tts != null) tts.shutdown();
                ttsReady = false;
                initTts();
            });
        }

        @JavascriptInterface
        public void saveFile(String name, String content) {
            runOnUiThread(() -> {
                pendingSave = content;
                Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("application/json");
                i.putExtra(Intent.EXTRA_TITLE, name);
                try {
                    startActivityForResult(i, REQ_SAVE);
                } catch (ActivityNotFoundException e) {
                    pendingSave = null;
                    Toast.makeText(MainActivity.this, "No se encontró dónde guardar el archivo", Toast.LENGTH_LONG).show();
                }
            });
        }

        @JavascriptInterface
        public void keepScreenOn(boolean on) {
            runOnUiThread(() -> {
                if (on) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            });
        }
    }

    /* ---------- ciclo de vida y botón atrás ---------- */
    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        web.evaluateJavascript("(window.__androidBack&&window.__androidBack())?'1':'0'", value -> {
            if (value == null || !value.contains("1")) superBack();
        });
    }

    @SuppressWarnings("deprecation")
    private void superBack() { super.onBackPressed(); }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        web.saveState(outState);
    }

    @Override
    protected void onPause() {
        if (tts != null) tts.stop();
        web.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
    }

    @Override
    protected void onDestroy() {
        if (tts != null) tts.shutdown();
        web.destroy();
        super.onDestroy();
    }
}
