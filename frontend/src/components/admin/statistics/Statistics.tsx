import { useMemo } from "react";
import { useSearchParams } from "react-router-dom";
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  ComposedChart,
  Legend,
  Line,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import useStatistics, {
  type GroupBy,
  type PeriodRow,
  type StaffRow,
} from "../../../hooks/admin/useStatistics";
import Loading from "../../Loading";
import { formatMoney, formatNumber, formatShortMoney, toISODate } from "../../../utils/format";

// Bảng màu phân loại (thứ tự cố định, đã kiểm định cho người mù màu)
const SERIES = { revenue: "#2a78d6", cost: "#eb6834", profit: "#1baf7a" };
const BEST = "#184f95"; // cùng tông xanh, đậm hơn: kỳ cao nhất
const GRID = "#e5e7eb";
const AXIS = "#52514e";

const TABS = [
  { key: "revenue", label: "Tổng tiền hóa đơn theo thời gian" },
  { key: "profit", label: "Thu – Chi – Lợi nhuận" },
  { key: "staff", label: "Theo nhân viên" },
] as const;

const GROUPS: { value: GroupBy; label: string }[] = [
  { value: "day", label: "Ngày" },
  { value: "month", label: "Tháng" },
  { value: "quarter", label: "Quý" },
  { value: "year", label: "Năm" },
];

const moneyTooltip = (value: unknown) => formatMoney(Number(value));

function Statistics() {
  const [searchParams, setSearchParams] = useSearchParams();
  const tab = (searchParams.get("tab") as (typeof TABS)[number]["key"]) || "revenue";
  const from = searchParams.get("from") || "2025-01-01";
  const to = searchParams.get("to") || toISODate(new Date());
  const groupBy = (searchParams.get("groupBy") as GroupBy) || "quarter";

  const setParam = (key: string, value: string) => {
    const params = new URLSearchParams(searchParams.toString());
    params.set(key, value);
    setSearchParams(params, { replace: true });
  };

  const setPreset = (f: string, t: string, g: GroupBy) => {
    const params = new URLSearchParams(searchParams.toString());
    params.set("from", f);
    params.set("to", t);
    params.set("groupBy", g);
    setSearchParams(params, { replace: true });
  };

  const year = new Date().getFullYear();
  const inputClass = "border border-gray-300 bg-white px-1.5 py-[3px] text-[13px]";

  return (
    <div className="bg-[#f1f4f9] p-2 space-y-2 text-[13px]">
      <div className="flex flex-wrap items-center gap-1">
        <h2 className="text-[15px] font-semibold text-gray-700 mr-3">Báo cáo thống kê</h2>
        {TABS.map((t) => (
          <button
            key={t.key}
            type="button"
            onClick={() => setParam("tab", t.key)}
            className={`px-3 py-[4px] border ${
              tab === t.key ? "bg-[#C62028] text-white border-[#C62028]" : "bg-white border-gray-300 hover:bg-gray-50"
            }`}
          >
            {t.label}
          </button>
        ))}
      </div>

      {/* Bộ lọc: một hàng phía trên biểu đồ */}
      <div className="bg-white border border-gray-300 px-2 py-1.5 flex flex-wrap items-center gap-2">
        <label className="flex items-center gap-1">
          Từ ngày
          <input type="date" value={from} max={to} onChange={(e) => setParam("from", e.target.value)} className={inputClass} />
        </label>
        <label className="flex items-center gap-1">
          đến ngày
          <input type="date" value={to} min={from} onChange={(e) => setParam("to", e.target.value)} className={inputClass} />
        </label>
        {tab !== "staff" && (
          <label className="flex items-center gap-1">
            Nhóm theo
            <select value={groupBy} onChange={(e) => setParam("groupBy", e.target.value)} className={inputClass}>
              {GROUPS.map((g) => (
                <option key={g.value} value={g.value}>
                  {g.label}
                </option>
              ))}
            </select>
          </label>
        )}
        <span className="text-gray-400">|</span>
        <button type="button" className="hover:underline text-blue-700" onClick={() => setPreset(`${year}-01-01`, toISODate(new Date()), "month")}>
          Năm nay theo tháng
        </button>
        <button type="button" className="hover:underline text-blue-700" onClick={() => setPreset(`${year - 1}-01-01`, toISODate(new Date()), "quarter")}>
          2 năm theo quý
        </button>
        <button
          type="button"
          className="hover:underline text-blue-700"
          onClick={() => {
            const d = new Date();
            const start = new Date(d.getFullYear(), d.getMonth(), 1);
            setPreset(toISODate(start), toISODate(d), "day");
          }}
        >
          Tháng này theo ngày
        </button>
      </div>

      {tab === "revenue" && <RevenueTab from={from} to={to} groupBy={groupBy} />}
      {tab === "profit" && <ProfitTab from={from} to={to} groupBy={groupBy} />}
      {tab === "staff" && <StaffTab from={from} to={to} />}
    </div>
  );
}

type RangeProps = { from: string; to: string; groupBy: GroupBy };

function SummaryStrip({ items }: { items: { label: string; value: string; strong?: boolean }[] }) {
  return (
    <div className="flex flex-wrap gap-2">
      {items.map((item) => (
        <div key={item.label} className="bg-white border border-gray-300 px-3 py-1.5 min-w-[150px]">
          <div className="text-[11px] text-gray-500">{item.label}</div>
          <div className={`text-[15px] tabular-nums ${item.strong ? "font-bold text-gray-900" : "font-semibold"}`}>
            {item.value}
          </div>
        </div>
      ))}
    </div>
  );
}

function Panel({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="bg-white border border-gray-300">
      <div className="px-2 py-1.5 border-b border-gray-200 bg-[#f7f8fa] font-semibold">{title}</div>
      <div className="p-2">{children}</div>
    </div>
  );
}

function RevenueTab({ from, to, groupBy }: RangeProps) {
  const { data, isLoading, error } = useStatistics<PeriodRow>("revenue", { from, to, groupBy });
  const rows = data?.rows ?? [];
  const best = data?.best;
  const s = data?.summary ?? {};

  if (error) return <p className="text-red-600">{error?.response?.data?.message ?? "Không tải được thống kê"}</p>;
  if (isLoading && !data) return <Loading height={60} size={40} color="black" thickness={2} />;

  return (
    <div className="space-y-2">
      <SummaryStrip
        items={[
          { label: "Tổng tiền hóa đơn (giao thành công)", value: formatMoney(s.revenue), strong: true },
          { label: "Số hóa đơn", value: formatNumber(s.orderCount) },
          { label: "Số cuốn bán ra", value: formatNumber(s.totalQuantity) },
          { label: "Tiền trước giảm", value: formatMoney(s.subtotal) },
          { label: "Tổng giảm giá", value: formatMoney(s.discountTotal) },
          { label: "Giá trị TB / hóa đơn", value: formatMoney(s.averageOrderValue) },
          { label: "Kỳ bán tốt nhất", value: best ? `${best.label} – ${formatMoney(best.revenue)}` : "-" },
        ]}
      />
      <div className="grid grid-cols-1 2xl:grid-cols-[minmax(0,1fr)_minmax(0,1fr)] gap-2">
        <Panel title={`Tổng tiền hóa đơn theo ${GROUPS.find((g) => g.value === groupBy)?.label.toLowerCase()}`}>
          <div className="overflow-auto max-h-[460px]">
            <table className="admin-table">
              <thead>
                <tr>
                  <th>Kỳ</th>
                  <th className="num">Số hóa đơn</th>
                  <th className="num">Số cuốn</th>
                  <th className="num">Trước giảm</th>
                  <th className="num">Giảm giá</th>
                  <th className="num">Tổng tiền</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((r) => (
                  <tr key={r.period} className={best?.period === r.period ? "is-selected" : ""}>
                    <td>
                      {r.label}
                      {best?.period === r.period && <span className="ml-1 text-[11px] font-semibold text-blue-800">★ cao nhất</span>}
                    </td>
                    <td className="num">{formatNumber(r.orderCount)}</td>
                    <td className="num">{formatNumber(r.totalQuantity)}</td>
                    <td className="num">{formatNumber(r.subtotal)}</td>
                    <td className="num">{formatNumber(r.discountTotal)}</td>
                    <td className="num font-semibold">{formatNumber(r.revenue)}</td>
                  </tr>
                ))}
              </tbody>
              <tfoot>
                <tr>
                  <td>Tổng</td>
                  <td className="num">{formatNumber(s.orderCount)}</td>
                  <td className="num">{formatNumber(s.totalQuantity)}</td>
                  <td className="num">{formatNumber(s.subtotal)}</td>
                  <td className="num">{formatNumber(s.discountTotal)}</td>
                  <td className="num">{formatNumber(s.revenue)}</td>
                </tr>
              </tfoot>
            </table>
          </div>
        </Panel>
        <Panel title="Biểu đồ tổng tiền hóa đơn (cột đậm = kỳ cao nhất)">
          <div className="h-[420px]">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={rows} margin={{ top: 8, right: 8, left: 4, bottom: 4 }}>
                <CartesianGrid stroke={GRID} vertical={false} />
                <XAxis dataKey="label" tick={{ fontSize: 11, fill: AXIS }} interval="preserveStartEnd" />
                <YAxis tickFormatter={formatShortMoney} tick={{ fontSize: 11, fill: AXIS }} width={56} />
                <Tooltip formatter={moneyTooltip} cursor={{ fill: "rgba(42,120,214,0.08)" }} />
                <Bar dataKey="revenue" name="Tổng tiền" radius={[4, 4, 0, 0]} maxBarSize={44}>
                  {rows.map((r) => (
                    <Cell key={r.period} fill={best?.period === r.period ? BEST : SERIES.revenue} />
                  ))}
                </Bar>
              </BarChart>
            </ResponsiveContainer>
          </div>
        </Panel>
      </div>
    </div>
  );
}

function ProfitTab({ from, to, groupBy }: RangeProps) {
  const { data, isLoading, error } = useStatistics<PeriodRow>("profit", { from, to, groupBy });
  const rows = data?.rows ?? [];
  const s = data?.summary ?? {};
  const best = data?.best;

  if (error) return <p className="text-red-600">{error?.response?.data?.message ?? "Không tải được thống kê"}</p>;
  if (isLoading && !data) return <Loading height={60} size={40} color="black" thickness={2} />;

  return (
    <div className="space-y-2">
      <SummaryStrip
        items={[
          { label: "Tổng thu (hóa đơn giao thành công)", value: formatMoney(s.revenue), strong: true },
          { label: "Tổng chi (phiếu nhập hàng)", value: formatMoney(s.importCost), strong: true },
          { label: "Lợi nhuận = Thu − Chi", value: formatMoney(s.profit), strong: true },
          { label: "Tỷ suất lợi nhuận", value: `${((s.margin ?? 0) * 100).toFixed(1)}%` },
          { label: "Số hóa đơn / phiếu nhập", value: `${formatNumber(s.orderCount)} / ${formatNumber(s.receiptCount)}` },
          { label: "Kỳ lãi cao nhất", value: best ? `${best.label} – ${formatMoney(best.profit)}` : "-" },
        ]}
      />
      <div className="grid grid-cols-1 2xl:grid-cols-[minmax(0,1fr)_minmax(0,1fr)] gap-2">
        <Panel title="Bảng thu – chi – lợi nhuận">
          <div className="overflow-auto max-h-[460px]">
            <table className="admin-table">
              <thead>
                <tr>
                  <th>Kỳ</th>
                  <th className="num">Số HĐ</th>
                  <th className="num">Tổng thu</th>
                  <th className="num">Số phiếu nhập</th>
                  <th className="num">Tổng chi</th>
                  <th className="num">Lợi nhuận</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((r) => (
                  <tr key={r.period}>
                    <td>{r.label}</td>
                    <td className="num">{formatNumber(r.orderCount)}</td>
                    <td className="num">{formatNumber(r.revenue)}</td>
                    <td className="num">{formatNumber(r.receiptCount)}</td>
                    <td className="num">{formatNumber(r.importCost)}</td>
                    <td className={`num font-semibold ${(r.profit ?? 0) < 0 ? "text-red-600" : ""}`}>
                      {formatNumber(r.profit)}
                    </td>
                  </tr>
                ))}
              </tbody>
              <tfoot>
                <tr>
                  <td>Tổng</td>
                  <td className="num">{formatNumber(s.orderCount)}</td>
                  <td className="num">{formatNumber(s.revenue)}</td>
                  <td className="num">{formatNumber(s.receiptCount)}</td>
                  <td className="num">{formatNumber(s.importCost)}</td>
                  <td className="num">{formatNumber(s.profit)}</td>
                </tr>
              </tfoot>
            </table>
          </div>
        </Panel>
        <Panel title="Biểu đồ thu – chi (cột) và lợi nhuận (đường)">
          <div className="h-[420px]">
            <ResponsiveContainer width="100%" height="100%">
              <ComposedChart data={rows} margin={{ top: 8, right: 8, left: 4, bottom: 4 }} barGap={2}>
                <CartesianGrid stroke={GRID} vertical={false} />
                <XAxis dataKey="label" tick={{ fontSize: 11, fill: AXIS }} interval="preserveStartEnd" />
                <YAxis tickFormatter={formatShortMoney} tick={{ fontSize: 11, fill: AXIS }} width={56} />
                <Tooltip formatter={moneyTooltip} />
                <Legend wrapperStyle={{ fontSize: 12 }} />
                <Bar dataKey="revenue" name="Tổng thu" fill={SERIES.revenue} radius={[4, 4, 0, 0]} maxBarSize={28} />
                <Bar dataKey="importCost" name="Tổng chi" fill={SERIES.cost} radius={[4, 4, 0, 0]} maxBarSize={28} />
                <Line
                  dataKey="profit"
                  name="Lợi nhuận"
                  stroke={SERIES.profit}
                  strokeWidth={2}
                  dot={{ r: 4, strokeWidth: 2, stroke: "#fcfcfb" }}
                  type="linear"
                />
              </ComposedChart>
            </ResponsiveContainer>
          </div>
        </Panel>
      </div>
    </div>
  );
}

function StaffTab({ from, to }: { from: string; to: string }) {
  const { data, isLoading, error } = useStatistics<StaffRow>("by-staff", { from, to });
  const rows = useMemo(() => data?.rows ?? [], [data]);
  const s = data?.summary ?? {};
  const chartRows = rows.filter((r) => r.staffId);

  if (error) return <p className="text-red-600">{error?.response?.data?.message ?? "Không tải được thống kê"}</p>;
  if (isLoading && !data) return <Loading height={60} size={40} color="black" thickness={2} />;

  return (
    <div className="space-y-2">
      <SummaryStrip
        items={[
          { label: "Tổng tiền hóa đơn giao thành công", value: formatMoney(s.revenue), strong: true },
          { label: "Số nhân viên có xác nhận đơn", value: formatNumber(s.staffCount) },
          { label: "Số đơn đã xác nhận", value: formatNumber(s.confirmedCount) },
          { label: "Số đơn giao thành công", value: formatNumber(s.deliveredCount) },
          { label: "Nhân viên dẫn đầu", value: data?.best ? data.best.staffName : "-" },
        ]}
      />
      <div className="grid grid-cols-1 2xl:grid-cols-[minmax(0,1fr)_minmax(0,1fr)] gap-2">
        <Panel title="Tổng tiền hóa đơn theo nhân viên xác nhận đơn">
          <div className="overflow-auto">
          <table className="admin-table">
            <thead>
              <tr>
                <th>Hạng</th>
                <th>Nhân viên</th>
                <th className="num">Đơn xác nhận</th>
                <th className="num">Giao thành công</th>
                <th className="num">Hủy / trả</th>
                <th className="num">Số cuốn</th>
                <th className="num">Tổng tiền</th>
                <th className="num">TB / đơn</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((r, i) => (
                <tr key={r.staffId ?? "none"}>
                  <td>{r.staffId ? i + 1 : ""}</td>
                  <td className={r.staffId ? "font-medium" : "text-gray-500 italic"}>{r.staffName}</td>
                  <td className="num">{formatNumber(r.confirmedCount)}</td>
                  <td className="num">{formatNumber(r.deliveredCount)}</td>
                  <td className="num">{formatNumber(r.cancelledCount)}</td>
                  <td className="num">{formatNumber(r.totalQuantity)}</td>
                  <td className="num font-semibold">{formatNumber(r.revenue)}</td>
                  <td className="num">{formatNumber(Math.round(r.averageOrderValue))}</td>
                </tr>
              ))}
            </tbody>
            <tfoot>
              <tr>
                <td colSpan={2}>Tổng</td>
                <td className="num">{formatNumber(s.confirmedCount)}</td>
                <td className="num">{formatNumber(s.deliveredCount)}</td>
                <td colSpan={2}></td>
                <td className="num">{formatNumber(s.revenue)}</td>
                <td></td>
              </tr>
            </tfoot>
          </table>
          </div>
        </Panel>
        <Panel title="Biểu đồ tổng tiền hóa đơn theo nhân viên">
          <div style={{ height: Math.max(220, chartRows.length * 44 + 40) }}>
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={chartRows} layout="vertical" margin={{ top: 4, right: 16, left: 8, bottom: 4 }}>
                <CartesianGrid stroke={GRID} horizontal={false} />
                <XAxis type="number" tickFormatter={formatShortMoney} tick={{ fontSize: 11, fill: AXIS }} />
                <YAxis type="category" dataKey="staffName" tick={{ fontSize: 12, fill: AXIS }} width={140} />
                <Tooltip formatter={moneyTooltip} cursor={{ fill: "rgba(42,120,214,0.08)" }} />
                <Bar dataKey="revenue" name="Tổng tiền" fill={SERIES.revenue} radius={[0, 4, 4, 0]} maxBarSize={26} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </Panel>
      </div>
    </div>
  );
}

export default Statistics;
