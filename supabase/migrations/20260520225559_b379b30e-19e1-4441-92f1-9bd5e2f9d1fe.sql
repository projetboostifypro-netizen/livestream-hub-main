
CREATE OR REPLACE FUNCTION public.register_external_reference_public(
  p_email text,
  p_external_ref text,
  p_amount integer,
  p_operator text,
  p_phone text
) RETURNS uuid
LANGUAGE plpgsql SECURITY DEFINER SET search_path TO 'public'
AS $$
DECLARE
  v_uid uuid;
  v_id uuid;
BEGIN
  IF p_email IS NULL OR length(p_email) < 5 THEN RAISE EXCEPTION 'invalid_email'; END IF;
  IF p_external_ref IS NULL OR length(p_external_ref) < 4 THEN RAISE EXCEPTION 'invalid_ref'; END IF;
  IF p_amount IS NULL OR p_amount < 100 OR p_amount > 100000 THEN RAISE EXCEPTION 'invalid_amount'; END IF;

  SELECT id INTO v_uid FROM auth.users WHERE lower(email) = lower(p_email) LIMIT 1;
  IF v_uid IS NULL THEN RAISE EXCEPTION 'user_not_found'; END IF;

  INSERT INTO public.external_references (user_id, email, external_ref, amount, operator, phone)
  VALUES (v_uid, lower(p_email), p_external_ref, p_amount, p_operator, p_phone)
  ON CONFLICT (external_ref) DO UPDATE SET amount = EXCLUDED.amount
  RETURNING id INTO v_id;

  RETURN v_id;
END;
$$;

REVOKE ALL ON FUNCTION public.register_external_reference_public(text,text,integer,text,text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.register_external_reference_public(text,text,integer,text,text) TO anon, authenticated;
