import { createFileRoute } from "@tanstack/react-router";
import { useState, useRef, useEffect } from "react";
import { useServerFn } from "@tanstack/react-start";
import { Loader2, Smartphone, Crown, ShieldCheck } from "lucide-react";
import { Logo } from "@/components/layout/Logo";
import { initiatePayment, verifyPayment } from "@/lib/pay.functions";

export const Route = createFileRoute("/")({
  head: () => ({
    meta: [
      { title: "FLOW+ — Recharger mon compte" },
      { name: "description", content: "Rechargez votre compte FLOW+ en Mobile Money. Dépôt minimum 100 FCFA." },
    ],
  }),
  component: PaymentPage,
});

const PACKS = [100, 500, 1000, 2000, 5000, 10000];
const OPERATORS = [
  { id: "orange-cm" as const, name: "Orange CM", color: "#FF6600", flag: "🇨🇲" },
  { id: "orange-ci" as const, name: "Orange CI", color: "#FF6600", flag: "🇨🇮" },
  { id: "mtn-ci" as const, name: "MOMO CI", color: "#FFCC00", flag: "🇨🇮" },
  { id: "moov-ci" as const, name: "Moov CI", color: "#0066FF", flag: "🇨🇮" },
  { id: "wave-ci" as const, name: "Wave CI", color: "#1DCFFF", flag: "🇨🇮" },
  { id: "moov-bf" as const, name: "Moov BF", color: "#0066FF", flag: "🇧🇫" },
  { id: "orange-bf" as const, name: "Orange BF", color: "#FF6600", flag: "🇧🇫" },
  { id: "mtn-bj" as const, name: "MOMO BJ", color: "#FFCC00", flag: "🇧🇯" },
  { id: "moov-bj" as const, name: "Moov BJ", color: "#0066FF", flag: "🇧🇯" },
  { id: "t-money-tg" as const, name: "T-Money TG", color: "#00A86B", flag: "🇹🇬" },
  { id: "moov-tg" as const, name: "Moov TG", color: "#0066FF", flag: "🇹🇬" },
  { id: "vodacom-cod" as const, name: "Vodacom COD", color: "#E60000", flag: "🇨🇩" },
  { id: "airtel-cod" as const, name: "Airtel COD", color: "#E60000", flag: "🇨🇩" },
  { id: "orange-cod" as const, name: "Orange COD", color: "#FF6600", flag: "🇨🇩" },
  { id: "airtel-cog" as const, name: "Airtel COG", color: "#E60000", flag: "🇨🇬" },
  { id: "airtel-gab" as const, name: "Airtel GAB", color: "#E60000", flag: "🇬🇦" },
  { id: "airtel-uga" as const, name: "Airtel UGA", color: "#E60000", flag: "🇺🇬" },
  { id: "mtn-uga" as const, name: "MOMO UGA", color: "#FFCC00", flag: "🇺🇬" },
];

type Status = "idle" | "initiating" | "polling" | "success" | "failed";

function PaymentPage() {
  const initiate = useServerFn(initiatePayment);
  const verify = useServerFn(verifyPayment);

  const [email, setEmail] = useState("");
  const [amount, setAmount] = useState(500);
  const [operator, setOperator] = useState<typeof OPERATORS[number]["id"]>("orange-cm");
  const [phone, setPhone] = useState("");
  const [otp, setOtp] = useState("");
  const [status, setStatus] = useState<Status>("idle");
  const [message, setMessage] = useState("");
  const stopRef = useRef(false);

  useEffect(() => () => { stopRef.current = true; }, []);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setMessage("");
    if (amount < 100) return setMessage("Montant minimum : 100 FCFA");
    if (!email.includes("@")) return setMessage("Email invalide");
    if (phone.replace(/\D/g, "").length < 8) return setMessage("Numéro invalide");

    setStatus("initiating");
    setMessage("Envoi de la demande à SoleasPay…");
    try {
      const res = await initiate({ data: { email, amount, operator, phone, otp } });
      if (!res.payId) {
        setStatus("failed");
        setMessage("Réponse SoleasPay invalide (payId manquant).");
        return;
      }
      setStatus("polling");
      setMessage("Validez la transaction sur votre téléphone (PIN/USSD)…");

      stopRef.current = false;
      for (let i = 0; i < 90 && !stopRef.current; i++) {
        await new Promise((r) => setTimeout(r, 3000));
        try {
          const v = await verify({ data: { orderId: res.orderId, payId: res.payId, operator } });
          setMessage(`Vérification… (${i + 1}) statut : ${v.status}`);
          if (v.status === "SUCCESS") {
            setStatus("success");
            setMessage(`Paiement validé ! ${amount} FCFA crédités sur ${email}.`);
            return;
          }
          if (v.status === "FAILED") {
            setStatus("failed");
            setMessage("Paiement échoué ou annulé.");
            return;
          }
        } catch {
          /* transient — retry */
        }
      }
      setStatus("failed");
      setMessage("Délai dépassé. Si vous avez été débité, contactez le support.");
    } catch (err) {
      setStatus("failed");
      setMessage(err instanceof Error ? err.message : "Erreur inconnue");
    }
  };

  const busy = status === "initiating" || status === "polling";

  return (
    <div className="min-h-screen bg-background text-foreground">
      <header className="mx-auto flex max-w-3xl items-center justify-between px-6 py-5">
        <Logo size={36} />
        <span className="text-xs font-semibold text-muted-foreground">Recharge officielle</span>
      </header>

      <main className="mx-auto max-w-2xl px-6 pb-16">
        <div className="text-center">
          <span className="mb-3 inline-flex items-center gap-2 rounded-full border px-3 py-1 text-xs font-semibold" style={{ borderColor: "var(--gold)", color: "var(--gold)" }}>
            <Crown className="h-3.5 w-3.5" /> Page de paiement FLOW+
          </span>
          <h1 className="text-3xl font-black md:text-4xl">Rechargez votre compte</h1>
          <p className="mt-3 text-sm text-muted-foreground">
            Utilisez cette page si le paiement dans l'application ne fonctionne pas.
            Votre compte sera crédité automatiquement à l'adresse email indiquée.
          </p>
        </div>

        <form onSubmit={submit} className="mt-8 space-y-6 rounded-2xl border border-border bg-card p-6 shadow-xl">
          <div>
            <label className="text-xs font-semibold text-muted-foreground">Email de votre compte FLOW+</label>
            <input type="email" required value={email} onChange={(e) => setEmail(e.target.value)} placeholder="vous@email.com" className="mt-1 w-full rounded-md border border-border bg-input px-3 py-3 text-sm outline-none focus:border-primary" />
          </div>

          <div>
            <label className="text-xs font-semibold text-muted-foreground">Montant (FCFA) — min. 100</label>
            <div className="mt-2 grid grid-cols-3 gap-2">
              {PACKS.map((p) => (
                <button type="button" key={p} onClick={() => setAmount(p)} className={`rounded-lg border-2 py-2 text-sm font-bold transition ${amount === p ? "border-primary bg-primary/10" : "border-border bg-background"}`}>{p}</button>
              ))}
            </div>
            <input type="number" min={100} max={100000} value={amount} onChange={(e) => setAmount(Number(e.target.value) || 0)} className="mt-2 w-full rounded-md border border-border bg-input px-3 py-3 text-sm outline-none focus:border-primary" />
          </div>

          <div>
            <label className="text-xs font-semibold text-muted-foreground">Opérateur</label>
            <div className="mt-2 grid grid-cols-2 gap-2 sm:grid-cols-4">
              {OPERATORS.map((o) => (
                <button type="button" key={o.id} onClick={() => setOperator(o.id)} className={`flex flex-col items-center gap-1 rounded-lg border-2 p-3 transition ${operator === o.id ? "border-primary" : "border-border"}`}>
                  <Smartphone className="h-5 w-5" style={{ color: o.color }} />
                  <span className="text-[11px] font-bold">{o.name}</span>
                </button>
              ))}
            </div>
          </div>

          <div>
            <label className="text-xs font-semibold text-muted-foreground">Numéro Mobile Money</label>
            <input type="tel" required value={phone} onChange={(e) => setPhone(e.target.value)} placeholder="6XXXXXXXX" className="mt-1 w-full rounded-md border border-border bg-input px-3 py-3 text-sm outline-none focus:border-primary" />
          </div>

          {operator.startsWith("orange") && (
            <div>
              <label className="text-xs font-semibold text-muted-foreground">Code OTP Orange Money</label>
              <input type="text" inputMode="numeric" value={otp} onChange={(e) => setOtp(e.target.value)} placeholder="Composez #144*82*MONTANT*CODE#" className="mt-1 w-full rounded-md border border-border bg-input px-3 py-3 text-sm outline-none focus:border-primary" />
              <p className="mt-1 text-[11px] text-muted-foreground">Laissez vide pour MTN/Moov/Wave.</p>
            </div>
          )}

          <button type="submit" disabled={busy} className="inline-flex w-full items-center justify-center gap-2 rounded-md py-4 text-base font-bold text-primary-foreground shadow-[var(--shadow-glow)] disabled:opacity-50" style={{ background: "var(--gradient-primary)" }}>
            {busy && <Loader2 className="h-4 w-4 animate-spin" />}
            {status === "polling" ? "En attente de validation…" : `Payer ${amount} FCFA`}
          </button>

          {message && (
            <div className={`rounded-md border p-3 text-sm ${status === "success" ? "border-green-500/40 bg-green-500/10 text-green-400" : status === "failed" ? "border-red-500/40 bg-red-500/10 text-red-400" : "border-primary/40 bg-primary/10"}`}>
              {message}
            </div>
          )}

          <div className="flex items-center gap-2 text-[11px] text-muted-foreground">
            <ShieldCheck className="h-4 w-4" />
            Paiement sécurisé via SoleasPay. Dépôt minimum 100 FCFA.
          </div>
        </form>
      </main>
    </div>
  );
}