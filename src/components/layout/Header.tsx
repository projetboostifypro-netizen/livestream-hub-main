import { Link, useNavigate } from "@tanstack/react-router";
import { useEffect, useState } from "react";
import { Coins, LogOut, User as UserIcon, Crown } from "lucide-react";
import { Logo } from "./Logo";
import { useAuth } from "@/hooks/useAuth";
import { useServerFn } from "@tanstack/react-start";
import { useQuery } from "@tanstack/react-query";
import { getProfile } from "@/lib/profile.functions";
import { getActiveSubscription } from "@/lib/subscriptions.functions";

export function Header() {
  const [scrolled, setScrolled] = useState(false);
  const { user, signOut } = useAuth();
  const navigate = useNavigate();
  const profileFn = useServerFn(getProfile);
  const subFn = useServerFn(getActiveSubscription);

  const profileQ = useQuery({ queryKey: ["profile"], queryFn: () => profileFn(), enabled: !!user });
  const subQ = useQuery({ queryKey: ["active-sub"], queryFn: () => subFn(), enabled: !!user });

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 20);
    window.addEventListener("scroll", onScroll);
    return () => window.removeEventListener("scroll", onScroll);
  }, []);

  const handleSignOut = async () => {
    await signOut();
    navigate({ to: "/" });
  };

  return (
    <header
      className={`fixed top-0 left-0 right-0 z-50 transition-all duration-300 ${
        scrolled ? "bg-background/95 backdrop-blur shadow-lg" : "bg-gradient-to-b from-background/90 to-transparent"
      }`}
    >
      <div className="mx-auto flex max-w-[1600px] items-center justify-between gap-4 px-4 py-3 md:px-8">
        <Link to="/_authenticated/browse" className="flex items-center">
          <Logo size={36} />
        </Link>
        <nav className="hidden items-center gap-6 text-sm font-medium md:flex">
          <Link to="/_authenticated/browse" className="text-foreground/90 hover:text-foreground" activeProps={{ className: "text-foreground" }}>
            Accueil
          </Link>
          <Link to="/_authenticated/plans" className="text-foreground/90 hover:text-foreground">
            Abonnements
          </Link>
          <Link to="/_authenticated/coins" className="text-foreground/90 hover:text-foreground">
            Pièces
          </Link>
        </nav>
        <div className="flex items-center gap-2">
          {subQ.data?.subscription && (
            <span className="hidden sm:inline-flex items-center gap-1 rounded-full px-2.5 py-1 text-xs font-bold" style={{ background: "var(--gradient-gold)", color: "var(--gold-foreground)" }}>
              <Crown className="h-3 w-3" /> Actif
            </span>
          )}
          <Link to="/_authenticated/coins" className="inline-flex items-center gap-1.5 rounded-full bg-secondary px-3 py-1.5 text-xs font-semibold hover:bg-accent">
            <Coins className="h-3.5 w-3.5" style={{ color: "var(--gold)" }} />
            {profileQ.data?.profile?.coins ?? 0}
          </Link>
          <Link to="/_authenticated/account" className="inline-flex h-9 w-9 items-center justify-center rounded-full bg-secondary hover:bg-accent">
            <UserIcon className="h-4 w-4" />
          </Link>
          <button onClick={handleSignOut} className="hidden md:inline-flex h-9 w-9 items-center justify-center rounded-full bg-secondary hover:bg-accent" title="Déconnexion">
            <LogOut className="h-4 w-4" />
          </button>
        </div>
      </div>
    </header>
  );
}