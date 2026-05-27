package com.flow.iptv;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONArray;
import org.json.JSONObject;

public class AdminPlaylistActivity extends AppCompatActivity {
    private EditText input;
    private TextView status;

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_admin_playlist);
        input = findViewById(R.id.playlist_url);
        status = findViewById(R.id.status);
        Switch toggle = findViewById(R.id.toggle_blocked);
        toggle.setVisibility(View.GONE);

        ((android.widget.Button) findViewById(R.id.btn_save)).setText("Ajouter au pool");
        ((android.widget.Button) findViewById(R.id.btn_delete)).setText("Supprimer ce lien du pool");
        ((android.widget.Button) findViewById(R.id.btn_sync)).setText("Rafraîchir la liste");

        findViewById(R.id.btn_save).setOnClickListener(v -> addLink());
        findViewById(R.id.btn_delete).setOnClickListener(v -> deleteByUrl());
        findViewById(R.id.btn_sync).setOnClickListener(v -> loadList());
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        loadList();
    }

    private void loadList() {
        status.setText("Chargement du pool…");
        new Thread(() -> {
            try {
                JSONArray a = SupabaseClient.get(this).rpcArray("admin_list_playlist_links", null);
                int total = a.length();
                int used = 0;
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < a.length(); i++) {
                    JSONObject l = a.getJSONObject(i);
                    boolean inUse = l.optBoolean("in_use", false);
                    if (inUse) used++;
                    sb.append(inUse ? "🔒 " : "✅ ").append(l.optString("url")).append("\n");
                }
                final String summary = "Pool : " + total + " liens, " + used
                    + " utilisés, " + (total - used) + " libres\n\n" + sb;
                runOnUiThread(() -> status.setText(summary));
            } catch (Exception e) {
                runOnUiThread(() -> status.setText("Erreur : " + e.getMessage()));
            }
        }).start();
    }

    private void addLink() {
        final String url = input.getText().toString().trim();
        if (url.isEmpty() || (!url.startsWith("http://") && !url.startsWith("https://"))) {
            Toast.makeText(this, "URL invalide", Toast.LENGTH_SHORT).show();
            return;
        }
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject().put("p_url", url);
                SupabaseClient.get(this).rpcArray("admin_add_playlist_link", body);
                runOnUiThread(() -> {
                    Toast.makeText(this, "Lien ajouté", Toast.LENGTH_SHORT).show();
                    input.setText("");
                    loadList();
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Erreur : " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void deleteByUrl() {
        final String url = input.getText().toString().trim();
        if (url.isEmpty()) { Toast.makeText(this, "Colle l'URL à supprimer", Toast.LENGTH_SHORT).show(); return; }
        new Thread(() -> {
            try {
                SupabaseClient c = SupabaseClient.get(this);
                JSONArray a = c.rpcArray("admin_list_playlist_links", null);
                String foundId = null;
                for (int i = 0; i < a.length(); i++) {
                    JSONObject l = a.getJSONObject(i);
                    if (url.equals(l.optString("url"))) { foundId = l.optString("id"); break; }
                }
                if (foundId == null) { runOnUiThread(() -> Toast.makeText(this, "Lien introuvable", Toast.LENGTH_SHORT).show()); return; }
                JSONObject body = new JSONObject().put("p_id", foundId);
                c.rpcArray("admin_delete_playlist_link", body);
                runOnUiThread(() -> {
                    Toast.makeText(this, "Lien supprimé", Toast.LENGTH_SHORT).show();
                    input.setText("");
                    loadList();
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Erreur : " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }
}