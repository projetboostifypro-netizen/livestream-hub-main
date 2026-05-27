import { useRef } from "react";
import { ChevronLeft, ChevronRight } from "lucide-react";
import { ChannelCard } from "./ChannelCard";

interface Channel {
  id: number;
  name: string;
  logo: string | null;
  group_title: string | null;
}

export function ChannelRow({ title, channels }: { title: string; channels: Channel[] }) {
  const scrollRef = useRef<HTMLDivElement>(null);

  const scroll = (dir: "left" | "right") => {
    if (!scrollRef.current) return;
    const amount = scrollRef.current.clientWidth * 0.8;
    scrollRef.current.scrollBy({ left: dir === "left" ? -amount : amount, behavior: "smooth" });
  };

  if (!channels.length) return null;

  return (
    <section className="group/row relative space-y-2 py-3">
      <h2 className="px-4 text-base font-bold tracking-tight md:px-8 md:text-xl">
        {title} <span className="text-sm font-normal text-muted-foreground">· {channels.length}</span>
      </h2>
      <div className="relative">
        <button
          onClick={() => scroll("left")}
          className="absolute left-0 top-0 z-10 hidden h-full w-12 items-center justify-center bg-gradient-to-r from-background/90 to-transparent opacity-0 transition-opacity group-hover/row:opacity-100 md:flex"
        >
          <ChevronLeft className="h-6 w-6" />
        </button>
        <div
          ref={scrollRef}
          className="flex gap-3 overflow-x-auto px-4 pb-2 scrollbar-hide md:px-8"
          style={{ scrollbarWidth: "none" }}
        >
          {channels.map((c) => (
            <ChannelCard key={c.id} id={c.id} name={c.name} logo={c.logo} group={c.group_title} />
          ))}
        </div>
        <button
          onClick={() => scroll("right")}
          className="absolute right-0 top-0 z-10 hidden h-full w-12 items-center justify-center bg-gradient-to-l from-background/90 to-transparent opacity-0 transition-opacity group-hover/row:opacity-100 md:flex"
        >
          <ChevronRight className="h-6 w-6" />
        </button>
      </div>
    </section>
  );
}