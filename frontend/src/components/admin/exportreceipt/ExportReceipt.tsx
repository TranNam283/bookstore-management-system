import { useCallback, useEffect, useMemo, useRef } from "react";
import { Link, useSearchParams } from "react-router-dom";
import Image from "../../Image";
import Pagination from "../Pagination";
import InputSearch from "../InputSearch";
import Loading from "../../Loading";
import SortableTh from "../SortableTh";
import AdvancedSearchPanel from "../AdvancedSearchPanel";
import PageHeader from "../ui/PageHeader";
import Badge from "../ui/Badge";
import KpiStrip from "../ui/KpiStrip";
import useAdvancedSearch, { useAdvancedSearchState } from "../../../hooks/admin/useAdvancedSearch";
import useSuppliers from "../../../hooks/admin/useSuppliers";
import type { ExportReceipt as ReceiptType, SearchCondition, SearchField } from "../../../types/type";
import { formatDateTime, formatMoney, formatNumber } from "../../../utils/format";
import { EXPORT_TYPE } from "../../../utils/labels";

const TYPE_FILTERS = [
  { label: "Tất cả", value: null },
  { label: "Xuất bán", value: "SALE" },
  { label: "Hư hỏng / mất", value: "DAMAGED" },
  { label: "Trả nhà cung cấp", value: "RETURN_SUPPLIER" },
  { label: "Xuất khác", value: "OTHER" },
];

/** Phiếu xuất kho: danh sách (main) + chi tiết phiếu đang chọn (sub). */
function ExportReceipt() {
  const [searchParams, setSearchParams] = useSearchParams();
  const typeParam = searchParams.get("type");
  const selectedId = searchParams.get("selected");
  const quick: SearchCondition[] = useMemo(
    () => (typeParam ? [{ field: "type", operator: "=", value: typeParam }] : []),
    [typeParam],
  );
  const { request, groups, applyGroups, sortBy, sortDir, toggleSort, page, limit } = useAdvancedSearchState(20, quick);
  const { items: receipts, total, totalPages, summary, isLoading } =
    useAdvancedSearch<ReceiptType>("/api/export-receipt/search", request);
  const { suppliers } = useSuppliers();

  const fields: SearchField[] = useMemo(
    () => [
      { key: "totalAmount", label: "Tổng giá trị", type: "number" },
      { key: "totalQuantity", label: "Tổng số lượng", type: "number" },
      { key: "createdAt", label: "Ngày xuất", type: "date" },
      {
        key: "type",
        label: "Loại phiếu",
        type: "select",
        options: Object.entries(EXPORT_TYPE).map(([value, t]) => ({ value, label: t.label })),
      },
      {
        key: "supplierId",
        label: "Nhà cung cấp",
        type: "select",
        options: suppliers.map((s) => ({ value: s.id, label: s.name })),
      },
      { key: "orderCode", label: "Mã hóa đơn", type: "text" },
      { key: "staffName", label: "Nhân viên lập", type: "text" },
      { key: "bookTitle", label: "Tên sách", type: "text" },
      { key: "reason", label: "Lý do", type: "text" },
    ],
    [suppliers],
  );

  const selected = receipts.find((r) => r.id === selectedId) ?? receipts[0];
  const select = useCallback(
    (id: string) => {
      const params = new URLSearchParams(searchParams.toString());
      params.set("selected", id);
      setSearchParams(params, { replace: true });
    },
    [searchParams, setSearchParams],
  );

  const rowRefs = useRef<Record<string, HTMLTableRowElement | null>>({});
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const tag = (e.target as HTMLElement)?.tagName;
      if (["INPUT", "SELECT", "TEXTAREA"].includes(tag) || !receipts.length) return;
      if (e.key !== "ArrowDown" && e.key !== "ArrowUp") return;
      e.preventDefault();
      const index = Math.max(0, receipts.findIndex((r) => r.id === selected?.id));
      const next = e.key === "ArrowDown" ? Math.min(index + 1, receipts.length - 1) : Math.max(index - 1, 0);
      select(receipts[next].id);
      rowRefs.current[receipts[next].id]?.scrollIntoView({ block: "nearest" });
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [receipts, selected, select]);

  const setType = (value: string | null) => {
    const params = new URLSearchParams(searchParams.toString());
    if (value) params.set("type", value);
    else params.delete("type");
    params.set("page", "1");
    params.delete("selected");
    setSearchParams(params);
  };

  const th = (field: string, label: string, className = "") => (
    <SortableTh field={field} label={label} sortBy={sortBy} sortDir={sortDir} onSort={toggleSort} className={className} />
  );

  return (
    <div className="bg-[#f1f4f9] min-h-full">
      <PageHeader
        title="Phiếu xuất kho"
        breadcrumb={[{ label: "Kho hàng" }, { label: "Phiếu xuất" }]}
        subtitle="Xuất bán được lập tự động khi hóa đơn chuyển sang “Đang giao”; các loại khác do nhân viên lập và trừ tồn kho."
        actions={
          <>
            <InputSearch />
            <Link
              to="/admin/add-export-receipt"
              className="rounded bg-[#C62028] px-3 py-[6px] text-[13px] font-medium text-white hover:bg-[#a81b22]"
            >
              + Lập phiếu xuất
            </Link>
          </>
        }
      />
      <div className="p-2 space-y-2">
        <KpiStrip
          items={[
            { label: "Số phiếu (theo lọc)", value: formatNumber(summary.count) },
            { label: "Tổng số lượng xuất", value: formatNumber(summary.totalQuantity) },
            { label: "Tổng giá trị xuất", value: formatMoney(summary.totalAmount), strong: true },
          ]}
        />
        <div className="flex flex-wrap gap-1">
          {TYPE_FILTERS.map((t) => (
            <button
              key={t.label}
              type="button"
              onClick={() => setType(t.value)}
              className={`rounded border px-2 py-[2px] text-[12px] ${
                typeParam === t.value ? "border-[#C62028] bg-[#C62028] text-white" : "border-gray-300 bg-white hover:bg-gray-50"
              }`}
            >
              {t.label}
            </button>
          ))}
        </div>
        <AdvancedSearchPanel fields={fields} value={groups} onApply={applyGroups} />

        <div className="grid grid-cols-1 xl:grid-cols-[minmax(0,1fr)_440px] gap-2">
          <div className="bg-white border border-gray-300 rounded">
            <div className="overflow-auto max-h-[calc(100vh-330px)]">
              <table className="admin-table">
                <thead>
                  <tr>
                    <th>#</th>
                    {th("receiptCode", "Mã phiếu")}
                    {th("createdAt", "Ngày xuất")}
                    {th("type", "Loại")}
                    <th>Chứng từ gốc</th>
                    {th("staffName", "Nhân viên")}
                    {th("totalQuantity", "SL", "num")}
                    {th("totalAmount", "Giá trị", "num")}
                    <th>Lý do</th>
                  </tr>
                </thead>
                <tbody>
                  {isLoading && receipts.length === 0 ? (
                    <tr>
                      <td colSpan={9}>
                        <Loading height={60} size={40} color="black" thickness={2} />
                      </td>
                    </tr>
                  ) : receipts.length ? (
                    receipts.map((r, i) => (
                      <tr
                        key={r.id}
                        ref={(el) => {
                          rowRefs.current[r.id] = el;
                        }}
                        onClick={() => select(r.id)}
                        className={`cursor-pointer ${selected?.id === r.id ? "is-selected" : ""}`}
                      >
                        <td className="text-gray-400">{(page - 1) * limit + i + 1}</td>
                        <td className="font-semibold">{r.receiptCode}</td>
                        <td>{formatDateTime(r.createdAt)}</td>
                        <td>
                          <Badge tone={EXPORT_TYPE[r.type]?.tone}>{EXPORT_TYPE[r.type]?.label ?? r.type}</Badge>
                        </td>
                        <td>{r.orderCode ? `HĐ ${r.orderCode}` : r.supplierName ?? "-"}</td>
                        <td>{r.staffName ?? "-"}</td>
                        <td className="num">{formatNumber(r.totalQuantity)}</td>
                        <td className="num font-semibold">{formatNumber(r.totalAmount)}</td>
                        <td className="max-w-[220px] truncate">{r.reason}</td>
                      </tr>
                    ))
                  ) : (
                    <tr>
                      <td colSpan={9} className="py-6 text-center text-gray-500">
                        Không có phiếu xuất phù hợp
                      </td>
                    </tr>
                  )}
                </tbody>
                {receipts.length > 0 && (
                  <tfoot>
                    <tr>
                      <td colSpan={6}>Tổng {formatNumber(summary.count)} phiếu</td>
                      <td className="num">{formatNumber(summary.totalQuantity)}</td>
                      <td className="num">{formatNumber(summary.totalAmount)}</td>
                      <td></td>
                    </tr>
                  </tfoot>
                )}
              </table>
            </div>
            <Pagination totalPages={totalPages} currentPage={page} limit={limit} totalItems={total} />
          </div>

          <aside className="bg-white border border-gray-300 rounded text-[13px] self-start xl:sticky xl:top-[60px]">
            <div className="flex justify-between border-b border-gray-200 bg-[#f7f8fa] px-3 py-2">
              <b>Chi tiết phiếu xuất {selected ? `#${selected.receiptCode}` : ""}</b>
              <span className="text-[11px] text-gray-500">↑/↓ để chuyển phiếu</span>
            </div>
            {selected ? (
              <div className="space-y-2 p-3">
                <div className="grid grid-cols-[110px_1fr] gap-y-[3px]">
                  <span className="text-gray-500">Loại phiếu</span>
                  <span>
                    <Badge tone={EXPORT_TYPE[selected.type]?.tone}>{EXPORT_TYPE[selected.type]?.label}</Badge>
                  </span>
                  <span className="text-gray-500">Ngày xuất</span>
                  <span>{formatDateTime(selected.createdAt)}</span>
                  {selected.orderCode && (
                    <>
                      <span className="text-gray-500">Hóa đơn</span>
                      <Link to={`/admin/orders?selected=${selected.orderId}&q=${selected.orderCode}`} className="text-blue-700 hover:underline">
                        {selected.orderCode} →
                      </Link>
                    </>
                  )}
                  {selected.supplierName && (
                    <>
                      <span className="text-gray-500">Nhà cung cấp</span>
                      <span>{selected.supplierName}</span>
                    </>
                  )}
                  <span className="text-gray-500">Nhân viên lập</span>
                  <span>{selected.staffName ?? "-"}</span>
                  <span className="text-gray-500">Lý do</span>
                  <span>{selected.reason || "-"}</span>
                </div>
                <table className="admin-table">
                  <thead>
                    <tr>
                      <th>Sách</th>
                      <th className="num">SL</th>
                      <th className="num">Đơn giá</th>
                      <th className="num">Thành tiền</th>
                    </tr>
                  </thead>
                  <tbody>
                    {selected.items.map((item) => (
                      <tr key={item.bookId}>
                        <td className="wrap">
                          <div className="flex items-center gap-1.5">
                            {item.image && (
                              <Image source={`${import.meta.env.VITE_BACKEND_URL}${item.image}`} alt="" className="w-8 h-8" loading="lazy" />
                            )}
                            <span className="max-w-[180px]">{item.title}</span>
                          </div>
                        </td>
                        <td className="num">{item.quantity}</td>
                        <td className="num">{formatNumber(item.unitPrice)}</td>
                        <td className="num font-semibold">{formatNumber(item.lineTotal)}</td>
                      </tr>
                    ))}
                  </tbody>
                  <tfoot>
                    <tr>
                      <td>Tổng</td>
                      <td className="num">{formatNumber(selected.totalQuantity)}</td>
                      <td></td>
                      <td className="num text-[#C62028]">{formatMoney(selected.totalAmount)}</td>
                    </tr>
                  </tfoot>
                </table>
              </div>
            ) : (
              <p className="py-8 text-center text-gray-500">Chọn một phiếu xuất để xem chi tiết</p>
            )}
          </aside>
        </div>
      </div>
    </div>
  );
}

export default ExportReceipt;
