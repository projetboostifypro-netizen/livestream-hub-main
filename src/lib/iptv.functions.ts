import { createServerFn } from "@tanstack/react-start";
import { z } from "zod";
import { requireSupabaseAuth } from "@/integrations/supabase/auth-middleware";
import { supabaseAdmin } from "@/integrations/supabase/client.server";

export interface ParsedChannel {
  id: number; // index dans la playlist
  tvg_id: string | null;
  name: string;
  logo: string | null;
  group_title: string | null;
  stream_url: string;
}

function attr(line: string, key: string): string | null {
  const m = line.match(new RegExp(`${key}="([^"]*)"`));
  return m ? m[1] : null;
}

function parseM3U(text: string): ParsedChannel[] {
  const lines = text.split(/\r?\n/);
  const out: ParsedChannel[] = [];
  let current: Partial<ParsedChannel> | null = null;
  let idx = 0;
  for (const raw of lines) {
    const line = raw.trim();
    if (!line) continue;
    if (line.startsWith("#EXTINF")) {
      const commaIdx = line.lastIndexOf(",");
      const name = commaIdx >= 0 ? line.slice(commaIdx + 1).trim() : "Sans nom";
      current = {
        tvg_id: attr(line, "tvg-id"),
        name,
        logo: attr(line, "tvg-logo"),
        group_title: attr(line, "group-title"),
      };
    } else if (!line.startsWith("#") && current) {
      out.push({
        id: idx,
        tvg_id: current.tvg_id ?? null,
        name: current.name ?? "Sans nom",
        logo: current.logo ?? null,
        group_title: current.group_title ?? null,
        stream_url: line,
      });
      idx += 1;
      current = null;
    }
  }
  return out;
}

async function fetchUserPlaylist(userId: string): Promise<ParsedChannel[]> {
  const { data: row } = await supabaseAdmin
    .from("subscriptions")
    .select("playlist_link_id, expires_at, playlist_links(url, is_active)")
    .eq("user_id", userId)
    .gt("expires_at", new Date().toISOString())
    .order("expires_at", { ascending: false })
    .limit(1)
    .maybeSingle();
  const link = (row as any)?.playlist_links;
  const url: string | null = link?.is_active ? (link.url as string) : null;
  if (!url) throw new Error("no_active_subscription");

  const res = await fetch(url, { signal: AbortSignal.timeout(20_000) });
  if (!res.ok) throw new Error(`playlist_fetch_failed_${res.status}`);
  const text = await res.text();
  if (!text.includes("#EXTINF")) throw new Error("invalid_playlist");
  return parseM3U(text);
}

export const getMyBrowseRows = createServerFn({ method: "POST" })
  .middleware([requireSupabaseAuth])
  .inputValidator((d) =>
    z
      .object({
        perRow: z.number().min(1).max(50).default(20),
        maxGroups: z.number().min(1).max(40).default(15),
      })
      .parse(d),
  )
  .handler(async ({ data, context }) => {
    const channels = await fetchUserPlaylist(context.userId);
    const byGroup = new Map<string, ParsedChannel[]>();
    for (const c of channels) {
      const g = c.group_title ?? "Autres";
      const arr = byGroup.get(g) ?? [];
      arr.push(c);
      byGroup.set(g, arr);
    }
    const groups = Array.from(byGroup.entries())
      .sort((a, b) => b[1].length - a[1].length)
      .slice(0, data.maxGroups);
    const rows = groups.map(([group, list]) => ({
      group,
      channels: list.slice(0, data.perRow).map((c) => ({
        id: c.id,
        name: c.name,
        logo: c.logo,
        group_title: c.group_title,
      })),
    }));
    const featured = rows[0]?.channels[0] ?? null;
    return { rows, featured };
  });

export const getMyChannelById = createServerFn({ method: "POST" })
  .middleware([requireSupabaseAuth])
  .inputValidator((d) => z.object({ id: z.number().int().min(0) }).parse(d))
  .handler(async ({ data, context }) => {
    const channels = await fetchUserPlaylist(context.userId);
    const channel = channels.find((c) => c.id === data.id) ?? null;
    return { channel };
  });

// --- Admin: gestion des liens M3U ---

export const adminListPlaylistLinks = createServerFn({ method: "GET" })
  .middleware([requireSupabaseAuth])
  .handler(async ({ context }) => {
    const { data, error } = await context.supabase.rpc("admin_list_playlist_links");
    if (error) throw new Error(error.message);
    return { links: data ?? [] };
  });

export const adminAddPlaylistLink = createServerFn({ method: "POST" })
  .middleware([requireSupabaseAuth])
  .inputValidator((d) => z.object({ url: z.string().url().max(500) }).parse(d))
  .handler(async ({ data, context }) => {
    const { data: id, error } = await context.supabase.rpc("admin_add_playlist_link", {
      p_url: data.url,
    });
    if (error) throw new Error(error.message);
    return { id };
  });

export const adminDeletePlaylistLink = createServerFn({ method: "POST" })
  .middleware([requireSupabaseAuth])
  .inputValidator((d) => z.object({ id: z.string().uuid() }).parse(d))
  .handler(async ({ data, context }) => {
    const { error } = await context.supabase.rpc("admin_delete_playlist_link", {
      p_id: data.id,
    });
    if (error) throw new Error(error.message);
    return { ok: true };
  });