import { useState } from "react";
import axios from "axios";
import useSWR from "swr";
import { Link } from "react-router-dom";
import type { StaffDetail } from "../../../types/type";
import { ORDER_STATUS, formatDate, formatDateTime, formatMoney, formatNumber } from "../../../utils/format";
import { EXPORT_TYPE, ORDER_STATUS_TONE, ROLE } from "../../../utils/labels";
import Badge from "../ui/Badge";
import Loading from "../../Loading";
import OrderMiniList from "../order/OrderMiniList";

const fetcher = (url: string) => axios.get(url).then((r) => r.data);

type Tab = "orders" | "imports" | "exports";

/** Hồ sơ nhân viên (sub form): vai trò, số liệu công việc, hóa đơn đã xử lý, phiếu nhập / xuất. */
function StaffDetailPanel({ staffId }: { staffId?: string }) {
  const [tab, setTab] = useState<Tab>("orders");
  const { data, isLoading } = useSWR<StaffDetail>(
    staffId ? `${import.meta.env.VITE_BACKEND_URL}/api/staff/${staffId}` : null,
    fetcher,
    { revalidateOnFocus: false },
  );

  if (!staffId) return <p className="py-8 text-center text-gray-500">Chọn một nhân viên để xem hồ sơ</p>;
  if (isLoading && !data) return <Loading height={60} size={40} color="black" thickness={2} />;
  if (!data) return null;
  const s = data.staff;
  const role = ROLE[s.role];

  const tabs: { key: Tab; label: string; count: number }[] = [
    { key: "orders", label: "Hóa đơn đã xử lý", count: data.orders.length },
    { key: "imports", label: "Phiếu nhập", count: data.importReceipts.length },
    { key: "exports", label: "Phiếu xuất", count: data.exportReceipts.length },
  ];

  return (
    <div className="space-y-3 p-3 text-[13px]">
      <div className="flex items-start justify-between gap-2">
        <div>
          <div className="text-[15px] font-semibold text-gray-900">{s.fullname}</div>
          <div className="text-gray-600">{s.email}</div>
          <div className="text-[12px] text-gray-500">Tạo tài khoản ngày {formatDateTime(s.createdAt)}</div>
        </div>
        {s.status === 1 ? <Badge tone="green">Hoạt động</Badge> : <Badge tone="red">Bị khóa</Badge>}
      </div>

      <div className="rounded border border-gray-200 px-2 py-1.5">
        <div className="flex items-center gap-2">
          <span className="text-[11px] uppercase text-gray-500">Vai trò</span>
          <Badge tone={role?.tone ?? "gray"}>{s.roleName}</Badge>
        </div>
        {role && <div className="mt-0.5 text-[12px] text-gray-600">{role.description}</div>}
      </div>

      <div className="grid grid-cols-3 gap-2">
        {[
          { label: "HĐ xử lý", value: formatNumber(s.confirmedCount) },
          { label: "Giao thành công", value: formatNumber(s.deliveredCount) },
          { label: "Hủy / trả", value: formatNumber(s.cancelledCount) },
          { label: "Doanh thu", value: formatMoney(s.revenue) },
          { label: "Phiếu nhập", value: `${formatNumber(s.importReceiptCount)} · ${formatMoney(s.importAmount)}` },
          { label: "Phiếu xuất", value: formatNumber(s.exportReceiptCount) },
        ].map((k) => (
          <div key={k.label} className="rounded border border-gray-200 px-2 py-1">
            <div className="text-[11px] uppercase text-gray-500">{k.label}</div>
            <div className="font-semibold tabular-nums">{k.value}</div>
          </div>
        ))}
      </div>
      <div className="text-[12px] text-gray-500">Hoạt động gần nhất: {formatDateTime(s.lastActivityAt)}</div>

      {Object.keys(data.ordersByStatus).length > 0 && (
        <div className="flex flex-wrap gap-1">
          {Object.entries(data.ordersByStatus).map(([status, count]) => (
            <Badge key={status} tone={ORDER_STATUS_TONE[Number(status)]}>
              {ORDER_STATUS[Number(status)]}: {count}
            </Badge>
          ))}
        </div>
      )}

      <div className="flex border-b border-gray-200">
        {tabs.map((t) => (
          <button
            key={t.key}
            type="button"
            onClick={() => setTab(t.key)}
            className={`-mb-px border-b-2 px-2 py-1 ${
              tab === t.key ? "border-[#C62028] font-semibold text-gray-900" : "border-transparent text-gray-600"
            }`}
          >
            {t.label} ({t.count})
          </button>
        ))}
      </div>

      <div className="max-h-[420px] overflow-auto">
        {tab === "orders" && (
          <OrderMiniList orders={data.orders} context="staff" emptyText="Nhân viên chưa xử lý hóa đơn nào" />
        )}
        {tab === "imports" &&
          (data.importReceipts.length ? (
            <table className="admin-table">
              <thead>
                <tr>
                  <th>Mã phiếu</th>
                  <th>Ngày lập</th>
                  <th>Nhà cung cấp</th>
                  <th className="num">SL</th>
                  <th className="num">Tổng tiền</th>
                </tr>
              </thead>
              <tbody>
                {data.importReceipts.map((r) => (
                  <tr key={r.id}>
                    <td>
                      <Link to={`/admin/import-receipts?selected=${r.id}&q=${r.receiptCode}`} className="font-semibold text-blue-700 hover:underline">
                        {r.receiptCode}
                      </Link>
                      {r.status === 0 && <span className="ml-1"><Badge tone="red">Đã hủy</Badge></span>}
                    </td>
                    <td>{formatDate(r.createdAt)}</td>
                    <td className="wrap max-w-[140px]">{r.supplierName ?? r.publisherName ?? "-"}</td>
                    <td className="num">{formatNumber(r.totalQuantity)}</td>
                    <td className="num">{formatNumber(r.totalAmount)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          ) : (
            <p className="py-4 text-center text-[12px] text-gray-500">Chưa lập phiếu nhập nào</p>
          ))}
        {tab === "exports" &&
          (data.exportReceipts.length ? (
            <table className="admin-table">
              <thead>
                <tr>
                  <th>Mã phiếu</th>
                  <th>Ngày lập</th>
                  <th>Loại</th>
                  <th className="num">SL</th>
                  <th className="num">Giá trị</th>
                </tr>
              </thead>
              <tbody>
                {data.exportReceipts.map((r) => (
                  <tr key={r.id}>
                    <td className="font-semibold">{r.receiptCode}</td>
                    <td>{formatDate(r.createdAt)}</td>
                    <td>
                      <Badge tone={EXPORT_TYPE[r.type]?.tone}>{EXPORT_TYPE[r.type]?.label ?? r.type}</Badge>
                      {r.orderCode && <span className="ml-1 text-[11.5px] text-gray-500">{r.orderCode}</span>}
                    </td>
                    <td className="num">{formatNumber(r.totalQuantity)}</td>
                    <td className="num">{formatNumber(r.totalAmount)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          ) : (
            <p className="py-4 text-center text-[12px] text-gray-500">Chưa lập phiếu xuất nào</p>
          ))}
      </div>
    </div>
  );
}

export default StaffDetailPanel;
