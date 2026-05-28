import { useEffect, useMemo, useRef, useState } from "react";
import { Loader2, AlertCircle, Copy, ExternalLink } from "lucide-react";
import { toast } from "sonner";

interface PlayerProps {
  streamUrl: string | null;
  channelName?: string;
}

// ─── Constantes ─────────────────────────────────────────────────────────────
const RECONNECT_INTERVAL_MS = 5_000;   // 5 s entre chaque tentative silencieuse
const MAX_SILENT_RETRIES    = 12;      // ~60 s avant d'afficher l'erreur à l'utilisateur

// ─── Helpers ─────────────────────────────────────────────────────────────────
function proxify(url: string) {
  return `/api/public/stream/${encodeURIComponent(btoa(url))}`;
}

function toHlsIfPossible(url: string): string {
  try {
    const u = new URL(url);
    const m = u.pathname.match(/^\/([^/]+)\/([^/]+)\/(\d+)\.ts$/i);
    if (m) { u.pathname = `/live/${m[1]}/${m[2]}/${m[3]}.m3u8`; return u.toString(); }
    return url;
  } catch { return url; }
}

function toTsIfPossible(url: string): string {
  try {
    const u = new URL(url);
    const m = u.pathname.match(/^\/live\/([^/]+)\/([^/]+)\/(\d+)\.m3u8$/i);
    if (m) { u.pathname = `/${m[1]}/${m[2]}/${m[3]}.ts`; return u.toString(); }
    return url;
  } catch { return url; }
}

async function probeUrl(url: string, timeoutMs = 4000): Promise<boolean> {
  try {
    const ctrl = new AbortController();
    const timer = setTimeout(() => ctrl.abort(), timeoutMs);
    const res = await fetch(proxify(url), { method: "GET", signal: ctrl.signal, headers: { Range: "bytes=0-0" } });
    clearTimeout(timer);
    if (!res.ok && res.status !== 206) return false;
    const ct = res.headers.get("content-type") || "";
    return ct.includes("mpegurl") || ct.includes("application/") || ct.includes("text/") || res.status < 300;
  } catch { return false; }
}

function toVlcIntent(url: string) { return `vlc://${url}`; }

// ─── Composant ───────────────────────────────────────────────────────────────
export function Player({ streamUrl, channelName }: PlayerProps) {
  const videoRef    = useRef<HTMLVideoElement>(null);
  const playerRef   = useRef<{ destroy: () => void } | null>(null);
  const retryCount  = useRef(0);
  const retryTimer  = useRef<ReturnType<typeof setTimeout> | null>(null);
  const destroyed   = useRef(false);
  const effectiveUrlRef = useRef<string>("");

  const [status,   setStatus]   = useState<"idle" | "loading" | "playing" | "error">("idle");
  const [errorMsg, setErrorMsg] = useState<string>("");

  const externalUrl = useMemo(() => (streamUrl ? toVlcIntent(streamUrl) : ""), [streamUrl]);

  const copyStreamUrl = async () => {
    if (!streamUrl) return;
    await navigator.clipboard.writeText(streamUrl);
    toast.success("Lien du flux copié");
  };

  // ── Annule le timer de reconnexion en cours ────────────────────────────────
  function clearRetry() {
    if (retryTimer.current) { clearTimeout(retryTimer.current); retryTimer.current = null; }
  }

  // ── Détruit le lecteur actif ───────────────────────────────────────────────
  function destroyPlayer() {
    clearRetry();
    if (playerRef.current) { playerRef.current.destroy(); playerRef.current = null; }
    const video = videoRef.current;
    if (video) { video.removeAttribute("src"); video.load(); }
  }

  // ── Planifie une reconnexion silencieuse dans 5 s ─────────────────────────
  function scheduleReconnect() {
    if (destroyed.current) return;
    clearRetry();
    retryCount.current += 1;
    if (retryCount.current > MAX_SILENT_RETRIES) {
      // Trop de tentatives → on affiche l'erreur
      setStatus("error");
      setErrorMsg("Signal indisponible après plusieurs tentatives. Vérifiez votre connexion ou réessayez plus tard.");
      return;
    }
    // Reconnexion silencieuse : l'utilisateur voit toujours le lecteur, pas d'écran d'erreur
    retryTimer.current = setTimeout(() => {
      if (destroyed.current) return;
      const video = videoRef.current;
      if (!video || !effectiveUrlRef.current) return;
      if (playerRef.current) { playerRef.current.destroy(); playerRef.current = null; }
      video.removeAttribute("src");
      video.load();
      const lower = effectiveUrlRef.current.toLowerCase();
      if (lower.includes(".m3u8")) {
        void loadHls(effectiveUrlRef.current, video);
      } else {
        void playMpegTs(effectiveUrlRef.current, video);
      }
    }, RECONNECT_INTERVAL_MS);
  }

  // ── Charge un flux HLS ────────────────────────────────────────────────────
  async function loadHls(url: string, video: HTMLVideoElement) {
    const proxied = proxify(url);
    const Hls = (await import("hls.js")).default;
    if (destroyed.current) return;
    if (Hls.isSupported()) {
      const hls = new Hls({
        enableWorker: true,
        lowLatencyMode: true,
        fragLoadingMaxRetry: 3,
        manifestLoadingMaxRetry: 3,
        levelLoadingMaxRetry: 3,
      });
      hls.loadSource(proxied);
      hls.attachMedia(video);
      hls.on(Hls.Events.MANIFEST_PARSED, () => {
        retryCount.current = 0; // réinitialise le compteur de reconnexions
        video.play().catch(() => {});
      });
      hls.on(Hls.Events.ERROR, async (_e, data) => {
        if (!data.fatal || destroyed.current) return;
        // Essai de fallback vers TS avant de planifier reconnexion
        const tsUrl = toTsIfPossible(url);
        if (tsUrl !== url) {
          try { hls.destroy(); } catch { /* noop */ }
          effectiveUrlRef.current = tsUrl;
          await playMpegTs(tsUrl, video);
          return;
        }
        try { hls.destroy(); } catch { /* noop */ }
        playerRef.current = null;
        scheduleReconnect(); // retry silencieux
      });
      playerRef.current = { destroy: () => hls.destroy() };
    } else if (video.canPlayType("application/vnd.apple.mpegurl")) {
      video.src = proxied;
      video.play().catch(() => {});
    }
  }

  // ── Charge un flux MPEG-TS ────────────────────────────────────────────────
  async function playMpegTs(url: string, video: HTMLVideoElement) {
    const mpegts = (await import("mpegts.js")).default;
    if (destroyed.current) return;
    mpegts.LoggingControl.enableError = false;
    const proxied = proxify(url);
    if (mpegts.getFeatureList().mseLivePlayback) {
      const player = mpegts.createPlayer(
        { type: "mpegts", isLive: true, url: proxied, cors: false },
        { seekType: "param", enableStashBuffer: false, liveBufferLatencyChasing: true, lazyLoad: false },
      );
      player.attachMediaElement(video);
      player.load();
      const playResult = player.play() as void | Promise<void>;
      if (playResult && typeof (playResult as Promise<void>).catch === "function") {
        (playResult as Promise<void>).catch(() => {});
      }
      player.on(mpegts.Events.ERROR, (_type: string, _detail: string, info?: { code?: number }) => {
        if (destroyed.current) return;
        if (info?.code === 403) {
          // 403 = refus serveur → inutile de retry, montrer l'erreur
          setStatus("error");
          setErrorMsg("Flux refusé par le fournisseur depuis cette connexion. Essayez le lecteur externe depuis votre appareil.");
          return;
        }
        try { player.unload(); player.detachMediaElement(); player.destroy(); } catch { /* noop */ }
        playerRef.current = null;
        scheduleReconnect(); // retry silencieux
      });
      playerRef.current = {
        destroy: () => {
          try { player.unload(); player.detachMediaElement(); player.destroy(); } catch { /* noop */ }
        },
      };
    } else {
      video.src = proxied;
      video.play().catch(() => {});
    }
  }

  // ── Effet principal : lance la lecture à chaque changement de streamUrl ────
  useEffect(() => {
    const video = videoRef.current;
    if (!video || !streamUrl) { setStatus("idle"); return; }

    destroyed.current = false;
    retryCount.current = 0;
    destroyPlayer();
    setStatus("loading");
    setErrorMsg("");

    let cancelled = false;

    async function load() {
      try {
        // Déterminer l'URL effective (HLS ou TS)
        const hlsCandidate = toHlsIfPossible(streamUrl!);
        const hasHlsCandidate = hlsCandidate !== streamUrl;
        let effectiveUrl = streamUrl!;
        if (hasHlsCandidate) {
          const ok = await probeUrl(hlsCandidate);
          if (cancelled) return;
          effectiveUrl = ok ? hlsCandidate : toTsIfPossible(streamUrl!);
        } else if (streamUrl!.toLowerCase().includes(".m3u8")) {
          const ok = await probeUrl(streamUrl!);
          if (cancelled) return;
          if (!ok) effectiveUrl = toTsIfPossible(streamUrl!);
        }
        effectiveUrlRef.current = effectiveUrl;

        const lower = effectiveUrl.toLowerCase();
        if (lower.includes(".m3u8")) {
          await loadHls(effectiveUrl, video);
        } else {
          await playMpegTs(effectiveUrl, video);
        }

        const onPlaying = () => { retryCount.current = 0; setStatus("playing"); };
        // Stall → reconnexion silencieuse automatique
        const onStall = () => {
          if (!destroyed.current && video.readyState < 3) scheduleReconnect();
        };
        const onError = () => {
          if (!destroyed.current) scheduleReconnect();
        };
        video.addEventListener("playing",  onPlaying);
        video.addEventListener("stalled",  onStall);
        video.addEventListener("error",    onError);
        return () => {
          video.removeEventListener("playing",  onPlaying);
          video.removeEventListener("stalled",  onStall);
          video.removeEventListener("error",    onError);
        };
      } catch (err) {
        if (!cancelled) scheduleReconnect(); // même en cas d'exception, on retry
      }
    }

    const cleanupPromise = load();
    return () => {
      cancelled = true;
      destroyed.current = true;
      cleanupPromise.then((fn) => fn && fn());
      destroyPlayer();
    };
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [streamUrl]);

  // ── Retry manuel (bouton) ─────────────────────────────────────────────────
  function handleRetry() {
    retryCount.current = 0;
    setStatus("loading");
    setErrorMsg("");
    const video = videoRef.current;
    if (!video || !effectiveUrlRef.current) return;
    destroyPlayer();
    destroyed.current = false;
    const lower = effectiveUrlRef.current.toLowerCase();
    if (lower.includes(".m3u8")) {
      void loadHls(effectiveUrlRef.current, video);
    } else {
      void playMpegTs(effectiveUrlRef.current, video);
    }
  }

  return (
    <div className="relative aspect-video w-full overflow-hidden rounded-xl bg-black shadow-[var(--shadow-glow)]">
      <video ref={videoRef} controls autoPlay playsInline className="h-full w-full" />

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
          <div className="mt-4 flex flex-wrap justify-center gap-2">
            <button
              type="button"
              onClick={handleRetry}
              className="inline-flex items-center gap-2 rounded-lg border border-primary bg-primary/20 px-3 py-2 text-xs font-medium text-foreground transition hover:bg-primary/30"
            >
              <Loader2 className="h-4 w-4" />
              Réessayer
            </button>
            {streamUrl && (
              <>
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
              </>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
