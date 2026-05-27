package com.flow.iptv;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class TestDebitActivity extends AppCompatActivity {
    // Fichier de test (Cloudflare speed) — taille configurable via query string.
    private static final String DOWNLOAD_URL = "https://speed.cloudflare.com/__down?bytes=";
    private static final long TEST_BYTES = 25L * 1024 * 1024; // 25 MB
    private static final int TEST_DURATION_MS = 12000;

    private TextView speed, status, result, log, summary;
    private ProgressBar progress;
    private Button run;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final StringBuilder logBuf = new StringBuilder();
    private volatile boolean running = false;

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_test_debit);
        speed = findViewById(R.id.speed);
        status = findViewById(R.id.status);
        result = findViewById(R.id.result);
        log = findViewById(R.id.log);
        summary = findViewById(R.id.summary);
        progress = findViewById(R.id.progress);
        run = findViewById(R.id.run);
        run.setOnClickListener(v -> startTest());
    }

    @Override protected void onDestroy() { running = false; super.onDestroy(); }

    private void appendLog(String tag, String text) {
        logBuf.append("[").append(tag).append("] ").append(text).append("\n");
        ui.post(() -> log.setText(logBuf.toString()));
    }

    private void setResult(String s, boolean ok) {
        ui.post(() -> {
            result.setText(s);
            result.setTextColor(ok ? 0xFF4ADE80 : 0xFFFF6B6B);
        });
    }

    private static String formatSpeed(double bps) {
        double mbps = bps / 1_000_000.0;
        if (mbps >= 1) return String.format("%.2f Mbps", mbps);
        return String.format("%.0f Kbps", bps / 1000.0);
    }

    private void startTest() {
        if (running) return;
        running = true;
        logBuf.setLength(0);
        ui.post(() -> {
            run.setEnabled(false);
            speed.setText("0.00 Mbps");
            status.setText("Connexion au serveur…");
            result.setText("Test en cours…");
            result.setTextColor(0xFFFFFFFF);
            progress.setProgress(0);
            summary.setText("Résumé : —");
        });

        new Thread(() -> {
            HttpURLConnection conn = null;
            InputStream is = null;
            long totalBytes = 0;
            long start = 0;
            try {
                String url = DOWNLOAD_URL + TEST_BYTES;
                appendLog("GET", url);
                conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(15000);
                conn.setRequestProperty("Cache-Control", "no-cache");
                int code = conn.getResponseCode();
                appendLog("HTTP", String.valueOf(code));
                if (code < 200 || code >= 300) throw new Exception("HTTP " + code);

                is = conn.getInputStream();
                byte[] buf = new byte[16 * 1024];
                start = System.nanoTime();
                long lastTick = start;
                int n;
                while (running && (n = is.read(buf)) != -1) {
                    totalBytes += n;
                    long now = System.nanoTime();
                    long elapsedMs = (now - start) / 1_000_000L;
                    if (now - lastTick > 200_000_000L) {
                        lastTick = now;
                        double bps = (totalBytes * 8.0) / ((now - start) / 1e9);
                        final String spd = formatSpeed(bps);
                        final int pct = (int) Math.min(100, (elapsedMs * 100) / TEST_DURATION_MS);
                        final long mb = totalBytes / (1024 * 1024);
                        ui.post(() -> {
                            speed.setText(spd);
                            status.setText(mb + " Mo téléchargés · " + (elapsedMs / 1000) + "s");
                            progress.setProgress(pct);
                        });
                    }
                    if (elapsedMs > TEST_DURATION_MS) break;
                }
                long end = System.nanoTime();
                double seconds = start == 0 ? 0 : (end - start) / 1e9;
                double bps = seconds > 0 ? (totalBytes * 8.0) / seconds : 0;
                final String finalSpd = formatSpeed(bps);
                final long finalBytes = totalBytes;
                final double finalSeconds = seconds;
                appendLog("DONE", totalBytes + " octets en " + String.format("%.2f", seconds) + "s");
                appendLog("DÉBIT", finalSpd);
                ui.post(() -> {
                    speed.setText(finalSpd);
                    status.setText("Terminé · " + (finalBytes / (1024 * 1024)) + " Mo en " + String.format("%.1f", finalSeconds) + "s");
                    progress.setProgress(100);
                    double mbps = finalSeconds > 0 ? (finalBytes * 8.0) / finalSeconds / 1_000_000.0 : 0;
                    summary.setText(
                        "Résumé\n" +
                        "  Octets   : " + finalBytes + " (" + (finalBytes / (1024 * 1024)) + " Mo)\n" +
                        "  Durée    : " + String.format("%.2f", finalSeconds) + " s\n" +
                        "  Vitesse  : " + String.format("%.2f", mbps) + " Mbps");
                });
                setResult("✅ Test réussi — " + finalSpd, true);
            } catch (Exception e) {
                String msg = e.getMessage() == null ? "inconnu" : e.getMessage();
                appendLog("ERROR", msg);
                final long fb = totalBytes;
                final double fs = start == 0 ? 0 : (System.nanoTime() - start) / 1e9;
                final double fmbps = fs > 0 ? (fb * 8.0) / fs / 1_000_000.0 : 0;
                ui.post(() -> summary.setText(
                    "Résumé (échec)\n" +
                    "  Octets   : " + fb + " (" + (fb / (1024 * 1024)) + " Mo)\n" +
                    "  Durée    : " + String.format("%.2f", fs) + " s\n" +
                    "  Vitesse  : " + String.format("%.2f", fmbps) + " Mbps\n" +
                    "  Erreur   : " + msg));
                setResult("❌ Échec : " + msg, false);
            } finally {
                try { if (is != null) is.close(); } catch (Exception ignored) {}
                if (conn != null) conn.disconnect();
                running = false;
                ui.post(() -> run.setEnabled(true));
            }
        }).start();
    }
}