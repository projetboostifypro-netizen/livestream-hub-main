package com.flow.iptv;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import org.json.JSONObject;

public class SoleasPayClient {
    private final String baseUrl;
    private final String apiKey;
    private final String serviceId;

    public SoleasPayClient(String baseUrl, String apiKey, String serviceId) {
        this.baseUrl = baseUrl == null || baseUrl.isEmpty() ? "https://soleaspay.com" : baseUrl;
        this.apiKey = apiKey;
        this.serviceId = serviceId;
    }

    /** Returns {"orderId":"...","payId":"...","raw":{...}} */
    public JSONObject payIn(String orderId, String wallet, int amount, String currency,
                             String payerName, String payerEmail, String description,
                             String otp) throws Exception {
        JSONObject body = new JSONObject();
        body.put("order_id", orderId);
        body.put("wallet", wallet);
        body.put("amount", amount);
        body.put("currency", currency);
        body.put("payer", payerName);
        body.put("payerEmail", payerEmail);
        body.put("description", description);
        body.put("successUrl", "https://flow.tv/success");
        body.put("failureUrl", "https://flow.tv/fail");

        java.util.Map<String,String> hdrs = new java.util.HashMap<>();
        hdrs.put("operation", "2");
        hdrs.put("service", serviceId);
        if (otp != null && !otp.isEmpty()) hdrs.put("otp", otp);
        String resp = http("POST", "/api/agent/bills/v3", body.toString(), hdrs);
        JSONObject json = new JSONObject(resp);
        JSONObject out = new JSONObject();
        out.put("orderId", orderId);
        // V3 returns the reference inside data.reference
        JSONObject data = json.optJSONObject("data");
        String pid = "";
        if (data != null) pid = data.optString("reference", "");
        if (pid.isEmpty()) pid = json.optString("payId", json.optString("reference", ""));
        out.put("payId", pid);
        out.put("raw", json);
        return out;
    }

    /** Returns the verification JSON. Caller should read status. */
    public JSONObject verify(String orderId, String payId) throws Exception {
        String q = "?orderId=" + enc(orderId) + "&payId=" + enc(payId);
        java.util.Map<String,String> hdrs = new java.util.HashMap<>();
        hdrs.put("operation", "2");
        hdrs.put("service", serviceId);
        String resp = http("GET", "/api/agent/verif-pay" + q, null, hdrs);
        return new JSONObject(resp);
    }

    /** Returns one of: SUCCESS / PENDING / FAILED. */
    public static String statusOf(JSONObject v) {
        // Hard fail signals from SoleasPay envelope ({"success":false,"code":404,...})
        if (v.has("success") && !v.optBoolean("success", true)) {
            String topStatus = v.optString("status", "").toUpperCase();
            if (topStatus.contains("PENDING") || topStatus.contains("WAIT") || topStatus.contains("PROCESS")) {
                // even pending if success=false stays pending only if not finalized; otherwise fail
            } else {
                return "FAILED";
            }
        }
        String[] keys = {"status", "state", "transaction_status", "paymentStatus"};
        String s = null;
        for (String k : keys) {
            String x = v.optString(k, null);
            if (x != null && !x.isEmpty()) { s = x; break; }
        }
        if (s == null) {
            // nested data
            JSONObject d = v.optJSONObject("data");
            if (d != null) return statusOf(d);
            return "PENDING";
        }
        String up = s.toUpperCase();
        if (up.contains("SUCCESS") || up.equals("OK") || up.contains("VALID") || up.contains("COMPLETED") || up.contains("PAID")) {
            // Only confirm SUCCESS when the envelope is not explicitly marked as failed
            if (v.has("success") && !v.optBoolean("success", true)) return "FAILED";
            return "SUCCESS";
        }
        if (up.contains("FAIL") || up.contains("CANCEL") || up.contains("REJECT") || up.contains("ERROR") || up.contains("EXPIRED")) return "FAILED";
        return "PENDING";
    }

    private String enc(String s) {
        try { return URLEncoder.encode(s == null ? "" : s, "UTF-8"); }
        catch (Exception e) { return ""; }
    }

    private String http(String method, String path, String body, java.util.Map<String,String> extraHeaders) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(baseUrl + path).openConnection();
        conn.setRequestMethod(method);
        conn.setRequestProperty("x-api-key", apiKey);
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("Content-Type", "application/json");
        if (extraHeaders != null) {
            for (java.util.Map.Entry<String,String> e : extraHeaders.entrySet()) {
                if (e.getValue() != null) conn.setRequestProperty(e.getKey(), e.getValue());
            }
        }
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);
        if (body != null) {
            conn.setDoOutput(true);
            byte[] data = body.getBytes("UTF-8");
            conn.getOutputStream().write(data);
            conn.getOutputStream().close();
        }
        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        StringBuilder sb = new StringBuilder();
        if (is != null) {
            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            String line; while ((line = br.readLine()) != null) sb.append(line);
            br.close();
        }
        String resp = sb.toString();
        if (code < 200 || code >= 300) {
            String msg = resp;
            try {
                JSONObject err = new JSONObject(resp);
                if (err.has("message")) msg = err.getString("message");
                else if (err.has("error")) msg = err.optString("error");
            } catch (Exception ignored) {}
            throw new Exception("SoleasPay " + code + ": " + msg);
        }
        return resp;
    }
}