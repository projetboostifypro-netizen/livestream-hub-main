
-- Table des liens M3U
CREATE TABLE public.playlist_links (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  url text NOT NULL UNIQUE,
  is_active boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE public.playlist_links ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Admins manage playlist_links"
ON public.playlist_links FOR SELECT TO authenticated
USING (has_role(auth.uid(), 'admin'::app_role));

-- Lien rattaché à l'abonnement
ALTER TABLE public.subscriptions
  ADD COLUMN playlist_link_id uuid REFERENCES public.playlist_links(id) ON DELETE SET NULL;

CREATE INDEX idx_subscriptions_link ON public.subscriptions(playlist_link_id) WHERE playlist_link_id IS NOT NULL;
CREATE INDEX idx_subscriptions_active ON public.subscriptions(expires_at);

-- Seed des 5 liens fournis
INSERT INTO public.playlist_links (url) VALUES
  ('http://51.75.118.17:20336/api/proxy/playlist/8ec5ebaf371845dc8fd1d11402cadb0f'),
  ('http://51.75.118.17:20336/api/proxy/playlist/0e8be0ff2832c4b80f51d0e7e097976a'),
  ('http://51.75.118.17:20336/api/proxy/playlist/d644f43de467675ce73451eef18d87eb'),
  ('http://51.75.118.17:20336/api/proxy/playlist/83246936b12b15d8ae970940979b97bc'),
  ('http://51.75.118.17:20336/api/proxy/playlist/339a4dfd8c67a37eb22c7cbc617b045d');

-- Admin: ajouter un lien
CREATE OR REPLACE FUNCTION public.admin_add_playlist_link(p_url text)
RETURNS uuid LANGUAGE plpgsql SECURITY DEFINER SET search_path TO 'public' AS $$
DECLARE v_id uuid;
BEGIN
  IF NOT public.has_role(auth.uid(), 'admin') THEN RAISE EXCEPTION 'forbidden'; END IF;
  IF p_url IS NULL OR length(p_url) < 10 THEN RAISE EXCEPTION 'invalid_url'; END IF;
  INSERT INTO public.playlist_links (url) VALUES (p_url)
  ON CONFLICT (url) DO UPDATE SET is_active = true
  RETURNING id INTO v_id;
  RETURN v_id;
END; $$;

-- Admin: lister les liens avec statut d'utilisation
CREATE OR REPLACE FUNCTION public.admin_list_playlist_links()
RETURNS TABLE(id uuid, url text, is_active boolean, created_at timestamptz, in_use boolean, assigned_email text, expires_at timestamptz)
LANGUAGE plpgsql SECURITY DEFINER SET search_path TO 'public' AS $$
BEGIN
  IF NOT public.has_role(auth.uid(), 'admin') THEN RAISE EXCEPTION 'forbidden'; END IF;
  RETURN QUERY
  SELECT l.id, l.url, l.is_active, l.created_at,
         (s.id IS NOT NULL) AS in_use,
         u.email::text AS assigned_email,
         s.expires_at
    FROM public.playlist_links l
    LEFT JOIN LATERAL (
      SELECT s.id, s.user_id, s.expires_at FROM public.subscriptions s
      WHERE s.playlist_link_id = l.id AND s.expires_at > now()
      ORDER BY s.expires_at DESC LIMIT 1
    ) s ON true
    LEFT JOIN auth.users u ON u.id = s.user_id
   ORDER BY l.created_at DESC;
END; $$;

-- Admin: supprimer un lien (uniquement si pas en cours d'utilisation)
CREATE OR REPLACE FUNCTION public.admin_delete_playlist_link(p_id uuid)
RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path TO 'public' AS $$
BEGIN
  IF NOT public.has_role(auth.uid(), 'admin') THEN RAISE EXCEPTION 'forbidden'; END IF;
  IF EXISTS (SELECT 1 FROM public.subscriptions WHERE playlist_link_id = p_id AND expires_at > now()) THEN
    RAISE EXCEPTION 'link_in_use';
  END IF;
  DELETE FROM public.playlist_links WHERE id = p_id;
END; $$;

-- Retourne l'URL M3U de l'utilisateur connecté (uniquement si abonnement actif)
CREATE OR REPLACE FUNCTION public.get_my_playlist_url()
RETURNS text LANGUAGE plpgsql SECURITY DEFINER SET search_path TO 'public' AS $$
DECLARE v_uid uuid := auth.uid(); v_url text;
BEGIN
  IF v_uid IS NULL THEN RAISE EXCEPTION 'not_authenticated'; END IF;
  SELECT l.url INTO v_url
    FROM public.subscriptions s
    JOIN public.playlist_links l ON l.id = s.playlist_link_id
   WHERE s.user_id = v_uid AND s.expires_at > now() AND l.is_active = true
   ORDER BY s.expires_at DESC LIMIT 1;
  RETURN v_url;
END; $$;

-- Remplace purchase_plan : assigne un lien libre lors d'un nouvel abonnement
CREATE OR REPLACE FUNCTION public.purchase_plan(p_plan_id bigint)
RETURNS TABLE(expires_at timestamptz, coins integer)
LANGUAGE plpgsql SECURITY DEFINER SET search_path TO 'public' AS $$
DECLARE
  v_uid uuid := auth.uid();
  v_plan public.subscription_plans%ROWTYPE;
  v_coins integer;
  v_base timestamptz;
  v_new_expires timestamptz;
  v_existing_link uuid;
  v_link_id uuid;
BEGIN
  IF v_uid IS NULL THEN RAISE EXCEPTION 'not_authenticated'; END IF;

  SELECT * INTO v_plan FROM public.subscription_plans WHERE id = p_plan_id;
  IF NOT FOUND THEN RAISE EXCEPTION 'plan_not_found'; END IF;

  SELECT p.coins INTO v_coins FROM public.profiles p WHERE p.user_id = v_uid FOR UPDATE;
  IF v_coins IS NULL THEN RAISE EXCEPTION 'profile_not_found'; END IF;
  IF v_coins < v_plan.price_coins THEN RAISE EXCEPTION 'insufficient_coins'; END IF;

  -- Cherche un abonnement actif existant (on étend et on garde son lien)
  SELECT s.expires_at, s.playlist_link_id INTO v_base, v_existing_link
    FROM public.subscriptions s
   WHERE s.user_id = v_uid AND s.expires_at > now()
   ORDER BY s.expires_at DESC LIMIT 1;

  IF v_existing_link IS NOT NULL THEN
    v_link_id := v_existing_link;
  ELSE
    -- Trouve un lien libre (pas utilisé par un abonnement actif)
    SELECT l.id INTO v_link_id
      FROM public.playlist_links l
     WHERE l.is_active = true
       AND NOT EXISTS (
         SELECT 1 FROM public.subscriptions s2
         WHERE s2.playlist_link_id = l.id AND s2.expires_at > now()
       )
     ORDER BY l.created_at ASC
     LIMIT 1
     FOR UPDATE SKIP LOCKED;

    IF v_link_id IS NULL THEN RAISE EXCEPTION 'no_link_available'; END IF;
  END IF;

  v_base := COALESCE(v_base, now());
  v_new_expires := v_base + make_interval(mins => v_plan.duration_minutes);

  INSERT INTO public.subscriptions (user_id, plan_id, starts_at, expires_at, playlist_link_id)
  VALUES (v_uid, v_plan.id, now(), v_new_expires, v_link_id);

  UPDATE public.profiles AS p
     SET coins = p.coins - v_plan.price_coins, updated_at = now()
   WHERE p.user_id = v_uid;

  INSERT INTO public.coin_transactions (user_id, amount, reason, metadata)
  VALUES (v_uid, -v_plan.price_coins, 'plan_purchase',
    jsonb_build_object('plan_id', v_plan.id, 'plan_name', v_plan.name, 'link_id', v_link_id));

  expires_at := v_new_expires;
  coins := v_coins - v_plan.price_coins;
  RETURN NEXT;
END; $$;

-- Supprimer l'ancien système playlist : tables channels et playlist_meta
DROP TABLE IF EXISTS public.channels CASCADE;
DROP TABLE IF EXISTS public.playlist_meta CASCADE;
