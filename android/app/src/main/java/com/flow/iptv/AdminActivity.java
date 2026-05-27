package com.flow.iptv;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

public class AdminActivity extends AppCompatActivity {
    private final List<JSONObject> users = new ArrayList<>();
    private final List<JSONObject> filtered = new ArrayList<>();
    private RecyclerView list;
    private TextView status;
    private String query = "";

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_admin);
        list = findViewById(R.id.users);
        status = findViewById(R.id.status);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(new UserAdapter());

        findViewById(R.id.btn_logout).setOnClickListener(v -> {
            SupabaseClient.get(this).signOut();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });

        findViewById(R.id.btn_menu).setOnClickListener(v -> {
            PopupMenu m = new PopupMenu(this, v);
            m.getMenu().add("Changer la clé API");
            m.getMenu().add("Modifier les plans");
            m.getMenu().add("Gérer la playlist");
            m.getMenu().add("VPS");
                m.getMenu().add("Historique des recharges");
            m.getMenu().add("Forfaits achetés");
            m.setOnMenuItemClickListener(it -> {
                String t = it.getTitle().toString();
                if ("Changer la clé API".equals(t)) {
                    startActivity(new Intent(this, AdminApiKeyActivity.class));
                } else if ("Modifier les plans".equals(t)) {
                    startActivity(new Intent(this, AdminPlansActivity.class));
                } else if ("Gérer la playlist".equals(t)) {
                    startActivity(new Intent(this, AdminPlaylistActivity.class));
                } else if ("VPS".equals(t)) {
                    startActivity(new Intent(this, AdminVpsActivity.class));
                    } else if ("Historique des recharges".equals(t)) {
                        startActivity(new Intent(this, AdminRechargesActivity.class));
                    } else if ("Forfaits achetés".equals(t)) {
                        startActivity(new Intent(this, AdminSubscriptionsActivity.class));
                    }
                return true;
            });
            m.show();
        });

        ((EditText) findViewById(R.id.search)).addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s,int a,int b,int c){}
            public void onTextChanged(CharSequence s,int a,int b,int c){
                query = s.toString().trim().toLowerCase(Locale.ROOT);
                applyFilter();
            }
            public void afterTextChanged(Editable e){}
        });

        loadUsers();
    }

    private void loadUsers() {
        status.setText("Chargement…");
        new Thread(() -> {
            try {
                JSONArray arr = SupabaseClient.get(this).rpcArray("admin_list_users", null);
                users.clear();
                for (int i = 0; i < arr.length(); i++) users.add(arr.getJSONObject(i));
                runOnUiThread(() -> {
                    status.setText(users.size() + " utilisateurs");
                    applyFilter();
                });
            } catch (Exception e) {
                runOnUiThread(() -> status.setText("Erreur : " + e.getMessage()));
            }
        }).start();
    }

    private void applyFilter() {
        filtered.clear();
        for (JSONObject u : users) {
            String em = u.optString("email", "").toLowerCase(Locale.ROOT);
            if (query.isEmpty() || em.contains(query)) filtered.add(u);
        }
        if (list.getAdapter() != null) list.getAdapter().notifyDataSetChanged();
    }

    private void runRpc(String fn, JSONObject body, String okMsg) {
        new Thread(() -> {
            try {
                SupabaseClient.get(this).rpcArray(fn, body);
                runOnUiThread(() -> {
                    Toast.makeText(this, okMsg, Toast.LENGTH_SHORT).show();
                    loadUsers();
                });
            } catch (Exception e) {
                runOnUiThread(() ->
                    Toast.makeText(this, "Erreur : " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void promptCredit(JSONObject u) {
        EditText delta = new EditText(this);
        delta.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
        delta.setHint("Ex: 500 ou -200");
        EditText reason = new EditText(this);
        reason.setHint("Motif (optionnel)");
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad, pad, 0);
        box.addView(delta);
        box.addView(reason);
        new AlertDialog.Builder(this)
            .setTitle("Ajuster pièces — " + u.optString("email"))
            .setView(box)
            .setPositiveButton("Valider", (d, w) -> {
                try {
                    int v = Integer.parseInt(delta.getText().toString().trim());
                    if (v == 0) return;
                    JSONObject body = new JSONObject();
                    body.put("p_user_id", u.optString("user_id"));
                    body.put("p_delta", v);
                    body.put("p_reason", reason.getText().toString());
                    runRpc("admin_adjust_coins", body, "Solde ajusté");
                } catch (Exception e) {
                    Toast.makeText(this, "Montant invalide", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("Annuler", null)
            .show();
    }

    private void promptBlock(JSONObject u) {
        boolean blocked = u.optBoolean("is_blocked", false);
        if (blocked) {
            try {
                JSONObject body = new JSONObject();
                body.put("p_user_id", u.optString("user_id"));
                runRpc("admin_unblock_user", body, "Débloqué");
            } catch (Exception ignored) {}
            return;
        }
        EditText reason = new EditText(this);
        reason.setHint("Motif (optionnel)");
        new AlertDialog.Builder(this)
            .setTitle("Bloquer " + u.optString("email"))
            .setView(reason)
            .setPositiveButton("Bloquer", (d, w) -> {
                try {
                    JSONObject body = new JSONObject();
                    body.put("p_user_id", u.optString("user_id"));
                    body.put("p_reason", reason.getText().toString());
                    runRpc("admin_block_user", body, "Bloqué");
                } catch (Exception ignored) {}
            })
            .setNegativeButton("Annuler", null)
            .show();
    }

    private void promptDelete(JSONObject u) {
        new AlertDialog.Builder(this)
            .setTitle("Supprimer le compte ?")
            .setMessage(u.optString("email") + "\n\nCette action est définitive.")
            .setPositiveButton("Supprimer", (d, w) -> {
                try {
                    JSONObject body = new JSONObject();
                    body.put("p_user_id", u.optString("user_id"));
                    runRpc("admin_delete_user", body, "Compte supprimé");
                } catch (Exception ignored) {}
            })
            .setNegativeButton("Annuler", null)
            .show();
    }

    class UserAdapter extends RecyclerView.Adapter<UserVH> {
        @NonNull @Override
        public UserVH onCreateViewHolder(@NonNull ViewGroup p, int t) {
            return new UserVH(LayoutInflater.from(p.getContext())
                .inflate(R.layout.item_admin_user, p, false));
        }
        @Override public void onBindViewHolder(@NonNull UserVH h, int pos) {
            JSONObject u = filtered.get(pos);
            TextView email = h.itemView.findViewById(R.id.email);
            TextView meta = h.itemView.findViewById(R.id.meta);
            Button credit = h.itemView.findViewById(R.id.btn_credit);
            Button block = h.itemView.findViewById(R.id.btn_block);
            Button delete = h.itemView.findViewById(R.id.btn_delete);

            boolean blocked = u.optBoolean("is_blocked", false);
            email.setText(u.optString("email", "(sans email)"));
            int coins = u.optInt("coins", 0);
            String session = u.optBoolean("has_active_session", false) ? " · session active" : "";
            String b = blocked ? " · BLOQUÉ" : "";
            meta.setText(coins + " FCFA" + session + b);

            block.setText(blocked ? "Débloquer" : "Bloquer");
            credit.setOnClickListener(v -> promptCredit(u));
            block.setOnClickListener(v -> promptBlock(u));
            delete.setOnClickListener(v -> promptDelete(u));
        }
        @Override public int getItemCount() { return filtered.size(); }
    }

    static class UserVH extends RecyclerView.ViewHolder {
        UserVH(View v) { super(v); }
    }
}