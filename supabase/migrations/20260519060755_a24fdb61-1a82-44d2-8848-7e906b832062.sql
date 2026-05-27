
CREATE TABLE public.channels (
  id BIGSERIAL PRIMARY KEY,
  tvg_id TEXT,
  name TEXT NOT NULL,
  logo TEXT,
  group_title TEXT,
  stream_url TEXT NOT NULL,
  sort_order INTEGER NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX channels_group_idx ON public.channels (group_title);
CREATE INDEX channels_name_idx ON public.channels (name);
CREATE INDEX channels_sort_idx ON public.channels (sort_order);

ALTER TABLE public.channels ENABLE ROW LEVEL SECURITY;

-- Public read access (IPTV playlist is public to the app)
CREATE POLICY "Channels are viewable by everyone"
  ON public.channels FOR SELECT
  USING (true);

CREATE TABLE public.playlist_meta (
  id INTEGER PRIMARY KEY DEFAULT 1,
  last_sync TIMESTAMPTZ,
  total_channels INTEGER NOT NULL DEFAULT 0,
  CONSTRAINT single_row CHECK (id = 1)
);

INSERT INTO public.playlist_meta (id, total_channels) VALUES (1, 0);

ALTER TABLE public.playlist_meta ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Meta viewable by everyone"
  ON public.playlist_meta FOR SELECT
  USING (true);
