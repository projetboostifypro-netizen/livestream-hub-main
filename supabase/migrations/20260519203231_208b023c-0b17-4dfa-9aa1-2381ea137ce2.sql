
-- 1) Rôles
DO $$ BEGIN
  CREATE TYPE public.app_role AS ENUM ('admin', 'user');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

CREATE TABLE IF NOT EXISTS public.user_roles (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL,
  role public.app_role NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (user_id, role)
);

ALTER TABLE public.user_roles ENABLE ROW LEVEL SECURITY;

CREATE OR REPLACE FUNCTION public.has_role(_user_id uuid, _role public.app_role)
RETURNS boolean
LANGUAGE sql
STABLE SECURITY DEFINER
SET search_path = public
AS $$
  SELECT EXISTS (
    SELECT 1 FROM public.user_roles WHERE user_id = _user_id AND role = _role
  )
$$;

DROP POLICY IF EXISTS "Users view own roles" ON public.user_roles;
CREATE POLICY "Users view own roles" ON public.user_roles
  FOR SELECT TO authenticated
  USING (auth.uid() = user_id OR public.has_role(auth.uid(), 'admin'));

-- 2) Profils : blocage + token de session unique
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS is_blocked boolean NOT NULL DEFAULT false;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS blocked_reason text;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS session_token text;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS session_updated_at timestamptz;

-- Admin peut voir tous les profils
DROP POLICY IF EXISTS "Admins view all profiles" ON public.profiles;
CREATE POLICY "Admins view all profiles" ON public.profiles
  FOR SELECT TO authenticated
  USING (public.has_role(auth.uid(), 'admin'));

-- Admin peut modifier tous les profils
DROP POLICY IF EXISTS "Admins update all profiles" ON public.profiles;
CREATE POLICY "Admins update all profiles" ON public.profiles
  FOR UPDATE TO authenticated
  USING (public.has_role(auth.uid(), 'admin'));

-- Admin peut voir toutes les transactions et abonnements
DROP POLICY IF EXISTS "Admins view all transactions" ON public.coin_transactions;
CREATE POLICY "Admins view all transactions" ON public.coin_transactions
  FOR SELECT TO authenticated
  USING (public.has_role(auth.uid(), 'admin'));

DROP POLICY IF EXISTS "Admins view all subscriptions" ON public.subscriptions;
CREATE POLICY "Admins view all subscriptions" ON public.subscriptions
  FOR SELECT TO authenticated
  USING (public.has_role(auth.uid(), 'admin'));

-- 3) Session unique : revendiquer un token (sur login Android)
CREATE OR REPLACE FUNCTION public.claim_session(p_token text)
RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public
AS $$
DECLARE v_uid uuid := auth.uid();
BEGIN
  IF v_uid IS NULL THEN RAISE EXCEPTION 'not_authenticated'; END IF;
  IF p_token IS NULL OR length(p_token) < 8 THEN RAISE EXCEPTION 'invalid_token'; END IF;

  UPDATE public.profiles
     SET session_token = p_token,
         session_updated_at = now()
   WHERE user_id = v_uid;
END;
$$;

-- 4) Vérifier la session courante : renvoie ok / blocked / session_lost
CREATE OR REPLACE FUNCTION public.check_session(p_token text)
RETURNS TABLE(status text, blocked_reason text)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public
AS $$
DECLARE v_uid uuid := auth.uid();
        v_profile public.profiles%ROWTYPE;
BEGIN
  IF v_uid IS NULL THEN RAISE EXCEPTION 'not_authenticated'; END IF;
  SELECT * INTO v_profile FROM public.profiles WHERE user_id = v_uid;
  IF NOT FOUND THEN
    status := 'no_profile'; blocked_reason := NULL; RETURN NEXT; RETURN;
  END IF;
  IF v_profile.is_blocked THEN
    status := 'blocked'; blocked_reason := v_profile.blocked_reason; RETURN NEXT; RETURN;
  END IF;
  IF v_profile.session_token IS NOT NULL AND v_profile.session_token <> p_token THEN
    status := 'session_lost'; blocked_reason := NULL; RETURN NEXT; RETURN;
  END IF;
  status := 'ok'; blocked_reason := NULL; RETURN NEXT;
END;
$$;

-- 5) RPC admin
CREATE OR REPLACE FUNCTION public.admin_block_user(p_user_id uuid, p_reason text)
RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
BEGIN
  IF NOT public.has_role(auth.uid(), 'admin') THEN RAISE EXCEPTION 'forbidden'; END IF;
  UPDATE public.profiles
     SET is_blocked = true, blocked_reason = COALESCE(p_reason, 'Bloqué par admin'),
         session_token = NULL, updated_at = now()
   WHERE user_id = p_user_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.admin_unblock_user(p_user_id uuid)
RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
BEGIN
  IF NOT public.has_role(auth.uid(), 'admin') THEN RAISE EXCEPTION 'forbidden'; END IF;
  UPDATE public.profiles
     SET is_blocked = false, blocked_reason = NULL, updated_at = now()
   WHERE user_id = p_user_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.admin_delete_user(p_user_id uuid)
RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
BEGIN
  IF NOT public.has_role(auth.uid(), 'admin') THEN RAISE EXCEPTION 'forbidden'; END IF;
  DELETE FROM auth.users WHERE id = p_user_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.admin_adjust_coins(p_user_id uuid, p_delta integer, p_reason text)
RETURNS integer LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE v_new integer;
BEGIN
  IF NOT public.has_role(auth.uid(), 'admin') THEN RAISE EXCEPTION 'forbidden'; END IF;
  IF p_delta IS NULL OR p_delta = 0 THEN RAISE EXCEPTION 'invalid_delta'; END IF;

  UPDATE public.profiles SET coins = GREATEST(0, coins + p_delta), updated_at = now()
   WHERE user_id = p_user_id RETURNING coins INTO v_new;
  IF v_new IS NULL THEN RAISE EXCEPTION 'profile_not_found'; END IF;

  INSERT INTO public.coin_transactions (user_id, amount, reason, metadata)
  VALUES (p_user_id, p_delta, 'admin_adjustment',
          jsonb_build_object('admin_id', auth.uid(), 'reason', COALESCE(p_reason,'')));
  RETURN v_new;
END;
$$;

-- 6) Vue admin pour lister les utilisateurs avec email
CREATE OR REPLACE FUNCTION public.admin_list_users()
RETURNS TABLE(user_id uuid, email text, display_name text, coins integer,
              is_blocked boolean, blocked_reason text, created_at timestamptz,
              last_sign_in timestamptz, has_active_session boolean)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
BEGIN
  IF NOT public.has_role(auth.uid(), 'admin') THEN RAISE EXCEPTION 'forbidden'; END IF;
  RETURN QUERY
  SELECT p.user_id, u.email::text, p.display_name, p.coins,
         p.is_blocked, p.blocked_reason, p.created_at,
         u.last_sign_in_at, (p.session_token IS NOT NULL)
    FROM public.profiles p
    LEFT JOIN auth.users u ON u.id = p.user_id
   ORDER BY p.created_at DESC;
END;
$$;

CREATE OR REPLACE FUNCTION public.admin_list_transactions(p_limit integer DEFAULT 200)
RETURNS TABLE(id uuid, user_id uuid, email text, amount integer, reason text,
              metadata jsonb, created_at timestamptz)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
BEGIN
  IF NOT public.has_role(auth.uid(), 'admin') THEN RAISE EXCEPTION 'forbidden'; END IF;
  RETURN QUERY
  SELECT t.id, t.user_id, u.email::text, t.amount, t.reason, t.metadata, t.created_at
    FROM public.coin_transactions t
    LEFT JOIN auth.users u ON u.id = t.user_id
   ORDER BY t.created_at DESC
   LIMIT GREATEST(1, LEAST(p_limit, 1000));
END;
$$;
