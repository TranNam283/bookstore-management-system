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
import useSuppliers from "../../../hooks/admin/useSuppliers";
import axios from "axios";
import toast from "react-hot-toast";
import Swal from "sweetalert2";
import Badge from "../ui/Badge";
import PageHeader from "../ui/PageHeader";
import { adminAuthHeader } from "../../../utils/adminAuth";
import type { ImportReceipt as ReceiptType, SearchField } from "../../../types/type";
import { formatDateTime, formatMoney, formatNumber } from "../../../utils/format";

/** Danh sách phiếu nhập (main) + chi tiết phiếu đang chọn (sub) trên cùng một màn hình. */
function ImportReceipt() {
  const [searchParams, setSearchParams] = useSearchParams();
  const selectedId = searchParams.get("selected");
  const { request, groups, applyGroups, sortBy, sortDir, toggleSort, page, limit } =
    useAdvancedSearchState(20);
  const { items: receipts, total, totalPages, summary, isLoading, mutate } =
    useAdvancedSearch<ReceiptType>("/api/import-receipt/search", request);
  const { suppliers } = useSuppliers();

  const cancelReceipt = async (receipt: ReceiptType) => {
    const result = await Swal.fire({
      title: `Hủy phiếu nhập ${receipt.receiptCode}?`,
      text: "Tồn kho các sách trong phiếu sẽ bị trừ lại. Nhập lý do hủy:",
      input: "text",
      inputPlaceholder: "vd: Nhập nhầm số lượng",
      inputValidator: (v) => (!v ? "Vui lòng nhập lý do" : undefined),
      icon: "warning",
      showCancelButton: true,
      confirmButtonText: "Hủy phiếu",
      cancelButtonText: "Đóng",
    });
    if (!result.isConfirmed) return;
    try {
      await axios.patch(
        `${import.meta.env.VITE_BACKEND_URL}/api/import-receipt/${receipt.id}/cancel`,
        { reason: result.value },
        { headers: adminAuthHeader() },
      );
      toast.success("Đã hủy phiếu nhập và trừ lại tồn kho");
      mutate();
    } catch (err: any) {
      toast.error(err?.response?.data?.message || "Hủy phiếu thất bại");
    }
  };

  const fields: SearchField[] = useMemo(
    () => [
      { key: "totalAmount", label: "Tổng tiền nhập", type: "number" },
      { key: "totalQuantity", label: "Tổng số lượng", type: "number" },
      { key: "createdAt", label: "Ngày nhập", type: "date" },
      {
        key: "supplierId",
        label: "Nhà cung cấp",
        type: "select",
        options: suppliers.map((s) => ({ value: s.id, label: s.name })),
      },
      {
        key: "status",
        label: "Trạng thái",
        type: "select",
        options: [
          { value: "1", label: "Hoàn tất" },
          { value: "0", label: "Đã hủy" },
        ],
      },
      { key: "staffName", label: "Nhân viên lập", type: "text" },
      { key: "receiptCode", label: "Mã phiếu", type: "text" },
      { key: "bookTitle", label: "Tên sách", type: "text" },
      { key: "note", label: "Ghi chú", type: "text" },
    ],
    [suppliers],
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
    <div className="bg-[#f1f4f9] min-h-full">
      <PageHeader
        title={`Phiếu nhập hàng (${formatNumber(total)})`}
        breadcrumb={[{ label: "Kho hàng" }, { label: "Nhập hàng" }]}
        subtitle={
          <>
            Tổng chi theo bộ lọc: <b>{formatMoney(summary.totalAmount)}</b> (không tính phiếu đã hủy). Lập phiếu
            nhập sẽ cộng tồn kho và ghi thẻ kho.
          </>
        }
        actions={
          <>
            <InputSearch />
            <Link
              to="/admin/add-import-receipt"
              className="rounded bg-[#C62028] text-white text-[13px] font-medium !flex px-3 py-[6px] items-center gap-1 hover:bg-[#a81b22]"
            >
              <IoMdAddCircle size={18} /> Lập phiếu nhập
            </Link>
          </>
        }
      />
    <div className="p-2 space-y-2">
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
                  {th("supplierName", "Nhà cung cấp")}
                  {th("staffName", "Nhân viên lập")}
                  {th("totalQuantity", "Số lượng", "num")}
                  {th("totalAmount", "Tổng tiền nhập", "num")}
                  {th("status", "Trạng thái")}
                  <th>Ghi chú</th>
                </tr>
              </thead>
              <tbody>
                {isLoading && receipts.length === 0 ? (
                  <tr>
                    <td colSpan={9}>
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
                      <td className="max-w-[220px] truncate" title={r.supplierName ?? ""}>{r.supplierName ?? "-"}</td>
                      <td>{r.staffName ?? "-"}</td>
                      <td className="num">{formatNumber(r.totalQuantity)}</td>
                      <td className={`num font-semibold ${r.status === 0 ? "line-through text-gray-400" : ""}`}>{formatNumber(r.totalAmount)}</td>
                      <td>{r.status === 0 ? <Badge tone="red">Đã hủy</Badge> : <Badge tone="green">Hoàn tất</Badge>}</td>
                      <td className="max-w-[220px] truncate">{r.note}</td>
                    </tr>
                  ))
                ) : (
                  <tr>
                    <td colSpan={9} className="text-center text-gray-500 py-6">
                      Không có phiếu nhập phù hợp
                    </td>
                  </tr>
                )}
              </tbody>
              {receipts.length > 0 && (
                <tfoot>
                  <tr>
                    <td colSpan={5}>
                      Tổng {formatNumber(summary.count)} phiếu
                      {summary.cancelledCount ? ` (${summary.cancelledCount} đã hủy, không tính tiền)` : ""}
                    </td>
                    <td className="num">{formatNumber(summary.totalQuantity)}</td>
                    <td className="num">{formatNumber(summary.totalAmount)}</td>
                    <td colSpan={2}></td>
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
                <span className="text-gray-500">Nhà cung cấp</span>
                <span>{selected.supplierName ?? "-"}</span>
                <span className="text-gray-500">Trạng thái</span>
                <span className="flex items-center gap-2">
                  {selected.status === 0 ? <Badge tone="red">Đã hủy</Badge> : <Badge tone="green">Hoàn tất</Badge>}
                  {selected.status !== 0 && (
                    <button
                      type="button"
                      onClick={() => cancelReceipt(selected)}
                      className="rounded border border-red-300 px-2 py-[1px] text-[12px] text-red-700 hover:bg-red-50"
                    >
                      Hủy phiếu
                    </button>
                  )}
                </span>
                {selected.status === 0 && (
                  <>
                    <span className="text-gray-500">Lý do hủy</span>
                    <span className="text-red-700">
                      {selected.cancelReason} ({formatDateTime(selected.cancelledAt)})
                    </span>
                  </>
                )}
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
    </div>
  );
}

export default ImportReceipt;
