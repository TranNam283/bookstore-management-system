import axios from "axios";
import { useState } from "react";
import toast from "react-hot-toast";
import type { ImportReceipt } from "../../types/type";
import { adminAuthHeader } from "../../utils/adminAuth";

type Payload = {
  publisherId?: string;
  note?: string;
  items: { bookId: string; quantity: number; importPrice: number }[];
};

export default function useAddImportReceipt() {
  const [isLoading, setIsLoading] = useState(false);

  const addImportReceipt = async (payload: Payload): Promise<ImportReceipt | null> => {
    setIsLoading(true);
    try {
      const res = await axios.post(
        `${import.meta.env.VITE_BACKEND_URL}/api/import-receipt`,
        payload,
        { headers: adminAuthHeader() },
      );
      toast.success(`Đã lập phiếu nhập ${res.data.receiptCode}`);
      return res.data;
    } catch (err: any) {
      toast.error(err?.response?.data?.message || "Lập phiếu nhập thất bại");
      return null;
    } finally {
      setIsLoading(false);
    }
  };

  return { addImportReceipt, isLoading };
}
