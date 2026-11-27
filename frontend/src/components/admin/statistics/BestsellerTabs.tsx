import { useState } from "react";
import { Link } from "react-router-dom";
import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import {
  useStatisticsApi,
  type BestsellersResult,
  type GroupBy,
  type SeasonalResult,
} from "../../../hooks/admin/useStatistics";
import { formatDate, formatMoney, formatNumber } from "../../../utils/format";
import Loading from "../../Loading";
import Badge from "../ui/Badge";
import { BookRankingTable, CategoryRanking } from "./RankingTables";

// Màu phân loại theo năm: thứ tự cố định (xanh, cam, ngọc) – đã kiểm định cho người mù màu
const YEAR_COLORS = ["#257de0", "#f47230", "#00b894", "#f2a600"];
const GRID = "#e5e7eb";
const AXIS = "#52514e";

function Strip({ items }: { items: { label: string; value: React.ReactNode; strong?: boolean }[] }) {
  return (
    <div className="flex flex-wrap gap-2">
      {items.map((item) => (
        <div key={item.label} className="bg-white border border-gray-300 px-3 py-1.5 min-w-[150px] max-w-[360px]">
          <div className="text-[11px] text-gray-500">{item.label}</div>
          <div className={`text-[14px] tabular-nums ${item.strong ? "font-bold text-gray-900" : "font-semibold"}`}>
            {item.value}
          </div>
        </div>
      ))}
    </div>
  );
}

function Box({ title, right, children }: { title: React.ReactNode; right?: React.ReactNode; children: React.ReactNode }) {
  return (
    <div className="bg-white border border-gray-300">
      <div className="flex flex-wrap items-center justify-between gap-2 px-2 py-1.5 border-b border-gray-200 bg-[#f7f8fa]">
        <span className="font-semibold">{title}</span>
        {right}
      </div>
      <div className="p-2">{children}</div>
    </div>
  );
}

function Options({
  limit,
  setLimit,
  deliveredOnly,
  setDeliveredOnly,
}: {
  limit: number;
  setLimit: (n: number) => void;
  deliveredOnly: boolean;
  setDeliveredOnly: (v: boolean) => void;
}) {
  return (
    <div className="flex items-center gap-3 text-[12.5px] font-normal">
      <label className="flex items-center gap-1">
        Top
        <select value={limit} onChange={(e) => setLimit(Number(e.target.value))} className="border border-gray-300 bg-white px-1 py-[2px]">
          {[5, 10, 20].map((n) => (
            <option key={n} value={n}>
              {n} sách
            </option>
          ))}
        </select>
      </label>
      <label className="flex items-center gap-1">
        <input type="checkbox" checked={deliveredOnly} onChange={(e) => setDeliveredOnly(e.target.checked)} />
        Chỉ đơn giao thành công
      </label>
    </div>
  );
}

const ALL = "__all__";

/**
 * Hàng bán chạy theo từng kỳ: bảng kỳ (main) – chọn 1 kỳ để xem xếp hạng sách và thể loại (sub).
 * Mục đích: biết ở mỗi thời điểm mặt hàng / thể loại nào bán chạy để sắp xếp nhập hàng.
 */
export function BestsellerTab({ from, to, groupBy }: { from: string; to: string; groupBy: GroupBy }) {
  const [limit, setLimit] = useState(10);
  const [deliveredOnly, setDeliveredOnly] = useState(false);
  const [selected, setSelected] = useState<string | null>(null);
  const { data, isLoading, error } = useStatisticsApi<BestsellersResult>("bestsellers", {
    from,
    to,
    groupBy,
    limit,
    deliveredOnly,
  });

  if (error) return <p className="text-red-600">{error?.response?.data?.message ?? "Không tải được thống kê"}</p>;
  if (isLoading && !data) return <Loading height={60} size={40} color="black" thickness={2} />;
  if (!data) return null;

  const rows = data.rows;
  const best = data.best;
  const current =
    selected === ALL ? null : (rows.find((r) => r.period === selected) ?? (selected === null ? best : null));
  const view = current ?? data.overall;
  const viewLabel = current ? current.label : data.overall.label;

  return (
    <div className="space-y-2">
      <Strip
        items={[
          { label: "Tổng số cuốn bán ra", value: formatNumber(data.overall.quantity), strong: true },
          { label: "Doanh thu từ sách bán ra", value: formatMoney(data.overall.revenue) },
          { label: "Kỳ bán nhiều nhất", value: best ? `${best.label} – ${formatNumber(best.quantity)} cuốn` : "-" },
          { label: "Sách bán chạy nhất cả khoảng", value: data.overall.books[0]?.title ?? "-" },
          { label: "Thể loại bán chạy nhất cả khoảng", value: data.overall.categories[0]?.name ?? "-" },
        ]}
      />
      <div className="grid grid-cols-1 gap-2 2xl:grid-cols-[minmax(0,2fr)_minmax(0,3fr)]">
        <Box title="Từng kỳ: sách & thể loại dẫn đầu (bấm 1 dòng để xem bảng xếp hạng)">
          <div className="overflow-auto max-h-[640px]">
            <table className="admin-table">
              <thead>
                <tr>
                  <th>Kỳ</th>
                  <th className="num">Số cuốn</th>
                  <th className="num">Doanh thu</th>
                  <th>Sách bán chạy nhất</th>
                  <th>Thể loại dẫn đầu</th>
                </tr>
              </thead>
              <tbody>
                <tr onClick={() => setSelected(ALL)} className={`cursor-pointer ${!current ? "is-selected" : ""}`}>
                  <td className="font-semibold">Cả khoảng</td>
                  <td className="num font-semibold">{formatNumber(data.overall.quantity)}</td>
                  <td className="num font-semibold">{formatNumber(data.overall.revenue)}</td>
                  <td className="wrap">{data.overall.books[0]?.title ?? "-"}</td>
                  <td>{data.overall.categories[0]?.name ?? "-"}</td>
                </tr>
                {rows.map((r) => (
                  <tr
                    key={r.period}
                    onClick={() => setSelected(r.period)}
                    className={`cursor-pointer ${current?.period === r.period ? "is-selected" : ""}`}
                  >
                    <td>
                      {r.label}
                      {best?.period === r.period && (
                        <span className="ml-1 text-[11px] font-semibold text-blue-800">★ bán nhiều nhất</span>
                      )}
                    </td>
                    <td className="num">{formatNumber(r.quantity)}</td>
                    <td className="num">{formatNumber(r.revenue)}</td>
                    <td className="wrap max-w-[240px]">{r.topBook ?? <span className="text-gray-400">–</span>}</td>
                    <td>{r.topCategory ?? <span className="text-gray-400">–</span>}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Box>

        <div className="space-y-2">
          <Box
            title={
              <>
                Bảng xếp hạng sách – {viewLabel}
                {current && (
                  <span className="ml-2 text-[12px] font-normal text-gray-500">
                    ({formatDate(current.from)} – {formatDate(current.to)})
                  </span>
                )}
              </>
            }
            right={<Options limit={limit} setLimit={setLimit} deliveredOnly={deliveredOnly} setDeliveredOnly={setDeliveredOnly} />}
          >
            <BookRankingTable rows={view.books} previousLabel="kỳ liền trước" showChange={!!current} />
          </Box>
          <Box title={`Xếp hạng thể loại – ${viewLabel}`}>
            <CategoryRanking rows={view.categories} showChange={!!current} />
          </Box>
        </div>
      </div>
      <p className="text-[11.5px] text-gray-500">
        Số liệu tính trên hóa đơn {deliveredOnly ? "giao thành công" : "đã xác nhận / đang giao / giao thành công"}, theo
        ngày đặt hàng. “So kỳ trước” so với kỳ liền trước cùng độ dài (ví dụ quý 3 so với quý 2).
      </p>
    </div>
  );
}

const nextQuarter = () => {
  const q = Math.floor(new Date().getMonth() / 3) + 1;
  return q === 4 ? 1 : q + 1;
};

/**
 * Mùa vụ: cùng một quý / tháng qua các năm. Dự báo = trung bình số cuốn bán ra trong kỳ đó ở các
 * năm đã kết thúc; đề xuất nhập = dự báo − tồn kho hiện tại.
 */
export function SeasonalTab() {
  const [unit, setUnit] = useState<"quarter" | "month">("quarter");
  const [index, setIndex] = useState(nextQuarter());
  const [limit, setLimit] = useState(10);
  const [deliveredOnly, setDeliveredOnly] = useState(false);
  const { data, isLoading, error } = useStatisticsApi<SeasonalResult>("seasonal", {
    unit,
    index,
    limit,
    deliveredOnly,
  });

  const indexes = unit === "quarter" ? [1, 2, 3, 4] : Array.from({ length: 12 }, (_, i) => i + 1);
  const years = data?.years ?? [];
  const yearKeys = years.map((y) => String(y.year));
  const chartRows = (data?.categories ?? []).map((c) => ({
    name: c.name,
    ...Object.fromEntries(yearKeys.map((y) => [y, c.byYear[y] ?? 0])),
  }));
  const forecastYears = data?.summary.forecastYears ?? [];

  const selector = (
    <div className="flex flex-wrap items-center gap-2 bg-white border border-gray-300 px-2 py-1.5">
      <span className="font-semibold">Xem mùa vụ theo</span>
      <select
        value={unit}
        onChange={(e) => {
          const u = e.target.value as "quarter" | "month";
          setUnit(u);
          setIndex(u === "quarter" ? nextQuarter() : ((new Date().getMonth() + 1) % 12) + 1);
        }}
        className="border border-gray-300 bg-white px-1.5 py-[3px]"
      >
        <option value="quarter">Quý</option>
        <option value="month">Tháng</option>
      </select>
      <div className="flex overflow-hidden rounded border border-gray-300">
        {indexes.map((i) => (
          <button
            key={i}
            type="button"
            onClick={() => setIndex(i)}
            className={`px-2.5 py-[3px] ${index === i ? "bg-[#C62028] text-white" : "bg-white hover:bg-gray-50"}`}
          >
            {unit === "quarter" ? `Quý ${i}` : `T${i}`}
          </button>
        ))}
      </div>
      <Options limit={limit} setLimit={setLimit} deliveredOnly={deliveredOnly} setDeliveredOnly={setDeliveredOnly} />
    </div>
  );

  if (error)
    return (
      <div className="space-y-2">
        {selector}
        <p className="text-red-600">{error?.response?.data?.message ?? "Không tải được thống kê"}</p>
      </div>
    );
  if (isLoading && !data)
    return (
      <div className="space-y-2">
        {selector}
        <Loading height={60} size={40} color="black" thickness={2} />
      </div>
    );
  if (!data) return selector;

  const s = data.summary;
  const needImport = data.books.filter((b) => b.suggestedImport > 0);

  return (
    <div className="space-y-2">
      {selector}
      <Strip
        items={[
          ...years.map((y) => ({
            label: `${y.label}${y.partial ? " (chưa hết kỳ)" : ""}`,
            value: `${formatNumber(y.quantity)} cuốn – ${formatMoney(y.revenue)}`,
          })),
          { label: `Sách bán chạy nhất ${data.label}`, value: data.books[0]?.title ?? "-", strong: true },
          { label: `Thể loại bán chạy nhất ${data.label}`, value: data.categories[0]?.name ?? "-", strong: true },
          {
            label: "Đề xuất nhập thêm (top sách)",
            value: `${formatNumber(s.suggestedImport)} cuốn / ${needImport.length} đầu sách`,
            strong: true,
          },
        ]}
      />

      <div className="grid grid-cols-1 gap-2 2xl:grid-cols-[minmax(0,1fr)_minmax(0,1fr)]">
        <Box title={`Thể loại bán chạy ${data.label} qua các năm (xếp từ trên xuống)`}>
          <div className="overflow-auto">
            <table className="admin-table">
              <thead>
                <tr>
                  <th>Hạng</th>
                  <th>Thể loại</th>
                  {years.map((y) => (
                    <th key={y.year} className="num">
                      {y.year}
                      {y.partial ? "*" : ""}
                    </th>
                  ))}
                  <th className="num" title="Trung bình số cuốn / năm (các năm đã kết thúc kỳ)">Dự báo</th>
                  <th className="num">Tỷ trọng</th>
                  <th className="num">Xu hướng</th>
                </tr>
              </thead>
              <tbody>
                {data.categories.map((c) => (
                  <tr key={c.name}>
                    <td className="tabular-nums">{c.rank}</td>
                    <td className="font-medium">{c.name}</td>
                    {years.map((y) => (
                      <td key={y.year} className="num">
                        {formatNumber(c.byYear[String(y.year)] ?? 0)}
                      </td>
                    ))}
                    <td className="num font-semibold">{formatNumber(Math.ceil(c.forecast))}</td>
                    <td className="num">{(c.share * 100).toFixed(1)}%</td>
                    <td className="num">
                      <Trend value={c.trend} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Box>
        <Box title={`Số cuốn bán theo thể loại – ${data.label} từng năm`}>
          <div style={{ height: Math.max(260, chartRows.length * 34 + 60) }}>
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={chartRows} layout="vertical" margin={{ top: 4, right: 16, left: 8, bottom: 4 }} barGap={2}>
                <CartesianGrid stroke={GRID} horizontal={false} />
                <XAxis type="number" tick={{ fontSize: 11, fill: AXIS }} allowDecimals={false} />
                <YAxis type="category" dataKey="name" tick={{ fontSize: 12, fill: AXIS }} width={130} />
                <Tooltip formatter={(v, name) => [`${formatNumber(Number(v))} cuốn`, `Năm ${name}`]} cursor={{ fill: "rgba(37,125,224,0.06)" }} />
                <Legend wrapperStyle={{ fontSize: 12 }} formatter={(v) => `Năm ${v}`} />
                {yearKeys.map((y, i) => (
                  <Bar key={y} dataKey={y} fill={YEAR_COLORS[i % YEAR_COLORS.length]} radius={[0, 4, 4, 0]} maxBarSize={14} />
                ))}
              </BarChart>
            </ResponsiveContainer>
          </div>
        </Box>
      </div>

      <Box title={`Top sách bán chạy ${data.label} qua các năm & đề xuất số lượng nhập trước mùa`}>
        {data.books.length === 0 ? (
          <p className="py-6 text-center text-gray-500">Chưa có dữ liệu bán hàng cho {data.label}</p>
        ) : (
          <div className="overflow-auto">
            <table className="admin-table">
              <thead>
                <tr>
                  <th>Hạng</th>
                  <th>Sách</th>
                  <th>Thể loại</th>
                  {years.map((y) => (
                    <th key={y.year} className="num">
                      {y.label}
                      {y.partial ? "*" : ""}
                    </th>
                  ))}
                  <th className="num">Dự báo</th>
                  <th className="num">Tồn kho</th>
                  <th className="num">Đề xuất nhập</th>
                  <th className="num">Xu hướng</th>
                </tr>
              </thead>
              <tbody>
                {data.books.map((b) => (
                  <tr key={b.bookId}>
                    <td className="tabular-nums">{b.rank}</td>
                    <td className="wrap">
                      <div className="flex min-w-[220px] items-center gap-2">
                        <div className="h-9 w-7 shrink-0 overflow-hidden bg-gray-50">
                          {b.image && (
                            <img src={`${import.meta.env.VITE_BACKEND_URL}${b.image}`} alt="" loading="lazy" className="h-full w-full object-contain" />
                          )}
                        </div>
                        <span className="font-medium">{b.title}</span>
                      </div>
                    </td>
                    <td>{b.category}</td>
                    {years.map((y) => (
                      <td key={y.year} className="num">
                        {formatNumber(b.byYear[String(y.year)] ?? 0)}
                      </td>
                    ))}
                    <td className="num font-semibold">{formatNumber(Math.ceil(b.forecast))}</td>
                    <td className="num">{formatNumber(b.stock)}</td>
                    <td className="num">
                      {b.suggestedImport > 0 ? (
                        <Badge tone="amber">Nhập thêm {formatNumber(b.suggestedImport)}</Badge>
                      ) : (
                        <Badge tone="green">Đủ hàng</Badge>
                      )}
                    </td>
                    <td className="num">
                      <Trend value={b.trend} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        <div className="mt-2 flex flex-wrap items-center justify-between gap-2 text-[11.5px] text-gray-500">
          <span>
            Dự báo = trung bình số cuốn bán ra trong {data.label} của các năm {forecastYears.join(", ") || "-"}; Đề xuất
            nhập = Dự báo − Tồn kho hiện tại. Xu hướng = năm gần nhất so với năm trước đó.
            {years.some((y) => y.partial) && " (*) kỳ của năm nay chưa kết thúc, không tính vào dự báo."}
          </span>
          {needImport.length > 0 && (
            <Link to="/admin/add-import-receipt" className="text-blue-700 hover:underline">
              Lập phiếu nhập →
            </Link>
          )}
        </div>
      </Box>
    </div>
  );
}

function Trend({ value }: { value: number | null }) {
  if (value === null || value === undefined) return <span className="text-gray-400">–</span>;
  if (value > 0) return <span className="text-emerald-700 tabular-nums">▲ +{formatNumber(value)}</span>;
  if (value < 0) return <span className="text-red-600 tabular-nums">▼ {formatNumber(value)}</span>;
  return <span className="text-gray-500">= 0</span>;
}
