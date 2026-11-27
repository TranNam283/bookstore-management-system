import axios from "axios";
import useSWR from "swr";
import type { Supplier } from "../../types/type";

const base = () => `${import.meta.env.VITE_BACKEND_URL}/api/supplier`;
const fetcher = (url: string) => axios.get(url).then((res) => res.data);

/** Danh sách nhà cung cấp (kèm số phiếu nhập, tổng tiền đã nhập) + thêm / sửa / xóa. */
export default function useSuppliers() {
  const { data, error, isLoading, mutate } = useSWR<Supplier[]>(base(), fetcher, {
    revalidateOnFocus: false,
    shouldRetryOnError: false,
  });

  const save = async (supplier: Partial<Supplier>) => {
    if (supplier.id) await axios.put(`${base()}/${supplier.id}`, supplier);
    else await axios.post(base(), supplier);
    await mutate();
  };

  const remove = async (id: string) => {
    await axios.delete(`${base()}/${id}`);
    await mutate();
  };

  return { suppliers: data ?? [], error, isLoading, mutate, save, remove };
}
