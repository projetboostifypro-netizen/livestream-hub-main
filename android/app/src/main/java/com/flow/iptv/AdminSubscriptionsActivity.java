package com.flow.iptv;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

public class AdminSubscriptionsActivity extends AppCompatActivity {
    private final List<JSONObject> items = new ArrayList<>();
    private RecyclerView list;
    private TextView status;
    private final SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
    private final SimpleDateFormat out = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE);

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_admin_subscriptions);
        status = findViewById(R.id.status);
        list = findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(new Adapter());
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        load();
    }

    private void load() {
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject().put("p_limit", 500);
                JSONArray arr = SupabaseClient.get(this).rpcArray("admin_list_subscriptions", body);
                items.clear();
                int active = 0;
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject t = arr.getJSONObject(i);
                    items.add(t);
                    if (t.optBoolean("is_active", false)) active++;
                }
                final int act = active;
                runOnUiThread(() -> {
                    status.setText(items.size() + " forfaits · " + act + " en cours");
                    list.getAdapter().notifyDataSetChanged();
                });
            } catch (Exception e) {
                runOnUiThread(() -> status.setText("Erreur : " + e.getMessage()));
            }
        }).start();
    }

    private String fmtDate(String iso) {
        if (iso == null || iso.isEmpty()) return "—";
        try {
            String s = iso.length() > 19 ? iso.substring(0, 19) : iso;
            Date d = in.parse(s);
            return out.format(d);
        } catch (Exception e) { return iso; }
    }

    class Adapter extends RecyclerView.Adapter<VH> {
        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int t) {
            int pad = (int)(12 * getResources().getDisplayMetrics().density);
            LinearLayout root = new LinearLayout(p.getContext());
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(pad, pad, pad, pad);
            root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);

            TextView l1 = new TextView(p.getContext());
            l1.setTextColor(0xFFFFFFFF); l1.setTextSize(15); l1.setLayoutParams(lp);
            TextView l2 = new TextView(p.getContext());
            l2.setTextColor(0xFFBBBBBB); l2.setTextSize(12); l2.setLayoutParams(lp);
            TextView l3 = new TextView(p.getContext());
            l3.setTextColor(0xFF888888); l3.setTextSize(11); l3.setLayoutParams(lp);

            root.addView(l1); root.addView(l2); root.addView(l3);
            View sep = new View(p.getContext());
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1);
            sp.topMargin = pad / 2;
            sep.setLayoutParams(sp); sep.setBackgroundColor(0xFF222222);
            root.addView(sep);

            VH h = new VH(root);
            h.l1 = l1; h.l2 = l2; h.l3 = l3;
            return h;
        }
        @Override public void onBindViewHolder(@NonNull VH h, int pos) {
            JSONObject t = items.get(pos);
            String email = t.optString("email", "(sans email)");
            String plan = t.optString("plan_name", "Plan #" + t.optLong("plan_id"));
            boolean active = t.optBoolean("is_active", false);
            String tag = active ? "● EN COURS" : "○ expiré";
            h.l1.setText(plan + "  ·  " + tag);
            h.l2.setText(email);
            h.l3.setText("Début : " + fmtDate(t.optString("starts_at", "")) +
                "   ·   Fin : " + fmtDate(t.optString("expires_at", "")));
            h.l1.setTextColor(active ? 0xFF4ADE80 : 0xFFFFFFFF);
        }
        @Override public int getItemCount() { return items.size(); }
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView l1, l2, l3;
        VH(View v) { super(v); }
    }
}