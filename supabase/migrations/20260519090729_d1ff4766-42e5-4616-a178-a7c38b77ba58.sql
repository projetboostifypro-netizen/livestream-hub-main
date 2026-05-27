
-- Active subscription getter
CREATE OR REPLACE FUNCTION public.get_active_subscription()
RETURNS TABLE (
  id uuid,
  plan_id bigint,
  plan_name text,
  starts_at timestamptz,
  expires_at timestamptz,
  seconds_remaining bigint
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  IF auth.uid() IS NULL THEN
    RAISE EXCEPTION 'not_authenticated';
  END IF;

  RETURN QUERY
  SELECT s.id, s.plan_id, p.name, s.starts_at, s.expires_at,
    GREATEST(0, EXTRACT(EPOCH FROM (s.expires_at - now()))::bigint) AS seconds_remaining
  FROM public.subscriptions s
  JOIN public.subscription_plans p ON p.id = s.plan_id
  WHERE s.user_id = auth.uid()
    AND s.expires_at > now()
  ORDER BY s.expires_at DESC
  LIMIT 1;
END;
$$;

-- Purchase plan with coins
CREATE OR REPLACE FUNCTION public.purchase_plan(p_plan_id bigint)
RETURNS TABLE (
  expires_at timestamptz,
  coins integer
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_uid uuid := auth.uid();
  v_plan public.subscription_plans%ROWTYPE;
  v_coins integer;
  v_base timestamptz;
  v_new_expires timestamptz;
BEGIN
  IF v_uid IS NULL THEN
    RAISE EXCEPTION 'not_authenticated';
  END IF;

  SELECT * INTO v_plan FROM public.subscription_plans WHERE id = p_plan_id;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'plan_not_found';
  END IF;

  SELECT p.coins INTO v_coins FROM public.profiles p WHERE p.user_id = v_uid FOR UPDATE;
  IF v_coins IS NULL THEN
    RAISE EXCEPTION 'profile_not_found';
  END IF;
  IF v_coins < v_plan.price_coins THEN
    RAISE EXCEPTION 'insufficient_coins';
  END IF;

  SELECT s.expires_at INTO v_base
  FROM public.subscriptions s
  WHERE s.user_id = v_uid AND s.expires_at > now()
  ORDER BY s.expires_at DESC
  LIMIT 1;

  v_base := COALESCE(v_base, now());
  v_new_expires := v_base + make_interval(mins => v_plan.duration_minutes);

  INSERT INTO public.subscriptions (user_id, plan_id, starts_at, expires_at)
  VALUES (v_uid, v_plan.id, now(), v_new_expires);

  UPDATE public.profiles SET coins = coins - v_plan.price_coins, updated_at = now()
  WHERE user_id = v_uid;

  INSERT INTO public.coin_transactions (user_id, amount, reason, metadata)
  VALUES (v_uid, -v_plan.price_coins, 'plan_purchase',
    jsonb_build_object('plan_id', v_plan.id, 'plan_name', v_plan.name));

  RETURN QUERY SELECT v_new_expires, v_coins - v_plan.price_coins;
END;
$$;

-- Purchase coins (Mobile Money simulated)
CREATE OR REPLACE FUNCTION public.purchase_coins(p_amount integer, p_operator text, p_phone text)
RETURNS TABLE (coins integer)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_uid uuid := auth.uid();
  v_coins integer;
BEGIN
  IF v_uid IS NULL THEN
    RAISE EXCEPTION 'not_authenticated';
  END IF;
  IF p_amount IS NULL OR p_amount <= 0 OR p_amount > 50000 THEN
    RAISE EXCEPTION 'invalid_amount';
  END IF;
  IF p_operator NOT IN ('orange','mtn','moov','wave') THEN
    RAISE EXCEPTION 'invalid_operator';
  END IF;
  IF p_phone IS NULL OR length(p_phone) < 8 OR length(p_phone) > 20 THEN
    RAISE EXCEPTION 'invalid_phone';
  END IF;

  UPDATE public.profiles
  SET coins = coins + p_amount, updated_at = now()
  WHERE user_id = v_uid
  RETURNING profiles.coins INTO v_coins;

  IF v_coins IS NULL THEN
    RAISE EXCEPTION 'profile_not_found';
  END IF;

  INSERT INTO public.coin_transactions (user_id, amount, reason, metadata)
  VALUES (v_uid, p_amount, 'purchase',
    jsonb_build_object('operator', p_operator, 'phone', p_phone, 'simulated', true));

  RETURN QUERY SELECT v_coins;
END;
$$;

GRANT EXECUTE ON FUNCTION public.get_active_subscription() TO authenticated;
GRANT EXECUTE ON FUNCTION public.purchase_plan(bigint) TO authenticated;
GRANT EXECUTE ON FUNCTION public.purchase_coins(integer, text, text) TO authenticated;
