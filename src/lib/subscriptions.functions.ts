import { createServerFn } from "@tanstack/react-start";
import { z } from "zod";
import { requireSupabaseAuth } from "@/integrations/supabase/auth-middleware";
import { supabaseAdmin } from "@/integrations/supabase/client.server";

export const getPlans = createServerFn({ method: "GET" }).handler(async () => {
  const { data, error } = await supabaseAdmin
    .from("subscription_plans")
    .select("id, name, duration_minutes, price_coins, sort_order")
    .order("sort_order", { ascending: true });
  if (error) throw new Error(error.message);
  return { plans: data ?? [] };
});

export const getActiveSubscription = createServerFn({ method: "GET" })
  .middleware([requireSupabaseAuth])
  .handler(async ({ context }) => {
    const { supabase, userId } = context;
    const { data, error } = await supabase
      .from("subscriptions")
      .select("id, plan_id, starts_at, expires_at, subscription_plans(name, duration_minutes)")
      .eq("user_id", userId)
      .gt("expires_at", new Date().toISOString())
      .order("expires_at", { ascending: false })
      .limit(1)
      .maybeSingle();
    if (error) throw new Error(error.message);
    return { subscription: data };
  });

export const purchasePlan = createServerFn({ method: "POST" })
  .middleware([requireSupabaseAuth])
  .inputValidator((d) => z.object({ planId: z.number().int().positive() }).parse(d))
  .handler(async ({ data, context }) => {
    const { userId } = context;

    const { data: plan, error: pe } = await supabaseAdmin
      .from("subscription_plans")
      .select("id, name, duration_minutes, price_coins")
      .eq("id", data.planId)
      .maybeSingle();
    if (pe || !plan) throw new Error("Plan introuvable");

    const { data: profile, error: prErr } = await supabaseAdmin
      .from("profiles")
      .select("coins")
      .eq("user_id", userId)
      .maybeSingle();
    if (prErr || !profile) throw new Error("Profil introuvable");
    if (profile.coins < plan.price_coins) {
      throw new Error("Solde insuffisant. Achetez des pièces pour continuer.");
    }

    // Extend if existing active sub, otherwise start now
    const now = new Date();
    const { data: existing } = await supabaseAdmin
      .from("subscriptions")
      .select("expires_at")
      .eq("user_id", userId)
      .gt("expires_at", now.toISOString())
      .order("expires_at", { ascending: false })
      .limit(1)
      .maybeSingle();

    const base = existing ? new Date(existing.expires_at) : now;
    const expires = new Date(base.getTime() + plan.duration_minutes * 60_000);

    const { error: subErr } = await supabaseAdmin.from("subscriptions").insert({
      user_id: userId,
      plan_id: plan.id,
      starts_at: now.toISOString(),
      expires_at: expires.toISOString(),
    });
    if (subErr) throw new Error(subErr.message);

    const newCoins = profile.coins - plan.price_coins;
    const { error: upErr } = await supabaseAdmin
      .from("profiles")
      .update({ coins: newCoins })
      .eq("user_id", userId);
    if (upErr) throw new Error(upErr.message);

    await supabaseAdmin.from("coin_transactions").insert({
      user_id: userId,
      amount: -plan.price_coins,
      reason: "plan_purchase",
      metadata: { plan_id: plan.id, plan_name: plan.name },
    });

    return { success: true, expiresAt: expires.toISOString(), coins: newCoins };
  });