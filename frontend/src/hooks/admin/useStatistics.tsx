import axios from "axios";
import useSWR from "swr";

export type GroupBy = "day" | "week" | "month" | "quarter" | "year";

export type PeriodRow = {
  period: string;
  label: string;
  orderCount: number;
  totalQuantity?: number;
  subtotal?: number;
  discountTotal?: number;
  revenue: number;
  receiptCount?: number;
  importCost?: number;
  profit?: number;
};

export type StaffRow = {
  staffId: string | null;
  staffName: string;
  confirmedCount: number;
  deliveredCount: number;
  cancelledCount: number;
  totalQuantity: number;
  revenue: number;
  averageOrderValue: number;
};

export type StatisticsResult<R> = {
  from: string;
  to: string;
  groupBy?: GroupBy;
  rows: R[];
  summary: Record<string, number>;
  best: R | null;
  cachedAt?: string | null;
};

const fetcher = (url: string) => axios.get(url).then((res) => res.data);

/** Gọi API thống kê (backend tính trên danh sách RAM). */
export default function useStatistics<R>(
  endpoint: "revenue" | "profit" | "by-staff",
  params: { from?: string; to?: string; groupBy?: GroupBy },
) {
  const query = new URLSearchParams();
  if (params.from) query.set("from", params.from);
  if (params.to) query.set("to", params.to);
  if (params.groupBy && endpoint !== "by-staff") query.set("groupBy", params.groupBy);
  const url = `${import.meta.env.VITE_BACKEND_URL}/api/statistics/${endpoint}?${query.toString()}`;

  const { data, error, isLoading } = useSWR<StatisticsResult<R>>(url, fetcher, {
    revalidateOnFocus: false,
    shouldRetryOnError: false,
    keepPreviousData: true,
  });

  return { data, error, isLoading };
}

// ---------------------------------------------------------------- hàng bán chạy

export type TopBookRow = {
  rank: number;
  bookId: string;
  title: string;
  image?: string | null;
  category: string;
  author?: string | null;
  price?: number | null;
  stock?: number | null;
  quantity: number;
  revenue: number;
  orderCount: number;
  share: number;
  previousQuantity: number;
  change: number;
};

export type TopCategoryRow = {
  rank: number;
  name: string;
  quantity: number;
  revenue: number;
  bookCount: number;
  orderCount: number;
  share: number;
  topBook?: string | null;
  previousQuantity: number;
  change: number;
};

export type SalesSummary = {
  quantity: number;
  revenue: number;
  orderCount: number;
  bookCount: number;
};

export type TopBooksResult = {
  period: GroupBy;
  from: string;
  to: string;
  label: string;
  previousFrom: string;
  previousTo: string;
  previousLabel: string;
  deliveredOnly: boolean;
  books: TopBookRow[];
  categories: TopCategoryRow[];
  summary: SalesSummary & { previousQuantity: number; previousRevenue: number };
  cachedAt?: string | null;
};

export type BestsellerPeriodRow = SalesSummary & {
  period: string;
  label: string;
  from: string;
  to: string;
  topBook?: string | null;
  topCategory?: string | null;
  books: TopBookRow[];
  categories: TopCategoryRow[];
};

export type BestsellersResult = {
  from: string;
  to: string;
  groupBy: GroupBy;
  rows: BestsellerPeriodRow[];
  overall: SalesSummary & { label: string; books: TopBookRow[]; categories: TopCategoryRow[] };
  best: BestsellerPeriodRow | null;
};

type SeasonalBase = {
  rank: number;
  byYear: Record<string, number>;
  quantity: number;
  revenue: number;
  forecast: number;
  trend: number | null;
};

export type SeasonalBookRow = SeasonalBase & {
  bookId: string;
  title: string;
  image?: string | null;
  category: string;
  stock: number;
  suggestedImport: number;
};

export type SeasonalCategoryRow = SeasonalBase & { name: string; bookCount: number; share: number };

export type SeasonalResult = {
  unit: "quarter" | "month";
  index: number;
  label: string;
  years: { year: number; label: string; from: string; to: string; partial: boolean; quantity: number; revenue: number }[];
  books: SeasonalBookRow[];
  categories: SeasonalCategoryRow[];
  summary: SalesSummary & { forecastQuantity: number; suggestedImport: number; forecastYears: number[] };
};

/** GET /api/statistics/{path}?params - bỏ qua tham số rỗng. */
export function useStatisticsApi<T>(
  path: "top-books" | "bestsellers" | "seasonal",
  params: Record<string, string | number | boolean | undefined | null>,
) {
  const query = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== "") query.set(k, String(v));
  });
  const url = `${import.meta.env.VITE_BACKEND_URL}/api/statistics/${path}?${query.toString()}`;
  const { data, error, isLoading } = useSWR<T>(url, fetcher, {
    revalidateOnFocus: false,
    shouldRetryOnError: false,
    keepPreviousData: true,
  });
  return { data, error, isLoading };
}
