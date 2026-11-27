import { Link } from "react-router-dom";
import { TbLock } from "react-icons/tb";
import LayoutPage from "./layout/LayoutPage";
import { roleLabel } from "../../utils/labels";

/** Hiển thị khi nhân viên mở chức năng không thuộc quyền của vai trò mình. */
function NoPermission({ role }: { role: number }) {
  return (
    <LayoutPage>
      <div className="flex min-h-[calc(100vh-52px)] items-center justify-center bg-[#f1f4f9] p-4">
        <div className="max-w-md rounded border border-gray-300 bg-white p-6 text-center text-[13px]">
          <TbLock size={36} className="mx-auto mb-2 text-gray-400" />
          <h1 className="text-[16px] font-semibold text-gray-900">Bạn không có quyền dùng chức năng này</h1>
          <p className="mt-1 text-gray-600">
            Tài khoản của bạn có vai trò <b>{roleLabel(role)}</b>. Chức năng này chỉ dành cho{" "}
            <b>Quản trị viên hệ thống</b>.
          </p>
          <Link to="/admin/dashboard" className="mt-3 inline-block text-blue-700 hover:underline">
            ← Về trang tổng quan
          </Link>
        </div>
      </div>
    </LayoutPage>
  );
}

export default NoPermission;
