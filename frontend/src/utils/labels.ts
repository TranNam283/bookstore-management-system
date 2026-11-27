// Nhãn + màu dùng chung cho các trạng thái / loại chứng từ trong trang quản trị.

export type Tone = "gray" | "blue" | "indigo" | "green" | "amber" | "red" | "orange" | "teal";

export const EXPORT_TYPE: Record<string, { label: string; tone: Tone }> = {
  SALE: { label: "Xuất bán", tone: "blue" },
  DAMAGED: { label: "Hư hỏng / mất", tone: "red" },
  RETURN_SUPPLIER: { label: "Trả nhà cung cấp", tone: "orange" },
  OTHER: { label: "Xuất khác", tone: "gray" },
};

export const MOVEMENT_TYPE: Record<string, { label: string; tone: Tone }> = {
  OPENING: { label: "Tồn đầu kỳ", tone: "gray" },
  IMPORT: { label: "Nhập hàng", tone: "green" },
  SALE: { label: "Bán hàng", tone: "blue" },
  CANCEL_RETURN: { label: "Hủy / trả - hoàn kho", tone: "teal" },
  EXPORT: { label: "Xuất kho", tone: "orange" },
  IMPORT_CANCEL: { label: "Hủy phiếu nhập", tone: "red" },
  ADJUST: { label: "Điều chỉnh", tone: "indigo" },
};

// Vai trò tài khoản (cột user.role)
export const ROLE: Record<number, { label: string; short: string; tone: Tone; description: string }> = {
  0: {
    label: "Quản trị viên hệ thống",
    short: "Quản trị",
    tone: "red",
    description: "Toàn quyền: quản lý tài khoản nhân viên, khách hàng và mọi chức năng",
  },
  1: {
    label: "Nhân viên bán hàng",
    short: "Bán hàng",
    tone: "blue",
    description: "Xác nhận, xử lý hóa đơn; xem khách hàng",
  },
  2: {
    label: "Nhân viên kho",
    short: "Kho",
    tone: "teal",
    description: "Lập phiếu nhập / xuất, theo dõi tồn kho",
  },
  3: { label: "Khách hàng", short: "Khách", tone: "gray", description: "Mua hàng trên website" },
};

export const roleLabel = (role?: number | null) =>
  role === undefined || role === null ? "-" : (ROLE[role]?.label ?? "Không xác định");

export const PAYMETHOD_LABEL: Record<string, string> = {
  cod: "COD (tiền mặt)",
  momo: "Ví MoMo",
};

export const ORDER_STATUS_TONE: Record<number, Tone> = {
  [-1]: "gray",
  0: "amber",
  1: "blue",
  2: "indigo",
  3: "green",
  4: "red",
  5: "orange",
};

export const TONE_CLASS: Record<Tone, string> = {
  gray: "bg-gray-100 text-gray-700 ring-gray-300",
  blue: "bg-blue-50 text-blue-700 ring-blue-200",
  indigo: "bg-indigo-50 text-indigo-700 ring-indigo-200",
  green: "bg-emerald-50 text-emerald-700 ring-emerald-200",
  amber: "bg-amber-50 text-amber-800 ring-amber-200",
  red: "bg-red-50 text-red-700 ring-red-200",
  orange: "bg-orange-50 text-orange-700 ring-orange-200",
  teal: "bg-teal-50 text-teal-700 ring-teal-200",
};
