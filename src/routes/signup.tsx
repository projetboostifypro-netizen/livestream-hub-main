import { createFileRoute, Link } from "@tanstack/react-router";
import { Logo } from "@/components/layout/Logo";
import { Smartphone } from "lucide-react";

export const Route = createFileRoute("/signup")({
  head: () => ({ meta: [{ title: "Inscription — FLOW+" }] }),
  component: SignupDisabled,
});

function SignupDisabled() {
  return (
    <div className="flex min-h-screen items-center justify-center bg-background px-4" style={{ background: "var(--gradient-surface)" }}>
      <div className="w-full max-w-md rounded-2xl border border-border bg-card p-8 text-center shadow-[var(--shadow-card)]">
        <div className="mb-6 flex justify-center"><Logo size={56} /></div>
        <Smartphone className="mx-auto h-10 w-10 text-primary" />
        <h1 className="mt-4 text-2xl font-bold">Inscription via l'application</h1>
        <p className="mt-3 text-sm text-muted-foreground">
          La création de compte se fait uniquement depuis l'application Android FLOW+.
          Téléchargez-la pour profiter de 120 FCFA offerts à l'inscription.
        </p>
        <Link to="/login" className="mt-6 inline-flex items-center justify-center rounded-md bg-primary px-4 py-2.5 text-sm font-bold text-primary-foreground">
          Retour à la connexion
        </Link>
      </div>
    </div>
  );
}