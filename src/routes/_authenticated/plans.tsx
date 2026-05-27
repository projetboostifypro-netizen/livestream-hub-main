import { createFileRoute, Link } from "@tanstack/react-router";
import { useServerFn } from "@tanstack/react-start";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Loader2, Check, Crown, Coins } from "lucide-react";
import { getPlans, purchasePlan, getActiveSubscription } from "@/lib/subscriptions.functions";
import { getProfile } from "@/lib/profile.functions";
import { toast } from "sonner";
import { Toaster } from "@/components/ui/sonner";

export const Route = createFileRoute("/_authenticated/plans")({
  head: () => ({ meta: [{ title: "Abonnements — FLOW+" }] }),
  component: PlansPage,
});

function formatDuration(minutes: number): string {
  if (minutes >= 1440) {
    const days = Math.floor(minutes / 1440);
    return days === 1 ? "1 jour" : `${days} jours`;
  }
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  if (m === 0) return `${h}h`;
  return `${h}h${String(m).padStart(2, "0")}`;
}

function PlansPage() {
  const qc = useQueryClient();
  const plansFn = useServerFn(getPlans);
  const buyFn = useServerFn(purchasePlan);
  const profileFn = useServerFn(getProfile);
  const subFn = useServerFn(getActiveSubscription);

  const plansQ = useQuery({ queryKey: ["plans"], queryFn: () => plansFn() });
  const profileQ = useQuery({ queryKey: ["profile"], queryFn: () => profileFn() });
  const subQ = useQuery({ queryKey: ["active-sub"], queryFn: () => subFn() });

  const buy = useMutation({
    mutationFn: (planId: number) => buyFn({ data: { planId } }),
    onSuccess: () => {
      toast.success("Abonnement activé !");
      qc.invalidateQueries({ queryKey: ["profile"] });
      qc.invalidateQueries({ queryKey: ["active-sub"] });
    },
    onError: (e) => toast.error(e instanceof Error ? e.message : "Erreur"),
  });

  const coins = profileQ.data?.profile?.coins ?? 0;
  const sub = subQ.data?.subscription;

  return (
    <div className="mx-auto max-w-6xl px-4 py-8 md:px-8">
      <Toaster theme="dark" position="top-right" />
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="text-3xl font-black md:text-4xl">Choisissez votre abonnement</h1>
          <p className="mt-2 text-muted-foreground">Payez avec vos pièces. 1 pièce = 1 FCFA.</p>
        </div>
        <div className="flex items-center gap-3">
          <div className="inline-flex items-center gap-2 rounded-full bg-card px-4 py-2 text-sm font-bold">
            <Coins className="h-4 w-4" style={{ color: "var(--gold)" }} />
            {coins} FCFA
          </div>
          <Link to="/_authenticated/coins" className="rounded-full px-4 py-2 text-sm font-bold text-primary-foreground" style={{ background: "var(--gradient-primary)" }}>
            Recharger
          </Link>
        </div>
      </div>

      {sub && (
        <div className="mt-6 rounded-xl border bg-card p-4" style={{ borderColor: "var(--gold)" }}>
          <div className="flex items-center gap-2 text-sm">
            <Crown className="h-4 w-4" style={{ color: "var(--gold)" }} />
            <span><strong>Abonnement actif</strong> jusqu'au {new Date(sub.expires_at).toLocaleString("fr-FR")}</span>
          </div>
        </div>
      )}

      {plansQ.isLoading ? (
        <div className="flex justify-center py-20"><Loader2 className="h-8 w-8 animate-spin" /></div>
      ) : (
        <div className="mt-8 grid gap-4 md:grid-cols-2 lg:grid-cols-3">
          {plansQ.data?.plans.map((p, idx) => {
            const popular = idx === 3;
            const insufficient = coins < p.price_coins;
            return (
              <div
                key={p.id}
                className={`relative rounded-2xl border bg-card p-6 transition hover:scale-[1.02] hover:shadow-[var(--shadow-glow)] ${popular ? "ring-2" : ""}`}
                style={popular ? { borderColor: "var(--gold)", ['--tw-ring-color' as string]: "var(--gold)" } : { borderColor: "var(--border)" }}
              >
                {popular && (
                  <span className="absolute -top-3 left-1/2 -translate-x-1/2 rounded-full px-3 py-1 text-xs font-bold" style={{ background: "var(--gradient-gold)", color: "var(--gold-foreground)" }}>
                    ⭐ Le plus populaire
                  </span>
                )}
                <div className="text-sm font-semibold text-muted-foreground">{p.name}</div>
                <div className="mt-2 flex items-baseline gap-1">
                  <span className="text-4xl font-black" style={{ color: popular ? "var(--gold)" : "var(--foreground)" }}>{p.price_coins}</span>
                  <span className="text-sm font-semibold text-muted-foreground">FCFA</span>
                </div>
                <ul className="mt-4 space-y-2 text-sm">
                  <li className="flex items-center gap-2"><Check className="h-4 w-4 text-primary" /> Accès illimité pendant {formatDuration(p.duration_minutes)}</li>
                  <li className="flex items-center gap-2"><Check className="h-4 w-4 text-primary" /> Toutes les chaînes</li>
                  <li className="flex items-center gap-2"><Check className="h-4 w-4 text-primary" /> Qualité HD/SD adaptative</li>
                </ul>
                <button
                  onClick={() => buy.mutate(p.id)}
                  disabled={buy.isPending || insufficient}
                  className="mt-6 inline-flex w-full items-center justify-center gap-2 rounded-md py-3 text-sm font-bold text-primary-foreground shadow-[var(--shadow-glow)] disabled:opacity-50"
                  style={{ background: "var(--gradient-primary)" }}
                >
                  {buy.isPending ? <Loader2 className="h-4 w-4 animate-spin" /> : insufficient ? "Solde insuffisant" : "Choisir ce plan"}
                </button>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}