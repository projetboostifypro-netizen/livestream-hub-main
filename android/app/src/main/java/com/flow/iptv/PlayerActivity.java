package com.flow.iptv;

import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.VideoView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import java.util.Collections;
import java.util.List;

public class PlayerActivity extends AppCompatActivity {

    private VideoView videoView;
    private TextView chName, chEpg;
    private View topOverlay, bottomOverlay;
    private List<Channel> channels;
    private int index;
    private RecyclerView switcher;
    private volatile boolean expired = false;

    // Watchdog : détecte les flux bloqués (position ne progresse plus)
    private int lastPosition = -1;
    private int stallCount = 0;
    private int retryCount = 0;
    private String currentUrl = null;

    private final Handler ui = new Handler(Looper.getMainLooper());

    private final Runnable hideOverlays = () -> setOverlaysVisible(false);

    private final Runnable watchdog = new Runnable() {
        @Override public void run() {
            if (videoView == null || currentUrl == null) return;
            if (videoView.isPlaying()) {
                int pos = videoView.getCurrentPosition();
                if (pos == lastPosition) {
                    stallCount++;
                    // Après 2 contrôles consécutifs sans avancement (10s) → reconnecte
                    if (stallCount >= 2) {
                        stallCount = 0;
                        reconnect();
                        return;
                    }
                } else {
                    stallCount = 0;
                    retryCount = 0;
                }
                lastPosition = pos;
            }
            ui.postDelayed(this, 5_000);
        }
    };

    private final Runnable epgTick = new Runnable() {
        @Override public void run() {
            updateEpgLabel();
            ui.postDelayed(this, 30_000);
        }
    };

    private final Runnable subCheck = new Runnable() {
        @Override public void run() {
            checkSubscription();
            ui.postDelayed(this, 30_000);
        }
    };

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_player);

        videoView     = findViewById(R.id.player_view);
        chName        = findViewById(R.id.ch_name);
        chEpg         = findViewById(R.id.ch_epg);
        topOverlay    = findViewById(R.id.top_overlay);
        bottomOverlay = findViewById(R.id.bottom_overlay);

        videoView.setOnClickListener(v -> toggleOverlays());

        // MediaPlayer natif : gère le MPEG-TS live sans re-sync A/V agressive
        videoView.setOnPreparedListener(mp -> {
            // Désactiver le looping (flux live infini)
            mp.setLooping(false);
            // Démarrer immédiatement
            mp.start();
            retryCount = 0;
            stallCount = 0;
            lastPosition = -1;
            // Lancer le watchdog
            ui.removeCallbacks(watchdog);
            ui.postDelayed(watchdog, 5_000);
        });

        videoView.setOnErrorListener((mp, what, extra) -> {
            // Reconnexion sur erreur avec backoff exponentiel
            reconnect();
            return true; // consommé, pas de dialog Android
        });

        videoView.setOnInfoListener((mp, what, extra) -> {
            // MediaPlayer.MEDIA_INFO_BUFFERING_START = 701
            // MediaPlayer.MEDIA_INFO_BUFFERING_END   = 702
            // On ne fait rien de spécial — le MediaPlayer natif gère le buffer lui-même
            return false;
        });

        channels = ChannelHolder.get();
        index = getIntent().getIntExtra("index", -1);

        if (channels.isEmpty() || index < 0 || index >= channels.size()) {
            String url  = getIntent().getStringExtra("url");
            String name = getIntent().getStringExtra("name");
            if (url != null) {
                channels = Collections.singletonList(new Channel(name == null ? "" : name, "", url, null, null));
                index = 0;
            } else {
                finish();
                return;
            }
        }

        switcher = findViewById(R.id.switcher);
        switcher.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        switcher.setAdapter(new SwitcherAdapter());
        switcher.scrollToPosition(Math.max(0, index - 2));

        startPlayback(index);
        scheduleHide();
    }

    private void startPlayback(int newIndex) {
        if (newIndex < 0 || newIndex >= channels.size()) return;
        if (expired) { redirectToDashboard(); return; }
        int oldIndex = index;
        index = newIndex;
        retryCount = 0;
        stallCount = 0;
        lastPosition = -1;

        Channel c = channels.get(index);
        setTitle(c.name);
        chName.setText(c.name);
        updateEpgLabel();

        play(c.url);

        if (switcher != null && switcher.getAdapter() != null) {
            if (oldIndex >= 0 && oldIndex < channels.size()) switcher.getAdapter().notifyItemChanged(oldIndex);
            switcher.getAdapter().notifyItemChanged(index);
            switcher.smoothScrollToPosition(index);
        }
        scheduleHide();
        checkSubscription();
    }

    private void play(String url) {
        currentUrl = url;
        ui.removeCallbacks(watchdog);
        videoView.stopPlayback();
        videoView.setVideoURI(Uri.parse(url));
        videoView.start();
    }

    private void reconnect() {
        if (expired || currentUrl == null || isFinishing()) return;
        // Backoff exponentiel : 2s → 4s → 8s (max)
        long delay = Math.min(2_000L * (1L << Math.min(retryCount, 2)), 8_000L);
        retryCount++;
        ui.removeCallbacks(watchdog);
        ui.postDelayed(() -> {
            if (!isFinishing() && currentUrl != null && !expired) {
                play(currentUrl);
            }
        }, delay);
    }

    private void setOverlaysVisible(boolean v) {
        int vis = v ? View.VISIBLE : View.GONE;
        if (topOverlay != null)    topOverlay.setVisibility(vis);
        if (bottomOverlay != null) bottomOverlay.setVisibility(vis);
    }

    private void toggleOverlays() {
        boolean show = topOverlay.getVisibility() != View.VISIBLE;
        setOverlaysVisible(show);
        ui.removeCallbacks(hideOverlays);
        if (show) scheduleHide();
    }

    private void scheduleHide() {
        ui.removeCallbacks(hideOverlays);
        ui.postDelayed(hideOverlays, 4000);
    }

    private void updateEpgLabel() {
        if (channels == null || index < 0 || index >= channels.size()) return;
        Channel c = channels.get(index);
        String label = EPGStore.get().currentLabel(c.tvgId);
        if (label != null && !label.isEmpty()) chEpg.setText(label);
        else chEpg.setText(c.group == null ? "" : c.group);
    }

    @Override protected void onResume() {
        super.onResume();
        if (currentUrl != null && !videoView.isPlaying()) {
            play(currentUrl);
        }
        ui.postDelayed(epgTick, 30_000);
        checkSubscription();
        ui.postDelayed(subCheck, 30_000);
    }

    @Override protected void onPause() {
        super.onPause();
        videoView.pause();
        ui.removeCallbacks(epgTick);
        ui.removeCallbacks(subCheck);
        ui.removeCallbacks(watchdog);
    }

    @Override protected void onDestroy() {
        ui.removeCallbacks(hideOverlays);
        ui.removeCallbacks(epgTick);
        ui.removeCallbacks(subCheck);
        ui.removeCallbacks(watchdog);
        if (videoView != null) {
            videoView.stopPlayback();
        }
        super.onDestroy();
    }

    private void checkSubscription() {
        new Thread(() -> {
            try {
                org.json.JSONArray subs = SupabaseClient.get(this).rpcArray("get_active_subscription", null);
                boolean active = false;
                if (subs != null && subs.length() > 0) {
                    long sec = subs.getJSONObject(0).optLong("seconds_remaining", 0);
                    active = sec > 0;
                }
                if (!active) {
                    expired = true;
                    runOnUiThread(this::redirectToDashboard);
                }
            } catch (Exception ignored) {}
        }).start();
    }

    private void redirectToDashboard() {
        ui.removeCallbacks(subCheck);
        ui.removeCallbacks(epgTick);
        ui.removeCallbacks(watchdog);
        if (videoView != null) { try { videoView.stopPlayback(); } catch (Exception ignored) {} }
        if (isFinishing()) return;
        View expiredOverlay = findViewById(R.id.expired_overlay);
        if (expiredOverlay != null) {
            setOverlaysVisible(false);
            expiredOverlay.setVisibility(View.VISIBLE);
        }
        ui.postDelayed(() -> {
            if (isFinishing()) return;
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(i);
            finish();
        }, 2200);
    }

    // ---------- Switcher adapter ----------
    class SwitcherAdapter extends RecyclerView.Adapter<SVH> {
        @NonNull @Override
        public SVH onCreateViewHolder(@NonNull ViewGroup p, int t) {
            return new SVH(LayoutInflater.from(p.getContext()).inflate(R.layout.item_player_channel, p, false));
        }
        @Override public void onBindViewHolder(@NonNull SVH h, int pos) {
            Channel c = channels.get(pos);
            TextView name  = h.itemView.findViewById(R.id.p_name);
            TextView epg   = h.itemView.findViewById(R.id.p_epg);
            ImageView logo = h.itemView.findViewById(R.id.p_logo);
            name.setText(c.name);
            String label = EPGStore.get().currentLabel(c.tvgId);
            epg.setText(label != null ? label : (c.group == null ? "" : c.group));
            if (c.logo != null && !c.logo.isEmpty()) {
                logo.setVisibility(View.VISIBLE);
                Glide.with(PlayerActivity.this).load(c.logo).into(logo);
            } else { logo.setVisibility(View.GONE); logo.setImageDrawable(null); }
            h.itemView.setAlpha(pos == index ? 1f : 0.75f);
            h.itemView.setOnClickListener(v -> {
                int bp = h.getBindingAdapterPosition();
                if (bp != RecyclerView.NO_POSITION) startPlayback(bp);
            });
        }
        @Override public int getItemCount() { return channels == null ? 0 : channels.size(); }
    }
    static class SVH extends RecyclerView.ViewHolder { SVH(View v) { super(v); } }
}
