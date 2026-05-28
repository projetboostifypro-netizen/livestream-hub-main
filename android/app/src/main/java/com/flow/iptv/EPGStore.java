package com.flow.iptv;

import android.content.Context;
import android.util.Xml;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.GZIPInputStream;
import org.xmlpull.v1.XmlPullParser;

/** Lightweight EPG (XMLTV) cache. Fetches once, parses on bg thread,
 *  exposes currentTitle(tvgId) for any channel. */
public class EPGStore {
    private static final String URL_XMLTV =
        "https://86279683.tvway.pro/xmltv.php?username=W71JB8XY&password=78055549";
    private static final String CACHE = "epg.xml";
    private static final long MAX_AGE_MS = 6L * 3600_000L;

    private static volatile EPGStore INSTANCE;
    private final Map<String, List<Prog>> byId = new HashMap<>();
    private volatile boolean ready = false;

    public static EPGStore get() {
        if (INSTANCE == null) synchronized (EPGStore.class) {
            if (INSTANCE == null) INSTANCE = new EPGStore();
        }
        return INSTANCE;
    }

    public boolean isReady() { return ready; }

    public void loadAsync(Context ctx, Runnable onReady) {
        new Thread(() -> {
            try {
                File cache = new File(ctx.getFilesDir(), CACHE);
                if (!cache.exists() || System.currentTimeMillis() - cache.lastModified() > MAX_AGE_MS) {
                    download(cache);
                }
                parse(cache);
                ready = true;
                if (onReady != null) onReady.run();
            } catch (Exception ignored) {}
        }, "epg-load").start();
    }

    private void download(File out) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(URL_XMLTV).openConnection();
        c.setRequestProperty("User-Agent", "OnE+/1.0");
        c.setRequestProperty("Accept-Encoding", "gzip");
        c.setConnectTimeout(20000);
        c.setReadTimeout(60000);
        c.setInstanceFollowRedirects(true);
        int code = c.getResponseCode();
        if (code < 200 || code >= 300) throw new IOException("HTTP " + code);
        InputStream in = c.getInputStream();
        String enc = c.getHeaderField("Content-Encoding");
        if (enc != null && enc.contains("gzip")) in = new GZIPInputStream(in);
        try (OutputStream os = new FileOutputStream(out)) {
            byte[] buf = new byte[8192]; int n;
            while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
        } finally { in.close(); }
    }

    private void parse(File f) throws Exception {
        byMapClear();
        XmlPullParser p = Xml.newPullParser();
        InputStream in = new BufferedInputStream(new FileInputStream(f));
        // sniff gzip magic
        in.mark(2); int b0 = in.read(), b1 = in.read(); in.reset();
        if (b0 == 0x1f && b1 == 0x8b) in = new GZIPInputStream(in);
        p.setInput(in, null);
        int ev = p.getEventType();
        Prog cur = null; String channelId = null; StringBuilder title = null;
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) {
                if ("programme".equals(p.getName())) {
                    cur = new Prog();
                    cur.start = parseTime(p.getAttributeValue(null, "start"));
                    cur.stop  = parseTime(p.getAttributeValue(null, "stop"));
                    channelId = p.getAttributeValue(null, "channel");
                } else if (cur != null && "title".equals(p.getName())) {
                    title = new StringBuilder();
                }
            } else if (ev == XmlPullParser.TEXT && title != null) {
                title.append(p.getText());
            } else if (ev == XmlPullParser.END_TAG) {
                if ("title".equals(p.getName()) && title != null) {
                    cur.title = title.toString().trim();
                    title = null;
                } else if ("programme".equals(p.getName())) {
                    if (cur != null && channelId != null && cur.title != null && cur.start > 0) {
                        List<Prog> list = byId.get(channelId);
                        if (list == null) { list = new ArrayList<>(); byId.put(channelId, list); }
                        list.add(cur);
                    }
                    cur = null; channelId = null;
                }
            }
            ev = p.next();
        }
        in.close();
    }

    private synchronized void byMapClear() { byId.clear(); }

    /** Returns "title · 20:30-21:25" for the programme on tvgId now, or null. */
    public String currentLabel(String tvgId) {
        if (!ready || tvgId == null || tvgId.isEmpty()) return null;
        List<Prog> list = byId.get(tvgId);
        if (list == null) return null;
        long now = System.currentTimeMillis();
        for (Prog pr : list) {
            if (pr.start <= now && now < pr.stop) {
                return pr.title + "  ·  " + hm(pr.start) + "-" + hm(pr.stop);
            }
        }
        return null;
    }

    private static final SimpleDateFormat HM = new SimpleDateFormat("HH:mm", Locale.ROOT);
    private static String hm(long ms) { return HM.format(new Date(ms)); }

    // XMLTV time: yyyyMMddHHmmss ±zzzz
    private static long parseTime(String s) {
        if (s == null || s.length() < 14) return 0;
        try {
            int y = Integer.parseInt(s.substring(0,4));
            int M = Integer.parseInt(s.substring(4,6));
            int d = Integer.parseInt(s.substring(6,8));
            int h = Integer.parseInt(s.substring(8,10));
            int mi = Integer.parseInt(s.substring(10,12));
            int se = Integer.parseInt(s.substring(12,14));
            Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            c.set(y, M-1, d, h, mi, se); c.set(Calendar.MILLISECOND, 0);
            long t = c.getTimeInMillis();
            int sp = s.indexOf(' ', 14);
            if (sp > 0 && s.length() >= sp + 6) {
                String tz = s.substring(sp+1);
                int sign = tz.charAt(0) == '-' ? 1 : -1; // subtract offset to get UTC
                int oh = Integer.parseInt(tz.substring(1,3));
                int om = Integer.parseInt(tz.substring(3,5));
                t += sign * (oh*3600_000L + om*60_000L);
            }
            return t;
        } catch (Exception e) { return 0; }
    }

    private static class Prog { long start, stop; String title; }
}
