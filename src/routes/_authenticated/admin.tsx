import { createFileRoute, redirect } from "@tanstack/react-router";
import { useState } from "react";
import { useServerFn } from "@tanstack/react-start";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Loader2, Ban, CheckCircle2, Trash2, Coins as CoinsIcon, Users, Receipt, Link2, Plus } from "lucide-react";
import { supabase } from "@/integrations/supabase/client";
import { toast } from "sonner";
import { Toaster } from "@/components/ui/sonner";
import {
  adminListUsers,
  adminListTransactions,
  adminBlockUser,
  adminUnblockUser,
  adminDeleteUser,
  adminAdjustCoins,
} from "@/lib/admin.functions";
import {
  adminListPlaylistLinks,
  adminAddPlaylistLink,
  adminDeletePlaylistLink,
} from "@/lib/iptv.functions";

export const Route = createFileRoute("/_authenticated/admin")({
  head: () => ({ meta: [{ title: "Admin — FLOW+" }] }),
  beforeLoad: async () => {
    const { data } = await supabase.auth.getUser();
    if (!data.user) throw redirect({ to: "/login" });
    const { data: roles } = await supabase
      .from("user_roles")
      .select("role")
      .eq("user_id", data.user.id)
      .eq("role", "admin");
    if (!roles || roles.length === 0) throw redirect({ to: "/" });
  },
  component: AdminPage,
});

function AdminPage() {
  const qc = useQueryClient();
  const usersFn = useServerFn(adminListUsers);
  const txFn = useServerFn(adminListTransactions);
  const blockFn = useServerFn(adminBlockUser);
  const unblockFn = useServerFn(adminUnblockUser);
  const deleteFn = useServerFn(adminDeleteUser);
  const adjustFn = useServerFn(adminAdjustCoins);
  const listLinksFn = useServerFn(adminListPlaylistLinks);
  const addLinkFn = useServerFn(adminAddPlaylistLink);
  const delLinkFn = useServerFn(adminDeletePlaylistLink);

  const [tab, setTab] = useState<"users" | "tx">("users");
  const [q, setQ] = useState("");
  const [newUrl, setNewUrl] = useState("");

  const usersQ = useQuery({ queryKey: ["admin-users"], queryFn: () => usersFn() });
  const txQ = useQuery({ queryKey: ["admin-tx"], queryFn: () => txFn(), enabled: tab === "tx" });
  const linksQ = useQuery({ queryKey: ["admin-links"], queryFn: () => listLinksFn() });

  const invalidate = () => qc.invalidateQueries({ queryKey: ["admin-users"] });

  const block = useMutation({
    mutationFn: (userId: string) => blockFn({ data: { userId, reason: "Bloqué par admin" } }),
    onSuccess: () => { toast.success("Compte bloqué"); invalidate(); },
    onError: (e) => toast.error(e instanceof Error ? e.message : "Erreur"),
  });
  const unblock = useMutation({
    mutationFn: (userId: string) => unblockFn({ data: { userId } }),
    onSuccess: () => { toast.success("Compte débloqué"); invalidate(); },
    onError: (e) => toast.error(e instanceof Error ? e.message : "Erreur"),
  });
  const del = useMutation({
    mutationFn: (userId: string) => deleteFn({ data: { userId } }),
    onSuccess: () => { toast.success("Compte supprimé"); invalidate(); },
    onError: (e) => toast.error(e instanceof Error ? e.message : "Erreur"),
  });
  const adjust = useMutation({
    mutationFn: (v: { userId: string; delta: number }) =>
      adjustFn({ data: { userId: v.userId, delta: v.delta, reason: "Ajustement manuel" } }),
    onSuccess: (r) => { toast.success(`Nouveau solde : ${r.coins} FCFA`); invalidate(); },
    onError: (e) => toast.error(e instanceof Error ? e.message : "Erreur"),
  });
  const invalidateLinks = () => qc.invalidateQueries({ queryKey: ["admin-links"] });
  const addLink = useMutation({
    mutationFn: (url: string) => addLinkFn({ data: { url } }),
    onSuccess: () => { toast.success("Lien M3U ajouté"); setNewUrl(""); invalidateLinks(); },
    onError: (e) => toast.error(e instanceof Error ? e.message : "Erreur"),
  });
  const delLink = useMutation({
    mutationFn: (id: string) => delLinkFn({ data: { id } }),
    onSuccess: () => { toast.success("Lien supprimé"); invalidateLinks(); },
    onError: (e) => toast.error(e instanceof Error ? e.message : "Erreur (le lien est peut-être en cours d'utilisation)"),
  });

  const askAdjust = (userId: string, email: string) => {
    const raw = window.prompt(`Ajuster les pièces de ${email}\nEntrez un nombre (+ pour créditer, - pour retirer) :`, "0");
    if (raw == null) return;
    const n = parseInt(raw, 10);
    if (Number.isNaN(n) || n === 0) return toast.error("Montant invalide");
    adjust.mutate({ userId, delta: n });
  };

  const askDelete = (userId: string, email: string) => {
    if (!window.confirm(`Supprimer définitivement ${email} ? Cette action est irréversible.`)) return;
    del.mutate(userId);
  };

  const users = (usersQ.data?.users ?? []).filter((u: any) => {
    if (!q) return true;
    const s = q.toLowerCase();
    return (u.email ?? "").toLowerCase().includes(s) || (u.display_name ?? "").toLowerCase().includes(s);
  });

  return (
    <div className="mx-auto max-w-7xl px-4 py-8 md:px-8">
      <Toaster theme="dark" position="top-right" />
      <header className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-3xl font-black md:text-4xl">Tableau de bord admin</h1>
          <p className="mt-1 text-sm text-muted-foreground">Gérez les utilisateurs, les comptes et les transactions FLOW+.</p>
        </div>
        <div className="inline-flex rounded-lg border border-border bg-card p-1">
          <button onClick={() => setTab("users")} className={`inline-flex items-center gap-2 rounded-md px-3 py-1.5 text-sm font-semibold ${tab === "users" ? "bg-primary text-primary-foreground" : "text-muted-foreground"}`}>
            <Users className="h-4 w-4" /> Utilisateurs
          </button>
          <button onClick={() => setTab("tx")} className={`inline-flex items-center gap-2 rounded-md px-3 py-1.5 text-sm font-semibold ${tab === "tx" ? "bg-primary text-primary-foreground" : "text-muted-foreground"}`}>
            <Receipt className="h-4 w-4" /> Transactions
          </button>
        </div>
      </header>

      <section className="mt-6 rounded-lg border border-border bg-card p-4">
        <div className="flex items-center gap-2">
          <Link2 className="h-5 w-5 text-primary" />
          <h2 className="text-base font-bold">Liens M3U (pool de playlists)</h2>
        </div>
        <p className="mt-1 text-xs text-muted-foreground">
          Chaque abonné se voit assigner automatiquement un lien libre. Un lien est libéré dès que l'abonnement expire.
          {" "}Total : {linksQ.data?.links?.length ?? 0} · Libres : {(linksQ.data?.links ?? []).filter((l: any) => !l.in_use).length}
        </p>

        <div className="mt-3 flex flex-wrap gap-2">
          <input
            value={newUrl}
            onChange={(e) => setNewUrl(e.target.value)}
            placeholder="https://... (lien M3U)"
            className="min-w-[260px] flex-1 rounded-md border border-border bg-input px-3 py-2 text-sm outline-none focus:border-primary"
          />
          <button
            onClick={() => newUrl.trim() && addLink.mutate(newUrl.trim())}
            disabled={addLink.isPending || !newUrl.trim()}
            className="inline-flex items-center gap-2 rounded-md bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground hover:opacity-90 disabled:opacity-60"
          >
            {addLink.isPending ? <Loader2 className="h-4 w-4 animate-spin" /> : <Plus className="h-4 w-4" />}
            Ajouter
          </button>
        </div>

        <div className="mt-4 overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="bg-secondary text-xs uppercase text-muted-foreground">
              <tr>
                <th className="px-3 py-2 text-left">Lien</th>
                <th className="px-3 py-2 text-center">Statut</th>
                <th className="px-3 py-2 text-left">Assigné à</th>
                <th className="px-3 py-2 text-left">Expire le</th>
                <th className="px-3 py-2 text-right">Action</th>
              </tr>
            </thead>
            <tbody>
              {(linksQ.data?.links ?? []).map((l: any) => (
                <tr key={l.id} className="border-t border-border">
                  <td className="max-w-md truncate px-3 py-2 font-mono text-xs">{l.url}</td>
                  <td className="px-3 py-2 text-center">
                    {l.in_use ? (
                      <span className="rounded-full bg-amber-500/15 px-2 py-0.5 text-xs font-bold text-amber-400">Utilisé</span>
                    ) : (
                      <span className="rounded-full bg-green-500/15 px-2 py-0.5 text-xs font-bold text-green-400">Libre</span>
                    )}
                  </td>
                  <td className="px-3 py-2 text-xs">{l.assigned_email ?? "—"}</td>
                  <td className="px-3 py-2 text-xs text-muted-foreground">{l.expires_at ? new Date(l.expires_at).toLocaleString() : "—"}</td>
                  <td className="px-3 py-2 text-right">
                    <button
                      onClick={() => { if (window.confirm("Supprimer ce lien ?")) delLink.mutate(l.id); }}
                      disabled={l.in_use || delLink.isPending}
                      className="rounded-md p-1.5 hover:bg-accent disabled:opacity-30"
                      title={l.in_use ? "Lien en cours d'utilisation" : "Supprimer"}
                    >
                      <Trash2 className="h-4 w-4 text-red-400" />
                    </button>
                  </td>
                </tr>
              ))}
              {(linksQ.data?.links ?? []).length === 0 && (
                <tr><td colSpan={5} className="px-3 py-6 text-center text-muted-foreground">Aucun lien dans le pool</td></tr>
              )}
            </tbody>
          </table>
        </div>
      </section>

      {tab === "users" && (
        <section className="mt-6">
          <div className="mb-4 flex items-center justify-between gap-3">
            <input
              placeholder="Rechercher par email ou nom…"
              value={q}
              onChange={(e) => setQ(e.target.value)}
              className="w-full max-w-sm rounded-md border border-border bg-input px-3 py-2 text-sm outline-none focus:border-primary"
            />
            <span className="text-xs text-muted-foreground">{users.length} compte(s)</span>
          </div>

          {usersQ.isLoading ? (
            <div className="flex items-center gap-2 text-muted-foreground"><Loader2 className="h-4 w-4 animate-spin" /> Chargement…</div>
          ) : (
            <div className="overflow-x-auto rounded-lg border border-border bg-card">
              <table className="w-full text-sm">
                <thead className="bg-secondary text-xs uppercase text-muted-foreground">
                  <tr>
                    <th className="px-3 py-2 text-left">Email</th>
                    <th className="px-3 py-2 text-left">Nom</th>
                    <th className="px-3 py-2 text-right">Pièces</th>
                    <th className="px-3 py-2 text-center">Statut</th>
                    <th className="px-3 py-2 text-center">Session</th>
                    <th className="px-3 py-2 text-left">Inscription</th>
                    <th className="px-3 py-2 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {users.map((u: any) => (
                    <tr key={u.user_id} className="border-t border-border hover:bg-secondary/40">
                      <td className="px-3 py-2 font-mono text-xs">{u.email}</td>
                      <td className="px-3 py-2">{u.display_name ?? "—"}</td>
                      <td className="px-3 py-2 text-right font-bold" style={{ color: "var(--gold)" }}>{u.coins}</td>
                      <td className="px-3 py-2 text-center">
                        {u.is_blocked ? (
                          <span className="rounded-full bg-red-500/20 px-2 py-0.5 text-xs font-bold text-red-400">Bloqué</span>
                        ) : (
                          <span className="rounded-full bg-green-500/15 px-2 py-0.5 text-xs font-bold text-green-400">Actif</span>
                        )}
                      </td>
                      <td className="px-3 py-2 text-center text-xs text-muted-foreground">
                        {u.has_active_session ? "Connecté" : "—"}
                      </td>
                      <td className="px-3 py-2 text-xs text-muted-foreground">{new Date(u.created_at).toLocaleDateString()}</td>
                      <td className="px-3 py-2">
                        <div className="flex items-center justify-end gap-1">
                          <button title="Ajuster pièces" onClick={() => askAdjust(u.user_id, u.email)} className="rounded-md p-1.5 hover:bg-accent" disabled={adjust.isPending}>
                            <CoinsIcon className="h-4 w-4" style={{ color: "var(--gold)" }} />
                          </button>
                          {u.is_blocked ? (
                            <button title="Débloquer" onClick={() => unblock.mutate(u.user_id)} className="rounded-md p-1.5 hover:bg-accent" disabled={unblock.isPending}>
                              <CheckCircle2 className="h-4 w-4 text-green-400" />
                            </button>
                          ) : (
                            <button title="Bloquer" onClick={() => block.mutate(u.user_id)} className="rounded-md p-1.5 hover:bg-accent" disabled={block.isPending}>
                              <Ban className="h-4 w-4 text-amber-400" />
                            </button>
                          )}
                          <button title="Supprimer" onClick={() => askDelete(u.user_id, u.email)} className="rounded-md p-1.5 hover:bg-accent" disabled={del.isPending}>
                            <Trash2 className="h-4 w-4 text-red-400" />
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                  {users.length === 0 && (
                    <tr><td colSpan={7} className="px-3 py-6 text-center text-muted-foreground">Aucun utilisateur</td></tr>
                  )}
                </tbody>
              </table>
            </div>
          )}
        </section>
      )}

      {tab === "tx" && (
        <section className="mt-6">
          {txQ.isLoading ? (
            <div className="flex items-center gap-2 text-muted-foreground"><Loader2 className="h-4 w-4 animate-spin" /> Chargement…</div>
          ) : (
            <div className="overflow-x-auto rounded-lg border border-border bg-card">
              <table className="w-full text-sm">
                <thead className="bg-secondary text-xs uppercase text-muted-foreground">
                  <tr>
                    <th className="px-3 py-2 text-left">Date</th>
                    <th className="px-3 py-2 text-left">Email</th>
                    <th className="px-3 py-2 text-left">Motif</th>
                    <th className="px-3 py-2 text-right">Montant</th>
                    <th className="px-3 py-2 text-left">Détails</th>
                  </tr>
                </thead>
                <tbody>
                  {(txQ.data?.transactions ?? []).map((t: any) => (
                    <tr key={t.id} className="border-t border-border hover:bg-secondary/40">
                      <td className="px-3 py-2 text-xs text-muted-foreground">{new Date(t.created_at).toLocaleString()}</td>
                      <td className="px-3 py-2 font-mono text-xs">{t.email ?? "—"}</td>
                      <td className="px-3 py-2">{t.reason}</td>
                      <td className={`px-3 py-2 text-right font-bold ${t.amount >= 0 ? "text-green-400" : "text-red-400"}`}>
                        {t.amount >= 0 ? "+" : ""}{t.amount}
                      </td>
                      <td className="px-3 py-2 max-w-md truncate text-xs text-muted-foreground">{JSON.stringify(t.metadata)}</td>
                    </tr>
                  ))}
                  {(!txQ.data || txQ.data.transactions.length === 0) && (
                    <tr><td colSpan={5} className="px-3 py-6 text-center text-muted-foreground">Aucune transaction</td></tr>
                  )}
                </tbody>
              </table>
            </div>
          )}
        </section>
      )}
    </div>
  );
}