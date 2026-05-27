import { createServerFn } from "@tanstack/react-start";
import { z } from "zod";
import { requireSupabaseAuth } from "@/integrations/supabase/auth-middleware";
import { supabaseAdmin } from "@/integrations/supabase/client.server";

// Mobile Money stub: in production this would verify a payment callback.
// For now it directly credits the user (dev-only).
export const purchaseCoins = createServerFn({ method: "POST" })
  .middleware([requireSupabaseAuth])
  .inputValidator((d) =>
    z
      .object({
        amount: z.number().int().positive().max(50000),
        operator: z.enum(["orange", "mtn", "moov", "wave"]),
        phone: z.string().min(8).max(20),
      })
      .parse(d),
  )
  .handler(async ({ data, context }) => {
    const { userId } = context;

    const { data: profile, error } = await supabaseAdmin
      .from("profiles")
      .select("coins")
      .eq("user_id", userId)
      .maybeSingle();
    if (error || !profile) throw new Error("Profil introuvable");

    const newCoins = profile.coins + data.amount;
    const { error: upErr } = await supabaseAdmin
      .from("profiles")
      .update({ coins: newCoins })
      .eq("user_id", userId);
    if (upErr) throw new Error(upErr.message);

    await supabaseAdmin.from("coin_transactions").insert({
      user_id: userId,
      amount: data.amount,
      reason: "purchase",
      metadata: { operator: data.operator, phone: data.phone, simulated: true },
    });

    return { success: true, coins: newCoins };
  });

export const getTransactions = createServerFn({ method: "GET" })
  .middleware([requireSupabaseAuth])
  .handler(async ({ context }) => {
    const { supabase, userId } = context;
    const { data, error } = await supabase
      .from("coin_transactions")
      .select("id, amount, reason, metadata, created_at")
      .eq("user_id", userId)
      .order("created_at", { ascending: false })
      .limit(50);
    if (error) throw new Error(error.message);
    return { transactions: data ?? [] };
  });