package com.flow.iptv;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONObject;

public class AdminApiKeyActivity extends AppCompatActivity {
    private EditText input;
    private TextView status;

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_admin_apikey);
        input = findViewById(R.id.api_key);
        status = findViewById(R.id.status);
        Button save = findViewById(R.id.btn_save);
        Button back = findViewById(R.id.btn_back);

        loadCurrent();
        save.setOnClickListener(v -> save());
        back.setOnClickListener(v -> finish());
    }

    private void loadCurrent() {
        status.setText("Chargement…");
        new Thread(() -> {
            try {
                org.json.JSONArray arr = SupabaseClient.get(this).selectTable(
                    "admin_settings", "select=value&key=eq.soleaspay_api_key");
                final String val = arr.length() > 0 ? arr.getJSONObject(0).optString("value", "") : "";
                runOnUiThread(() -> {
                    input.setText(val);
                    status.setText(val.isEmpty() ? "Aucune clé enregistrée" : "Clé actuelle chargée");
                });
            } catch (Exception e) {
                runOnUiThread(() -> status.setText("Erreur : " + e.getMessage()));
            }
        }).start();
    }

    private void save() {
        final String val = input.getText().toString().trim();
        if (val.isEmpty()) {
            Toast.makeText(this, "Clé vide", Toast.LENGTH_SHORT).show();
            return;
        }
        status.setText("Enregistrement…");
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject()
                    .put("p_key", "soleaspay_api_key")
                    .put("p_value", val);
                SupabaseClient.get(this).rpcArray("admin_set_setting", body);
                runOnUiThread(() -> {
                    Toast.makeText(this, "Clé API mise à jour", Toast.LENGTH_LONG).show();
                    status.setText("Enregistrée");
                });
            } catch (Exception e) {
                runOnUiThread(() -> status.setText("Erreur : " + e.getMessage()));
            }
        }).start();
    }
}