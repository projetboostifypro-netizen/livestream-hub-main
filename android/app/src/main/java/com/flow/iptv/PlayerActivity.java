package com.flow.iptv;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import java.util.Collections;
import java.util.List;

@OptIn(markerClass = UnstableApi.class)
public class PlayerActivity extends AppCompatActivity {

    private ExoPlayer player;
    private PlayerView playerView;
    private TextView chName, chEpg;
    private View topOverlay, bottomOverlay;
    private List<Channel> channels;
    private int index;
    private RecyclerView switcher;
    private volatile boolean expired = false;
    private int retryCount = 0;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Runnable hideOverlays = () -> setOverlaysVisible(false);
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

        playerView = findViewById(R.id.player_view);
        chName     = findViewById(R.id.ch_name);
        chEpg      = findViewById(R.id.ch_epg);
        topOverlay = findViewById(R.id.top_overlay);
        bottomOverlay = findViewById(R.id.bottom_overlay);

        buildPlayer();

        playerView.setOnClickListener(v -> toggleOverlays());

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

    private void buildPlayer() {
        // Buffer réduit pour IPTV live : démarre dès 1s en mémoire, max 8s
        // Le buffer par défaut (50s) était la cause du "Mise en mémoire..." prolongé
        DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                        3_000,   // minBufferMs : tampon minimum maintenu
                        10_000,  // maxBufferMs : tampon maximum
                        1_000,   // bufferForPlaybackMs : démarre à 1s
                        2_000    // bufferForPlaybackAfterRebufferMs
                )
                .build();

        DefaultHttpDataSource.Factory httpFactory = new DefaultHttpDataSource.Factory()
                .setConnectTimeoutMs(10_000)
                .setReadTimeoutMs(15_000)
                .setAllowCrossProtocolRedirects(true);

        player = new ExoPlayer.Builder(this)
                .setLoadControl(loadControl)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(httpFactory))
                .build();

        player.addListener(new Player.Listener() {
            @Override
            public void onPlayerError(@NonNull PlaybackException e) {
                if (expired) return;
                // Retry exponentiel : 2s, 4s, 8s, 8s max
                long delay = Math.min(2_000L * (1L << Math.min(retryCount, 2)), 8_000L);
                retryCount++;
                ui.postDelayed(() -> {
                    if (!isFinishing() && player != null) {
                        player.prepare();
                        player.play();
                    }
                }, delay);
            }

            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_READY) {
                    retryCount = 0;
                }
            }
        });

        playerView.setPlayer(player);
    }

    private void startPlayback(int newIndex) {
        if (newIndex < 0 || newIndex >= channels.size()) return;
        if (expired) { redirectToDashboard(); return; }
        int oldIndex = index;
        index = newIndex;
        retryCount = 0;

        Channel c = channels.get(index);
        setTitle(c.name);
        chName.setText(c.name);
        updateEpgLabel();

        if (player != null) {
            player.stop();
            player.setMediaItem(MediaItem.fromUri(c.url));
            player.prepare();
            player.setPlayWhenReady(true);
        }

        if (switcher != null && switcher.getAdapter() != null) {
            if (oldIndex >= 0 && oldIndex < channels.size()) switcher.getAdapter().notifyItemChanged(oldIndex);
            switcher.getAdapter().notifyItemChanged(index);
            switcher.smoothScrollToPosition(index);
        }
        scheduleHide();
        checkSubscription();
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
        if (player != null) player.setPlayWhenReady(true);
        ui.postDelayed(epgTick, 30_000);
        checkSubscription();
        ui.postDelayed(subCheck, 30_000);
    }

    @Override protected void onPause() {
        super.onPause();
        if (player != null) player.setPlayWhenReady(false);
        ui.removeCallbacks(epgTick);
        ui.removeCallbacks(subCheck);
    }

    @Override protected void onDestroy() {
        ui.removeCallbacks(hideOverlays);
        ui.removeCallbacks(epgTick);
        ui.removeCallbacks(subCheck);
        if (player != null) {
            player.release();
            player = null;
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
        if (player != null) { try { player.stop(); } catch (Exception ignored) {} }
        ui.removeCallbacks(subCheck);
        ui.removeCallbacks(epgTick);
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
            TextView name = h.itemView.findViewById(R.id.p_name);
            TextView epg  = h.itemView.findViewById(R.id.p_epg);
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
