import { createFileRoute, Link, useNavigate } from "@tanstack/react-router";
import { useServerFn } from "@tanstack/react-start";
import { useQuery } from "@tanstack/react-query";
import { Coins, Crown, LogOut, User as UserIcon, History } from "lucide-react";
import { getProfile } from "@/lib/profile.functions";
import { getActiveSubscription } from "@/lib/subscriptions.functions";
import { getTransactions } from "@/lib/coins.functions";
import { useAuth } from "@/hooks/useAuth";

export const Route = createFileRoute("/_authenticated/account")({
  head: () => ({ meta: [{ title: "Mon compte — FLOW+" }] }),
  component: AccountPage,
});

function AccountPage() {
  const navigate = useNavigate();
  const { user, signOut } = useAuth();
  const profileFn = useServerFn(getProfile);
  const subFn = useServerFn(getActiveSubscription);
  const txFn = useServerFn(getTransactions);

  const profileQ = useQuery({ queryKey: ["profile"], queryFn: () => profileFn() });
  const subQ = useQuery({ queryKey: ["active-sub"], queryFn: () => subFn() });
  const txQ = useQuery({ queryKey: ["tx"], queryFn: () => txFn() });

  const handleSignOut = async () => {
    await signOut();
    navigate({ to: "/" });
  };

  return (
    <div className="mx-auto max-w-3xl px-4 py-8 md:px-8">
      <h1 className="text-3xl font-black md:text-4xl">Mon compte</h1>

      <div className="mt-6 rounded-2xl border border-border bg-card p-6">
        <div className="flex items-center gap-4">
          <div className="flex h-14 w-14 items-center justify-center rounded-full" style={{ background: "var(--gradient-primary)" }}>
            <UserIcon className="h-6 w-6 text-primary-foreground" />
          </div>
          <div>
            <div className="text-lg font-bold">{profileQ.data?.profile?.display_name ?? "Utilisateur"}</div>
            <div className="text-sm text-muted-foreground">{user?.email}</div>
          </div>
        </div>
      </div>

      <div className="mt-4 grid gap-4 sm:grid-cols-2">
        <Link to="/_authenticated/coins" className="rounded-2xl border border-border bg-card p-6 transition hover:border-primary">
          <div className="flex items-center gap-2 text-xs font-semibold uppercase text-muted-foreground"><Coins className="h-4 w-4" style={{ color: "var(--gold)" }} /> Solde</div>
          <div className="mt-2 text-3xl font-black" style={{ color: "var(--gold)" }}>{profileQ.data?.profile?.coins ?? 0} <span className="text-base font-semibold text-muted-foreground">FCFA</span></div>
          <div className="mt-1 text-xs text-primary">Recharger →</div>
        </Link>
        <Link to="/_authenticated/plans" className="rounded-2xl border border-border bg-card p-6 transition hover:border-primary">
          <div className="flex items-center gap-2 text-xs font-semibold uppercase text-muted-foreground"><Crown className="h-4 w-4" style={{ color: "var(--gold)" }} /> Abonnement</div>
          {subQ.data?.subscription ? (
            <>
              <div className="mt-2 text-base font-bold">Actif</div>
              <div className="text-xs text-muted-foreground">Expire le {new Date(subQ.data.subscription.expires_at).toLocaleString("fr-FR")}</div>
            </>
          ) : (
            <>
              <div className="mt-2 text-base font-bold text-muted-foreground">Aucun</div>
              <div className="text-xs text-primary">Souscrire →</div>
            </>
          )}
        </Link>
      </div>

      <div className="mt-6 rounded-2xl border border-border bg-card p-6">
        <h2 className="flex items-center gap-2 text-lg font-bold"><History className="h-5 w-5" /> Historique des pièces</h2>
        <div className="mt-4 divide-y divide-border">
          {txQ.data?.transactions.length === 0 && <div className="py-4 text-center text-sm text-muted-foreground">Aucune transaction</div>}
          {txQ.data?.transactions.map((t) => (
            <div key={t.id} className="flex items-center justify-between py-3 text-sm">
              <div>
                <div className="font-semibold capitalize">{t.reason.replace(/_/g, " ")}</div>
                <div className="text-xs text-muted-foreground">{new Date(t.created_at).toLocaleString("fr-FR")}</div>
              </div>
              <div className={`font-bold ${t.amount > 0 ? "text-emerald-400" : "text-primary"}`}>
                {t.amount > 0 ? "+" : ""}{t.amount} FCFA
              </div>
            </div>
          ))}
        </div>
      </div>

      <button onClick={handleSignOut} className="mt-6 inline-flex items-center gap-2 rounded-md border border-border bg-secondary px-4 py-2 text-sm font-bold hover:bg-accent">
        <LogOut className="h-4 w-4" /> Se déconnecter
      </button>
    </div>
  );
}