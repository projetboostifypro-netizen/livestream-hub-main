import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { useState } from "react";
import { supabase } from "@/integrations/supabase/client";
import { Logo } from "@/components/layout/Logo";
import { Loader2, ShieldAlert } from "lucide-react";
import { toast } from "sonner";
import { Toaster } from "@/components/ui/sonner";

export const Route = createFileRoute("/login")({
  head: () => ({ meta: [{ title: "Connexion — OnE+" }] }),
  component: LoginPage,
});

function LoginPage() {
  const navigate = useNavigate();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [loading, setLoading] = useState(false);

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    const { data, error } = await supabase.auth.signInWithPassword({ email, password });
    if (error || !data.user) {
      setLoading(false);
      toast.error(error?.message ?? "Connexion échouée");
      return;
    }
    const { data: roles } = await supabase
      .from("user_roles")
      .select("role")
      .eq("user_id", data.user.id)
      .eq("role", "admin");
    if (!roles || roles.length === 0) {
      await supabase.auth.signOut();
      setLoading(false);
      toast.error("Accès réservé aux administrateurs. Utilisez l'application Android.");
      return;
    }
    setLoading(false);
    toast.success("Bienvenue admin !");
    navigate({ to: "/admin" });
  };

  return (
    <div className="flex min-h-screen flex-col items-center justify-center bg-background px-4" style={{ background: "var(--gradient-surface)" }}>
      <Toaster theme="dark" position="top-right" />
      <div className="w-full max-w-md rounded-2xl border border-border bg-card p-8 shadow-[var(--shadow-card)]">
        <div className="mb-8 flex justify-center"><Logo size={72} /></div>
        <h1 className="text-2xl font-bold">Connexion administrateur</h1>
        <p className="mt-1 text-sm text-muted-foreground">Accès réservé aux administrateurs de OnE+.</p>
        <div className="mt-4 flex items-start gap-2 rounded-md border border-primary/30 bg-primary/5 p-3 text-xs text-muted-foreground">
          <ShieldAlert className="mt-0.5 h-4 w-4 shrink-0 text-primary" />
          <span>Les utilisateurs doivent se connecter depuis l'application Android OnE+. Ce site est le tableau de bord administrateur.</span>
        </div>
        <form onSubmit={onSubmit} className="mt-6 space-y-4">
          <div>
            <label className="text-xs font-semibold text-muted-foreground">Email</label>
            <input type="email" required value={email} onChange={(e) => setEmail(e.target.value)} className="mt-1 w-full rounded-md border border-border bg-input px-3 py-2.5 text-sm outline-none focus:border-primary" />
          </div>
          <div>
            <label className="text-xs font-semibold text-muted-foreground">Mot de passe</label>
            <input type="password" required value={password} onChange={(e) => setPassword(e.target.value)} className="mt-1 w-full rounded-md border border-border bg-input px-3 py-2.5 text-sm outline-none focus:border-primary" />
          </div>
          <button type="submit" disabled={loading} className="inline-flex w-full items-center justify-center gap-2 rounded-md py-3 text-sm font-bold text-primary-foreground shadow-[var(--shadow-glow)] disabled:opacity-50" style={{ background: "var(--gradient-primary)" }}>
            {loading && <Loader2 className="h-4 w-4 animate-spin" />} Se connecter
          </button>
        </form>
      </div>
      <p className="mt-6 text-xs text-muted-foreground/60">Powered by arianetv</p>
    </div>
  );
}
