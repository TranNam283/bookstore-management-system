import { useCallback, useEffect, useMemo, useRef } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { IoMdAddCircle } from "react-icons/io";
import Image from "../../Image";
import Pagination from "../Pagination";
import InputSearch from "../InputSearch";
import Loading from "../../Loading";
import SortableTh from "../SortableTh";
import AdvancedSearchPanel from "../AdvancedSearchPanel";
import useAdvancedSearch, { useAdvancedSearchState } from "../../../hooks/admin/useAdvancedSearch";
import useGetPublishers1 from "../../../hooks/admin/useGetPublishers1";
import type { ImportReceipt as ReceiptType, SearchField } from "../../../types/type";
import { formatDateTime, formatMoney, formatNumber } from "../../../utils/format";

/** Danh sách phiếu nhập (main) + chi tiết phiếu đang chọn (sub) trên cùng một màn hình. */
function ImportReceipt() {
  const [searchParams, setSearchParams] = useSearchParams();
  const selectedId = searchParams.get("selected");
  const { request, groups, applyGroups, sortBy, sortDir, toggleSort, page, limit } =
    useAdvancedSearchState(20);
  const { items: receipts, total, totalPages, summary, isLoading } =
    useAdvancedSearch<ReceiptType>("/api/import-receipt/search", request);
  const { publishers } = useGetPublishers1();

  const fields: SearchField[] = useMemo(
    () => [
      { key: "totalAmount", label: "Tổng tiền nhập", type: "number" },
      { key: "totalQuantity", label: "Tổng số lượng", type: "number" },
      { key: "createdAt", label: "Ngày nhập", type: "date" },
      {
        key: "publisherId",
        label: "Nhà xuất bản",
        type: "select",
        options: publishers.map((p) => ({ value: p.id ?? "", label: p.name })),
      },
      { key: "staffName", label: "Nhân viên lập", type: "text" },
      { key: "receiptCode", label: "Mã phiếu", type: "text" },
      { key: "bookTitle", label: "Tên sách", type: "text" },
      { key: "note", label: "Ghi chú", type: "text" },
    ],
    [publishers],
  );

  const presets = useMemo(
    () => [
      {
        label: "Tiền nhập 1tr – 3tr",
        groups: [
          {
            logic: "AND" as const,
            conditions: [
              { field: "totalAmount", operator: ">=" as const, value: "1000000" },
              { field: "totalAmount", operator: "<=" as const, value: "3000000" },
            ],
          },
        ],
      },
      {
        label: "Nhập trong năm 2026",
        groups: [
          {
            logic: "AND" as const,
            conditions: [
              { field: "createdAt", operator: ">=" as const, value: "2026-01-01" },
              { field: "createdAt", operator: "<=" as const, value: "2026-12-31" },
            ],
          },
        ],
      },
    ],
    [],
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
      if (["INPUT", "SELECT", "TEXTAREA"].includes(tag)) return;
      if (e.key !== "ArrowDown" && e.key !== "ArrowUp") return;
      if (!receipts.length) return;
      e.preventDefault();
      const index = Math.max(0, receipts.findIndex((r) => r.id === selected?.id));
      const next = e.key === "ArrowDown" ? Math.min(index + 1, receipts.length - 1) : Math.max(index - 1, 0);
      select(receipts[next].id);
      rowRefs.current[receipts[next].id]?.scrollIntoView({ block: "nearest" });
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [receipts, selected, select]);

  const th = (field: string, label: string, className = "") => (
    <SortableTh field={field} label={label} sortBy={sortBy} sortDir={sortDir} onSort={toggleSort} className={className} />
  );

  return (
    <div className="bg-[#f1f4f9] p-2 space-y-2">
      <div className="flex justify-between items-center flex-wrap gap-2">
        <h2 className="text-[15px] font-semibold text-gray-700">
          Phiếu nhập ({formatNumber(total)}) – tổng chi {formatMoney(summary.totalAmount)}
        </h2>
        <div className="flex items-center gap-2">
          <InputSearch />
          <Link
            to="/admin/add-import-receipt"
            className="bg-[#C62028] text-white text-[13px] font-medium !flex px-3 py-[5px] items-center gap-1"
          >
            <IoMdAddCircle size={18} /> Lập phiếu nhập
          </Link>
        </div>
      </div>

      <AdvancedSearchPanel fields={fields} value={groups} onApply={applyGroups} presets={presets} />

      <div className="grid grid-cols-1 xl:grid-cols-[minmax(0,1fr)_440px] gap-2">
        <div className="bg-white border border-gray-300">
          <div className="overflow-auto max-h-[calc(100vh-260px)]">
            <table className="admin-table">
              <thead>
                <tr>
                  <th>#</th>
                  {th("receiptCode", "Mã phiếu")}
                  {th("createdAt", "Ngày nhập")}
                  {th("publisherName", "Nhà xuất bản")}
                  {th("staffName", "Nhân viên lập")}
                  {th("totalQuantity", "Số lượng", "num")}
                  {th("totalAmount", "Tổng tiền nhập", "num")}
                  <th>Ghi chú</th>
                </tr>
              </thead>
              <tbody>
                {isLoading && receipts.length === 0 ? (
                  <tr>
                    <td colSpan={8}>
                      <Loading height={60} size={40} color="black" thickness={2} />
                    </td>
                  </tr>
                ) : receipts.length > 0 ? (
                  receipts.map((r, index) => (
                    <tr
                      key={r.id}
                      ref={(el) => {
                        rowRefs.current[r.id] = el;
                      }}
                      onClick={() => select(r.id)}
                      className={`cursor-pointer ${selected?.id === r.id ? "is-selected" : ""}`}
                    >
                      <td className="text-gray-400">{(page - 1) * limit + index + 1}</td>
                      <td className="font-semibold">{r.receiptCode}</td>
                      <td>{formatDateTime(r.createdAt)}</td>
                      <td>{r.publisherName ?? "-"}</td>
                      <td>{r.staffName ?? "-"}</td>
                      <td className="num">{formatNumber(r.totalQuantity)}</td>
                      <td className="num font-semibold">{formatNumber(r.totalAmount)}</td>
                      <td className="max-w-[220px] truncate">{r.note}</td>
                    </tr>
                  ))
                ) : (
                  <tr>
                    <td colSpan={8} className="text-center text-gray-500 py-6">
                      Không có phiếu nhập phù hợp
                    </td>
                  </tr>
                )}
              </tbody>
              {receipts.length > 0 && (
                <tfoot>
                  <tr>
                    <td colSpan={5}>Tổng {formatNumber(summary.count)} phiếu (theo bộ lọc)</td>
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

        <aside className="bg-white border border-gray-300 text-[13px] xl:sticky xl:top-[60px] self-start">
          <div className="px-3 py-2 border-b border-gray-200 bg-[#f7f8fa] flex justify-between">
            <b>Chi tiết phiếu nhập {selected ? `#${selected.receiptCode}` : ""}</b>
            <span className="text-[11px] text-gray-500">↑/↓ để chuyển phiếu</span>
          </div>
          {selected ? (
            <div className="p-3 space-y-2">
              <div className="grid grid-cols-[110px_1fr] gap-y-[3px]">
                <span className="text-gray-500">Ngày nhập</span>
                <span>{formatDateTime(selected.createdAt)}</span>
                <span className="text-gray-500">Nhà xuất bản</span>
                <span>{selected.publisherName ?? "-"}</span>
                <span className="text-gray-500">Nhân viên lập</span>
                <span>{selected.staffName ?? "-"}</span>
                <span className="text-gray-500">Ghi chú</span>
                <span>{selected.note || "-"}</span>
              </div>
              <table className="admin-table">
                <thead>
                  <tr>
                    <th>Sách</th>
                    <th className="num">SL</th>
                    <th className="num">Giá nhập</th>
                    <th className="num">Thành tiền</th>
                  </tr>
                </thead>
                <tbody>
                  {selected.items.map((item) => (
                    <tr key={item.bookId}>
                      <td className="wrap">
                        <div className="flex items-center gap-1.5">
                          {item.image && (
                            <Image
                              source={`${import.meta.env.VITE_BACKEND_URL}${item.image}`}
                              alt=""
                              className="w-8 h-8"
                              loading="lazy"
                            />
                          )}
                          <span className="max-w-[180px]">{item.title}</span>
                        </div>
                      </td>
                      <td className="num">{item.quantity}</td>
                      <td className="num">{formatNumber(item.importPrice)}</td>
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
            <p className="text-gray-500 py-8 text-center">Chọn một phiếu nhập để xem chi tiết</p>
          )}
        </aside>
      </div>
    </div>
  );
}

export default ImportReceipt;
