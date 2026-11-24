import Cookies from "js-cookie";

/** Header Authorization của tài khoản admin đang đăng nhập (để backend ghi nhận nhân viên). */
export const adminAuthHeader = (): Record<string, string> => {
  const token = Cookies.get("token-admin");
  return token ? { Authorization: `Bearer ${token}` } : {};
};
