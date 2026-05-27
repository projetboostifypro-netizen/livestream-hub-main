
-- Allow admins to read/update admin_settings via RPCs
CREATE OR REPLACE FUNCTION public.admin_get_setting(p_key text)
RETURNS text
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public
AS $$
DECLARE v text;
BEGIN
  IF NOT public.has_role(auth.uid(), 'admin') THEN RAISE EXCEPTION 'forbidden'; END IF;
  SELECT value INTO v FROM public.admin_settings WHERE key = p_key;
  RETURN v;
END;
$$;

CREATE OR REPLACE FUNCTION public.admin_set_setting(p_key text, p_value text)
RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public
AS $$
BEGIN
  IF NOT public.has_role(auth.uid(), 'admin') THEN RAISE EXCEPTION 'forbidden'; END IF;
  INSERT INTO public.admin_settings (key, value, updated_at)
  VALUES (p_key, p_value, now())
  ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value, updated_at = now();
END;
$$;

-- Plans management
CREATE OR REPLACE FUNCTION public.admin_list_plans()
RETURNS SETOF public.subscription_plans
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public
AS $$
BEGIN
  IF NOT public.has_role(auth.uid(), 'admin') THEN RAISE EXCEPTION 'forbidden'; END IF;
  RETURN QUERY SELECT * FROM public.subscription_plans ORDER BY sort_order ASC, id ASC;
END;
$$;

CREATE OR REPLACE FUNCTION public.admin_upsert_plan(
  p_id bigint,
  p_name text,
  p_duration_minutes integer,
  p_price_coins integer,
  p_sort_order integer
) RETURNS bigint
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public
AS $$
DECLARE v_id bigint;
BEGIN
  IF NOT public.has_role(auth.uid(), 'admin') THEN RAISE EXCEPTION 'forbidden'; END IF;
  IF p_id IS NULL THEN
    INSERT INTO public.subscription_plans (name, duration_minutes, price_coins, sort_order, is_popular)
    VALUES (p_name, p_duration_minutes, p_price_coins, COALESCE(p_sort_order,0), false)
    RETURNING id INTO v_id;
  ELSE
    UPDATE public.subscription_plans
       SET name = p_name,
           duration_minutes = p_duration_minutes,
           price_coins = p_price_coins,
           sort_order = COALESCE(p_sort_order, sort_order)
     WHERE id = p_id
    RETURNING id INTO v_id;
  END IF;
  RETURN v_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.admin_delete_plan(p_id bigint)
RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public
AS $$
BEGIN
  IF NOT public.has_role(auth.uid(), 'admin') THEN RAISE EXCEPTION 'forbidden'; END IF;
  DELETE FROM public.subscription_plans WHERE id = p_id;
END;
$$;
