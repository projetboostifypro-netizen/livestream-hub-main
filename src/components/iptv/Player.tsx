import { useEffect, useMemo, useRef, useState } from "react";
import { Loader2, AlertCircle, Copy, ExternalLink } from "lucide-react";
import { toast } from "sonner";

interface PlayerProps {
  streamUrl: string | null;
  channelName?: string;
}

function proxify(url: string) {
  return `/api/public/stream/${encodeURIComponent(btoa(url))}`;
}

// Xtream Codes: convert a live .ts URL to the HLS variant.
function toHlsIfPossible(url: string): string {
  try {
    const u = new URL(url);
    const m = u.pathname.match(/^\/([^/]+)\/([^/]+)\/(\d+)\.ts$/i);
    if (m) {
      u.pathname = `/live/${m[1]}/${m[2]}/${m[3]}.m3u8`;
      return u.toString();
    }
    return url;
  } catch {
    return url;
  }
}

// Revert HLS m3u8 back to original .ts URL (Xtream Codes pattern).
function toTsIfPossible(url: string): string {
  try {
    const u = new URL(url);
    const m = u.pathname.match(/^\/live\/([^/]+)\/([^/]+)\/(\d+)\.m3u8$/i);
    if (m) {
      u.pathname = `/${m[1]}/${m[2]}/${m[3]}.ts`;
      return u.toString();
    }
    return url;
  } catch {
    return url;
  }
}

// HEAD/GET probe through our proxy to check if the HLS variant actually serves.
async function probeUrl(url: string, timeoutMs = 4000): Promise<boolean> {
  try {
    const ctrl = new AbortController();
    const timer = setTimeout(() => ctrl.abort(), timeoutMs);
    const res = await fetch(proxify(url), { method: "GET", signal: ctrl.signal, headers: { Range: "bytes=0-0" } });
    clearTimeout(timer);
    if (!res.ok && res.status !== 206) return false;
    const ct = res.headers.get("content-type") || "";
    // Accept HLS manifest content-types or anything non-empty 2xx
    return ct.includes("mpegurl") || ct.includes("application/") || ct.includes("text/") || res.status < 300;
  } catch {
    return false;
  }
}

function toVlcIntent(url: string) {
  return `vlc://${url}`;
}

export function Player({ streamUrl, channelName }: PlayerProps) {
  const videoRef = useRef<HTMLVideoElement>(null);
  const playerRef = useRef<{ destroy: () => void } | null>(null);
  const [status, setStatus] = useState<"idle" | "loading" | "playing" | "error">("idle");
  const [errorMsg, setErrorMsg] = useState<string>("");
  const externalUrl = useMemo(() => (streamUrl ? toVlcIntent(streamUrl) : ""), [streamUrl]);

  const copyStreamUrl = async () => {
    if (!streamUrl) return;
    await navigator.clipboard.writeText(streamUrl);
    toast.success("Lien du flux copié");
  };

  useEffect(() => {
    const video = videoRef.current;
    if (!video || !streamUrl) {
      setStatus("idle");
      return;
    }

    setStatus("loading");
    setErrorMsg("");
    let cancelled = false;

    async function load() {
      try {
        // Try HLS first if available; probe it; otherwise fall back to original TS.
        const hlsCandidate = toHlsIfPossible(streamUrl!);
        const hasHlsCandidate = hlsCandidate !== streamUrl;
        let effectiveUrl = streamUrl!;
        if (hasHlsCandidate) {
          const ok = await probeUrl(hlsCandidate);
          if (cancelled) return;
          effectiveUrl = ok ? hlsCandidate : toTsIfPossible(streamUrl!);
        } else if (streamUrl!.toLowerCase().includes(".m3u8")) {
          // Already HLS — probe; fallback to TS equivalent if it fails.
          const ok = await probeUrl(streamUrl!);
          if (cancelled) return;
          if (!ok) effectiveUrl = toTsIfPossible(streamUrl!);
        }
        const proxied = proxify(effectiveUrl);

        if (playerRef.current) {
          playerRef.current.destroy();
          playerRef.current = null;
        }

        const lower = effectiveUrl.toLowerCase();
        const isHls = lower.includes(".m3u8");

        if (isHls) {
          const Hls = (await import("hls.js")).default;
          if (cancelled) return;
          if (Hls.isSupported()) {
            const hls = new Hls({ enableWorker: true, lowLatencyMode: true });
            hls.loadSource(proxied);
            hls.attachMedia(video!);
            hls.on(Hls.Events.MANIFEST_PARSED, () => video!.play().catch(() => {}));
            let fellBack = false;
            hls.on(Hls.Events.ERROR, async (_e, data) => {
              if (!data.fatal) return;
              // Fallback: try original TS via mpegts.js
              if (!fellBack) {
                fellBack = true;
                const tsUrl = toTsIfPossible(effectiveUrl);
                if (tsUrl !== effectiveUrl) {
                  try { hls.destroy(); } catch { /* noop */ }
                  await playMpegTs(tsUrl);
                  return;
                }
              }
              setStatus("error");
              setErrorMsg(data.details || "Erreur de lecture HLS");
            });
            playerRef.current = { destroy: () => hls.destroy() };
          } else if (video!.canPlayType("application/vnd.apple.mpegurl")) {
            video!.src = proxied;
            video!.play().catch(() => {});
          }
        } else {
          await playMpegTs(effectiveUrl);
        }

        const onPlaying = () => setStatus("playing");
        const onError = () => {
          setStatus("error");
          setErrorMsg("Impossible de charger ce flux");
        };
        video!.addEventListener("playing", onPlaying);
        video!.addEventListener("error", onError);
        return () => {
          video!.removeEventListener("playing", onPlaying);
          video!.removeEventListener("error", onError);
        };
      } catch (err) {
        setStatus("error");
        setErrorMsg(err instanceof Error ? err.message : "Erreur inconnue");
      }
    }

    async function playMpegTs(url: string) {
      const mpegts = (await import("mpegts.js")).default;
      if (cancelled) return;
      mpegts.LoggingControl.enableError = false;
      const proxied = proxify(url);
      if (mpegts.getFeatureList().mseLivePlayback) {
        const player = mpegts.createPlayer(
          { type: "mpegts", isLive: true, url: proxied, cors: false },
          { seekType: "param", enableStashBuffer: false, liveBufferLatencyChasing: true, lazyLoad: false },
        );
        player.attachMediaElement(video!);
        player.load();
        const playResult = player.play() as void | Promise<void>;
        if (playResult && typeof (playResult as Promise<void>).catch === "function") {
          (playResult as Promise<void>).catch(() => {});
        }
        player.on(mpegts.Events.ERROR, (type: string, detail: string, info?: { code?: number; msg?: string }) => {
          setStatus("error");
          setErrorMsg(
            info?.code === 403
              ? "Flux refusé par le fournisseur depuis cette connexion. Essayez le lecteur externe depuis votre appareil."
              : `${type}: ${detail}`,
          );
        });
        playerRef.current = {
          destroy: () => {
            try { player.unload(); player.detachMediaElement(); player.destroy(); } catch { /* noop */ }
          },
        };
      } else {
        video!.src = proxied;
        video!.play().catch(() => {});
      }
    }

    const cleanupPromise = load();
    return () => {
      cancelled = true;
      cleanupPromise.then((fn) => fn && fn());
      if (playerRef.current) {
        playerRef.current.destroy();
        playerRef.current = null;
      }
      if (video) {
        video.removeAttribute("src");
        video.load();
      }
    };
  }, [streamUrl]);

  return (
    <div className="relative aspect-video w-full overflow-hidden rounded-xl bg-black shadow-[var(--shadow-glow)]">
      <video
        ref={videoRef}
        controls
        autoPlay
        playsInline
        className="h-full w-full"
      />
      {status === "idle" && (
        <div className="absolute inset-0 flex flex-col items-center justify-center text-muted-foreground">
          <div className="text-6xl">📺</div>
          <p className="mt-3 text-sm">Sélectionnez une chaîne pour commencer</p>
        </div>
      )}
      {status === "loading" && (
        <div className="absolute inset-0 flex flex-col items-center justify-center bg-black/60 text-foreground">
          <Loader2 className="h-10 w-10 animate-spin text-primary" />
          <p className="mt-3 text-sm">Chargement {channelName ? `· ${channelName}` : ""}…</p>
        </div>
      )}
      {status === "error" && (
        <div className="absolute inset-0 flex flex-col items-center justify-center bg-black/70 px-4 text-center">
          <AlertCircle className="h-10 w-10 text-destructive" />
          <p className="mt-3 text-sm font-medium">Lecture impossible</p>
          <p className="mt-1 max-w-md text-xs text-muted-foreground">{errorMsg}</p>
          {streamUrl && (
            <div className="mt-4 flex flex-wrap justify-center gap-2">
              <a
                href={externalUrl}
                className="inline-flex items-center gap-2 rounded-lg border border-border bg-secondary px-3 py-2 text-xs font-medium text-foreground transition hover:bg-accent"
              >
                <ExternalLink className="h-4 w-4" />
                Ouvrir dans VLC
              </a>
              <button
                type="button"
                onClick={copyStreamUrl}
                className="inline-flex items-center gap-2 rounded-lg border border-border bg-secondary px-3 py-2 text-xs font-medium text-foreground transition hover:bg-accent"
              >
                <Copy className="h-4 w-4" />
                Copier le lien
              </button>
            </div>
          )}
        </div>
      )}
    </div>
  );
}