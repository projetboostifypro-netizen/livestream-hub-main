import { createFileRoute } from "@tanstack/react-router";
import { useState } from "react";
import { useServerFn } from "@tanstack/react-start";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Coins, Loader2, Smartphone } from "lucide-react";
import { purchaseCoins } from "@/lib/coins.functions";
import { getProfile } from "@/lib/profile.functions";
import { toast } from "sonner";
import { Toaster } from "@/components/ui/sonner";

export const Route = createFileRoute("/_authenticated/coins")({
  head: () => ({ meta: [{ title: "Acheter des pièces — FLOW+" }] }),
  component: CoinsPage,
});

const PACKS = [
  { amount: 500, bonus: 0 },
  { amount: 1000, bonus: 50 },
  { amount: 2000, bonus: 200 },
  { amount: 5000, bonus: 700 },
];

const OPERATORS = [
  { id: "orange" as const, name: "Orange Money", color: "#FF6600" },
  { id: "mtn" as const, name: "MTN MoMo", color: "#FFCC00" },
  { id: "moov" as const, name: "Moov Money", color: "#0066FF" },
  { id: "wave" as const, name: "Wave", color: "#1DCFFF" },
];

function CoinsPage() {
  const qc = useQueryClient();
  const buyFn = useServerFn(purchaseCoins);
  const profileFn = useServerFn(getProfile);
  const profileQ = useQuery({ queryKey: ["profile"], queryFn: () => profileFn() });

  const [pack, setPack] = useState(PACKS[1]);
  const [op, setOp] = useState<typeof OPERATORS[number]["id"]>("orange");
  const [phone, setPhone] = useState("");

  const buy = useMutation({
    mutationFn: () => buyFn({ data: { amount: pack.amount + pack.bonus, operator: op, phone } }),
    onSuccess: () => {
      toast.success(`${pack.amount + pack.bonus} FCFA crédités !`);
      qc.invalidateQueries({ queryKey: ["profile"] });
      setPhone("");
    },
    onError: (e) => toast.error(e instanceof Error ? e.message : "Erreur"),
  });

  const onSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!phone.trim()) return toast.error("Entrez votre numéro");
    buy.mutate();
  };

  return (
    <div className="mx-auto max-w-4xl px-4 py-8 md:px-8">
      <Toaster theme="dark" position="top-right" />
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="text-3xl font-black md:text-4xl">Acheter des pièces</h1>
          <p className="mt-2 text-muted-foreground">1 pièce = 1 FCFA. Utilisez vos pièces pour souscrire aux plans.</p>
        </div>
        <div className="inline-flex items-center gap-2 rounded-full bg-card px-4 py-2 text-sm font-bold">
          <Coins className="h-4 w-4" style={{ color: "var(--gold)" }} />
          {profileQ.data?.profile?.coins ?? 0} FCFA
        </div>
      </div>

      <div className="mt-6 rounded-lg border border-primary/40 bg-primary/10 p-4 text-sm">
        <strong>Mobile Money</strong> : l'intégration des paiements est en cours. Pour les tests, l'achat crédite directement votre compte.
      </div>

      {/* Packs */}
      <h2 className="mt-8 text-lg font-bold">Choisissez un pack</h2>
      <div className="mt-4 grid gap-3 sm:grid-cols-2 md:grid-cols-4">
        {PACKS.map((p) => (
          <button
            key={p.amount}
            onClick={() => setPack(p)}
            className={`rounded-xl border-2 bg-card p-4 text-left transition hover:scale-105 ${pack.amount === p.amount ? "border-primary shadow-[var(--shadow-glow)]" : "border-border"}`}
          >
            <div className="text-2xl font-black">{p.amount}</div>
            <div className="text-xs text-muted-foreground">FCFA</div>
            {p.bonus > 0 && <div className="mt-1 text-xs font-bold" style={{ color: "var(--gold)" }}>+ {p.bonus} bonus</div>}
          </button>
        ))}
      </div>

      {/* Operator */}
      <h2 className="mt-8 text-lg font-bold">Opérateur</h2>
      <div className="mt-4 grid gap-3 sm:grid-cols-2 md:grid-cols-4">
        {OPERATORS.map((o) => (
          <button
            key={o.id}
            onClick={() => setOp(o.id)}
            className={`flex items-center gap-2 rounded-xl border-2 bg-card p-4 transition ${op === o.id ? "border-primary" : "border-border"}`}
          >
            <Smartphone className="h-5 w-5" style={{ color: o.color }} />
            <span className="text-sm font-bold">{o.name}</span>
          </button>
        ))}
      </div>

      {/* Phone */}
      <form onSubmit={onSubmit} className="mt-8 space-y-4">
        <div>
          <label className="text-xs font-semibold text-muted-foreground">Numéro Mobile Money</label>
          <input type="tel" required value={phone} onChange={(e) => setPhone(e.target.value)} placeholder="07 00 00 00 00" className="mt-1 w-full rounded-md border border-border bg-input px-3 py-3 text-sm outline-none focus:border-primary" />
        </div>
        <button type="submit" disabled={buy.isPending} className="inline-flex w-full items-center justify-center gap-2 rounded-md py-4 text-base font-bold text-primary-foreground shadow-[var(--shadow-glow)] disabled:opacity-50" style={{ background: "var(--gradient-primary)" }}>
          {buy.isPending ? <Loader2 className="h-4 w-4 animate-spin" /> : null}
          Payer {pack.amount} FCFA {pack.bonus > 0 && `(+ ${pack.bonus} bonus)`}
        </button>
      </form>
    </div>
  );
}