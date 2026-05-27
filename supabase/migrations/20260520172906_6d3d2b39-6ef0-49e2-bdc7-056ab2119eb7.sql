
CREATE OR REPLACE FUNCTION public.admin_list_subscriptions(p_limit integer DEFAULT 500)
 RETURNS TABLE(id uuid, user_id uuid, email text, plan_id bigint, plan_name text, starts_at timestamptz, expires_at timestamptz, is_active boolean, created_at timestamptz)
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
BEGIN
  IF NOT public.has_role(auth.uid(), 'admin') THEN RAISE EXCEPTION 'forbidden'; END IF;
  RETURN QUERY
  SELECT s.id, s.user_id, u.email::text, s.plan_id, p.name, s.starts_at, s.expires_at,
         (s.expires_at > now()) AS is_active, s.created_at
    FROM public.subscriptions s
    LEFT JOIN auth.users u ON u.id = s.user_id
    LEFT JOIN public.subscription_plans p ON p.id = s.plan_id
   ORDER BY (s.expires_at > now()) DESC, s.expires_at DESC
   LIMIT GREATEST(1, LEAST(p_limit, 2000));
END;
$function$;
