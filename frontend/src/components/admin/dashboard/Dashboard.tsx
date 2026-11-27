import { Link } from "react-router-dom";
import {
  Bar,
  BarChart,
  CartesianGrid,
  ReferenceLine,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import TopSelling from "./TopSelling";
import useGetStatistic from "../../../hooks/admin/useGetStatistic";
import { formatMoney, formatNumber, formatShortMoney } from "../../../utils/format";

const BAR = "#257de0";
const GRID = "#e5e7eb";
const AXIS = "#52514e";

type MonthPoint = { key: string; month: number; year: number; revenue: number; orderCount?: number };

/** Nhãn trục 2 dòng: tháng ở trên, năm ở dưới (năm in đậm ở tháng đầu tiên của mỗi năm). */
function MonthTick(props: { x?: number | string; y?: number | string; index?: number; data: MonthPoint[] }) {
  const { x = 0, y = 0, index = 0, data } = props;
  const point = data[index];
  if (!point) return null;
  const startOfYear = index === 0 || point.month === 1;
  return (
    <g transform={`translate(${x},${y})`}>
      <text dy={12} textAnchor="middle" fontSize={11} fill={AXIS}>
        T{point.month}
      </text>
      <text
        dy={26}
        textAnchor="middle"
        fontSize={11}
        fontWeight={startOfYear ? 700 : 400}
        fill={startOfYear ? "#1f2937" : "#a3a3a3"}
      >
        {point.year}
      </text>
    </g>
  );
}

function MonthTooltip({ active, payload }: { active?: boolean; payload?: { payload: MonthPoint }[] }) {
  if (!active || !payload?.length) return null;
  const p = payload[0].payload;
  return (
    <div className="rounded border border-gray-300 bg-white px-2 py-1 text-[12px] shadow-sm">
      <div className="font-semibold">
        Tháng {String(p.month).padStart(2, "0")}/{p.year}
      </div>
      <div>
        Doanh thu: <b className="tabular-nums">{formatMoney(p.revenue)}</b>
      </div>
      {p.orderCount !== undefined && <div>Số hóa đơn: {formatNumber(p.orderCount)}</div>}
    </div>
  );
}

function Dashboard() {
  const {
    totalRevenue,
    todayRevenue,
    todaySoldQuantity,
    totalSoldQuantity,
    monthlyRevenue,
    totalImportCost,
    totalProfit,
  } = useGetStatistic();

  const cards = [
    { title: "Tổng doanh thu", value: formatMoney(totalRevenue) },
    { title: "Tổng chi nhập hàng", value: formatMoney(totalImportCost) },
    { title: "Lợi nhuận", value: formatMoney(totalProfit) },
    { title: "Doanh thu hôm nay", value: formatMoney(todayRevenue) },
    { title: "Số cuốn đã bán", value: formatNumber(totalSoldQuantity) },
    { title: "Số cuốn bán hôm nay", value: formatNumber(todaySoldQuantity) },
  ];

  // month = "2025-12"
  const data: MonthPoint[] = monthlyRevenue.map((m) => ({
    key: m.month,
    year: Number(m.month.slice(0, 4)),
    month: Number(m.month.slice(5, 7)),
    revenue: m.revenue,
    orderCount: m.orderCount,
  }));
  const first = data[0];
  const last = data[data.length - 1];
  const range =
    first && last ? `T${first.month}/${first.year} – T${last.month}/${last.year}` : "";
  // vạch phân cách giữa hai năm (tại tháng 1)
  const yearStarts = data.filter((d, i) => i > 0 && d.month === 1);
  const yearTotals = Object.entries(
    data.reduce<Record<string, number>>((acc, d) => {
      acc[d.year] = (acc[d.year] ?? 0) + d.revenue;
      return acc;
    }, {}),
  );

  return (
    <div className="bg-[#f1f4f9] p-2 space-y-2 text-[13px]">
      <div className="flex items-center justify-between">
        <h2 className="text-[15px] font-semibold text-gray-700">Tổng quan</h2>
        <Link to="/admin/statistics" className="text-blue-700 hover:underline">
          Xem báo cáo thống kê chi tiết →
        </Link>
      </div>
      <div className="grid grid-cols-2 md:grid-cols-3 xl:grid-cols-6 gap-2">
        {cards.map((c) => (
          <div key={c.title} className="bg-white border border-gray-300 px-3 py-2">
            <div className="text-[11px] text-gray-500">{c.title}</div>
            <div className="text-[16px] font-bold tabular-nums text-gray-900">{c.value}</div>
          </div>
        ))}
      </div>

      <div className="bg-white border border-gray-300">
        <div className="flex flex-wrap items-center justify-between gap-2 px-2 py-1.5 border-b border-gray-200 bg-[#f7f8fa]">
          <span className="font-semibold">
            Doanh thu 12 tháng gần nhất {range && <span className="font-normal text-gray-600">({range})</span>}
          </span>
          <span className="flex flex-wrap gap-3 text-[12px] text-gray-600">
            {yearTotals.map(([year, total]) => (
              <span key={year}>
                Năm {year} (trong 12 tháng): <b className="tabular-nums text-gray-900">{formatMoney(total)}</b>
              </span>
            ))}
          </span>
        </div>
        <div className="h-[280px] p-2">
          <ResponsiveContainer width="100%" height="100%">
            <BarChart data={data} margin={{ top: 16, right: 8, left: 4, bottom: 4 }}>
              <CartesianGrid stroke={GRID} vertical={false} />
              <XAxis
                dataKey="key"
                interval={0}
                height={36}
                tickLine={false}
                tick={(props) => <MonthTick {...props} data={data} />}
              />
              <YAxis tickFormatter={formatShortMoney} tick={{ fontSize: 11, fill: AXIS }} width={56} />
              <Tooltip content={<MonthTooltip />} cursor={{ fill: "rgba(37,125,224,0.08)" }} />
              {yearStarts.map((d) => (
                <ReferenceLine
                  key={d.key}
                  x={d.key}
                  stroke="#9ca3af"
                  strokeDasharray="4 3"
                  position="start"
                  label={{ value: `Năm ${d.year}`, position: "insideTopLeft", fontSize: 11, fill: "#374151", dy: -14 }}
                />
              ))}
              <Bar dataKey="revenue" name="Doanh thu" fill={BAR} radius={[4, 4, 0, 0]} maxBarSize={40} />
            </BarChart>
          </ResponsiveContainer>
        </div>
      </div>
      <TopSelling />
    </div>
  );
}

export default Dashboard;
