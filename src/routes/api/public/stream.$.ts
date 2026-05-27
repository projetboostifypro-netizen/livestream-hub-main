import { createFileRoute } from "@tanstack/react-router";

// Proxies an upstream IPTV stream URL so the browser can play it
// without CORS issues. Usage: /api/public/stream/<base64-encoded-url>
// When the upstream returns an HLS playlist (.m3u8), we rewrite every
// segment / sub-playlist URL inside so they also flow through this
// proxy. Otherwise the browser tries to fetch raw .ts segments directly
// from the IPTV server and CORS blocks them — which is the #1 reason
// browser-based IPTV "doesn't work" while native apps like Televizo do.

function b64urlEncode(s: string) {
  // Use URL-safe base64 (browser btoa equivalent)
  const b = typeof btoa === "function" ? btoa(s) : Buffer.from(s, "binary").toString("base64");
  return b;
}

function proxyPathFor(absoluteUrl: string) {
  return `/api/public/stream/${encodeURIComponent(b64urlEncode(absoluteUrl))}`;
}

function rewriteM3U8(text: string, baseUrl: URL): string {
  return text
    .split(/\r?\n/)
    .map((line) => {
      const trimmed = line.trim();
      if (!trimmed) return line;
      // Rewrite URI="..." inside tags like #EXT-X-KEY, #EXT-X-MAP, #EXT-X-MEDIA
      if (trimmed.startsWith("#")) {
        return line.replace(/URI="([^"]+)"/g, (_m, u) => {
          try {
            const abs = new URL(u, baseUrl).toString();
            return `URI="${proxyPathFor(abs)}"`;
          } catch {
            return _m;
          }
        });
      }
      // Bare URL line (segment or sub-playlist)
      try {
        const abs = new URL(trimmed, baseUrl).toString();
        return proxyPathFor(abs);
      } catch {
        return line;
      }
    })
    .join("\n");
}

export const Route = createFileRoute("/api/public/stream/$")({
  server: {
    handlers: {
      GET: async ({ params, request }) => {
        const encoded = params._splat ?? "";
        let target: string;
        try {
          target = atob(decodeURIComponent(encoded));
        } catch {
          return new Response("Invalid stream URL", { status: 400 });
        }
        let url: URL;
        try {
          url = new URL(target);
        } catch {
          return new Response("Invalid stream URL", { status: 400 });
        }
        if (url.protocol !== "http:" && url.protocol !== "https:") {
          return new Response("Forbidden", { status: 403 });
        }

        const range = request.headers.get("range");

        // ---- Edge cache fan-out -------------------------------------------------
        // The upstream IPTV account only allows 1 concurrent connection. By
        // caching the manifest (~2s) and segments (~30s) on the edge, thousands
        // of viewers watching the same channel share a single upstream pull.
        // Range requests bypass the cache (rarely used for live HLS).
        const isLikelyManifest = /\.m3u8(\?|$)/i.test(url.pathname + url.search);
        const isLikelySegment = /\.(ts|m4s|aac|mp4)(\?|$)/i.test(url.pathname + url.search);
        const cache = (globalThis as any).caches?.default as Cache | undefined;
        const cacheKey = new Request(`https://stream-cache.internal/${encoded}`, { method: "GET" });
        const canCache = !range && (isLikelyManifest || isLikelySegment);
        if (canCache && cache) {
          const hit = await cache.match(cacheKey);
          if (hit) {
            const h = new Headers(hit.headers);
            h.set("access-control-allow-origin", "*");
            h.set("x-cache", "HIT");
            return new Response(hit.body, { status: hit.status, headers: h });
          }
        }

        const upstream = await fetch(url.toString(), {
          // Follow Xtream Codes redirects (often 302 to the real CDN)
          redirect: "follow",
          headers: {
            // Pretend to be VLC — Xtream providers whitelist this UA.
            "User-Agent": "VLC/3.0.20 LibVLC/3.0.20",
            Accept: "*/*",
            ...(range ? { Range: range } : {}),
          },
        });

        const ct = (upstream.headers.get("content-type") || "").toLowerCase();
        const isManifest =
          ct.includes("mpegurl") ||
          ct.includes("vnd.apple.mpegurl") ||
          ct.includes("x-mpegurl") ||
          /\.m3u8(\?|$)/i.test(url.pathname + url.search);

        // For HLS playlists, rewrite all inner URLs so segments also pass
        // through this proxy. Browsers fetch sub-resources of the manifest
        // directly against the host it returned, so without rewriting CORS
        // blocks every segment download.
        if (isManifest && upstream.ok) {
          const text = await upstream.text();
          // Final URL after redirects (TanStack/undici sets response.url)
          const finalBase = new URL(upstream.url || url.toString());
          const rewritten = rewriteM3U8(text, finalBase);
          const headers = new Headers({
            "content-type": "application/vnd.apple.mpegurl",
            "access-control-allow-origin": "*",
            "access-control-expose-headers": "*",
            // Short edge cache: live manifest refreshes every ~2s, but during
            // that window thousands of viewers share one upstream pull.
            "cache-control": "public, max-age=2",
          });
          const res = new Response(rewritten, { status: upstream.status, headers });
          if (cache && upstream.ok) {
            try { await cache.put(cacheKey, res.clone()); } catch {}
          }
          return res;
        }

        const headers = new Headers();
        const passthrough = ["content-type", "content-length", "accept-ranges", "content-range"];
        for (const h of passthrough) {
          const v = upstream.headers.get(h);
          if (v) headers.set(h, v);
        }
        if (!headers.get("content-type")) headers.set("content-type", "video/mp2t");
        headers.set("access-control-allow-origin", "*");
        headers.set("access-control-expose-headers", "*");
        headers.set("cache-control", "no-store");

        // Segments are immutable — cache for ~30s so a fleet of viewers
        // pulls them once from upstream.
        if (isLikelySegment && upstream.ok) {
          headers.set("cache-control", "public, max-age=30, immutable");
        }
        const res = new Response(upstream.body, { status: upstream.status, headers });
        if (canCache && cache && upstream.ok && upstream.status === 200) {
          try { await cache.put(cacheKey, res.clone()); } catch {}
        }
        return res;
      },
      OPTIONS: async () =>
        new Response(null, {
          status: 204,
          headers: {
            "access-control-allow-origin": "*",
            "access-control-allow-methods": "GET, OPTIONS",
            "access-control-allow-headers": "*",
          },
        }),
    },
  },
});