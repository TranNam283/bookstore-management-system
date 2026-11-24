import axios from "axios";
import useSWR from "swr";

type OrderSearchOptions = {
  cities: string[];
  paymethods: string[];
  staffs: { id: string; name: string }[];
};

const fetcher = (url: string) => axios.get(url).then((res) => res.data);

/** Giá trị gợi ý cho bộ lọc đơn hàng (thành phố, phương thức thanh toán, nhân viên). */
export function useOrderSearchOptions() {
  const { data } = useSWR<OrderSearchOptions>(
    `${import.meta.env.VITE_BACKEND_URL}/api/order/search-options`,
    fetcher,
    { revalidateOnFocus: false, shouldRetryOnError: false },
  );
  return {
    cities: data?.cities ?? [],
    paymethods: data?.paymethods ?? [],
    staffs: data?.staffs ?? [],
  };
}

/** Số đơn theo từng trạng thái (tính trên danh sách RAM). */
export function useOrderStatusCount() {
  const { data, mutate } = useSWR<Record<string, number>>(
    `${import.meta.env.VITE_BACKEND_URL}/api/order/status-count`,
    fetcher,
    { revalidateOnFocus: false, shouldRetryOnError: false },
  );
  return { counts: data ?? {}, mutate };
}
