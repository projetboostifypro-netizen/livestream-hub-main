import { createFileRoute, Link } from "@tanstack/react-router";
import { useServerFn } from "@tanstack/react-start";
import { useQuery } from "@tanstack/react-query";
import { Loader2, Play, Info, Tv, Lock } from "lucide-react";
import { getMyBrowseRows } from "@/lib/iptv.functions";
import { getActiveSubscription } from "@/lib/subscriptions.functions";
import { ChannelRow } from "@/components/iptv/ChannelRow";

export const Route = createFileRoute("/_authenticated/browse")({
  head: () => ({ meta: [{ title: "Accueil — FLOW+" }] }),
  component: BrowsePage,
});

function BrowsePage() {
  const browseFn = useServerFn(getMyBrowseRows);
  const subFn = useServerFn(getActiveSubscription);

  const browseQ = useQuery({
    queryKey: ["browse-rows"],
    queryFn: () => browseFn({ data: { perRow: 20, maxGroups: 15 } }),
    retry: false,
  });
  const subQ = useQuery({ queryKey: ["active-sub"], queryFn: () => subFn() });

  if (browseQ.isLoading) {
    return (
      <div className="flex min-h-[80vh] items-center justify-center">
        <Loader2 className="h-10 w-10 animate-spin text-primary" />
      </div>
    );
  }

  const hasSub = !!subQ.data?.subscription;
  const featured = browseQ.data?.featured;

  if (!hasSub || browseQ.isError) {
    return (
      <div className="mx-auto max-w-md p-8 text-center">
        <Lock className="mx-auto h-12 w-12 text-primary" />
        <h1 className="mt-4 text-2xl font-bold">Abonnement requis</h1>
        <p className="mt-2 text-sm text-muted-foreground">
          Souscrivez à un plan pour accéder à vos chaînes.
        </p>
        <Link
          to="/_authenticated/plans"
          className="mt-6 inline-flex items-center gap-2 rounded-md bg-primary px-6 py-3 text-sm font-bold text-primary-foreground"
        >
          <Info className="h-4 w-4" /> Voir les plans
        </Link>
      </div>
    );
  }

  return (
    <div className="pb-20">
      {/* Hero */}
      <section className="relative -mt-16 h-[70vh] min-h-[480px] w-full overflow-hidden">
        <div className="absolute inset-0" style={{ background: "radial-gradient(ellipse at center, oklch(0.20 0.10 25 / 0.5), transparent 70%), var(--gradient-surface)" }} />
        {featured?.logo && (
          <img src={featured.logo} alt="" className="absolute inset-0 h-full w-full object-cover opacity-20 blur-sm" onError={(e) => ((e.currentTarget.style.display = "none"))} />
        )}
        <div className="absolute inset-0" style={{ background: "var(--gradient-hero)" }} />
        <div className="relative z-10 mx-auto flex h-full max-w-[1600px] flex-col justify-end px-4 pb-16 md:px-8">
          <div className="max-w-2xl">
            {!hasSub && (
              <span className="mb-3 inline-block rounded-full bg-primary/20 px-3 py-1 text-xs font-bold text-primary">
                ABONNEMENT REQUIS
              </span>
            )}
            <h1 className="text-4xl font-black md:text-6xl">
              {featured?.name ?? "Bienvenue sur FLOW+"}
            </h1>
            <p className="mt-3 max-w-xl text-base text-foreground/90 md:text-lg">
              {featured?.group_title ? `Catégorie : ${featured.group_title}` : ""}
              {hasSub ? " · Profitez du live en illimité." : " · Souscrivez à un plan pour démarrer la lecture."}
            </p>
            <div className="mt-6 flex flex-wrap gap-3">
              {featured && (
                <Link
                  to="/_authenticated/channel/$id"
                  params={{ id: String(featured.id) }}
                  className="inline-flex items-center gap-2 rounded-md bg-white px-6 py-3 text-sm font-bold text-black transition hover:bg-white/90"
                >
                  <Play className="h-5 w-5 fill-current" /> Lecture
                </Link>
              )}
              <Link
                to="/_authenticated/plans"
                className="inline-flex items-center gap-2 rounded-md bg-secondary/80 px-6 py-3 text-sm font-bold text-foreground backdrop-blur transition hover:bg-accent"
              >
                <Info className="h-5 w-5" /> {hasSub ? "Voir les plans" : "S'abonner"}
              </Link>
            </div>
          </div>
        </div>
      </section>

      {/* Rows */}
      <div className="relative z-10 -mt-20 space-y-4">
        {browseQ.data?.rows.map((row: { group: string; channels: Array<{ id: number; name: string; logo: string | null; group_title: string | null }> }) => (
          <ChannelRow key={row.group} title={row.group} channels={row.channels} />
        ))}
        {browseQ.data?.rows.length === 0 && (
          <div className="mx-auto mt-20 max-w-md px-6 text-center">
            <Tv className="mx-auto h-12 w-12 text-muted-foreground" />
            <h3 className="mt-4 text-lg font-bold">Aucune chaîne disponible</h3>
            <p className="mt-2 text-sm text-muted-foreground">La playlist n'est pas encore synchronisée.</p>
          </div>
        )}
      </div>
    </div>
  );
}