import { Link } from "@tanstack/react-router";
import { Tv } from "lucide-react";

interface Props {
  id: number;
  name: string;
  logo: string | null;
  group: string | null;
}

export function ChannelCard({ id, name, logo, group }: Props) {
  return (
    <Link
      to="/_authenticated/channel/$id"
      params={{ id: String(id) }}
      className="group relative block w-[160px] shrink-0 sm:w-[200px]"
    >
      <div className="relative aspect-video overflow-hidden rounded-md bg-card transition-all duration-300 group-hover:scale-105 group-hover:ring-2 group-hover:ring-primary group-hover:shadow-[var(--shadow-glow)]">
        {logo ? (
          <img
            src={logo}
            alt={name}
            loading="lazy"
            onError={(e) => ((e.currentTarget.style.display = "none"))}
            className="h-full w-full object-contain p-3"
          />
        ) : (
          <div className="flex h-full w-full items-center justify-center">
            <Tv className="h-8 w-8 text-muted-foreground" />
          </div>
        )}
        <div className="absolute inset-x-0 bottom-0 bg-gradient-to-t from-black/90 to-transparent p-2 opacity-0 transition-opacity group-hover:opacity-100">
          <div className="truncate text-xs font-semibold">{name}</div>
          {group && <div className="truncate text-[10px] text-muted-foreground">{group}</div>}
        </div>
      </div>
    </Link>
  );
}