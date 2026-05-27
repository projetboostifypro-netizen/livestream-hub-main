import logo from "@/assets/flow-logo.png";

export function Logo({ size = 36 }: { size?: number }) {
  return (
    <div className="flex items-center gap-2">
      <img
        src={logo}
        alt="FLOW+"
        width={size}
        height={size}
        className="object-contain"
        style={{ width: size, height: size }}
      />
      <span
        className="text-xl font-black tracking-tight"
        style={{
          background: "var(--gradient-primary)",
          WebkitBackgroundClip: "text",
          WebkitTextFillColor: "transparent",
        }}
      >
        FLOW<span style={{ color: "var(--gold)", WebkitTextFillColor: "var(--gold)" }}>+</span>
      </span>
    </div>
  );
}