import Cookies from "js-cookie";
import { jwtDecode } from "jwt-decode";

/** Vai trò của tài khoản đang đăng nhập trang quản trị (đọc từ JWT trong cookie). */
export function getAdminRole(): number | null {
  const token = Cookies.get("token-admin");
  if (!token) return null;
  try {
    const role = Number(jwtDecode<{ role: number }>(token).role);
    return Number.isNaN(role) ? null : role;
  } catch {
    return null;
  }
}

/**
 * Phân quyền trang quản trị:
 *  - 0 Quản trị viên hệ thống: toàn quyền, thêm / sửa / khóa / xóa tài khoản
 *  - 1 Nhân viên bán hàng, 2 Nhân viên kho: xem danh sách nhân viên, khách hàng (chỉ đọc)
 */
export default function useAdminRole() {
  const role = getAdminRole();
  return { role, isAdmin: role === 0 };
}
