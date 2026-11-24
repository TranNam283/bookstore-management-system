import { Link } from "react-router-dom";
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import BestsellerBook from "./BestsellerBook";
import useGetStatistic from "../../../hooks/admin/useGetStatistic";
import { formatMoney, formatNumber, formatShortMoney } from "../../../utils/format";

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
  const data = monthlyRevenue.map((m) => ({ ...m, label: `${m.month.slice(5)}/${m.month.slice(2, 4)}` }));

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
        <div className="px-2 py-1.5 border-b border-gray-200 bg-[#f7f8fa] font-semibold">
          Doanh thu 12 tháng gần nhất
        </div>
        <div className="h-[260px] p-2">
          <ResponsiveContainer width="100%" height="100%">
            <BarChart data={data} margin={{ top: 8, right: 8, left: 4, bottom: 4 }}>
              <CartesianGrid stroke="#e5e7eb" vertical={false} />
              <XAxis dataKey="label" tick={{ fontSize: 11, fill: "#52514e" }} />
              <YAxis tickFormatter={formatShortMoney} tick={{ fontSize: 11, fill: "#52514e" }} width={56} />
              <Tooltip formatter={(v) => formatMoney(Number(v))} cursor={{ fill: "rgba(42,120,214,0.08)" }} />
              <Bar dataKey="revenue" name="Doanh thu" fill="#2a78d6" radius={[4, 4, 0, 0]} maxBarSize={40} />
            </BarChart>
          </ResponsiveContainer>
        </div>
      </div>
      <BestsellerBook />
    </div>
  );
}

export default Dashboard;
