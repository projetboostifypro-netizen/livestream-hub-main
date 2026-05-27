import { createFileRoute, Link, useNavigate } from "@tanstack/react-router";
import { useServerFn } from "@tanstack/react-start";
import { useQuery } from "@tanstack/react-query";
import { Loader2, Play, Lock, ArrowLeft, Crown } from "lucide-react";
import { useState } from "react";
import { getMyChannelById } from "@/lib/iptv.functions";
import { getActiveSubscription } from "@/lib/subscriptions.functions";
import { Player } from "@/components/iptv/Player";

export const Route = createFileRoute("/_authenticated/channel/$id")({
  component: ChannelPage,
});

function ChannelPage() {
  const { id } = Route.useParams();
  const navigate = useNavigate();
  const channelFn = useServerFn(getMyChannelById);
  const subFn = useServerFn(getActiveSubscription);
  const [playing, setPlaying] = useState(false);

  const channelQ = useQuery({
    queryKey: ["channel", id],
    queryFn: () => channelFn({ data: { id: Number(id) } }),
    retry: false,
  });
  const subQ = useQuery({ queryKey: ["active-sub"], queryFn: () => subFn() });

  if (channelQ.isLoading || subQ.isLoading) {
    return <div className="flex min-h-[80vh] items-center justify-center"><Loader2 className="h-10 w-10 animate-spin text-primary" /></div>;
  }

  const ch = channelQ.data?.channel;
  if (!ch) {
    return (
      <div className="mx-auto max-w-md p-8 text-center">
        <h1 className="text-2xl font-bold">Chaîne introuvable</h1>
        <Link to="/_authenticated/browse" className="mt-4 inline-block text-primary hover:underline">← Retour</Link>
      </div>
    );
  }

  const hasSub = !!subQ.data?.subscription;

  return (
    <div className="min-h-screen pb-20">
      <div className="relative">
        <button onClick={() => navigate({ to: "/_authenticated/browse" })} className="absolute left-4 top-4 z-20 inline-flex items-center gap-2 rounded-full bg-black/60 px-3 py-2 text-sm font-semibold backdrop-blur hover:bg-black/80">
          <ArrowLeft className="h-4 w-4" /> Retour
        </button>
        {playing && hasSub ? (
          <div className="mx-auto max-w-5xl px-4 pt-16">
            <Player streamUrl={ch.stream_url} channelName={ch.name} />
          </div>
        ) : (
          <div className="relative h-[60vh] min-h-[400px] overflow-hidden">
            {ch.logo && (
              <img src={ch.logo} alt="" className="absolute inset-0 h-full w-full object-cover opacity-30 blur-xl" onError={(e) => ((e.currentTarget.style.display = "none"))} />
            )}
            <div className="absolute inset-0" style={{ background: "var(--gradient-hero)" }} />
            <div className="relative z-10 mx-auto flex h-full max-w-5xl flex-col items-center justify-center gap-6 px-4 text-center">
              {ch.logo ? (
                <img src={ch.logo} alt={ch.name} className="h-32 w-32 object-contain rounded-xl bg-black/40 p-4 backdrop-blur" onError={(e) => ((e.currentTarget.style.display = "none"))} />
              ) : null}
              <div>
                <h1 className="text-4xl font-black md:text-5xl">{ch.name}</h1>
                {ch.group_title && <p className="mt-2 text-base text-muted-foreground">{ch.group_title}</p>}
              </div>
              {hasSub ? (
                <button onClick={() => setPlaying(true)} className="inline-flex items-center gap-2 rounded-md bg-white px-8 py-4 text-base font-bold text-black hover:bg-white/90">
                  <Play className="h-6 w-6 fill-current" /> Lecture
                </button>
              ) : (
                <div className="flex flex-col items-center gap-3 rounded-2xl border border-border bg-card/80 p-6 backdrop-blur">
                  <Lock className="h-10 w-10 text-primary" />
                  <h2 className="text-xl font-bold">Abonnement requis</h2>
                  <p className="max-w-md text-sm text-muted-foreground">Souscrivez à un plan pour démarrer la lecture de cette chaîne.</p>
                  <Link to="/_authenticated/plans" className="inline-flex items-center gap-2 rounded-md px-6 py-3 text-sm font-bold text-primary-foreground shadow-[var(--shadow-glow)]" style={{ background: "var(--gradient-primary)" }}>
                    <Crown className="h-4 w-4" /> Voir les abonnements
                  </Link>
                </div>
              )}
            </div>
          </div>
        )}
      </div>

      {hasSub && subQ.data?.subscription && (
        <div className="mx-auto mt-6 max-w-5xl px-4">
          <div className="rounded-lg border border-border bg-card p-4 text-sm">
            <Crown className="mr-2 inline h-4 w-4" style={{ color: "var(--gold)" }} />
            Abonnement actif jusqu'au{" "}
            <strong>{new Date(subQ.data.subscription.expires_at).toLocaleString("fr-FR")}</strong>
          </div>
        </div>
      )}
    </div>
  );
}