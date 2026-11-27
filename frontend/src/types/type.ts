export interface User {
  id?: string;
  fullname: string;
  email: string;
  role?: number;
  password?: string;
  status?: number;
}

export interface Category {
  id?: string;
  name: string;
  slug?: string;
  status: number;
}

export interface Author {
  id?: string;
  fullname: string;
  slug?: string;
}

export interface Publisher {
  id?: string;
  name: string;
  slug?: string;
}

export interface Book {
  id: string;
  images: ImageBook[];
  title: string;
  price: number;
  discount: number;
  description: string;
  publicationDate: string;
  numberOfPages: number;
  weight: number;
  width: number;
  length: number;
  thickness: number;
  slug: string;
  status: number;
  stock: number;
  category: Category;
  author: Author;
  publisher: Publisher;
  totalSold?: number;
  createdAt?: string;
}

export interface ImageBook {
  id: string;
  image: string;
}

export interface Address {
  id?: string;
  fullname: string;
  phone: string;
  speaddress: string;
  ward: string;
  city: string;
  user?: string;
}

export interface Order {
  id: string;
  orderCode: string;
  userId: string;
  fullname: string;
  phone: string;
  speaddress: string;
  city: string;
  ward: string;
  accountEmail: string;
  paymethod: string;
  items: ProductBuy[];
  status: number;
  total: number; // tổng thanh toán (sau giảm)
  subtotal?: number; // tổng tiền trước giảm
  discountTotal?: number; // tổng tiền giảm
  totalQuantity?: number;
  staffId?: string | null; // nhân viên xác nhận đơn
  staffName?: string | null;
  confirmedAt?: string | null;
  history?: OrderStatusHistory[];
  exportReceiptCode?: string | null;
  createdAt: string;
}

export interface OrderStatusHistory {
  fromStatus: number | null;
  toStatus: number;
  staffName?: string | null;
  note?: string | null;
  createdAt: string;
}

export interface ProductBuy {
  id: string;
  bookId: string;
  title: string;
  images: string[];
  quantity: number;
  price: number;
  discount: number;
  subtotal?: number; // quantity * price
  discountAmount?: number; // quantity * discount
  lineTotal?: number; // thành tiền
}

export interface OrderAdd {
  fullname: string;
  phone: string;
  speaddress: string;
  city: string;
  ward: string;
  paymethod: string;
  items: {
    bookId: string;
    quantity: number;
    price: number;
    discount: number;
  }[];
}

export interface Payment {
  id?: string;
  orderId: string;
  orderCode: string;
  paymethod: string;
  amount: number;
  transactionId: string;
  status: number;
  createdAt: string;
}

export interface ProductInCart {
  id: string;
  bookId: string;
  title: string;
  images: string[];
  price: number;
  discount: number;
  slug: string;
  name:string;
  quantity: number;
  stock: number;
}

export interface Cart {
  id?: string;
  user?: string;
  items: ProductInCart[];
}

export type Ward = {
  name: string;
  mergedFrom: string[];
};

export type Province = {
  id: string;
  province: string;
  wards: Ward[];
};

// ---------------------------------------------------------------- phiếu nhập
export interface ImportReceiptItem {
  bookId: string;
  title?: string;
  image?: string | null;
  quantity: number;
  importPrice: number;
  lineTotal?: number;
}

export interface ImportReceipt {
  id: string;
  receiptCode: string;
  supplierId?: string | null;
  supplierName?: string | null;
  status?: number; // 1 hoàn tất, 0 đã hủy
  cancelledAt?: string | null;
  cancelReason?: string | null;
  publisherId?: string | null;
  publisherName?: string | null;
  staffId?: string | null;
  staffName?: string | null;
  note?: string | null;
  totalQuantity: number;
  totalAmount: number;
  createdAt: string;
  items: ImportReceiptItem[];
}

// ---------------------------------------------------------------- khách hàng (admin)
export interface CustomerSummary {
  id: string;
  fullname: string;
  email: string;
  phone?: string | null;
  address?: string | null;
  status: number;
  createdAt: string;
  cities: string[];
  orderCount: number;
  deliveredCount: number;
  cancelledCount?: number;
  totalSpent: number;
  firstOrderAt?: string | null;
  lastOrderAt?: string | null;
}

// ---------------------------------------------------------------- nhân viên (admin)
export interface StaffSummary {
  id: string;
  fullname: string;
  email: string;
  role: number; // 0 quản trị, 1 bán hàng, 2 kho
  roleName: string;
  status: number;
  createdAt: string;
  confirmedCount: number; // hóa đơn đã xác nhận / xử lý
  deliveredCount: number;
  cancelledCount: number;
  revenue: number; // tổng tiền hóa đơn giao thành công
  importReceiptCount: number;
  importAmount: number;
  exportReceiptCount: number;
  lastActivityAt?: string | null;
}

// ---------------------------------------------------------------- tìm kiếm nâng cao
export type SearchOperator = "=" | "!=" | ">" | "<" | ">=" | "<=" | "contains";

export interface SearchCondition {
  field: string;
  operator: SearchOperator;
  value: string;
}

export interface SearchGroup {
  logic: "AND" | "OR";
  conditions: SearchCondition[];
}

export interface AdvancedSearchRequest {
  keyword?: string;
  groups: SearchGroup[];
  page: number;
  limit: number;
  sortBy?: string;
  sortDir?: "asc" | "desc";
}

export interface SearchResponse<T> {
  items: T[];
  total: number;
  totalPages: number;
  page: number;
  limit: number;
  summary: Record<string, number>;
  cachedAt?: string | null;
}

export interface SearchField {
  key: string;
  label: string;
  type: "number" | "date" | "text" | "select";
  options?: { value: string; label: string }[];
}

// ---------------------------------------------------------------- kho hàng
export interface Supplier {
  id: string;
  code: string;
  name: string;
  contactName?: string | null;
  phone?: string | null;
  email?: string | null;
  address?: string | null;
  taxCode?: string | null;
  status: number; // 1 đang hợp tác, 0 ngừng
  createdAt?: string;
  receiptCount?: number;
  totalImported?: number;
  lastImportAt?: string | null;
}

export type ExportType = "SALE" | "DAMAGED" | "RETURN_SUPPLIER" | "OTHER";

export interface ExportReceiptItem {
  bookId: string;
  title?: string;
  image?: string | null;
  quantity: number;
  unitPrice?: number;
  lineTotal?: number;
}

export interface ExportReceipt {
  id: string;
  receiptCode: string;
  type: ExportType;
  orderId?: string | null;
  orderCode?: string | null;
  supplierId?: string | null;
  supplierName?: string | null;
  staffName?: string | null;
  reason?: string | null;
  totalQuantity: number;
  totalAmount: number;
  createdAt: string;
  items: ExportReceiptItem[];
}

export interface StockMovement {
  id: string;
  bookId: string;
  bookTitle: string;
  type: string;
  quantityChange: number;
  stockAfter: number;
  refType?: string | null;
  refId?: string | null;
  refCode?: string | null;
  staffName?: string | null;
  note?: string | null;
  createdAt: string;
}

export interface CustomerDetail {
  customer: CustomerSummary;
  addresses: { fullname: string; phone: string; speaddress: string; ward: string; city: string }[];
  orders: Order[];
  ordersByStatus: Record<string, number>;
  topBooks: { title: string; quantity: number; amount: number }[];
}

export interface StaffDetail {
  staff: StaffSummary;
  orders: Order[];
  ordersByStatus: Record<string, number>;
  importReceipts: ImportReceipt[];
  exportReceipts: ExportReceipt[];
}
