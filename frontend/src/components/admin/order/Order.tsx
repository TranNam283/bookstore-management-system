import { useCallback, useEffect, useMemo, useRef } from "react";
import { useSearchParams } from "react-router-dom";
import toast from "react-hot-toast";
import Image from "../../Image";
import Pagination from "../Pagination";
import InputSearch from "../InputSearch";
import Loading from "../../Loading";
import SortableTh from "../SortableTh";
import AdvancedSearchPanel from "../AdvancedSearchPanel";
import useAdvancedSearch, { useAdvancedSearchState } from "../../../hooks/admin/useAdvancedSearch";
import { useOrderSearchOptions, useOrderStatusCount } from "../../../hooks/admin/useSearchOptions";
import useUpdateStatusOrder from "../../../hooks/admin/useUpdateStatusOrder";
import type { Order as OrderType, SearchCondition, SearchField } from "../../../types/type";
import {
  ORDER_STATUS,
  ORDER_STATUS_COLOR,
  formatDateTime,
  formatMoney,
  formatNumber,
} from "../../../utils/format";

// trạng thái kế tiếp được phép chọn
const NEXT_STATUS: Record<number, number[]> = {
  [-1]: [],
  0: [1, 4],
  1: [2, 4],
  2: [3, 4],
  3: [5],
  4: [],
  5: [],
};

const STATUS_FILTERS: { label: string; value: string | null }[] = [
  { label: "Tất cả", value: null },
  { label: "Chờ thanh toán", value: "-1" },
  { label: "Chờ xác nhận", value: "0" },
  { label: "Xác nhận", value: "1" },
  { label: "Đang giao", value: "2" },
  { label: "Giao thành công", value: "3" },
  { label: "Đã hủy", value: "4" },
  { label: "Trả hàng", value: "5" },
];

function Order() {
  const [searchParams, setSearchParams] = useSearchParams();
  const statusParam = searchParams.get("status");
  const selectedId = searchParams.get("selected");

  const quickConditions: SearchCondition[] = useMemo(
    () => (statusParam !== null ? [{ field: "status", operator: "=", value: statusParam }] : []),
    [statusParam],
  );
  const { request, groups, applyGroups, sortBy, sortDir, toggleSort, page, limit } =
    useAdvancedSearchState(20, quickConditions);
  const { items: orders, total, totalPages, summary, isLoading, mutate } =
    useAdvancedSearch<OrderType>("/api/order/search", request);
  const { counts, mutate: mutateCounts } = useOrderStatusCount();
  const { cities, paymethods, staffs } = useOrderSearchOptions();
  const { updateStatusOrder, isLoading: isUpdating } = useUpdateStatusOrder();

  const fields: SearchField[] = useMemo(
    () => [
      { key: "total", label: "Tổng tiền", type: "number" },
      { key: "subtotal", label: "Tiền trước giảm", type: "number" },
      { key: "discountTotal", label: "Tiền giảm", type: "number" },
      { key: "totalQuantity", label: "Số cuốn", type: "number" },
      { key: "createdAt", label: "Ngày đặt", type: "date" },
      { key: "confirmedAt", label: "Ngày xác nhận", type: "date" },
      {
        key: "city",
        label: "Thành phố",
        type: "select",
        options: cities.map((c) => ({ value: c, label: c })),
      },
      {
        key: "status",
        label: "Trạng thái",
        type: "select",
        options: Object.entries(ORDER_STATUS).map(([v, l]) => ({ value: v, label: l })),
      },
      {
        key: "paymethod",
        label: "Thanh toán",
        type: "select",
        options: paymethods.map((p) => ({ value: p, label: p.toUpperCase() })),
      },
      {
        key: "staffId",
        label: "Nhân viên xác nhận",
        type: "select",
        options: staffs.map((s) => ({ value: s.id, label: s.name })),
      },
      { key: "orderCode", label: "Mã đơn", type: "text" },
      { key: "fullname", label: "Người nhận", type: "text" },
      { key: "accountEmail", label: "Email tài khoản", type: "text" },
      { key: "bookTitle", label: "Tên sách trong đơn", type: "text" },
    ],
    [cities, paymethods, staffs],
  );

  const presets = useMemo(
    () => [
      {
        label: "Tổng tiền 1tr – 2tr",
        groups: [
          {
            logic: "AND" as const,
            conditions: [
              { field: "total", operator: ">=" as const, value: "1000000" },
              { field: "total", operator: "<=" as const, value: "2000000" },
            ],
          },
        ],
      },
      {
        label: "TP.HCM hoặc Hà Nội",
        groups: [
          {
            logic: "OR" as const,
            conditions: [
              { field: "city", operator: "=" as const, value: "Hồ Chí Minh" },
              { field: "city", operator: "=" as const, value: "Hà Nội" },
            ],
          },
        ],
      },
      {
        label: "(1tr–2tr) AND (HCM OR HN)",
        groups: [
          {
            logic: "AND" as const,
            conditions: [
              { field: "total", operator: ">=" as const, value: "1000000" },
              { field: "total", operator: "<=" as const, value: "2000000" },
            ],
          },
          {
            logic: "OR" as const,
            conditions: [
              { field: "city", operator: "=" as const, value: "Hồ Chí Minh" },
              { field: "city", operator: "=" as const, value: "Hà Nội" },
            ],
          },
        ],
      },
    ],
    [],
  );

  // chọn hóa đơn: lưu trên URL, mặc định chọn dòng đầu tiên
  const selected = orders.find((o) => o.id === selectedId) ?? orders[0];
  const select = useCallback(
    (id: string) => {
      const params = new URLSearchParams(searchParams.toString());
      params.set("selected", id);
      setSearchParams(params, { replace: true });
    },
    [searchParams, setSearchParams],
  );

  // phím ↑ / ↓ để chuyển hóa đơn
  const rowRefs = useRef<Record<string, HTMLTableRowElement | null>>({});
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const tag = (e.target as HTMLElement)?.tagName;
      if (["INPUT", "SELECT", "TEXTAREA"].includes(tag)) return;
      if (e.key !== "ArrowDown" && e.key !== "ArrowUp") return;
      if (!orders.length) return;
      e.preventDefault();
      const index = Math.max(0, orders.findIndex((o) => o.id === selected?.id));
      const next = e.key === "ArrowDown" ? Math.min(index + 1, orders.length - 1) : Math.max(index - 1, 0);
      select(orders[next].id);
      rowRefs.current[orders[next].id]?.scrollIntoView({ block: "nearest" });
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [orders, selected, select]);

  const setStatusFilter = (value: string | null) => {
    const params = new URLSearchParams(searchParams.toString());
    if (value === null) params.delete("status");
    else params.set("status", value);
    params.set("page", "1");
    params.delete("selected");
    setSearchParams(params);
  };

  const handleUpdateStatus = async (id: string, status: number) => {
    try {
      const done = await updateStatusOrder(id, status);
      if (done) {
        await Promise.all([mutate(), mutateCounts()]);
      }
    } catch (err: any) {
      toast.error(err?.response?.data?.message || "Cập nhật thất bại");
    }
  };

  const countOf = (value: string | null) =>
    value === null
      ? Object.values(counts).reduce((s, n) => s + n, 0)
      : (counts[value] ?? 0);

  return (
    <div className="bg-[#f1f4f9] p-2 space-y-2">
      <div className="flex flex-wrap items-center gap-2">
        <h2 className="text-[15px] font-semibold text-gray-700 mr-2">Hóa đơn ({formatNumber(total)})</h2>
        {STATUS_FILTERS.map((s) => (
          <button
            key={s.label}
            type="button"
            onClick={() => setStatusFilter(s.value)}
            className={`text-[12px] border px-2 py-[2px] ${
              statusParam === s.value ? "bg-[#C62028] text-white border-[#C62028]" : "bg-white border-gray-300 hover:bg-gray-50"
            }`}
          >
            {s.label} <b>{formatNumber(countOf(s.value))}</b>
          </button>
        ))}
        <div className="ml-auto">
          <InputSearch />
        </div>
      </div>

      <AdvancedSearchPanel fields={fields} value={groups} onApply={applyGroups} presets={presets} />

      <div className="grid grid-cols-1 xl:grid-cols-[minmax(0,1fr)_460px] gap-2">
        {/* MAIN: danh sách hóa đơn */}
        <div className="bg-white border border-gray-300 flex flex-col min-h-[300px]">
          <div className="overflow-auto max-h-[calc(100vh-260px)]">
            <table className="admin-table">
              <thead>
                <tr>
                  <th>#</th>
                  <SortableTh field="orderCode" label="Mã đơn" sortBy={sortBy} sortDir={sortDir} onSort={toggleSort} />
                  <SortableTh field="createdAt" label="Ngày đặt" sortBy={sortBy} sortDir={sortDir} onSort={toggleSort} />
                  <SortableTh field="fullname" label="Người nhận" sortBy={sortBy} sortDir={sortDir} onSort={toggleSort} />
                  <SortableTh field="city" label="Thành phố" sortBy={sortBy} sortDir={sortDir} onSort={toggleSort} />
                  <th>TT</th>
                  <SortableTh field="totalQuantity" label="SL" sortBy={sortBy} sortDir={sortDir} onSort={toggleSort} className="num" />
                  <SortableTh field="subtotal" label="Trước giảm" sortBy={sortBy} sortDir={sortDir} onSort={toggleSort} className="num" />
                  <SortableTh field="discountTotal" label="Giảm" sortBy={sortBy} sortDir={sortDir} onSort={toggleSort} className="num" />
                  <SortableTh field="total" label="Tổng tiền" sortBy={sortBy} sortDir={sortDir} onSort={toggleSort} className="num" />
                  <SortableTh field="staffName" label="NV xác nhận" sortBy={sortBy} sortDir={sortDir} onSort={toggleSort} />
                  <SortableTh field="status" label="Trạng thái" sortBy={sortBy} sortDir={sortDir} onSort={toggleSort} />
                </tr>
              </thead>
              <tbody>
                {isLoading && orders.length === 0 ? (
                  <tr>
                    <td colSpan={12}>
                      <Loading height={60} size={40} color="black" thickness={2} />
                    </td>
                  </tr>
                ) : orders.length > 0 ? (
                  orders.map((order, index) => (
                    <tr
                      key={order.id}
                      ref={(el) => {
                        rowRefs.current[order.id] = el;
                      }}
                      onClick={() => select(order.id)}
                      className={`cursor-pointer ${selected?.id === order.id ? "is-selected" : ""}`}
                    >
                      <td className="text-gray-400">{(page - 1) * limit + index + 1}</td>
                      <td className="font-semibold">{order.orderCode}</td>
                      <td>{formatDateTime(order.createdAt)}</td>
                      <td>{order.fullname}</td>
                      <td>{order.city}</td>
                      <td className="uppercase">{order.paymethod}</td>
                      <td className="num">{order.totalQuantity}</td>
                      <td className="num">{formatNumber(order.subtotal)}</td>
                      <td className="num text-red-600">{order.discountTotal ? `-${formatNumber(order.discountTotal)}` : "0"}</td>
                      <td className="num font-semibold">{formatNumber(order.total)}</td>
                      <td>{order.staffName ?? <span className="text-gray-400">-</span>}</td>
                      <td className={ORDER_STATUS_COLOR[order.status]}>{ORDER_STATUS[order.status]}</td>
                    </tr>
                  ))
                ) : (
                  <tr>
                    <td colSpan={12} className="text-center text-gray-500 py-6">
                      Không có hóa đơn phù hợp
                    </td>
                  </tr>
                )}
              </tbody>
              {orders.length > 0 && (
                <tfoot>
                  <tr>
                    <td colSpan={6}>Tổng {formatNumber(summary.count)} hóa đơn (theo bộ lọc)</td>
                    <td className="num">{formatNumber(summary.totalQuantity)}</td>
                    <td className="num">{formatNumber(summary.subtotal)}</td>
                    <td className="num">{summary.discountTotal ? `-${formatNumber(summary.discountTotal)}` : "0"}</td>
                    <td className="num">{formatNumber(summary.total)}</td>
                    <td colSpan={2}></td>
                  </tr>
                </tfoot>
              )}
            </table>
          </div>
          <Pagination totalPages={totalPages} currentPage={page} limit={limit} totalItems={total} />
        </div>

        {/* SUB: chi tiết hóa đơn đang chọn */}
        <aside className="bg-white border border-gray-300 text-[13px] xl:sticky xl:top-[60px] self-start">
          <div className="px-3 py-2 border-b border-gray-200 bg-[#f7f8fa] flex items-center justify-between">
            <b>Chi tiết hóa đơn {selected ? `#${selected.orderCode}` : ""}</b>
            <span className="text-[11px] text-gray-500">↑/↓ để chuyển hóa đơn</span>
          </div>
          {selected ? (
            <div className="p-3 space-y-2">
              <div className="grid grid-cols-[110px_1fr] gap-y-[3px]">
                <span className="text-gray-500">Ngày đặt</span>
                <span>{formatDateTime(selected.createdAt)}</span>
                <span className="text-gray-500">Người nhận</span>
                <span>
                  {selected.fullname} – {selected.phone}
                </span>
                <span className="text-gray-500">Tài khoản</span>
                <span>{selected.accountEmail || "-"}</span>
                <span className="text-gray-500">Địa chỉ</span>
                <span>{[selected.speaddress, selected.ward, selected.city].filter(Boolean).join(", ")}</span>
                <span className="text-gray-500">Thanh toán</span>
                <span className="uppercase">{selected.paymethod}</span>
                <span className="text-gray-500">NV xác nhận</span>
                <span>
                  {selected.staffName ?? "-"}
                  {selected.confirmedAt ? ` (${formatDateTime(selected.confirmedAt)})` : ""}
                </span>
                <span className="text-gray-500">Trạng thái</span>
                <span className="flex items-center gap-2">
                  <b className={ORDER_STATUS_COLOR[selected.status]}>{ORDER_STATUS[selected.status]}</b>
                  {NEXT_STATUS[selected.status]?.length > 0 && (
                    <select
                      disabled={isUpdating}
                      value=""
                      onChange={(e) => e.target.value && handleUpdateStatus(selected.id, Number(e.target.value))}
                      className="border border-gray-300 px-1 py-[2px] text-[12px]"
                    >
                      <option value="">Chuyển sang...</option>
                      {NEXT_STATUS[selected.status].map((s) => (
                        <option key={s} value={s}>
                          {ORDER_STATUS[s]}
                        </option>
                      ))}
                    </select>
                  )}
                </span>
              </div>

              <table className="admin-table">
                <thead>
                  <tr>
                    <th>Sách</th>
                    <th className="num">SL</th>
                    <th className="num">Đơn giá</th>
                    <th className="num">Giảm/cuốn</th>
                    <th className="num">Thành tiền</th>
                  </tr>
                </thead>
                <tbody>
                  {selected.items.map((item) => (
                    <tr key={item.bookId}>
                      <td className="wrap">
                        <div className="flex items-center gap-1.5">
                          {item.images?.[0] && (
                            <Image
                              source={`${import.meta.env.VITE_BACKEND_URL}${item.images[0]}`}
                              alt=""
                              className="w-8 h-8"
                              loading="lazy"
                            />
                          )}
                          <span className="max-w-[150px]">{item.title}</span>
                        </div>
                      </td>
                      <td className="num">{item.quantity}</td>
                      <td className="num">{formatNumber(item.price)}</td>
                      <td className="num">{formatNumber(item.discount)}</td>
                      <td className="num font-semibold">
                        {formatNumber(item.lineTotal ?? item.quantity * (item.price - item.discount))}
                      </td>
                    </tr>
                  ))}
                </tbody>
                <tfoot>
                  <tr>
                    <td colSpan={4}>Tiền trước giảm (SL × Đơn giá)</td>
                    <td className="num">{formatMoney(selected.subtotal)}</td>
                  </tr>
                  <tr>
                    <td colSpan={4}>Giảm giá</td>
                    <td className="num text-red-600">{selected.discountTotal ? `-${formatMoney(selected.discountTotal)}` : formatMoney(0)}</td>
                  </tr>
                  <tr>
                    <td colSpan={4}>Tổng thanh toán</td>
                    <td className="num text-[#C62028]">{formatMoney(selected.total)}</td>
                  </tr>
                </tfoot>
              </table>
            </div>
          ) : (
            <p className="text-gray-500 py-8 text-center">Chọn một hóa đơn để xem chi tiết</p>
          )}
        </aside>
      </div>
    </div>
  );
}

export default Order;
