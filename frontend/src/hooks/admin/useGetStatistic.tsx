import axios from "axios";
import useSWR from "swr";

interface ResponseType {
  totalRevenue: number;
  todayRevenue: number;
  totalSoldQuantity: number;
  todaySoldQuantity: number;
  monthlyRevenue: { month: string; revenue: number }[];
  totalImportCost?: number;
  totalProfit?: number;
  deliveredOrderCount?: number;
}

const fetcher = (url: string) => axios.get(url).then((res) => res.data);

export default function useGetStatistic() {
  const url = `${import.meta.env.VITE_BACKEND_URL}/api/order/statistics`;
  const { data, error, isLoading, mutate } = useSWR<ResponseType>(url, fetcher, {
    shouldRetryOnError: false,
    revalidateOnFocus: false,
  });

  return {
    totalRevenue: data?.totalRevenue ?? 0,
    todayRevenue: data?.todayRevenue ?? 0,
    totalSoldQuantity: data?.totalSoldQuantity ?? 0,
    todaySoldQuantity: data?.todaySoldQuantity ?? 0,
    monthlyRevenue: data?.monthlyRevenue ?? [],
    totalImportCost: data?.totalImportCost ?? 0,
    totalProfit: data?.totalProfit ?? 0,
    isLoading,
    error,
    mutate,
  };
}
