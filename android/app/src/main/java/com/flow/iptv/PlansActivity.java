package com.flow.iptv;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

public class PlansActivity extends AppCompatActivity {
    private LinearLayout container;
    private TextView coinsView, status;

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_plans);
        container = findViewById(R.id.plans);
        coinsView = findViewById(R.id.coins);
        status = findViewById(R.id.status);
        loadAll();
    }

    private void loadAll() {
        status.setText("Chargement des abonnements…");
        new Thread(() -> {
            try {
                SupabaseClient c = SupabaseClient.get(this);
                JSONArray plans = c.selectTable("subscription_plans",
                    "select=id,name,duration_minutes,price_coins,sort_order,is_popular&order=sort_order.asc");
                JSONArray prof = c.selectTable("profiles",
                    "select=coins&user_id=eq." + c.userId());
                int coins = prof.length() > 0 ? prof.getJSONObject(0).optInt("coins", 0) : 0;
                runOnUiThread(() -> render(plans, coins));
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() ->
                    status.setText("Erreur : " + e.getMessage()));
            }
        }).start();
    }

    private void render(JSONArray plans, int coins) {
        coinsView.setText("Solde : " + coins + " FCFA");
        status.setText("Choisissez un plan");
        container.removeAllViews();
        for (int i = 0; i < plans.length(); i++) {
            JSONObject p = plans.optJSONObject(i);
            if (p == null) continue;
            View row = LayoutInflater.from(this).inflate(R.layout.item_plan, container, false);
            String name = p.optString("name");
            String trimmed = name.trim().toLowerCase(Locale.ROOT);
            if (trimmed.equals("3 mois")) name = name + " ⭐⭐";
            else if (trimmed.equals("1 mois")) name = name + " ⭐";
            ((TextView) row.findViewById(R.id.name)).setText(name);
            ((TextView) row.findViewById(R.id.price)).setText(p.optInt("price_coins") + " FCFA");
            Button buy = row.findViewById(R.id.buy);
            long planId = p.optLong("id");
            int price = p.optInt("price_coins");
            buy.setOnClickListener(v -> purchase(planId, price > coins));
            container.addView(row);
        }
    }

    private void purchase(long planId, boolean insufficient) {
        if (insufficient) {
            Toast.makeText(this, "Solde insuffisant. Achetez des pièces.", Toast.LENGTH_LONG).show();
            startActivity(new android.content.Intent(this, CoinsActivity.class));
            return;
        }
        status.setText("Achat en cours…");
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject().put("p_plan_id", planId);
                JSONArray res = SupabaseClient.get(this).rpcArray("purchase_plan", body);
                runOnUiThread(() -> {
                    Toast.makeText(this, "Abonnement activé !", Toast.LENGTH_LONG).show();
                    finish();
                });
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    status.setText("Échec : " + e.getMessage());
                    if (e.getMessage() != null && e.getMessage().contains("insufficient_coins")) {
                        startActivity(new android.content.Intent(this, CoinsActivity.class));
                    }
                });
            }
        }).start();
    }
}