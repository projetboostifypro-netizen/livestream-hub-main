import { createFileRoute } from "@tanstack/react-router";
import { supabaseAdmin } from "@/integrations/supabase/client.server";

export const Route = createFileRoute("/api/public/soleaspay-webhook")({
  server: {
    handlers: {
      POST: async ({ request }) => {
        let payload: unknown;
        try {
          payload = await request.json();
        } catch {
          return new Response(
            JSON.stringify({ ok: false, error: "invalid_json" }),
            { status: 400, headers: { "Content-Type": "application/json" } },
          );
        }

        const { data, error } = await supabaseAdmin.rpc(
          "process_soleaspay_webhook",
          { p_payload: payload as never },
        );

        if (error) {
          console.error("[soleaspay-webhook] rpc error", error);
          return new Response(
            JSON.stringify({ ok: false, error: error.message }),
            { status: 500, headers: { "Content-Type": "application/json" } },
          );
        }

        return new Response(JSON.stringify({ ok: true, result: data }), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        });
      },
      GET: async () => {
        return new Response(
          JSON.stringify({ ok: true, service: "soleaspay-webhook" }),
          { status: 200, headers: { "Content-Type": "application/json" } },
        );
      },
    },
  },
});