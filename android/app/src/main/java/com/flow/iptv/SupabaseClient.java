package com.flow.iptv;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONObject;

public class SupabaseClient {
    public static final String URL_BASE = "https://oxqjdncqcopcptxkikes.supabase.co";
    public static final String ANON_KEY =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im94cWpkbmNxY29wY3B0eGtpa2VzIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzkxNDE3OTUsImV4cCI6MjA5NDcxNzc5NX0.GVqCm_lDS1pKQPuQ5mK4QgJD50AB5jZ6MWoXY-mUAng";

    private static SupabaseClient INSTANCE;
    private final SharedPreferences prefs;
    private String accessToken;
    private String refreshToken;
    private long expiresAt;
    private String userId;
    private String userEmail;
    private String sessionToken;
    private boolean isAdmin;

    public static synchronized SupabaseClient get(Context c) {
        if (INSTANCE == null) INSTANCE = new SupabaseClient(c.getApplicationContext());
        return INSTANCE;
    }

    private SupabaseClient(Context c) {
        prefs = c.getSharedPreferences("flow_auth", Context.MODE_PRIVATE);
        accessToken = prefs.getString("access_token", null);
        refreshToken = prefs.getString("refresh_token", null);
        expiresAt = prefs.getLong("expires_at", 0);
        userId = prefs.getString("user_id", null);
        userEmail = prefs.getString("user_email", null);
        sessionToken = prefs.getString("session_token", null);
        isAdmin = prefs.getBoolean("is_admin", false);
    }

    public boolean isLoggedIn() { return accessToken != null && userId != null; }
    public String userId() { return userId; }
    public String accessToken() { return accessToken; }
    public String userEmail() { return userEmail; }
    public String sessionToken() { return sessionToken; }
    public boolean isAdminCached() { return isAdmin; }

    /** Vérifie côté serveur si l'utilisateur courant a le rôle admin. Met en cache. */
    public boolean checkIsAdmin() throws Exception {
        if (!isLoggedIn()) return false;
        JSONArray roles = selectTable("user_roles",
            "select=role&user_id=eq." + userId + "&role=eq.admin");
        boolean admin = roles.length() > 0;
        isAdmin = admin;
        prefs.edit().putBoolean("is_admin", admin).apply();
        return admin;
    }

    /** Génère et persiste un nouveau token de session, puis le revendique côté serveur. */
    public String claimNewSession() throws Exception {
        String token = UUID.randomUUID().toString();
        JSONObject body = new JSONObject();
        body.put("p_token", token);
        httpRaw("POST", "/rest/v1/rpc/claim_session", body, true);
        sessionToken = token;
        prefs.edit().putString("session_token", token).apply();
        return token;
    }

    /** Vérifie le statut du compte/session courant. Renvoie "ok", "blocked", "session_lost", "no_profile". */
    public JSONObject checkSession() throws Exception {
        if (sessionToken == null) return null;
        JSONObject body = new JSONObject();
        body.put("p_token", sessionToken);
        JSONArray arr = rpcArray("check_session", body);
        if (arr.length() == 0) return null;
        return arr.getJSONObject(0);
    }

    public void signOut() {
        accessToken = refreshToken = userId = userEmail = sessionToken = null;
        isAdmin = false;
        expiresAt = 0;
        prefs.edit().clear().apply();
    }

    public JSONObject signUp(String email, String password) throws Exception {
        JSONObject body = new JSONObject();
        body.put("email", email);
        body.put("password", password);
        String resp = httpRaw("POST", "/auth/v1/signup", body, false);
        JSONObject res = new JSONObject(resp);
        if (res.has("access_token")) saveSession(res);
        return res;
    }

    public JSONObject signIn(String email, String password) throws Exception {
        JSONObject body = new JSONObject();
        body.put("email", email);
        body.put("password", password);
        String resp = httpRaw("POST", "/auth/v1/token?grant_type=password", body, false);
        JSONObject res = new JSONObject(resp);
        saveSession(res);
        return res;
    }

    /** Met à jour le mot de passe de l'utilisateur courant. */
    public void updatePassword(String newPassword) throws Exception {
        if (!isLoggedIn()) throw new Exception("not_authenticated");
        if (newPassword == null || newPassword.length() < 6) throw new Exception("Mot de passe trop court (min 6)");
        refreshIfNeeded();
        JSONObject body = new JSONObject();
        body.put("password", newPassword);
        httpRaw("PUT", "/auth/v1/user", body, true);
    }

    private void saveSession(JSONObject res) {
        accessToken = res.optString("access_token", null);
        refreshToken = res.optString("refresh_token", null);
        long expiresIn = res.optLong("expires_in", 3600);
        expiresAt = System.currentTimeMillis() + expiresIn * 1000L;
        JSONObject user = res.optJSONObject("user");
        if (user != null) {
            userId = user.optString("id", null);
            userEmail = user.optString("email", null);
        }
        prefs.edit()
            .putString("access_token", accessToken)
            .putString("refresh_token", refreshToken)
            .putLong("expires_at", expiresAt)
            .putString("user_id", userId)
            .putString("user_email", userEmail)
            .apply();
    }

    public void refreshIfNeeded() throws Exception {
        if (accessToken == null || refreshToken == null) return;
        if (System.currentTimeMillis() < expiresAt - 60_000L) return;
        JSONObject body = new JSONObject();
        body.put("refresh_token", refreshToken);
        String resp = httpRaw("POST", "/auth/v1/token?grant_type=refresh_token", body, false);
        saveSession(new JSONObject(resp));
    }

    public JSONArray rpcArray(String fn, JSONObject body) throws Exception {
        refreshIfNeeded();
        String resp = httpRaw("POST", "/rest/v1/rpc/" + fn, body == null ? new JSONObject() : body, true);
        if (resp == null || resp.isEmpty()) return new JSONArray();
        String t = resp.trim();
        if (t.startsWith("[")) return new JSONArray(t);
        if (t.startsWith("{")) {
            JSONArray a = new JSONArray();
            a.put(new JSONObject(t));
            return a;
        }
        return new JSONArray();
    }

    /** Appelle un RPC qui renvoie un scalaire (string/null). */
    public String rpcScalar(String fn, JSONObject body) throws Exception {
        refreshIfNeeded();
        String resp = httpRaw("POST", "/rest/v1/rpc/" + fn, body == null ? new JSONObject() : body, true);
        if (resp == null) return null;
        String t = resp.trim();
        if (t.isEmpty() || "null".equals(t)) return null;
        if (t.startsWith("\"") && t.endsWith("\"")) {
            return t.substring(1, t.length() - 1)
                .replace("\\\"", "\"").replace("\\\\", "\\").replace("\\/", "/");
        }
        return t;
    }

    public JSONArray selectTable(String table, String params) throws Exception {
        refreshIfNeeded();
        String path = "/rest/v1/" + table + (params == null ? "" : "?" + params);
        String resp = httpRaw("GET", path, null, true);
        return new JSONArray(resp);
    }

    private String httpRaw(String method, String path, JSONObject body, boolean auth) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(URL_BASE + path).openConnection();
        conn.setRequestMethod(method);
        conn.setRequestProperty("apikey", ANON_KEY);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json");
        if (auth && accessToken != null) {
            conn.setRequestProperty("Authorization", "Bearer " + accessToken);
        } else {
            conn.setRequestProperty("Authorization", "Bearer " + ANON_KEY);
        }
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);
        if (body != null) {
            conn.setDoOutput(true);
            byte[] data = body.toString().getBytes("UTF-8");
            conn.getOutputStream().write(data);
            conn.getOutputStream().close();
        }
        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        StringBuilder sb = new StringBuilder();
        if (is != null) {
            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();
        }
        String response = sb.toString();
        if (code < 200 || code >= 300) {
            String msg = response;
            try {
                JSONObject err = new JSONObject(response);
                if (err.has("msg")) msg = err.getString("msg");
                else if (err.has("error_description")) msg = err.getString("error_description");
                else if (err.has("message")) msg = err.getString("message");
                else if (err.has("error")) msg = err.optString("error");
            } catch (Exception ignored) {}
            throw new Exception(msg);
        }
        return response;
    }
}