package com.flow.iptv;

import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

public class AdminPlansActivity extends AppCompatActivity {
    private final List<JSONObject> plans = new ArrayList<>();
    private RecyclerView list;
    private TextView status;

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_admin_plans);
        list = findViewById(R.id.plans);
        status = findViewById(R.id.status);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(new PlanAdapter());

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_add).setOnClickListener(v -> editDialog(null));
        load();
    }

    private void load() {
        status.setText("Chargement…");
        new Thread(() -> {
            try {
                JSONArray arr = SupabaseClient.get(this).rpcArray("admin_list_plans", null);
                plans.clear();
                for (int i = 0; i < arr.length(); i++) plans.add(arr.getJSONObject(i));
                runOnUiThread(() -> {
                    status.setText(plans.size() + " plans");
                    if (list.getAdapter() != null) list.getAdapter().notifyDataSetChanged();
                });
            } catch (Exception e) {
                runOnUiThread(() -> status.setText("Erreur : " + e.getMessage()));
            }
        }).start();
    }

    private EditText field(String hint, String value, int type) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setInputType(type);
        if (value != null) e.setText(value);
        return e;
    }

    private void editDialog(JSONObject existing) {
        boolean isNew = existing == null;
        EditText name = field("Nom (ex: 1 Mois)", isNew ? "" : existing.optString("name"), InputType.TYPE_CLASS_TEXT);
        EditText mins = field("Durée (minutes)", isNew ? "" : String.valueOf(existing.optInt("duration_minutes")), InputType.TYPE_CLASS_NUMBER);
        EditText price = field("Prix (FCFA)", isNew ? "" : String.valueOf(existing.optInt("price_coins")), InputType.TYPE_CLASS_NUMBER);
        EditText order = field("Ordre d'affichage", isNew ? "" : String.valueOf(existing.optInt("sort_order")), InputType.TYPE_CLASS_NUMBER);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad, pad, 0);
        box.addView(name); box.addView(mins); box.addView(price); box.addView(order);

        new AlertDialog.Builder(this)
            .setTitle(isNew ? "Nouveau plan" : "Modifier le plan")
            .setView(box)
            .setPositiveButton("Enregistrer", (d, w) -> {
                try {
                    JSONObject body = new JSONObject();
                    body.put("p_id", isNew ? JSONObject.NULL : existing.optLong("id"));
                    body.put("p_name", name.getText().toString().trim());
                    body.put("p_duration_minutes", Integer.parseInt(mins.getText().toString().trim()));
                    body.put("p_price_coins", Integer.parseInt(price.getText().toString().trim()));
                    String o = order.getText().toString().trim();
                    body.put("p_sort_order", o.isEmpty() ? 0 : Integer.parseInt(o));
                    runRpc("admin_upsert_plan", body, "Plan enregistré");
                } catch (Exception e) {
                    Toast.makeText(this, "Champs invalides", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("Annuler", null)
            .show();
    }

    private void deleteDialog(JSONObject p) {
        new AlertDialog.Builder(this)
            .setTitle("Supprimer ce plan ?")
            .setMessage(p.optString("name"))
            .setPositiveButton("Supprimer", (d, w) -> {
                try {
                    JSONObject body = new JSONObject().put("p_id", p.optLong("id"));
                    runRpc("admin_delete_plan", body, "Plan supprimé");
                } catch (Exception ignored) {}
            })
            .setNegativeButton("Annuler", null)
            .show();
    }

    private void runRpc(String fn, JSONObject body, String okMsg) {
        new Thread(() -> {
            try {
                SupabaseClient.get(this).rpcArray(fn, body);
                runOnUiThread(() -> {
                    Toast.makeText(this, okMsg, Toast.LENGTH_SHORT).show();
                    load();
                });
            } catch (Exception e) {
                runOnUiThread(() ->
                    Toast.makeText(this, "Erreur : " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    class PlanAdapter extends RecyclerView.Adapter<PlanVH> {
        @NonNull @Override
        public PlanVH onCreateViewHolder(@NonNull ViewGroup p, int t) {
            return new PlanVH(LayoutInflater.from(p.getContext())
                .inflate(R.layout.item_admin_plan, p, false));
        }
        @Override public void onBindViewHolder(@NonNull PlanVH h, int pos) {
            JSONObject p = plans.get(pos);
            ((TextView) h.itemView.findViewById(R.id.name)).setText(p.optString("name"));
            ((TextView) h.itemView.findViewById(R.id.meta))
                .setText(p.optInt("duration_minutes") + " min · " + p.optInt("price_coins") + " FCFA");
            h.itemView.findViewById(R.id.btn_edit).setOnClickListener(v -> editDialog(p));
            h.itemView.findViewById(R.id.btn_delete).setOnClickListener(v -> deleteDialog(p));
        }
        @Override public int getItemCount() { return plans.size(); }
    }

    static class PlanVH extends RecyclerView.ViewHolder {
        PlanVH(View v) { super(v); }
    }
}