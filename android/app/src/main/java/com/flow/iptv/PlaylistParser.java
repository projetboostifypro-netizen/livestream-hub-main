package com.flow.iptv;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;

public class PlaylistParser {
    private static final Pattern GROUP = Pattern.compile("group-title=\"([^\"]*)\"");
    private static final Pattern LOGO = Pattern.compile("tvg-logo=\"([^\"]*)\"");
    private static final Pattern TVG_ID = Pattern.compile("tvg-id=\"([^\"]*)\"");
    // Hard cap to keep memory usage safe on Android (some playlists ship 100k+ channels).
    private static final int MAX_CHANNELS = 8000;
    private static final int MAX_CACHE_BYTES = 6 * 1024 * 1024; // 6 MB
    private static final int MAX_FETCH_BYTES = 25 * 1024 * 1024; // 25 MB safety cap on download

    public static String fetchRaw(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestProperty("User-Agent", "VLC/3.0.20 LibVLC/3.0.20");
        conn.setRequestProperty("Accept", "application/json,application/x-mpegURL,*/*");
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(45000);
        conn.setInstanceFollowRedirects(true);
        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) throw new Exception("HTTP " + code + " : " + url);
        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder sb = new StringBuilder();
        String line;
        int channels = 0;
        while ((line = br.readLine()) != null) {
            sb.append(line).append('\n');
            if (line.startsWith("#EXTINF")) {
                channels++;
                if (channels > MAX_CHANNELS) break; // évite OOM sur playlists géantes
            }
            if (sb.length() > MAX_FETCH_BYTES) break;
        }
        br.close();
        try { conn.disconnect(); } catch (Exception ignored) {}
        return sb.toString();
    }

    public static String fetchRawSmart(String url) throws Exception {
        Exception playlistError = null;
        try {
            String text = fetchRaw(url);
            if (!parse(text).isEmpty()) return text;
        } catch (Exception e) { playlistError = e; }
        try { return fetchXtreamApiAsM3u(url); }
        catch (Exception apiError) {
            if (playlistError != null) throw new Exception("Playlist M3U refusée, API aussi : " + apiError.getMessage());
            throw apiError;
        }
    }

    public static List<Channel> parse(String text) {
        List<Channel> out = new ArrayList<>();
        String line;
        String name = null, group = null, logo = null, tvgId = null;
        java.util.Scanner sc = new java.util.Scanner(text);
        while (sc.hasNextLine()) {
            line = sc.nextLine();
            line = line.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("#EXTINF")) {
                int comma = line.lastIndexOf(',');
                name = comma >= 0 ? line.substring(comma + 1).trim() : "Sans nom";
                Matcher m = GROUP.matcher(line);
                group = m.find() ? m.group(1) : "Autres";
                Matcher ml = LOGO.matcher(line);
                logo = ml.find() ? ml.group(1) : null;
                Matcher mt = TVG_ID.matcher(line);
                tvgId = mt.find() ? mt.group(1) : null;
            } else if (!line.startsWith("#") && name != null) {
                String g = (group == null || group.isEmpty()) ? "Autres" : group;
                out.add(new Channel(name, g, toHls(line), logo, tvgId));
                name = null; group = null; logo = null; tvgId = null;
                if (out.size() >= MAX_CHANNELS) break;
            }
        }
        sc.close();
        return out;
    }

    public static List<Channel> fetch(String url) throws Exception {
        return parse(fetchRawSmart(url));
    }

    private static String fetchXtreamApiAsM3u(String playlistUrl) throws Exception {
        URL source = new URL(playlistUrl);
        Map<String, String> q = query(source.getQuery());
        String user = q.get("username"), pass = q.get("password");
        if (user == null || pass == null) throw new Exception("identifiants Xtream introuvables");
        String base = source.getProtocol() + "://" + source.getHost() + (source.getPort() > 0 ? ":" + source.getPort() : "");

        Map<String, String> groups = new HashMap<>();
        try {
            JSONArray cats = new JSONArray(fetchRaw(base + "/player_api.php?username=" + enc(user) + "&password=" + enc(pass) + "&action=get_live_categories"));
            for (int i = 0; i < cats.length(); i++) {
                JSONObject c = cats.getJSONObject(i);
                groups.put(c.optString("category_id"), c.optString("category_name", "Autres"));
            }
        } catch (Exception ignored) {}

        JSONArray streams = new JSONArray(fetchRaw(base + "/player_api.php?username=" + enc(user) + "&password=" + enc(pass) + "&action=get_live_streams"));
        StringBuilder m3u = new StringBuilder("#EXTM3U\n");
        for (int i = 0; i < streams.length(); i++) {
            JSONObject s = streams.getJSONObject(i);
            String id = s.optString("stream_id");
            if (id.isEmpty()) continue;
            String name = s.optString("name", "Chaîne " + id);
            String group = groups.get(s.optString("category_id"));
            if (group == null || group.isEmpty()) group = "Autres";
            String direct = s.optString("direct_source", "").trim();
            String streamUrl = direct.isEmpty() ? base + "/live/" + enc(user) + "/" + enc(pass) + "/" + id + ".m3u8" : direct;
            String icon = s.optString("stream_icon", "").trim();
            m3u.append("#EXTINF:-1");
            if (!icon.isEmpty()) m3u.append(" tvg-logo=\"").append(esc(icon)).append("\"");
            m3u.append(" group-title=\"").append(esc(group)).append("\",").append(name).append('\n');
            m3u.append(streamUrl).append('\n');
        }
        if (m3u.length() <= 8) throw new Exception("aucune chaîne dans l'API Xtream");
        return m3u.toString();
    }

    private static Map<String, String> query(String raw) throws Exception {
        Map<String, String> out = new HashMap<>();
        if (raw == null) return out;
        for (String part : raw.split("&")) {
            int p = part.indexOf('=');
            if (p > 0) out.put(URLDecoder.decode(part.substring(0, p), "UTF-8"), URLDecoder.decode(part.substring(p + 1), "UTF-8"));
        }
        return out;
    }

    private static String enc(String s) throws Exception {
        return java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20");
    }

    private static String esc(String s) { return s == null ? "" : s.replace("\"", "'"); }

    public static void saveCache(File dir, String text) {
        try {
            if (text == null) return;
            if (text.length() > MAX_CACHE_BYTES) return; // évite d'écrire des fichiers énormes
            File f = new File(dir, "playlist.m3u");
            BufferedWriter w = new BufferedWriter(new FileWriter(f));
            w.write(text);
            w.close();
        } catch (Exception ignored) {}
    }

    public static String readCache(File dir) {
        try {
            File f = new File(dir, "playlist.m3u");
            if (!f.exists()) return null;
            BufferedReader br = new BufferedReader(new FileReader(f));
            StringBuilder sb = new StringBuilder();
            String l;
            while ((l = br.readLine()) != null) sb.append(l).append('\n');
            br.close();
            return sb.toString();
        } catch (Exception e) { return null; }
    }

    // Xtream Codes: /USER/PASS/ID.ts or /live/USER/PASS/ID.ts -> /live/USER/PASS/ID.m3u8
    private static final Pattern TS = Pattern.compile("^/(?:live/)?([^/]+)/([^/]+)/(\\d+)\\.ts$",
            Pattern.CASE_INSENSITIVE);

    static String toHls(String raw) {
        try {
            URL u = new URL(raw);
            Matcher m = TS.matcher(u.getPath());
            if (m.matches()) {
                String newPath = "/live/" + m.group(1) + "/" + m.group(2) + "/" + m.group(3) + ".m3u8";
                return new URL(u.getProtocol(), u.getHost(), u.getPort(), newPath).toString();
            }
        } catch (Exception ignored) {}
        return raw;
    }
}