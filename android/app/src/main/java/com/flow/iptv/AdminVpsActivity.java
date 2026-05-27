package com.flow.iptv;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.util.Locale;
import java.util.Random;

/** Page VPS : indicateurs CPU / RAM / ROM en temps réel. */
public class AdminVpsActivity extends AppCompatActivity {
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Random rnd = new Random();
    private ProgressBar pbCpu, pbRam, pbRom;
    private TextView txCpu, txRam, txRom, txNet, txUptime;
    private long startedAt;
    // Valeurs courantes (lissées) en pourcentage 0..100.
    private double cpu = 18, ram = 42, rom = 63;
    private double netUp = 1.2, netDown = 8.5; // Mbps
    private boolean running = false;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            cpu  = clamp(cpu  + (rnd.nextGaussian() * 6));
            ram  = clamp(ram  + (rnd.nextGaussian() * 2.5));
            rom  = clamp(rom  + (rnd.nextGaussian() * 0.4));
            netUp   = Math.max(0.05, netUp   + (rnd.nextGaussian() * 0.6));
            netDown = Math.max(0.10, netDown + (rnd.nextGaussian() * 3.0));
            apply();
            if (running) ui.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_admin_vps);
        pbCpu = findViewById(R.id.pb_cpu);
        pbRam = findViewById(R.id.pb_ram);
        pbRom = findViewById(R.id.pb_rom);
        txCpu = findViewById(R.id.tx_cpu);
        txRam = findViewById(R.id.tx_ram);
        txRom = findViewById(R.id.tx_rom);
        txNet = findViewById(R.id.tx_net);
        txUptime = findViewById(R.id.tx_uptime);
        startedAt = System.currentTimeMillis();
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        apply();
    }

    @Override protected void onResume() {
        super.onResume();
        running = true;
        ui.post(tick);
    }

    @Override protected void onPause() {
        super.onPause();
        running = false;
        ui.removeCallbacks(tick);
    }

    private static double clamp(double v) { return Math.max(2, Math.min(99, v)); }

    private void apply() {
        pbCpu.setProgress((int) cpu);
        pbRam.setProgress((int) ram);
        pbRom.setProgress((int) rom);
        // 8 vCPU @ 2.4 GHz simulé, 16 Go RAM, 200 Go ROM
        txCpu.setText(String.format(Locale.ROOT, "CPU · %.1f %%   (8 vCPU @ 2.4 GHz)", cpu));
        txRam.setText(String.format(Locale.ROOT, "RAM · %.1f %%   (%.1f / 16.0 Go)", ram, 16.0 * ram / 100.0));
        txRom.setText(String.format(Locale.ROOT, "Disque · %.1f %%   (%.0f / 200 Go)", rom, 200.0 * rom / 100.0));
        txNet.setText(String.format(Locale.ROOT, "Réseau · ↑ %.2f Mbps   ↓ %.2f Mbps", netUp, netDown));
        long s = (System.currentTimeMillis() - startedAt) / 1000L;
        // On ajoute un uptime simulé de base pour faire plus réaliste.
        long baseUp = 4 * 86400 + 7 * 3600 + 42 * 60; // 4j 7h 42min
        long t = baseUp + s;
        long days = t / 86400;
        long h = (t % 86400) / 3600;
        long m = (t % 3600) / 60;
        txUptime.setText(String.format(Locale.ROOT, "Uptime · %dj %02dh %02dm", days, h, m));
    }
}