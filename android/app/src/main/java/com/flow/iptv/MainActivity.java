package com.flow.iptv;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends AppCompatActivity {
    private static final String CHIP_ALL = "Tout";

    private final List<Channel> all = new ArrayList<>();
    private final List<String> topGroups = new ArrayList<>();
    // Ordre préféré des préfixes de catégories (premier token du group_title).
    private static final String[] GROUP_PREFIX_ORDER = {
        "ALL", "FR", "BE", "AF", "CAR", "UK", "US", "CA", "DE",
        "IT", "ES", "LAT", "AR", "ARG", "BR", "CH", "TR", "IN", "PL"
    };
    private TextView status, accountStatus;
    private LinearLayout chipsBar;
    private RecyclerView list;
    private String currentQuery = "";
    private String currentGroup = CHIP_ALL;
    private Channel featured;

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        if (!SupabaseClient.get(this).isLoggedIn()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        setContentView(R.layout.activity_main);
        status = findViewById(R.id.status);
        accountStatus = findViewById(R.id.account_status);
        chipsBar = findViewById(R.id.chips);
        list = findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(this));

        findViewById(R.id.btn_menu).setOnClickListener(v -> {
            PopupMenu m = new PopupMenu(this, v);
            m.getMenu().add("Profil");
            m.getMenu().add("Acheter un abonnement");
            m.getMenu().add("Recharger mon compte");
            m.getMenu().add("Test débit");
            m.getMenu().add("Déconnexion");
            m.setOnMenuItemClickListener(it -> {
                String t = it.getTitle().toString();
                if ("Profil".equals(t)) showProfileDialog();
                else if ("Acheter un abonnement".equals(t)) startActivity(new Intent(this, PlansActivity.class));
                else if ("Recharger mon compte".equals(t)) startActivity(new Intent(this, CoinsActivity.class));
                else if ("Test débit".equals(t)) {
                    startActivity(new Intent(this, TestDebitActivity.class));
                } else {
                    SupabaseClient.get(this).signOut();
                    startActivity(new Intent(this, LoginActivity.class));
                    finish();
                }
                return true;
            });
            m.show();
        });

        EditText search = findViewById(R.id.search);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s,int a,int b,int c){}
            public void onTextChanged(CharSequence s,int a,int b,int c){ currentQuery = s.toString(); render(); }
            public void afterTextChanged(Editable e){}
        });

        String cached = PlaylistParser.readCache(getFilesDir());
        if (cached != null) {
            ingest(PlaylistParser.parse(cached));
            status.setText(all.size() + " chaînes (cache) · mise à jour…");
        }
        loadPlaylist(cached == null);
        refreshAccountStatus();
        EPGStore.get().loadAsync(this, () -> runOnUiThread(() -> {
            if (list.getAdapter() != null) list.getAdapter().notifyDataSetChanged();
        }));
    }

    private void showProfileDialog() {
        SupabaseClient c = SupabaseClient.get(this);
        String info = accountStatus.getText().toString().isEmpty()
            ? c.userEmail() : accountStatus.getText().toString();
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Mon profil")
            .setMessage(info)
            .setPositiveButton("Changer mon mot de passe", (d, w) -> showChangePasswordDialog())
            .setNegativeButton("Fermer", null)
            .setNeutralButton("Déconnexion", (d, w) -> {
                c.signOut();
                startActivity(new Intent(this, LoginActivity.class));
                finish();
            })
            .show();
    }

    private void showChangePasswordDialog() {
        final SupabaseClient c = SupabaseClient.get(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad, pad, 0);
        final EditText p1 = new EditText(this);
        p1.setHint("Nouveau mot de passe (min 6)");
        p1.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        final EditText p2 = new EditText(this);
        p2.setHint("Confirmer le mot de passe");
        p2.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        box.addView(p1);
        box.addView(p2);
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Changer mon mot de passe")
            .setView(box)
            .setPositiveButton("Enregistrer", (d, w) -> {
                final String a = p1.getText().toString();
                final String b = p2.getText().toString();
                if (a.length() < 6) { Toast.makeText(this, "Min. 6 caractères", Toast.LENGTH_SHORT).show(); return; }
                if (!a.equals(b)) { Toast.makeText(this, "Les mots de passe ne correspondent pas", Toast.LENGTH_SHORT).show(); return; }
                new Thread(() -> {
                    try {
                        c.updatePassword(a);
                        runOnUiThread(() -> Toast.makeText(this, "Mot de passe mis à jour", Toast.LENGTH_LONG).show());
                    } catch (Exception e) {
                        runOnUiThread(() -> Toast.makeText(this, "Erreur : " + e.getMessage(), Toast.LENGTH_LONG).show());
                    }
                }).start();
            })
            .setNegativeButton("Annuler", null)
            .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (accountStatus != null) refreshAccountStatus();
    }

    private void refreshAccountStatus() {
        SupabaseClient c = SupabaseClient.get(this);
        accountStatus.setText(c.userEmail() + " · …");
        new Thread(() -> {
            try {
                // Vérifie la session : si bloqué ou un autre appareil a pris le relais → déconnexion.
                JSONObject sess = c.checkSession();
                if (sess != null) {
                    String st = sess.optString("status", "ok");
                    if (!"ok".equals(st)) {
                        final String reason;
                        if ("blocked".equals(st)) {
                            String r = sess.optString("blocked_reason", null);
                            reason = "Compte bloqué" + (r != null && !r.isEmpty() && !"null".equals(r) ? " : " + r : "");
                        } else if ("session_lost".equals(st)) {
                            reason = "Compte utilisé sur un autre appareil. Vous avez été déconnecté.";
                        } else {
                            reason = "Session expirée";
                        }
                        runOnUiThread(() -> {
                            Toast.makeText(this, reason, Toast.LENGTH_LONG).show();
                            c.signOut();
                            startActivity(new Intent(this, LoginActivity.class));
                            finish();
                        });
                        return;
                    }
                }
                JSONArray prof = c.selectTable("profiles", "select=coins&user_id=eq." + c.userId());
                int coins = prof.length() > 0 ? prof.getJSONObject(0).optInt("coins", 0) : 0;
                JSONArray subs = c.rpcArray("get_active_subscription", null);
                String sub = "Aucun abonnement";
                if (subs.length() > 0) {
                    long sec = subs.getJSONObject(0).optLong("seconds_remaining", 0);
                    sub = formatDuration(sec) + " restant";
                }
                String text = c.userEmail() + "  ·  " + coins + " FCFA  ·  " + sub;
                runOnUiThread(() -> accountStatus.setText(text));
            } catch (Exception e) {
                runOnUiThread(() -> accountStatus.setText(c.userEmail() + " · " + e.getMessage()));
            }
        }).start();
    }

    private static String formatDuration(long sec) {
        if (sec <= 0) return "0min";
        long h = sec / 3600, m = (sec % 3600) / 60;
        if (h >= 24) return (h / 24) + "j " + (h % 24) + "h";
        if (h > 0) return h + "h " + m + "min";
        return m + "min";
    }

    private void loadPlaylist(boolean showLoading) {
        if (showLoading) status.setText("Chargement…");
        new Thread(() -> {
            try {
                // Récupère le lien M3U personnel de l'utilisateur (assigné via son abonnement).
                String url = SupabaseClient.get(this).rpcScalar("get_my_playlist_url", null);
                if (url == null || url.isEmpty()) {
                    new Handler(Looper.getMainLooper()).post(() -> {
                        all.clear();
                        topGroups.clear();
                        featured = null;
                        buildChips();
                        render();
                        status.setText("Aucun abonnement actif. Souscrivez à un plan pour accéder aux chaînes.");
                    });
                    return;
                }
                String text = PlaylistParser.fetchRawSmart(url);
                PlaylistParser.saveCache(getFilesDir(), text);
                List<Channel> data = PlaylistParser.parse(text);
                if (data.isEmpty()) throw new Exception("aucune chaîne trouvée");
                new Handler(Looper.getMainLooper()).post(() -> {
                    ingest(data);
                    status.setText(all.size() + " chaînes  ·  " + topGroups.size() + " catégories");
                });
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (all.isEmpty()) status.setText("Impossible de charger : " + e.getMessage());
                    else status.setText(all.size() + " chaînes (hors-ligne)");
                });
            }
        }).start();
    }

    private void ingest(List<Channel> data) {
        all.clear(); all.addAll(data);
        // Featured: pick first channel that has a logo, else first.
        featured = null;
        for (Channel c : all) if (c.logo != null && !c.logo.isEmpty()) { featured = c; break; }
        if (featured == null && !all.isEmpty()) featured = all.get(0);
        // Count groups, keep ordered by frequency.
        Map<String,Integer> counts = new LinkedHashMap<>();
        for (Channel c : all) counts.merge(c.group, 1, Integer::sum);
        List<Map.Entry<String,Integer>> entries = new ArrayList<>(counts.entrySet());
        entries.sort((a, b) -> {
            int ra = prefixRank(a.getKey());
            int rb = prefixRank(b.getKey());
            if (ra != rb) return Integer.compare(ra, rb);
            // Même rang : tri par fréquence décroissante.
            return Integer.compare(b.getValue(), a.getValue());
        });
        topGroups.clear();
        for (Map.Entry<String,Integer> e : entries) topGroups.add(e.getKey());
        buildChips();
        render();
    }

    private static int prefixRank(String group) {
        if (group == null) return Integer.MAX_VALUE;
        String up = group.trim().toUpperCase(Locale.ROOT);
        // Premier token alphanumérique (avant espace, séparateur, ou symbole).
        StringBuilder tok = new StringBuilder();
        for (int i = 0; i < up.length(); i++) {
            char ch = up.charAt(i);
            if (Character.isLetterOrDigit(ch)) tok.append(ch);
            else if (tok.length() > 0) break;
        }
        String first = tok.toString();
        for (int i = 0; i < GROUP_PREFIX_ORDER.length; i++) {
            if (GROUP_PREFIX_ORDER[i].equals(first)) return i;
        }
        return GROUP_PREFIX_ORDER.length; // inconnus à la fin
    }

    private void buildChips() {
        chipsBar.removeAllViews();
        List<String> labels = new ArrayList<>();
        labels.add(CHIP_ALL);
        labels.addAll(topGroups);
        for (String g : labels) {
            TextView chip = new TextView(this);
            chip.setText(g);
            chip.setTextSize(13);
            chip.setPadding(dp(16), dp(7), dp(16), dp(7));
            chip.setBackgroundResource(R.drawable.chip_bg);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(dp(8));
            chip.setLayoutParams(lp);
            chip.setSelected(g.equals(currentGroup));
            chip.setTextColor(g.equals(currentGroup) ? Color.BLACK : Color.WHITE);
            chip.setOnClickListener(v -> {
                currentGroup = g;
                buildChips();
                render();
            });
            chipsBar.addView(chip);
        }
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private static String norm(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s, Normalizer.Form.NFD)
            .replaceAll("\\p{InCombiningDiacriticalMarks}+", "").toLowerCase(Locale.ROOT);
    }

    private void render() {
        boolean filtering = !currentQuery.trim().isEmpty() || !currentGroup.equals(CHIP_ALL);
        if (filtering) {
            List<Channel> filtered = new ArrayList<>();
            String needle = norm(currentQuery).trim();
            for (Channel c : all) {
                if (!currentGroup.equals(CHIP_ALL) && !currentGroup.equals(c.group)) continue;
                if (!needle.isEmpty()
                        && !norm(c.name).contains(needle)
                        && !norm(c.group).contains(needle)) continue;
                filtered.add(c);
            }
            list.setLayoutManager(new GridLayoutManager(this, 3));
            list.setAdapter(new GridAdapter(filtered));
        } else {
            list.setLayoutManager(new LinearLayoutManager(this));
            list.setAdapter(new HomeAdapter());
        }
    }

    private void play(Channel c) {
        Toast.makeText(this, "Vérification…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                JSONArray subs = SupabaseClient.get(this).rpcArray("get_active_subscription", null);
                runOnUiThread(() -> {
                    if (subs.length() == 0) {
                        Toast.makeText(this, "Aucun abonnement actif.", Toast.LENGTH_LONG).show();
                        startActivity(new Intent(this, PlansActivity.class));
                        return;
                    }
                    ChannelHolder.set(all);
                    int idx = all.indexOf(c);
                    Intent i = new Intent(this, PlayerActivity.class);
                    i.putExtra("index", idx);
                    startActivity(i);
                });
            } catch (Exception e) {
                runOnUiThread(() ->
                    Toast.makeText(this, "Erreur : " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void bindPoster(View v, Channel c) {
        ImageView logo = v.findViewById(R.id.logo);
        TextView letter = v.findViewById(R.id.letter);
        TextView name = v.findViewById(R.id.name);
        name.setText(c.name);
        String initial = (c.name == null || c.name.isEmpty()) ? "?" : c.name.substring(0, 1).toUpperCase(Locale.ROOT);
        letter.setText(initial);
        if (c.logo != null && !c.logo.isEmpty()) {
            logo.setVisibility(View.VISIBLE);
            Glide.with(this).load(c.logo).into(logo);
        } else {
            logo.setVisibility(View.GONE);
        }
        v.setOnClickListener(x -> play(c));
    }

    // ---------- Home (browse) adapter ----------
    private static final int TYPE_HERO = 0, TYPE_ROW = 1;

    class HomeAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @Override public int getItemViewType(int position) {
            return position == 0 ? TYPE_HERO : TYPE_ROW;
        }
        @NonNull @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup p, int t) {
            LayoutInflater inf = LayoutInflater.from(p.getContext());
            if (t == TYPE_HERO) return new HeroVH(inf.inflate(R.layout.item_hero, p, false));
            return new RowVH(inf.inflate(R.layout.item_row_section, p, false));
        }
        @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int pos) {
            if (h instanceof HeroVH) ((HeroVH) h).bind(featured);
            else {
                String group = topGroups.get(pos - 1);
                List<Channel> chans = new ArrayList<>();
                for (Channel c : all) if (group.equals(c.group)) { chans.add(c); if (chans.size() >= 20) break; }
                ((RowVH) h).bind(group, chans);
            }
        }
        @Override public int getItemCount() {
            return (featured == null ? 0 : 1) + topGroups.size();
        }
    }

    class HeroVH extends RecyclerView.ViewHolder {
        HeroVH(View v) { super(v); }
        void bind(Channel c) {
            TextView name = itemView.findViewById(R.id.hero_name);
            TextView group = itemView.findViewById(R.id.hero_group);
            TextView letter = itemView.findViewById(R.id.hero_letter);
            ImageView logo = itemView.findViewById(R.id.hero_logo);
            if (c == null) { name.setText("Bienvenue sur OnE+"); group.setText(""); letter.setText("F"); logo.setVisibility(View.GONE); return; }
            name.setText(c.name);
            String epg = EPGStore.get().currentLabel(c.tvgId);
            if (epg != null) group.setText(epg);
            else group.setText(c.group == null ? "" : c.group.toUpperCase(Locale.ROOT));
            String init = c.name.isEmpty() ? "F" : c.name.substring(0,1).toUpperCase(Locale.ROOT);
            letter.setText(init);
            if (c.logo != null && !c.logo.isEmpty()) {
                logo.setVisibility(View.VISIBLE);
                Glide.with(MainActivity.this).load(c.logo).into(logo);
            } else logo.setVisibility(View.GONE);
            itemView.findViewById(R.id.hero_play).setOnClickListener(v -> play(c));
        }
    }

    class RowVH extends RecyclerView.ViewHolder {
        RowVH(View v) { super(v); }
        void bind(String title, List<Channel> chans) {
            TextView t = itemView.findViewById(R.id.row_title);
            t.setText(title);
            RecyclerView rv = itemView.findViewById(R.id.row_list);
            rv.setLayoutManager(new LinearLayoutManager(itemView.getContext(), LinearLayoutManager.HORIZONTAL, false));
            rv.setAdapter(new PosterAdapter(chans));
        }
    }

    class PosterAdapter extends RecyclerView.Adapter<PosterVH> {
        final List<Channel> data;
        PosterAdapter(List<Channel> d) { data = d; }
        @NonNull @Override public PosterVH onCreateViewHolder(@NonNull ViewGroup p, int t) {
            return new PosterVH(LayoutInflater.from(p.getContext()).inflate(R.layout.item_poster, p, false));
        }
        @Override public void onBindViewHolder(@NonNull PosterVH h, int pos) { bindPoster(h.itemView, data.get(pos)); }
        @Override public int getItemCount() { return data.size(); }
    }
    static class PosterVH extends RecyclerView.ViewHolder { PosterVH(View v) { super(v); } }

    // ---------- Grid (filtered) adapter ----------
    class GridAdapter extends RecyclerView.Adapter<PosterVH> {
        final List<Channel> data;
        GridAdapter(List<Channel> d) { data = d; }
        @NonNull @Override public PosterVH onCreateViewHolder(@NonNull ViewGroup p, int t) {
            View v = LayoutInflater.from(p.getContext()).inflate(R.layout.item_poster, p, false);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(dp(6), dp(6), dp(6), dp(6));
            v.setLayoutParams(lp);
            // Make the poster image stretch to the column width
            View frame = ((ViewGroup) v).getChildAt(0);
            ViewGroup.LayoutParams fp = frame.getLayoutParams();
            fp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            frame.setLayoutParams(fp);
            View name = ((ViewGroup) v).getChildAt(1);
            ViewGroup.LayoutParams np = name.getLayoutParams();
            np.width = ViewGroup.LayoutParams.MATCH_PARENT;
            name.setLayoutParams(np);
            return new PosterVH(v);
        }
        @Override public void onBindViewHolder(@NonNull PosterVH h, int pos) { bindPoster(h.itemView, data.get(pos)); }
        @Override public int getItemCount() { return data.size(); }
    }
}