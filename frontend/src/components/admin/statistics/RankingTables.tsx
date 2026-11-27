import { Link } from "react-router-dom";
import type { TopBookRow, TopCategoryRow } from "../../../hooks/admin/useStatistics";
import { formatNumber } from "../../../utils/format";
import Badge from "../ui/Badge";

const BAR = "#257de0";

const pct = (v: number) => `${(v * 100).toLocaleString("vi-VN", { maximumFractionDigits: 1 })}%`;

/** So sánh với kỳ trước: mũi tên + số (không chỉ dựa vào màu). */
function Change({ quantity, previous, change }: { quantity: number; previous: number; change: number }) {
  if (previous === 0 && quantity > 0) return <Badge tone="indigo">Mới</Badge>;
  if (change > 0) return <span className="text-emerald-700 tabular-nums">▲ +{formatNumber(change)}</span>;
  if (change < 0) return <span className="text-red-600 tabular-nums">▼ {formatNumber(change)}</span>;
  return <span className="text-gray-400">–</span>;
}

function Rank({ rank }: { rank: number }) {
  const top = rank <= 3;
  return (
    <span
      className={`inline-flex h-5 min-w-5 items-center justify-center rounded-full px-1 text-[11.5px] font-semibold tabular-nums ${
        top ? "bg-[#C62028] text-white" : "bg-gray-100 text-gray-600"
      }`}
    >
      {rank}
    </span>
  );
}

type BookProps = {
  rows: TopBookRow[];
  previousLabel?: string;
  emptyText?: string;
  showChange?: boolean;
};

/** Bảng xếp hạng sách bán chạy: số cuốn, doanh thu, tỷ trọng, so với kỳ trước, tồn kho. */
export function BookRankingTable({
  rows,
  previousLabel,
  emptyText = "Không có sách nào được bán trong kỳ này",
  showChange = true,
}: BookProps) {
  if (rows.length === 0) return <p className="py-6 text-center text-gray-500">{emptyText}</p>;
  return (
    <div className="overflow-auto">
      <table className="admin-table">
        <thead>
          <tr>
            <th>Hạng</th>
            <th>Sách</th>
            <th>Thể loại</th>
            <th className="num">Số cuốn bán</th>
            <th className="num">Doanh thu</th>
            <th className="num">Tỷ trọng</th>
            {showChange && (
              <th className="num" title={previousLabel ? `So với ${previousLabel}` : undefined}>
                So kỳ trước
              </th>
            )}
            <th className="num">Tồn kho</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((b) => (
            <tr key={b.bookId}>
              <td>
                <Rank rank={b.rank} />
              </td>
              <td className="wrap">
                <div className="flex min-w-[220px] items-center gap-2">
                  <div className="h-9 w-7 shrink-0 overflow-hidden bg-gray-50">
                    {b.image && (
                      <img
                        src={`${import.meta.env.VITE_BACKEND_URL}${b.image}`}
                        alt=""
                        loading="lazy"
                        className="h-full w-full object-contain"
                      />
                    )}
                  </div>
                  <div className="min-w-0">
                    <Link to={`/admin/edit-book/${b.bookId}`} className="font-medium text-gray-900 hover:underline">
                      {b.title}
                    </Link>
                    {b.author && <div className="text-[11.5px] text-gray-500">{b.author}</div>}
                  </div>
                </div>
              </td>
              <td>{b.category}</td>
              <td className="num font-semibold">{formatNumber(b.quantity)}</td>
              <td className="num">{formatNumber(b.revenue)}</td>
              <td className="num">{pct(b.share)}</td>
              {showChange && (
                <td className="num" title={`Kỳ trước: ${formatNumber(b.previousQuantity)} cuốn`}>
                  <Change quantity={b.quantity} previous={b.previousQuantity} change={b.change} />
                </td>
              )}
              <td className="num">
                {b.stock === null || b.stock === undefined ? (
                  "-"
                ) : b.stock === 0 ? (
                  <Badge tone="red">Hết hàng</Badge>
                ) : b.stock < b.quantity ? (
                  <span title="Tồn kho ít hơn số đã bán trong kỳ – nên nhập thêm">
                    <Badge tone="amber">{formatNumber(b.stock)} · nên nhập</Badge>
                  </span>
                ) : (
                  formatNumber(b.stock)
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

type CategoryProps = { rows: TopCategoryRow[]; showChange?: boolean; compact?: boolean };

/** Xếp hạng thể loại từ trên xuống: thanh ngang theo số cuốn bán + doanh thu, sách dẫn đầu. */
export function CategoryRanking({ rows, showChange = true, compact = false }: CategoryProps) {
  if (rows.length === 0) return <p className="py-6 text-center text-gray-500">Chưa có dữ liệu thể loại</p>;
  const max = Math.max(...rows.map((r) => r.quantity), 1);
  return (
    <div className="overflow-auto">
      <table className="admin-table">
        <thead>
          <tr>
            <th>Hạng</th>
            <th>Thể loại</th>
            <th className={compact ? "min-w-[130px]" : "min-w-[160px]"}>Số cuốn bán</th>
            <th className="num">Doanh thu</th>
            {!compact && <th className="num">Đầu sách</th>}
            {showChange && <th className="num">So kỳ trước</th>}
          </tr>
        </thead>
        <tbody>
          {rows.map((c) => (
            <tr key={c.name}>
              <td>
                <Rank rank={c.rank} />
              </td>
              <td className="wrap">
                <div className="font-medium">{c.name}</div>
                {c.topBook && (
                  <div className="max-w-[200px] truncate text-[11.5px] text-gray-500" title={`Sách dẫn đầu: ${c.topBook}`}>
                    Dẫn đầu: {c.topBook}
                  </div>
                )}
              </td>
              <td title={`${c.name}: ${formatNumber(c.quantity)} cuốn (${pct(c.share)})`}>
                <div className="flex items-center gap-2">
                  <div className="h-3 flex-1 rounded-sm bg-gray-100">
                    <div
                      className="h-3 rounded-r"
                      style={{ width: `${(c.quantity / max) * 100}%`, background: BAR, minWidth: 2 }}
                    />
                  </div>
                  <span className="w-[72px] shrink-0 text-right tabular-nums">
                    {formatNumber(c.quantity)} <span className="text-[11px] text-gray-500">({pct(c.share)})</span>
                  </span>
                </div>
              </td>
              <td className="num">{formatNumber(c.revenue)}</td>
              {!compact && <td className="num">{formatNumber(c.bookCount)}</td>}
              {showChange && (
                <td className="num" title={`Kỳ trước: ${formatNumber(c.previousQuantity)} cuốn`}>
                  <Change quantity={c.quantity} previous={c.previousQuantity} change={c.change} />
                </td>
              )}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
