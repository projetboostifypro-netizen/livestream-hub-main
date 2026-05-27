package com.flow.iptv;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class LoginActivity extends AppCompatActivity {
    private boolean signupMode = false;
    private EditText email, password;
    private TextView title, error, toggle;
    private Button submit;

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_login);

        email = findViewById(R.id.email);
        password = findViewById(R.id.password);
        title = findViewById(R.id.title);
        error = findViewById(R.id.error);
        toggle = findViewById(R.id.toggle);
        submit = findViewById(R.id.submit);

        updateMode();
        toggle.setOnClickListener(v -> { signupMode = !signupMode; updateMode(); });
        submit.setOnClickListener(v -> doSubmit());
    }

    private void updateMode() {
        title.setText(signupMode ? "Créer un compte" : "Connexion");
        submit.setText(signupMode ? "S'inscrire (+120 FCFA offerts)" : "Se connecter");
        toggle.setText(signupMode ? "Déjà un compte ? Se connecter" : "Pas de compte ? Créer un compte");
        error.setVisibility(View.GONE);
    }

    private void doSubmit() {
        String em = email.getText().toString().trim();
        String pw = password.getText().toString();
        if (em.isEmpty() || pw.length() < 6) {
            showError("E-mail invalide ou mot de passe < 6 caractères");
            return;
        }
        submit.setEnabled(false);
        submit.setText("…");
        new Thread(() -> {
            try {
                SupabaseClient c = SupabaseClient.get(this);
                if (signupMode) c.signUp(em, pw); else c.signIn(em, pw);
                // Revendique une session unique côté serveur (déconnecte tout autre appareil).
                c.claimNewSession();
                boolean admin = false;
                try { admin = c.checkIsAdmin(); } catch (Exception ignored) {}
                final boolean isAdmin = admin;
                runOnUiThread(() -> {
                    Class<?> target = isAdmin ? AdminActivity.class : MainActivity.class;
                    startActivity(new Intent(this, target));
                    finish();
                });
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    showError(e.getMessage() == null ? "Erreur" : e.getMessage());
                    submit.setEnabled(true);
                    updateMode();
                });
            }
        }).start();
    }

    private void showError(String msg) {
        error.setText(msg);
        error.setVisibility(View.VISIBLE);
    }
}