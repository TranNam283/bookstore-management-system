import axios from "axios";
import useSWR from "swr";
import type { CustomerDetail } from "../../../types/type";
import { ORDER_STATUS, formatDate, formatDateTime, formatMoney, formatNumber } from "../../../utils/format";
import { ORDER_STATUS_TONE } from "../../../utils/labels";
import Badge from "../ui/Badge";
import Loading from "../../Loading";
import OrderMiniList from "../order/OrderMiniList";

const fetcher = (url: string) => axios.get(url).then((r) => r.data);

/**
 * Hồ sơ khách hàng (sub form): thông tin tài khoản, sổ địa chỉ, sách mua nhiều, và từng hóa đơn
 * đã mua (bấm để xem các món, nhân viên xử lý hóa đơn).
 */
function CustomerDetailPanel({ customerId }: { customerId?: string }) {
  const { data, isLoading } = useSWR<CustomerDetail>(
    customerId ? `${import.meta.env.VITE_BACKEND_URL}/api/customer/${customerId}` : null,
    fetcher,
    { revalidateOnFocus: false },
  );

  if (!customerId) return <p className="py-8 text-center text-gray-500">Chọn một khách hàng để xem hồ sơ</p>;
  if (isLoading && !data) return <Loading height={60} size={40} color="black" thickness={2} />;
  if (!data) return null;
  const c = data.customer;
  const avg = c.deliveredCount ? c.totalSpent / c.deliveredCount : 0;

  return (
    <div className="space-y-3 p-3 text-[13px]">
      <div className="flex items-start justify-between gap-2">
        <div>
          <div className="text-[15px] font-semibold text-gray-900">{c.fullname}</div>
          <div className="text-gray-600">{c.email}</div>
        </div>
        {c.status === 1 ? <Badge tone="green">Bình thường</Badge> : <Badge tone="red">Bị chặn</Badge>}
      </div>

      <dl className="grid grid-cols-[130px_minmax(0,1fr)] gap-x-2 gap-y-0.5 rounded border border-gray-200 px-2 py-1.5 text-[12.5px]">
        <dt className="text-gray-500">Điện thoại</dt>
        <dd className="tabular-nums">{c.phone || "-"}</dd>
        <dt className="text-gray-500">Địa chỉ</dt>
        <dd>{c.address || "-"}</dd>
        <dt className="text-gray-500">Ngày tạo tài khoản</dt>
        <dd>{formatDateTime(c.createdAt)}</dd>
        <dt className="text-gray-500">Mua lần đầu</dt>
        <dd>{formatDate(c.firstOrderAt)}</dd>
        <dt className="text-gray-500">Mua gần nhất</dt>
        <dd>{formatDate(c.lastOrderAt)}</dd>
      </dl>

      <div className="grid grid-cols-3 gap-2">
        {[
          { label: "Tổng đơn", value: formatNumber(c.orderCount) },
          { label: "Giao thành công", value: formatNumber(c.deliveredCount) },
          { label: "Hủy / trả", value: formatNumber(c.cancelledCount ?? 0) },
          { label: "Tổng chi tiêu", value: formatMoney(c.totalSpent) },
          { label: "TB / đơn", value: formatMoney(avg) },
          { label: "Địa chỉ đã lưu", value: formatNumber(data.addresses.length) },
        ].map((k) => (
          <div key={k.label} className="rounded border border-gray-200 px-2 py-1">
            <div className="text-[11px] uppercase text-gray-500">{k.label}</div>
            <div className="font-semibold tabular-nums">{k.value}</div>
          </div>
        ))}
      </div>

      {Object.keys(data.ordersByStatus).length > 0 && (
        <div className="flex flex-wrap gap-1">
          {Object.entries(data.ordersByStatus).map(([status, count]) => (
            <Badge key={status} tone={ORDER_STATUS_TONE[Number(status)]}>
              {ORDER_STATUS[Number(status)]}: {count}
            </Badge>
          ))}
        </div>
      )}

      <div>
        <div className="mb-1 font-semibold text-gray-700">
          Hóa đơn đã mua ({data.orders.length}) <span className="font-normal text-[12px] text-gray-500">– bấm để xem từng món</span>
        </div>
        <div className="max-h-[420px] overflow-auto">
          <OrderMiniList orders={data.orders} context="customer" emptyText="Khách chưa mua hóa đơn nào" />
        </div>
      </div>

      {data.topBooks.length > 0 && (
        <div>
          <div className="mb-1 font-semibold text-gray-700">Sách mua nhiều nhất</div>
          <table className="admin-table">
            <thead>
              <tr>
                <th>Sách</th>
                <th className="num">SL</th>
                <th className="num">Thành tiền</th>
              </tr>
            </thead>
            <tbody>
              {data.topBooks.map((b) => (
                <tr key={b.title}>
                  <td className="wrap max-w-[220px]">{b.title}</td>
                  <td className="num">{b.quantity}</td>
                  <td className="num">{formatNumber(b.amount)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <div>
        <div className="mb-1 font-semibold text-gray-700">Sổ địa chỉ ({data.addresses.length})</div>
        {data.addresses.length ? (
          <ul className="space-y-1">
            {data.addresses.map((a, i) => (
              <li key={i} className="rounded border border-gray-200 px-2 py-1">
                <b>{a.fullname}</b> – {a.phone}
                <div className="text-gray-600">{[a.speaddress, a.ward, a.city].filter(Boolean).join(", ")}</div>
              </li>
            ))}
          </ul>
        ) : (
          <p className="text-[12px] text-gray-500">Chưa lưu địa chỉ</p>
        )}
      </div>
    </div>
  );
}

export default CustomerDetailPanel;
