package com.flow.iptv;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.appcompat.app.AppCompatActivity;

@SuppressLint("CustomSplashScreen")
public class SplashActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_splash);
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            SupabaseClient c = SupabaseClient.get(this);
            Class<?> target;
            if (!c.isLoggedIn()) target = LoginActivity.class;
            else if (c.isAdminCached()) target = AdminActivity.class;
            else target = MainActivity.class;
            Intent i = new Intent(this, target);
            startActivity(i);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
        }, 1400);
    }
}