
-- external_references: link external payment ref -> user
CREATE TABLE public.external_references (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL,
  email text,
  external_ref text NOT NULL UNIQUE,
  amount integer NOT NULL,
  operator text,
  phone text,
  status text NOT NULL DEFAULT 'PENDING',
  credited boolean NOT NULL DEFAULT false,
  credited_at timestamptz,
  payid text,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_external_references_user ON public.external_references(user_id);
CREATE INDEX idx_external_references_ref ON public.external_references(external_ref);

ALTER TABLE public.external_references ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Users view own external refs" ON public.external_references
  FOR SELECT USING (auth.uid() = user_id);
CREATE POLICY "Admins view all external refs" ON public.external_references
  FOR SELECT TO authenticated USING (public.has_role(auth.uid(), 'admin'));

-- webhook_events: raw log of incoming webhooks
CREATE TABLE public.webhook_events (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  source text NOT NULL DEFAULT 'soleaspay',
  payload jsonb NOT NULL,
  processed boolean NOT NULL DEFAULT false,
  message text,
  external_ref text,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_webhook_events_ref ON public.webhook_events(external_ref);

ALTER TABLE public.webhook_events ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Admins view all webhook events" ON public.webhook_events
  FOR SELECT TO authenticated USING (public.has_role(auth.uid(), 'admin'));

-- Register external_reference (called by app before initiating SoleasPay payment)
CREATE OR REPLACE FUNCTION public.register_external_reference(
  p_external_ref text,
  p_amount integer,
  p_operator text,
  p_phone text
) RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path TO 'public'
AS $$
DECLARE
  v_uid uuid := auth.uid();
  v_email text;
BEGIN
  IF v_uid IS NULL THEN RAISE EXCEPTION 'not_authenticated'; END IF;
  IF p_external_ref IS NULL OR length(p_external_ref) < 4 THEN RAISE EXCEPTION 'invalid_ref'; END IF;
  IF p_amount IS NULL OR p_amount < 100 OR p_amount > 100000 THEN RAISE EXCEPTION 'invalid_amount'; END IF;

  SELECT email::text INTO v_email FROM auth.users WHERE id = v_uid;

  INSERT INTO public.external_references (user_id, email, external_ref, amount, operator, phone)
  VALUES (v_uid, v_email, p_external_ref, p_amount, p_operator, p_phone)
  ON CONFLICT (external_ref) DO NOTHING;
END;
$$;

-- Process SoleasPay webhook (called by service role from edge route)
CREATE OR REPLACE FUNCTION public.process_soleaspay_webhook(p_payload jsonb)
RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path TO 'public'
AS $$
DECLARE
  v_ref text;
  v_status text;
  v_internal text;
  v_amount integer;
  v_row public.external_references%ROWTYPE;
  v_event_id uuid;
BEGIN
  -- Extract externalRef from any of the known shapes
  v_ref := COALESCE(
    p_payload->>'externalRef',
    p_payload->'données'->>'référence_externe',
    p_payload->'data'->>'external_reference',
    p_payload->>'external_reference'
  );
  v_status := UPPER(COALESCE(
    p_payload->>'status',
    p_payload->'data'->>'status',
    ''
  ));
  v_internal := COALESCE(
    p_payload->>'internalRef',
    p_payload->'données'->>'référence',
    p_payload->'data'->>'reference'
  );
  v_amount := COALESCE(
    NULLIF(p_payload->>'amount','')::int,
    NULLIF(p_payload->'données'->>'montant','')::int,
    NULLIF(p_payload->'data'->>'amount','')::int
  );

  -- Log the event
  INSERT INTO public.webhook_events (source, payload, external_ref)
  VALUES ('soleaspay', p_payload, v_ref)
  RETURNING id INTO v_event_id;

  IF v_ref IS NULL THEN
    UPDATE public.webhook_events SET message = 'missing_external_ref' WHERE id = v_event_id;
    RETURN jsonb_build_object('ok', false, 'reason', 'missing_external_ref');
  END IF;

  SELECT * INTO v_row FROM public.external_references WHERE external_ref = v_ref;
  IF NOT FOUND THEN
    UPDATE public.webhook_events SET message = 'reference_not_found' WHERE id = v_event_id;
    RETURN jsonb_build_object('ok', false, 'reason', 'reference_not_found', 'ref', v_ref);
  END IF;

  IF v_status <> 'SUCCESS' THEN
    UPDATE public.external_references
       SET status = COALESCE(NULLIF(v_status,''), status), payid = COALESCE(v_internal, payid)
     WHERE id = v_row.id;
    UPDATE public.webhook_events SET processed = true, message = 'status_' || v_status WHERE id = v_event_id;
    RETURN jsonb_build_object('ok', true, 'credited', false, 'status', v_status);
  END IF;

  IF v_row.credited THEN
    UPDATE public.webhook_events SET processed = true, message = 'already_credited' WHERE id = v_event_id;
    RETURN jsonb_build_object('ok', true, 'credited', false, 'reason', 'already_credited');
  END IF;

  -- Credit the user
  UPDATE public.profiles
     SET coins = coins + v_row.amount, updated_at = now()
   WHERE user_id = v_row.user_id;

  INSERT INTO public.coin_transactions (user_id, amount, reason, metadata)
  VALUES (v_row.user_id, v_row.amount, 'purchase',
    jsonb_build_object(
      'operator', v_row.operator,
      'phone', v_row.phone,
      'payId', COALESCE(v_internal, ''),
      'externalRef', v_ref,
      'status', 'SUCCESS',
      'source', 'webhook'
    ));

  UPDATE public.external_references
     SET credited = true, credited_at = now(), status = 'SUCCESS',
         payid = COALESCE(v_internal, payid)
   WHERE id = v_row.id;

  UPDATE public.webhook_events SET processed = true, message = 'credited' WHERE id = v_event_id;

  RETURN jsonb_build_object('ok', true, 'credited', true, 'user_id', v_row.user_id, 'amount', v_row.amount);
END;
$$;

-- Update purchase_coins minimum amount to 100
CREATE OR REPLACE FUNCTION public.purchase_coins(p_amount integer, p_operator text, p_phone text, p_payid text DEFAULT NULL::text, p_status text DEFAULT 'SUCCESS'::text)
 RETURNS TABLE(coins integer)
 LANGUAGE plpgsql SECURITY DEFINER SET search_path TO 'public'
AS $function$
DECLARE
  v_uid uuid := auth.uid();
  v_coins integer;
BEGIN
  IF v_uid IS NULL THEN RAISE EXCEPTION 'not_authenticated'; END IF;
  IF p_amount IS NULL OR p_amount < 100 OR p_amount > 100000 THEN RAISE EXCEPTION 'invalid_amount'; END IF;
  IF p_operator IS NULL OR length(p_operator) < 2 OR length(p_operator) > 40 THEN RAISE EXCEPTION 'invalid_operator'; END IF;
  IF p_phone IS NULL OR length(p_phone) < 8 OR length(p_phone) > 20 THEN RAISE EXCEPTION 'invalid_phone'; END IF;

  UPDATE public.profiles SET coins = coins + p_amount, updated_at = now()
   WHERE user_id = v_uid RETURNING profiles.coins INTO v_coins;
  IF v_coins IS NULL THEN RAISE EXCEPTION 'profile_not_found'; END IF;

  INSERT INTO public.coin_transactions (user_id, amount, reason, metadata)
  VALUES (v_uid, p_amount, 'purchase',
    jsonb_build_object('operator', p_operator, 'phone', p_phone,
                       'payId', COALESCE(p_payid,''), 'status', COALESCE(p_status,'SUCCESS')));

  RETURN QUERY SELECT v_coins;
END;
$function$;
