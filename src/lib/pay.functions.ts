import { createServerFn } from "@tanstack/react-start";
import { z } from "zod";
import { supabaseAdmin } from "@/integrations/supabase/client.server";

const OPERATOR_SERVICE: Record<string, string> = {
  "orange-cm": "2",
  "orange-ci": "29",
  "mtn-ci": "30",
  "moov-ci": "31",
  "wave-ci": "32",
  "moov-bf": "33",
  "orange-bf": "34",
  "mtn-bj": "35",
  "moov-bj": "36",
  "t-money-tg": "37",
  "moov-tg": "38",
  "vodacom-cod": "52",
  "airtel-cod": "53",
  "orange-cod": "54",
  "airtel-cog": "55",
  "airtel-gab": "57",
  "airtel-uga": "58",
  "mtn-uga": "59",
};

const SOLEASPAY_API_KEY = "SP_PyWmCdy82M3ItajYYytc6sJiOwpWUzlWIobxvRw8ANM_AP";
const SOLEASPAY_BASE_URL = "https://soleaspay.com";

function getApiConfig() {
  return { apiKey: SOLEASPAY_API_KEY, baseUrl: SOLEASPAY_BASE_URL };
}

function makeOrderId() {
  const rand = Math.random().toString(16).slice(2, 10).toUpperCase();
  return `FLOW-${rand}`;
}

export const initiatePayment = createServerFn({ method: "POST" })
  .inputValidator((d) =>
    z
      .object({
        email: z.string().email(),
        amount: z.number().int().min(100).max(100000),
        operator: z.enum([
          "orange-cm", "orange-ci", "mtn-ci", "moov-ci", "wave-ci",
          "moov-bf", "orange-bf", "mtn-bj", "moov-bj",
          "t-money-tg", "moov-tg",
          "vodacom-cod", "airtel-cod", "orange-cod",
          "airtel-cog", "airtel-gab", "airtel-uga", "mtn-uga",
        ]),
        phone: z.string().min(8).max(20),
        otp: z.string().max(20).optional().default(""),
      })
      .parse(d),
  )
  .handler(async ({ data }) => {
    const { apiKey, baseUrl } = getApiConfig();
    const orderId = makeOrderId();

    // Register external_ref + email -> user mapping
    const { error: regErr } = await supabaseAdmin.rpc(
      "register_external_reference_public",
      {
        p_email: data.email,
        p_external_ref: orderId,
        p_amount: data.amount,
        p_operator: data.operator,
        p_phone: data.phone,
      } as never,
    );
    if (regErr) {
      if ((regErr.message || "").includes("user_not_found")) {
        throw new Error(
          "Aucun compte trouvé avec cet email. Inscrivez-vous d'abord dans l'application.",
        );
      }
      throw new Error(regErr.message);
    }

    const headers: Record<string, string> = {
      "Content-Type": "application/json",
      Accept: "application/json",
      "x-api-key": apiKey,
      operation: "2",
      service: OPERATOR_SERVICE[data.operator] ?? "2",
    };
    if (data.otp) headers.otp = data.otp;

    const body = {
      wallet: data.phone,
      amount: data.amount,
      currency: "XAF",
      order_id: orderId,
      description: `Recharge FLOW ${data.amount} FCFA`,
      payer: data.email.split("@")[0],
      payerEmail: data.email,
      successUrl: "https://flow.tv/success",
      failureUrl: "https://flow.tv/fail",
    };

    const resp = await fetch(`${baseUrl}/api/agent/bills/v3`, {
      method: "POST",
      headers,
      body: JSON.stringify(body),
    });
    const text = await resp.text();
    let json: any = {};
    try {
      json = text ? JSON.parse(text) : {};
    } catch {
      throw new Error(`Réponse SoleasPay invalide (${resp.status})`);
    }
    if (!resp.ok) {
      const msg = json?.message || json?.error || `Erreur SoleasPay ${resp.status}`;
      throw new Error(msg);
    }
    const payId: string =
      json?.data?.reference || json?.payId || json?.reference || "";

    return { orderId, payId, raw: json };
  });

export const verifyPayment = createServerFn({ method: "POST" })
  .inputValidator((d) =>
    z
      .object({
        orderId: z.string().min(4).max(64),
        payId: z.string().min(1).max(128),
        operator: z.enum([
          "orange-cm", "orange-ci", "mtn-ci", "moov-ci", "wave-ci",
          "moov-bf", "orange-bf", "mtn-bj", "moov-bj",
          "t-money-tg", "moov-tg",
          "vodacom-cod", "airtel-cod", "orange-cod",
          "airtel-cog", "airtel-gab", "airtel-uga", "mtn-uga",
        ]),
      })
      .parse(d),
  )
  .handler(async ({ data }) => {
    const { apiKey, baseUrl } = getApiConfig();
    const headers: Record<string, string> = {
      "x-api-key": apiKey,
      operation: "2",
      service: OPERATOR_SERVICE[data.operator] ?? "2",
      Accept: "application/json",
    };
    const url = `${baseUrl}/api/agent/verif-pay?orderId=${encodeURIComponent(data.orderId)}&payId=${encodeURIComponent(data.payId)}`;
    const resp = await fetch(url, { method: "GET", headers });
    const text = await resp.text();
    let v: any = {};
    try {
      v = text ? JSON.parse(text) : {};
    } catch {
      v = {};
    }

    // Also check our own DB (webhook may have credited)
    const { data: ref } = await supabaseAdmin
      .from("external_references")
      .select("credited,status")
      .eq("external_ref", data.orderId)
      .maybeSingle();

    let status: "SUCCESS" | "PENDING" | "FAILED" = "PENDING";
    if (ref?.credited) status = "SUCCESS";
    else {
      const raw = (
        v?.status ||
        v?.state ||
        v?.data?.status ||
        ""
      )
        .toString()
        .toUpperCase();
      const failedEnvelope = v?.success === false;
      if (
        raw.includes("SUCCESS") ||
        raw === "OK" ||
        raw.includes("PAID") ||
        raw.includes("COMPLETED")
      ) {
        status = failedEnvelope ? "FAILED" : "SUCCESS";
      } else if (
        raw.includes("FAIL") ||
        raw.includes("CANCEL") ||
        raw.includes("REJECT") ||
        raw.includes("EXPIRED")
      ) {
        status = "FAILED";
      }
    }

    return { status, credited: !!ref?.credited, raw: v };
  });