import { useMemo } from "react";
import axios from "axios";
import useSWR from "swr";
import { Link, useSearchParams } from "react-router-dom";
import Pagination from "../Pagination";
import InputSearch from "../InputSearch";
import Loading from "../../Loading";
import SortableTh from "../SortableTh";
import AdvancedSearchPanel from "../AdvancedSearchPanel";
import PageHeader from "../ui/PageHeader";
import Badge from "../ui/Badge";
import KpiStrip from "../ui/KpiStrip";
import useAdvancedSearch, { useAdvancedSearchState } from "../../../hooks/admin/useAdvancedSearch";
import type { Book, SearchField, StockMovement } from "../../../types/type";
import { formatDateTime, formatMoney, formatNumber, toISODate } from "../../../utils/format";
import { MOVEMENT_TYPE } from "../../../utils/labels";
import { downloadCsv } from "../../../utils/csv";

const LOW_STOCK = 10;

/** +12 / −5, số 0 hiển thị "0" (không có dấu). */
const signed = (n: number | undefined, sign: "+" | "−") => (n ? `${sign}${formatNumber(n)}` : "0");

const REF_LINK: Record<string, (id: string, code: string) => string> = {
  ORDER: (id, code) => `/admin/orders?selected=${id}&q=${code}`,
  IMPORT: (id) => `/admin/import-receipts?selected=${id}`,
  EXPORT: (id) => `/admin/export-receipts?selected=${id}`,
};

/** Tồn kho & thẻ kho từng sách + báo cáo nhập - xuất - tồn. */
function Inventory() {
  const [searchParams, setSearchParams] = useSearchParams();
  const tab = searchParams.get("tab") || "stock";
  const setTab = (t: string) => {
    const params = new URLSearchParams(searchParams.toString());
    params.set("tab", t);
    setSearchParams(params, { replace: true });
  };

  return (
    <div className="bg-[#f1f4f9] min-h-full">
      <PageHeader
        title="Tồn kho"
        breadcrumb={[{ label: "Kho hàng" }, { label: "Tồn kho" }]}
        subtitle="Mọi lần tồn kho thay đổi (nhập, bán, hủy / trả, xuất, điều chỉnh) đều được ghi vào thẻ kho."
        actions={
          <div className="flex rounded border border-gray-300 bg-white p-0.5 text-[13px]">
            {[
              { key: "stock", label: "Tồn kho & thẻ kho" },
              { key: "report", label: "Báo cáo nhập – xuất – tồn" },
            ].map((t) => (
              <button
                key={t.key}
                type="button"
                onClick={() => setTab(t.key)}
                className={`rounded px-3 py-[4px] ${tab === t.key ? "bg-[#C62028] text-white" : "hover:bg-gray-100"}`}
              >
                {t.label}
              </button>
            ))}
          </div>
        }
      />
      <div className="p-2 space-y-2">{tab === "report" ? <InventoryReport /> : <StockTab />}</div>
    </div>
  );
}

function StockTab() {
  const [searchParams, setSearchParams] = useSearchParams();
  const selectedId = searchParams.get("book");
  const { request, groups, applyGroups, sortDir, toggleSort, page, limit } = useAdvancedSearchState(20);
  const { items: books, total, totalPages, summary, isLoading } = useAdvancedSearch<Book>("/api/book/admin/search", {
    ...request,
    sortBy: request.sortBy ?? "stock",
    sortDir: request.sortBy ? request.sortDir : "asc",
  });
  const selected = books.find((b) => b.id === selectedId) ?? books[0];

  const movementRequest = useMemo(
    () => ({
      keyword: "",
      groups: selected ? [{ logic: "AND" as const, conditions: [{ field: "bookId", operator: "=" as const, value: selected.id }] }] : [],
      page: 1,
      limit: 200,
      sortBy: "createdAt",
      sortDir: "desc" as const,
    }),
    [selected],
  );
  const { items: movements, summary: mSummary, isLoading: mLoading } = useAdvancedSearch<StockMovement>(
    "/api/inventory/movements/search",
    movementRequest,
  );

  const fields: SearchField[] = [
    { key: "stock", label: "Tồn kho", type: "number" },
    { key: "totalSold", label: "Đã bán", type: "number" },
    { key: "price", label: "Giá bán", type: "number" },
    { key: "title", label: "Tên sách", type: "text" },
  ];

  const select = (id: string) => {
    const params = new URLSearchParams(searchParams.toString());
    params.set("book", id);
    setSearchParams(params, { replace: true });
  };

  const th = (field: string, label: string, className = "") => (
    <SortableTh field={field} label={label} sortBy={request.sortBy ?? "stock"} sortDir={request.sortBy ? sortDir : "asc"} onSort={toggleSort} className={className} />
  );

  return (
    <>
      <KpiStrip
        items={[
          { label: "Đầu sách", value: formatNumber(summary.count) },
          { label: "Tổng tồn kho", value: `${formatNumber(summary.totalStock)} cuốn` },
          { label: "Giá trị tồn (giá bán)", value: formatMoney(summary.stockValue), strong: true },
          { label: "Tổng đã bán", value: `${formatNumber(summary.totalSold)} cuốn` },
        ]}
      />
      <div className="flex flex-wrap items-center gap-2">
        <InputSearch />
        <button
          type="button"
          onClick={() => applyGroups([{ logic: "AND", conditions: [{ field: "stock", operator: "<=", value: String(LOW_STOCK) }] }])}
          className="rounded border border-amber-300 bg-amber-50 px-2 py-[3px] text-[12px] text-amber-800 hover:bg-amber-100"
        >
          Sắp hết hàng (≤ {LOW_STOCK})
        </button>
      </div>
      <AdvancedSearchPanel fields={fields} value={groups} onApply={applyGroups} />

      <div className="grid grid-cols-1 xl:grid-cols-[minmax(0,1fr)_minmax(0,1.1fr)] gap-2">
        <div className="rounded border border-gray-300 bg-white">
          <div className="overflow-auto max-h-[calc(100vh-330px)]">
            <table className="admin-table">
              <thead>
                <tr>
                  {th("title", "Sách")}
                  {th("stock", "Tồn", "num")}
                  {th("totalSold", "Đã bán", "num")}
                  {th("price", "Giá bán", "num")}
                  <th className="num">Giá trị tồn</th>
                  <th>Tình trạng</th>
                </tr>
              </thead>
              <tbody>
                {isLoading && !books.length ? (
                  <tr>
                    <td colSpan={6}>
                      <Loading height={60} size={40} color="black" thickness={2} />
                    </td>
                  </tr>
                ) : (
                  books.map((b) => (
                    <tr key={b.id} onClick={() => select(b.id)} className={`cursor-pointer ${selected?.id === b.id ? "is-selected" : ""}`}>
                      <td className="wrap max-w-[260px] font-medium">{b.title}</td>
                      <td className={`num font-semibold ${b.stock <= 5 ? "text-red-600" : ""}`}>{b.stock}</td>
                      <td className="num">{formatNumber(b.totalSold)}</td>
                      <td className="num">{formatNumber(b.price)}</td>
                      <td className="num">{formatNumber(b.stock * b.price)}</td>
                      <td>
                        {b.stock === 0 ? (
                          <Badge tone="red">Hết hàng</Badge>
                        ) : b.stock <= LOW_STOCK ? (
                          <Badge tone="amber">Sắp hết</Badge>
                        ) : (
                          <Badge tone="green">Còn hàng</Badge>
                        )}
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
          <Pagination totalPages={totalPages} currentPage={page} limit={limit} totalItems={total} />
        </div>

        <aside className="rounded border border-gray-300 bg-white self-start xl:sticky xl:top-[60px]">
          <div className="flex flex-wrap items-center justify-between gap-2 border-b border-gray-200 bg-[#f7f8fa] px-3 py-2 text-[13px]">
            <b className="truncate">Thẻ kho: {selected?.title ?? "-"}</b>
            {selected && (
              <span className="text-[12px] text-gray-600">
                Nhập/tăng <b className="text-emerald-700">+{formatNumber(mSummary.totalIn)}</b> · Xuất/giảm{" "}
                <b className="text-red-700">−{formatNumber(mSummary.totalOut)}</b> · Tồn hiện tại <b>{selected.stock}</b>
              </span>
            )}
          </div>
          <div className="overflow-auto max-h-[calc(100vh-260px)]">
            <table className="admin-table">
              <thead>
                <tr>
                  <th>Thời gian</th>
                  <th>Nghiệp vụ</th>
                  <th>Chứng từ</th>
                  <th className="num">Thay đổi</th>
                  <th className="num">Tồn sau</th>
                  <th>Người thực hiện</th>
                  <th>Ghi chú</th>
                </tr>
              </thead>
              <tbody>
                {mLoading && !movements.length ? (
                  <tr>
                    <td colSpan={7}>
                      <Loading height={40} size={30} color="black" thickness={2} />
                    </td>
                  </tr>
                ) : movements.length ? (
                  movements.map((m) => (
                    <tr key={m.id}>
                      <td>{formatDateTime(m.createdAt)}</td>
                      <td>
                        <Badge tone={MOVEMENT_TYPE[m.type]?.tone}>{MOVEMENT_TYPE[m.type]?.label ?? m.type}</Badge>
                      </td>
                      <td>
                        {m.refCode && m.refId && m.refType && REF_LINK[m.refType] ? (
                          <Link to={REF_LINK[m.refType](m.refId, m.refCode)} className="text-blue-700 hover:underline">
                            {m.refCode}
                          </Link>
                        ) : (
                          m.refCode ?? "-"
                        )}
                      </td>
                      <td className={`num font-semibold ${m.quantityChange > 0 ? "text-emerald-700" : "text-red-700"}`}>
                        {m.quantityChange > 0 ? `+${m.quantityChange}` : `−${-m.quantityChange}`}
                      </td>
                      <td className="num">{m.stockAfter}</td>
                      <td>{m.staffName ?? "-"}</td>
                      <td className="max-w-[200px] truncate" title={m.note ?? ""}>
                        {m.note}
                      </td>
                    </tr>
                  ))
                ) : (
                  <tr>
                    <td colSpan={7} className="py-6 text-center text-gray-500">
                      Chưa có biến động tồn kho
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </aside>
      </div>
    </>
  );
}

type ReportRow = {
  bookId: string;
  title: string;
  price: number;
  opening: number;
  imported: number;
  returned: number;
  sold: number;
  exported: number;
  closing: number;
  closingValue: number;
};
type Report = { from: string; to: string; rows: ReportRow[]; summary: Record<string, number> };

const fetcher = (url: string) => axios.get(url).then((r) => r.data);

function InventoryReport() {
  const [searchParams, setSearchParams] = useSearchParams();
  const today = new Date();
  const from = searchParams.get("from") || toISODate(new Date(today.getFullYear(), today.getMonth(), 1));
  const to = searchParams.get("to") || toISODate(today);
  const { data, isLoading, error } = useSWR<Report>(
    `${import.meta.env.VITE_BACKEND_URL}/api/inventory/report?from=${from}&to=${to}`,
    fetcher,
    { revalidateOnFocus: false, keepPreviousData: true },
  );
  const setParam = (k: string, v: string) => {
    const params = new URLSearchParams(searchParams.toString());
    params.set(k, v);
    setSearchParams(params, { replace: true });
  };
  const s = data?.summary ?? {};
  const rows = data?.rows ?? [];

  const exportCsv = () =>
    downloadCsv(
      `nhap-xuat-ton_${from}_${to}.csv`,
      ["Sách", "Tồn đầu kỳ", "Nhập NCC", "Hoàn kho (hủy/trả)", "Bán ra", "Xuất khác", "Tồn cuối kỳ", "Giá bán", "Giá trị tồn cuối"],
      rows.map((r) => [r.title, r.opening, r.imported, r.returned, r.sold, r.exported, r.closing, r.price, r.closingValue]),
    );

  const input = "border border-gray-300 rounded px-2 py-[3px] text-[13px]";
  return (
    <>
      <div className="flex flex-wrap items-center gap-2 rounded border border-gray-300 bg-white px-2 py-1.5 text-[13px]">
        <label className="flex items-center gap-1">
          Từ ngày <input type="date" value={from} max={to} onChange={(e) => setParam("from", e.target.value)} className={input} />
        </label>
        <label className="flex items-center gap-1">
          đến ngày <input type="date" value={to} min={from} onChange={(e) => setParam("to", e.target.value)} className={input} />
        </label>
        <span className="text-gray-500">Công thức: Tồn cuối = Tồn đầu + Nhập + Hoàn kho − Bán − Xuất khác</span>
        <button type="button" onClick={exportCsv} disabled={!rows.length} className="ml-auto rounded border border-gray-300 px-3 py-[4px] hover:bg-gray-50">
          ⭳ Xuất Excel (CSV)
        </button>
      </div>
      {error && <p className="text-red-600">{error?.response?.data?.message ?? "Không tải được báo cáo"}</p>}
      <KpiStrip
        items={[
          { label: "Tồn đầu kỳ", value: formatNumber(s.opening) },
          { label: "Nhập từ NCC", value: signed(s.imported, "+") },
          { label: "Hoàn kho", value: signed(s.returned, "+") },
          { label: "Bán ra", value: signed(s.sold, "−") },
          { label: "Xuất khác", value: signed(s.exported, "−") },
          { label: "Tồn cuối kỳ", value: `${formatNumber(s.closing)} (${formatMoney(s.closingValue)})`, strong: true },
        ]}
      />
      <div className="rounded border border-gray-300 bg-white">
        <div className="overflow-auto max-h-[calc(100vh-280px)]">
          <table className="admin-table">
            <thead>
              <tr>
                <th>#</th>
                <th>Sách</th>
                <th className="num">Tồn đầu</th>
                <th className="num">Nhập NCC</th>
                <th className="num">Hoàn kho</th>
                <th className="num">Bán ra</th>
                <th className="num">Xuất khác</th>
                <th className="num">Tồn cuối</th>
                <th className="num">Giá trị tồn cuối</th>
              </tr>
            </thead>
            <tbody>
              {isLoading && !rows.length ? (
                <tr>
                  <td colSpan={9}>
                    <Loading height={60} size={40} color="black" thickness={2} />
                  </td>
                </tr>
              ) : (
                rows.map((r, i) => (
                  <tr key={r.bookId}>
                    <td className="text-gray-400">{i + 1}</td>
                    <td className="wrap max-w-[320px] font-medium">{r.title}</td>
                    <td className="num">{formatNumber(r.opening)}</td>
                    <td className="num text-emerald-700">{r.imported ? `+${formatNumber(r.imported)}` : "0"}</td>
                    <td className="num text-teal-700">{r.returned ? `+${formatNumber(r.returned)}` : "0"}</td>
                    <td className="num text-blue-700">{r.sold ? `−${formatNumber(r.sold)}` : "0"}</td>
                    <td className="num text-orange-700">{r.exported ? `−${formatNumber(r.exported)}` : "0"}</td>
                    <td className="num font-semibold">{formatNumber(r.closing)}</td>
                    <td className="num">{formatNumber(r.closingValue)}</td>
                  </tr>
                ))
              )}
            </tbody>
            {rows.length > 0 && (
              <tfoot>
                <tr>
                  <td colSpan={2}>Tổng {rows.length} đầu sách</td>
                  <td className="num">{formatNumber(s.opening)}</td>
                  <td className="num">{signed(s.imported, "+")}</td>
                  <td className="num">{signed(s.returned, "+")}</td>
                  <td className="num">{signed(s.sold, "−")}</td>
                  <td className="num">{signed(s.exported, "−")}</td>
                  <td className="num">{formatNumber(s.closing)}</td>
                  <td className="num">{formatNumber(s.closingValue)}</td>
                </tr>
              </tfoot>
            )}
          </table>
        </div>
      </div>
    </>
  );
}

export default Inventory;
