import { Navigate, useParams } from "react-router-dom";

// Chi tiết hóa đơn hiển thị ngay trên màn hình danh sách (main - sub form)
function OrderDetailAdminPage() {
  const { id } = useParams();
  return <Navigate to={`/admin/orders${id ? `?selected=${id}` : ""}`} replace />;
}

export default OrderDetailAdminPage;
