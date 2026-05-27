CREATE TABLE public.admin_settings (
  key text PRIMARY KEY,
  value text NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE public.admin_settings ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Authenticated can read settings"
ON public.admin_settings FOR SELECT
TO authenticated
USING (true);

INSERT INTO public.admin_settings (key, value) VALUES
  ('soleaspay_api_key', 'REPLACE_WITH_LIVE_KEY'),
  ('soleaspay_service_id', 'REPLACE_WITH_SERVICE_ID'),
  ('soleaspay_base_url', 'https://api.soleaspay.com');