import axios from "axios";
import { useState } from "react";
import toast from "react-hot-toast";
import Swal from "sweetalert2";
import { adminAuthHeader } from "../../utils/adminAuth";

export default function useUpdateStatusOrder() {
  const [isLoading, setIsLoading] = useState(false);
  const updateStatusOrder = async (id: string, status: number) => {
    const result = await Swal.fire({
      title: `Xác nhận cập nhật trạng thái?`,
      text: `Bạn có chắc muốn cập nhật trạng thái đơn hàng này không?`,
      icon: "warning",
      showCancelButton: true,
      confirmButtonText: "Đồng ý",
      cancelButtonText: "Hủy",
    });

    if (!result.isConfirmed || !id) {
      return false;
    }

    const loadingToast = toast.loading("Đang cập nhật...");

    setIsLoading(true);
    try {
      const url = `${
        import.meta.env.VITE_BACKEND_URL
      }/api/order/${id}?status=${status}`;
      // gửi token để backend ghi nhận nhân viên xác nhận đơn
      await axios.put(url, null, { headers: adminAuthHeader() });

      toast.dismiss(loadingToast);
      toast.success("Cập nhật thành công");
      return true;
    } catch (err) {
      console.error("Lỗi:", err);
      throw err;
    } finally {
      toast.dismiss(loadingToast);
      setIsLoading(false);
    }
  };

  return { updateStatusOrder, isLoading };
}
