/** Chuẩn hóa chuỗi để tìm kiếm: chữ thường, bỏ dấu tiếng Việt. */
export const normalizeText = (value: string) =>
  value
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "")
    .replace(/đ/g, "d")
    .replace(/Đ/g, "D")
    .toLowerCase()
    .trim();
