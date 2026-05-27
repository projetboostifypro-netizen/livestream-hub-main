
DELETE FROM public.subscriptions;
DELETE FROM public.subscription_plans;

ALTER TABLE public.subscription_plans
  ADD COLUMN IF NOT EXISTS is_popular boolean NOT NULL DEFAULT false;

INSERT INTO public.subscription_plans (name, duration_minutes, price_coins, sort_order, is_popular) VALUES
  ('1 heure',  60,      100,   1, false),
  ('1 jour',   1440,    600,   2, false),
  ('7 jours',  10080,   1500,  3, false),
  ('14 jours', 20160,   2500,  4, false),
  ('30 jours', 43200,   4000,  5, false),
  ('3 mois',   129600,  10000, 6, true),
  ('12 mois',  525600,  30000, 7, false);

CREATE OR REPLACE FUNCTION public.purchase_coins(p_amount integer, p_operator text, p_phone text)
RETURNS TABLE(coins integer)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $function$
DECLARE
  v_uid uuid := auth.uid();
  v_coins integer;
BEGIN
  IF v_uid IS NULL THEN
    RAISE EXCEPTION 'not_authenticated';
  END IF;
  IF p_amount IS NULL OR p_amount < 100 OR p_amount > 100000 THEN
    RAISE EXCEPTION 'invalid_amount';
  END IF;
  IF p_operator IS NULL OR length(p_operator) < 2 OR length(p_operator) > 40 THEN
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
    jsonb_build_object('operator', p_operator, 'phone', p_phone));

  RETURN QUERY SELECT v_coins;
END;
$function$;
