import axios from "axios";
import useSWR from "swr";

export type GroupBy = "day" | "month" | "quarter" | "year";

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
