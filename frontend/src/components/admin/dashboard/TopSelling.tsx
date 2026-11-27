import { useState } from "react";
import { Link } from "react-router-dom";
import { IoChevronBack, IoChevronForward } from "react-icons/io5";
import {
  useStatisticsApi,
  type GroupBy,
  type TopBooksResult,
} from "../../../hooks/admin/useStatistics";
import { formatDate, formatMoney, formatNumber, toISODate } from "../../../utils/format";
import Loading from "../../Loading";
import { BookRankingTable, CategoryRanking } from "../statistics/RankingTables";

const PERIODS: { value: GroupBy; label: string; current: string }[] = [
  { value: "day", label: "Ngày", current: "Hôm nay" },
  { value: "week", label: "Tuần", current: "Tuần này" },
  { value: "month", label: "Tháng", current: "Tháng này" },
  { value: "quarter", label: "Quý", current: "Quý này" },
  { value: "year", label: "Năm", current: "Năm nay" },
];

const addDays = (iso: string, days: number) => {
  const d = new Date(`${iso}T00:00:00`);
  d.setDate(d.getDate() + days);
  return toISODate(d);
};

/** Top sách & thể loại bán chạy theo ngày / tuần / tháng / quý / năm (chuyển kỳ trước - sau). */
function TopSelling() {
  const today = toISODate(new Date());
  const [period, setPeriod] = useState<GroupBy>("month");
  const [anchor, setAnchor] = useState(today);
  const [deliveredOnly, setDeliveredOnly] = useState(false);
  const [limit, setLimit] = useState(10);

  const { data, isLoading, error } = useStatisticsApi<TopBooksResult>("top-books", {
    period,
    date: anchor,
    limit,
    deliveredOnly,
  });
  const s = data?.summary;
  const isCurrent = !!data && data.from <= today && today <= data.to;
  const canNext = !!data && data.to < today;

  return (
    <div className="bg-white border border-gray-300">
      <div className="flex flex-wrap items-center gap-2 border-b border-gray-200 bg-[#f7f8fa] px-2 py-1.5">
        <span className="font-semibold">Top bán chạy</span>
        <div className="flex overflow-hidden rounded border border-gray-300">
          {PERIODS.map((p) => (
            <button
              key={p.value}
              type="button"
              onClick={() => {
                setPeriod(p.value);
                setAnchor(today);
              }}
              className={`px-2.5 py-[3px] text-[12.5px] ${
                period === p.value ? "bg-[#C62028] text-white" : "bg-white hover:bg-gray-50"
              }`}
            >
              {p.label}
            </button>
          ))}
        </div>

        <div className="flex items-center gap-1">
          <button
            type="button"
            title="Kỳ trước"
            disabled={!data}
            onClick={() => data && setAnchor(data.previousFrom)}
            className="rounded border border-gray-300 bg-white p-1 hover:bg-gray-50 disabled:opacity-40"
          >
            <IoChevronBack size={14} />
          </button>
          <span className="min-w-[190px] text-center font-semibold text-gray-900">
            {data?.label ?? "…"}
            {isCurrent && <span className="ml-1 text-[11px] font-normal text-gray-500">(hiện tại)</span>}
          </span>
          <button
            type="button"
            title="Kỳ sau"
            disabled={!canNext}
            onClick={() => data && setAnchor(addDays(data.to, 1))}
            className="rounded border border-gray-300 bg-white p-1 hover:bg-gray-50 disabled:opacity-40"
          >
            <IoChevronForward size={14} />
          </button>
          {!isCurrent && (
            <button type="button" onClick={() => setAnchor(today)} className="ml-1 text-blue-700 hover:underline">
              {PERIODS.find((p) => p.value === period)?.current}
            </button>
          )}
          <input
            type="date"
            value={anchor}
            max={today}
            onChange={(e) => e.target.value && setAnchor(e.target.value)}
            className="ml-1 border border-gray-300 bg-white px-1 py-[2px] text-[12.5px]"
            title="Chọn ngày bất kỳ trong kỳ cần xem"
          />
        </div>

        <label className="flex items-center gap-1 text-[12.5px]">
          <input type="checkbox" checked={deliveredOnly} onChange={(e) => setDeliveredOnly(e.target.checked)} />
          Chỉ đơn giao thành công
        </label>
        <label className="flex items-center gap-1 text-[12.5px]">
          Top
          <select
            value={limit}
            onChange={(e) => setLimit(Number(e.target.value))}
            className="border border-gray-300 bg-white px-1 py-[2px]"
          >
            {[5, 10, 20].map((n) => (
              <option key={n} value={n}>
                {n}
              </option>
            ))}
          </select>
        </label>
        <Link to="/admin/statistics?tab=bestseller" className="ml-auto text-blue-700 hover:underline">
          Phân tích theo kỳ & mùa vụ →
        </Link>
      </div>

      {error ? (
        <p className="p-3 text-red-600">{error?.response?.data?.message ?? "Không tải được dữ liệu"}</p>
      ) : isLoading && !data ? (
        <Loading height={60} size={40} color="black" thickness={2} />
      ) : data && s ? (
        <div className="space-y-2 p-2">
          <div className="flex flex-wrap gap-x-5 gap-y-1 text-[12.5px] text-gray-600">
            <span>
              Từ <b>{formatDate(data.from)}</b> đến <b>{formatDate(data.to)}</b>
            </span>
            <span>
              Đã bán <b className="text-gray-900">{formatNumber(s.quantity)}</b> cuốn / {formatNumber(s.bookCount)} đầu
              sách / {formatNumber(s.orderCount)} hóa đơn
            </span>
            <span>
              Doanh thu <b className="text-gray-900">{formatMoney(s.revenue)}</b>
            </span>
            <span>
              {data.previousLabel}: {formatNumber(s.previousQuantity)} cuốn – {formatMoney(s.previousRevenue)}
            </span>
          </div>
          <div className="grid grid-cols-1 gap-2 2xl:grid-cols-[minmax(0,3fr)_minmax(0,2fr)]">
            <div className="rounded border border-gray-200">
              <div className="border-b border-gray-200 px-2 py-1 font-semibold text-gray-700">
                Sách bán chạy – {data.label}
              </div>
              <BookRankingTable rows={data.books} previousLabel={data.previousLabel} />
            </div>
            <div className="rounded border border-gray-200">
              <div className="border-b border-gray-200 px-2 py-1 font-semibold text-gray-700">
                Thể loại bán chạy – {data.label}
              </div>
              <CategoryRanking rows={data.categories} compact />
            </div>
          </div>
          <p className="text-[11.5px] text-gray-500">
            Tính trên hóa đơn {deliveredOnly ? "giao thành công" : "đã xác nhận, đang giao và giao thành công"} (không
            tính đơn chờ xác nhận, đã hủy, trả hàng). Cột “Tồn kho” cảnh báo khi tồn ít hơn số đã bán trong kỳ.
          </p>
        </div>
      ) : null}
    </div>
  );
}

export default TopSelling;
