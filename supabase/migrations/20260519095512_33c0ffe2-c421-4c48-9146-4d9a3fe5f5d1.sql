DROP FUNCTION IF EXISTS public.purchase_plan(bigint);

CREATE OR REPLACE FUNCTION public.purchase_plan(p_plan_id bigint)
 RETURNS TABLE(expires_at timestamp with time zone, coins integer)
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
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

  UPDATE public.profiles AS p
     SET coins = p.coins - v_plan.price_coins, updated_at = now()
   WHERE p.user_id = v_uid;

  INSERT INTO public.coin_transactions (user_id, amount, reason, metadata)
  VALUES (v_uid, -v_plan.price_coins, 'plan_purchase',
    jsonb_build_object('plan_id', v_plan.id, 'plan_name', v_plan.name));

  expires_at := v_new_expires;
  coins := v_coins - v_plan.price_coins;
  RETURN NEXT;
END;
$function$;