package com.flow.iptv;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONObject;

public class CoinsActivity extends AppCompatActivity {
    private static final int[] PACKS = {500, 1000, 2000, 5000, 10000};
    private static final int MIN_AMOUNT = 500;
    // SoleasPay credentials embedded directly in the app (same as the web fallback page).
    private static final String SOLEASPAY_API_KEY = "SP_PyWmCdy82M3ItajYYytc6sJiOwpWUzlWIobxvRw8ANM_AP";
    private static final String SOLEASPAY_BASE_URL = "https://soleaspay.com";
    // SoleasPay services. Label shown to user, code stored in tx, serviceId sent to SoleasPay.
    private static final String[][] OPERATORS = {
        {"Orange Money — Cameroun",          "orange_cm",   "2"},
        {"Orange Money — Côte d'Ivoire",     "orange_ci",   "29"},
        {"MTN MoMo — Côte d'Ivoire",         "momo_ci",     "30"},
        {"Moov Money — Côte d'Ivoire",       "moov_ci",     "31"},
        {"Wave — Côte d'Ivoire",             "wave_ci",     "32"},
        {"Moov Money — Burkina Faso",        "moov_bf",     "33"},
        {"Orange Money — Burkina Faso",      "orange_bf",   "34"},
        {"MTN MoMo — Bénin",                 "momo_bj",     "35"},
        {"Moov Money — Bénin",               "moov_bj",     "36"},
        {"T-Money — Togo",                   "tmoney_tg",   "37"},
        {"Moov Money — Togo",                "moov_tg",     "38"},
        {"Vodacom M-Pesa — RDC",             "vodacom_cod", "52"},
        {"Airtel Money — RDC",               "airtel_cod",  "53"},
        {"Orange Money — RDC",               "orange_cod",  "54"},
        {"Airtel Money — Congo",             "airtel_cog",  "55"},
        {"Airtel Money — Gabon",             "airtel_gab",  "57"},
        {"Airtel Money — Ouganda",           "airtel_uga",  "58"},
        {"MTN MoMo — Ouganda",               "momo_uga",    "59"},
    };

    private Spinner operator;
    private EditText phone, otp, amountInput;
    private TextView status, coinsView;
    private LinearLayout packsRow;
    private Button confirm;
    private int selectedPack = MIN_AMOUNT;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private volatile boolean polling = false;

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_coins);
        operator = findViewById(R.id.operator);
        phone = findViewById(R.id.phone);
        otp = findViewById(R.id.otp);
        amountInput = findViewById(R.id.amount);
        status = findViewById(R.id.status);
        coinsView = findViewById(R.id.coins);
        packsRow = findViewById(R.id.packs);
        confirm = findViewById(R.id.confirm);

        String[] labels = new String[OPERATORS.length];
        for (int i = 0; i < OPERATORS.length; i++) labels[i] = OPERATORS[i][0];
        ArrayAdapter<String> ad = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        operator.setAdapter(ad);

        renderPacks();
        confirm.setOnClickListener(v -> startPayment());
        loadCoins();
    }

    @Override
    protected void onDestroy() {
        polling = false;
        super.onDestroy();
    }

    private void renderPacks() {
        packsRow.removeAllViews();
        for (int amount : PACKS) {
            Button b = new Button(this);
            b.setText(amount + " FCFA");
            b.setTextColor(0xFFFFFFFF);
            b.setBackgroundColor(amount == selectedPack ? 0xFF0AB1F0 : 0xFF0F1216);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(8, 0, 8, 0);
            b.setLayoutParams(lp);
            b.setOnClickListener(v -> {
                selectedPack = amount;
                amountInput.setText(String.valueOf(amount));
                renderPacks();
            });
            packsRow.addView(b);
        }
    }

    private void loadCoins() {
        new Thread(() -> {
            try {
                SupabaseClient c = SupabaseClient.get(this);
                JSONArray prof = c.selectTable("profiles",
                    "select=coins&user_id=eq." + c.userId());
                int coins = prof.length() > 0 ? prof.getJSONObject(0).optInt("coins", 0) : 0;
                runOnUiThread(() -> coinsView.setText("Solde : " + coins + " FCFA"));
            } catch (Exception ignored) {}
        }).start();
    }

    private void setStatus(String s) { ui.post(() -> status.setText(s)); }
    private void setBusy(boolean busy) { ui.post(() -> confirm.setEnabled(!busy)); }

    private void startPayment() {
        String amountStr = amountInput.getText().toString().trim();
        int entered;
        try {
            entered = amountStr.isEmpty() ? selectedPack : Integer.parseInt(amountStr);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Montant invalide", Toast.LENGTH_SHORT).show();
            return;
        }
        if (entered < MIN_AMOUNT) {
            Toast.makeText(this, "Montant minimum : " + MIN_AMOUNT + " FCFA", Toast.LENGTH_SHORT).show();
            return;
        }
        String tel = phone.getText().toString().trim();
        if (tel.length() < 8) {
            Toast.makeText(this, "Numéro invalide", Toast.LENGTH_SHORT).show();
            return;
        }
        final String otpCode = otp.getText().toString().trim();
        final int amount = entered;
        int opIdx = operator.getSelectedItemPosition();
        if (opIdx < 0) opIdx = 0;
        final String op = OPERATORS[opIdx][1];
        final String opServiceId = OPERATORS[opIdx][2];
        final String opLabel = OPERATORS[opIdx][0];
        setBusy(true);
        setStatus("Initialisation du paiement…");

        new Thread(() -> {
            try {
                SupabaseClient sb = SupabaseClient.get(this);

                // 1) Use embedded API key (same as fallback web page).
                SoleasPayClient pay = new SoleasPayClient(SOLEASPAY_BASE_URL, SOLEASPAY_API_KEY, opServiceId);

                // 2) Pay-in
                String orderId = "FLOW-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                String email = sb.userEmail() == null ? "user@flow.tv" : sb.userEmail();
                String name = email.split("@")[0];
                // Register the external reference (orderId) so the webhook can credit the right user.
                try {
                    JSONObject reg = new JSONObject()
                        .put("p_external_ref", orderId)
                        .put("p_amount", amount)
                        .put("p_operator", op)
                        .put("p_phone", tel);
                    sb.rpcArray("register_external_reference", reg);
                } catch (Exception ignoredReg) {}
                setStatus("Demande de paiement envoyée. Validez sur votre téléphone…");
                // Description must contain only alphanumeric and space characters (SoleasPay constraint).
                JSONObject init = pay.payIn(orderId, tel, amount, "XAF", name, email,
                    "Recharge FLOW " + amount + " FCFA",
                    otpCode.isEmpty() ? null : otpCode);
                String payId = init.optString("payId", "");
                if (payId.isEmpty()) throw new Exception("Réponse SoleasPay invalide (payId manquant)");

                // 3) Poll every 3s, up to 90 attempts (~4.5 min)
                polling = true;
                for (int i = 0; i < 90 && polling; i++) {
                    Thread.sleep(3000);
                    try {
                        JSONObject v = pay.verify(orderId, payId);
                        String st = SoleasPayClient.statusOf(v);
                        final int attempt = i + 1;
                        setStatus("Vérification… (" + attempt + ") statut : " + st);
                        if ("SUCCESS".equals(st)) {
                            // 4) Double-check: only credit if envelope is success=true AND no failure markers.
                            if (v.has("success") && !v.optBoolean("success", false)) {
                                setStatus("Paiement refusé par l'opérateur.");
                                setBusy(false);
                                return;
                            }
                            // 5) Credit the user via existing RPC
                            JSONObject body = new JSONObject()
                                .put("p_amount", amount)
                                .put("p_operator", op)
                                .put("p_phone", tel)
                                .put("p_payid", payId)
                                .put("p_status", "SUCCESS");
                            sb.rpcArray("purchase_coins", body);
                            ui.post(() -> {
                                Toast.makeText(this, "+" + amount + " FCFA crédités !", Toast.LENGTH_LONG).show();
                                setStatus("Paiement validé. Solde mis à jour.");
                                loadCoins();
                                setBusy(false);
                            });
                            return;
                        }
                        if ("FAILED".equals(st)) {
                            String reason = v.optString("message", "Paiement échoué ou annulé.");
                            setStatus("Échec : " + reason);
                            setBusy(false);
                            return;
                        }
                    } catch (Exception verr) {
                        // transient verify error, continue
                    }
                }
                setStatus("Délai dépassé. Si débité, contactez le support.");
                setBusy(false);
            } catch (Exception e) {
                final String msg = e.getMessage() == null ? "Erreur inconnue" : e.getMessage();
                ui.post(() -> {
                    setStatus("Échec : " + msg);
                    setBusy(false);
                });
            }
        }).start();
    }
}