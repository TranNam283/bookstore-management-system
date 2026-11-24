export const formatMoney = (value?: number | null) =>
  `${Math.round(value ?? 0).toLocaleString("vi-VN")}₫`;

export const formatNumber = (value?: number | null) =>
  (value ?? 0).toLocaleString("vi-VN");

/** Rút gọn số tiền cho trục biểu đồ: 1.500.000 -> 1,5tr */
export const formatShortMoney = (value: number) => {
  const abs = Math.abs(value);
  if (abs >= 1_000_000_000) return `${(value / 1_000_000_000).toLocaleString("vi-VN", { maximumFractionDigits: 1 })}tỷ`;
  if (abs >= 1_000_000) return `${(value / 1_000_000).toLocaleString("vi-VN", { maximumFractionDigits: 1 })}tr`;
  if (abs >= 1_000) return `${(value / 1_000).toLocaleString("vi-VN", { maximumFractionDigits: 0 })}k`;
  return String(value);
};

export const formatDate = (value?: string | null) =>
  value
    ? new Date(value).toLocaleDateString("vi-VN", {
        year: "numeric",
        month: "2-digit",
        day: "2-digit",
      })
    : "-";

export const formatDateTime = (value?: string | null) =>
  value
    ? new Date(value).toLocaleString("vi-VN", {
        year: "numeric",
        month: "2-digit",
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit",
      })
    : "-";

/** yyyy-MM-dd theo giờ máy */
export const toISODate = (date: Date) => {
  const p = (n: number) => String(n).padStart(2, "0");
  return `${date.getFullYear()}-${p(date.getMonth() + 1)}-${p(date.getDate())}`;
};

export const ORDER_STATUS: Record<number, string> = {
  [-1]: "Chờ thanh toán",
  0: "Chờ xác nhận",
  1: "Xác nhận",
  2: "Đang giao",
  3: "Giao thành công",
  4: "Đã hủy",
  5: "Trả hàng",
};

export const ORDER_STATUS_COLOR: Record<number, string> = {
  [-1]: "text-gray-500",
  0: "text-amber-600",
  1: "text-blue-600",
  2: "text-indigo-600",
  3: "text-emerald-700",
  4: "text-red-600",
  5: "text-orange-600",
};
