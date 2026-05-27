package com.flow.iptv;

import android.content.Context;
import android.content.Intent;
import android.annotation.SuppressLint;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.webkit.WebView;
import android.webkit.WebSettings;
import android.webkit.WebChromeClient;
import android.webkit.WebViewClient;
import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.util.UnstableApi;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import java.util.List;

@OptIn(markerClass = UnstableApi.class)
public class PlayerActivity extends AppCompatActivity {
    private WebView webView;
    private boolean webReady = false;
    private String pendingPlay = null;
    private TextView chName, chEpg;
    private View topOverlay, bottomOverlay;
    private List<Channel> channels;
    private int index;
    private RecyclerView switcher;
    private volatile boolean expired = false;
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
    @SuppressLint("SetJavaScriptEnabled")
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_player);
        webView = findViewById(R.id.player_web);
        chName = findViewById(R.id.ch_name);
        chEpg = findViewById(R.id.ch_epg);
        topOverlay = findViewById(R.id.top_overlay);
        bottomOverlay = findViewById(R.id.bottom_overlay);
        // Accélération hardware : décode H.264/H.265 via GPU → pas de freeze
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setMediaPlaybackRequiresUserGesture(false);
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        ws.setAllowFileAccess(true);
        ws.setAllowContentAccess(true);
        // Désactiver le cache : garantit que player.html et mpegts.js sont
        // toujours chargés depuis les assets (jamais une ancienne version en cache)
        ws.setCacheMode(WebSettings.LOAD_NO_CACHE);
        webView.setBackgroundColor(0xFF000000);
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView v, String u) {
                webReady = true;
                if (pendingPlay != null) { v.evaluateJavascript(pendingPlay, null); pendingPlay = null; }
            }
        });
        webView.setOnClickListener(v -> toggleOverlays());
        webView.loadUrl("file:///android_asset/player.html");

        channels = ChannelHolder.get();
        index = getIntent().getIntExtra("index", -1);

        // Backwards-compat: legacy launches via extras.
        if (channels.isEmpty() || index < 0 || index >= channels.size()) {
            String url = getIntent().getStringExtra("url");
            String name = getIntent().getStringExtra("name");
            if (url != null) {
                channels = java.util.Collections.singletonList(new Channel(name == null ? "" : name, "", url, null, null));
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

    private void setOverlaysVisible(boolean v) {
        int vis = v ? View.VISIBLE : View.GONE;
        if (topOverlay != null) topOverlay.setVisibility(vis);
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

    private void startPlayback(int newIndex) {
        if (newIndex < 0 || newIndex >= channels.size()) return;
        if (expired) { redirectToDashboard(); return; }
        int oldIndex = index;
        index = newIndex;
        Channel c = channels.get(index);
        setTitle(c.name);
        chName.setText(c.name);
        updateEpgLabel();

        String js = "window.IPTV && window.IPTV.play("
            + jsStr(c.url) + "," + jsStr(c.name) + "," + jsStr(c.logo) + ");";
        if (webReady) webView.evaluateJavascript(js, null);
        else pendingPlay = js;

        if (switcher != null && switcher.getAdapter() != null) {
            if (oldIndex >= 0 && oldIndex < channels.size()) switcher.getAdapter().notifyItemChanged(oldIndex);
            switcher.getAdapter().notifyItemChanged(index);
            switcher.smoothScrollToPosition(index);
        }
        scheduleHide();
        // Vérifie l'abonnement immédiatement à chaque changement de chaîne
        checkSubscription();
    }

    private static String jsStr(String s) {
        if (s == null) return "''";
        StringBuilder sb = new StringBuilder("'");
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\\' || ch == '\'' ) sb.append('\\').append(ch);
            else if (ch == '\n') sb.append("\\n");
            else if (ch == '\r') sb.append("\\r");
            else if (ch < 0x20) sb.append(String.format("\\u%04x", (int) ch));
            else sb.append(ch);
        }
        sb.append('\'');
        return sb.toString();
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
        ui.postDelayed(epgTick, 30_000);
        // Vérifie immédiatement, puis périodiquement
        checkSubscription();
        ui.postDelayed(subCheck, 30_000);
    }

    @Override protected void onPause() {
        super.onPause();
        ui.removeCallbacks(epgTick);
        ui.removeCallbacks(subCheck);
    }

    @Override protected void onStop() {
        super.onStop();
        if (webView != null) {
            try { webView.evaluateJavascript("window.IPTV && window.IPTV.stop();", null); } catch (Exception ignored) {}
            webView.onPause();
        }
    }

    @Override protected void onDestroy() {
        if (webView != null) {
            try { webView.loadUrl("about:blank"); webView.removeAllViews(); webView.destroy(); } catch (Exception ignored) {}
            webView = null;
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
        try {
            if (webView != null) { webView.evaluateJavascript("window.IPTV && window.IPTV.stop();", null); }
        } catch (Exception ignored) {}
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
            TextView epg = h.itemView.findViewById(R.id.p_epg);
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
