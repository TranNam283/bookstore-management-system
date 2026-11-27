import { useState } from "react";
import { Link } from "react-router-dom";
import { IoChevronDown, IoChevronForward } from "react-icons/io5";
import type { Order } from "../../../types/type";
import { ORDER_STATUS, formatDateTime, formatMoney, formatNumber } from "../../../utils/format";
import { ORDER_STATUS_TONE, PAYMETHOD_LABEL } from "../../../utils/labels";
import Badge from "../ui/Badge";

type Props = {
  orders: Order[];
  /** "customer": hồ sơ khách (hiện nhân viên xử lý); "staff": hồ sơ nhân viên (hiện khách hàng) */
  context: "customer" | "staff";
  emptyText?: string;
};

/** Nhân viên xử lý: người xác nhận đơn, nếu không có thì người gần nhất đổi trạng thái đơn. */
const handlerOf = (o: Order) =>
  o.staffName ?? [...(o.history ?? [])].reverse().find((h) => h.staffName)?.staffName ?? null;

/** Danh sách hóa đơn gọn: bấm 1 hóa đơn để xem từng món (SL × đơn giá − giảm = thành tiền). */
function OrderMiniList({ orders, context, emptyText = "Chưa có hóa đơn" }: Props) {
  const [open, setOpen] = useState<string | null>(null);
  if (orders.length === 0) return <p className="py-4 text-center text-[12px] text-gray-500">{emptyText}</p>;

  return (
    <ul className="divide-y divide-gray-200 rounded border border-gray-200">
      {orders.map((o) => {
        const expanded = open === o.id;
        return (
          <li key={o.id} className={expanded ? "bg-[#f7f9fc]" : ""}>
            <button
              type="button"
              onClick={() => setOpen(expanded ? null : o.id)}
              className="flex w-full items-start gap-1.5 px-2 py-1.5 text-left hover:bg-gray-50"
            >
              <span className="mt-[3px] text-gray-500">
                {expanded ? <IoChevronDown size={12} /> : <IoChevronForward size={12} />}
              </span>
              <span className="min-w-0 flex-1">
                <span className="flex flex-wrap items-center gap-x-2 gap-y-0.5">
                  <span className="font-semibold text-blue-700">{o.orderCode}</span>
                  <Badge tone={ORDER_STATUS_TONE[o.status]}>{ORDER_STATUS[o.status]}</Badge>
                  <span className="ml-auto font-semibold tabular-nums">{formatMoney(o.total)}</span>
                </span>
                <span className="mt-0.5 block text-[11.5px] text-gray-600">
                  {formatDateTime(o.createdAt)} · {formatNumber(o.totalQuantity ?? o.items.length)} cuốn ·{" "}
                  {PAYMETHOD_LABEL[o.paymethod] ?? o.paymethod?.toUpperCase()}
                </span>
                <span className="block text-[11.5px] text-gray-600">
                  {context === "customer" ? (
                    <>
                      NV xử lý:{" "}
                      {handlerOf(o) ? (
                        <b className="text-gray-800">{handlerOf(o)}</b>
                      ) : (
                        <i className="text-gray-400">
                          {o.status <= 0
                            ? "chưa có – đơn đang chờ xác nhận"
                            : o.status === 4
                              ? "không có – đơn hủy trước khi nhân viên xác nhận"
                              : "không ghi nhận"}
                        </i>
                      )}
                      {o.confirmedAt && <> · xác nhận {formatDateTime(o.confirmedAt)}</>}
                    </>
                  ) : (
                    <>
                      Khách: <b className="text-gray-800">{o.fullname}</b> – {o.phone} · {o.city}
                    </>
                  )}
                </span>
              </span>
            </button>

            {expanded && (
              <div className="space-y-1 px-2 pb-2 pl-6">
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
                    {o.items.map((it, i) => (
                      <tr key={`${it.bookId}-${i}`}>
                        <td className="wrap max-w-[180px]">{it.title}</td>
                        <td className="num">{it.quantity}</td>
                        <td className="num">{formatNumber(it.price)}</td>
                        <td className="num">{it.discount ? formatNumber(it.discount) : "-"}</td>
                        <td className="num font-semibold">
                          {formatNumber(it.lineTotal ?? it.quantity * (it.price - (it.discount ?? 0)))}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                  <tfoot>
                    <tr>
                      <td colSpan={4}>
                        Tổng (trước giảm {formatNumber(o.subtotal)} − giảm {formatNumber(o.discountTotal)})
                      </td>
                      <td className="num">{formatNumber(o.total)}</td>
                    </tr>
                  </tfoot>
                </table>
                <div className="flex flex-wrap justify-between gap-2 text-[11.5px] text-gray-600">
                  <span>
                    Giao tới: {o.fullname} – {o.phone}, {[o.speaddress, o.ward, o.city].filter(Boolean).join(", ")}
                  </span>
                  <Link to={`/admin/orders?selected=${o.id}&q=${o.orderCode}`} className="text-blue-700 hover:underline">
                    Mở hóa đơn →
                  </Link>
                </div>
              </div>
            )}
          </li>
        );
      })}
    </ul>
  );
}

export default OrderMiniList;
