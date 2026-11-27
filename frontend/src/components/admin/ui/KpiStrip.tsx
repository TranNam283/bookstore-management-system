import { memo } from "react";

type Item = { label: string; value: React.ReactNode; hint?: string; strong?: boolean };

/** Dải chỉ số tóm tắt phía trên bảng dữ liệu. */
function KpiStrip({ items }: { items: Item[] }) {
  return (
    <div className="grid grid-cols-2 gap-2 sm:grid-cols-3 lg:grid-cols-6">
      {items.map((item) => (
        <div key={item.label} className="rounded border border-gray-200 bg-white px-3 py-1.5" title={item.hint}>
          <div className="truncate text-[11px] uppercase tracking-wide text-gray-500">{item.label}</div>
          <div className={`tabular-nums text-[15px] ${item.strong ? "font-bold text-gray-900" : "font-semibold text-gray-800"}`}>
            {item.value}
          </div>
        </div>
      ))}
    </div>
  );
}

export default memo(KpiStrip);
