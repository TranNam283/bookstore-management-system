import type { Order } from "../types/type";
import { ORDER_STATUS, formatDateTime } from "./format";

const money = (v?: number | null) => `${Math.round(v ?? 0).toLocaleString("vi-VN")}₫`;
const esc = (v?: string | null) =>
  (v ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c] as string);

/** Mở cửa sổ in hóa đơn bán hàng (khổ A4 / A5) cho đơn hàng đang chọn. */
export function printInvoice(order: Order) {
  const rows = order.items
    .map(
      (item, i) => `<tr>
        <td class="c">${i + 1}</td>
        <td>${esc(item.title)}</td>
        <td class="r">${item.quantity}</td>
        <td class="r">${money(item.price)}</td>
        <td class="r">${money(item.discount)}</td>
        <td class="r">${money(item.subtotal ?? item.quantity * item.price)}</td>
        <td class="r">${money(item.lineTotal ?? item.quantity * (item.price - item.discount))}</td>
      </tr>`,
    )
    .join("");

  const html = `<!doctype html><html lang="vi"><head><meta charset="utf-8">
<title>Hóa đơn ${esc(order.orderCode)}</title>
<style>
  * { box-sizing: border-box; }
  body { font-family: "Segoe UI", Arial, sans-serif; color: #111; margin: 24px; font-size: 13px; }
  .head { display: flex; justify-content: space-between; border-bottom: 2px solid #111; padding-bottom: 8px; }
  .shop b { font-size: 18px; color: #C62028; }
  h1 { text-align: center; font-size: 20px; margin: 16px 0 4px; letter-spacing: 1px; }
  .sub { text-align: center; color: #555; margin-bottom: 14px; }
  .info { display: grid; grid-template-columns: 120px 1fr 120px 1fr; gap: 3px 8px; margin-bottom: 12px; }
  .info span:nth-child(odd) { color: #555; }
  table { width: 100%; border-collapse: collapse; }
  th, td { border: 1px solid #999; padding: 5px 6px; }
  th { background: #eee; }
  .r { text-align: right; } .c { text-align: center; }
  tfoot td { font-weight: 600; }
  .total td { font-size: 15px; font-weight: 700; }
  .sign { display: flex; justify-content: space-around; margin-top: 36px; text-align: center; }
  .sign div { width: 200px; } .sign i { color: #666; font-size: 12px; }
  @media print { body { margin: 10mm; } .noprint { display: none; } }
</style></head><body>
  <div class="head">
    <div class="shop"><b>FAHASA – NHÀ SÁCH TRỰC TUYẾN</b><br>60-62 Lê Lợi, Phường Bến Thành, TP. Hồ Chí Minh<br>Hotline: 1900 636 467</div>
    <div style="text-align:right">Mã hóa đơn: <b>${esc(order.orderCode)}</b><br>Ngày đặt: ${formatDateTime(order.createdAt)}<br>Trạng thái: ${ORDER_STATUS[order.status] ?? ""}</div>
  </div>
  <h1>HÓA ĐƠN BÁN HÀNG</h1>
  <div class="sub">${order.exportReceiptCode ? `Phiếu xuất kho: ${esc(order.exportReceiptCode)}` : ""}</div>
  <div class="info">
    <span>Khách hàng</span><span>${esc(order.fullname)}</span>
    <span>Điện thoại</span><span>${esc(order.phone)}</span>
    <span>Tài khoản</span><span>${esc(order.accountEmail)}</span>
    <span>Thanh toán</span><span>${esc(order.paymethod?.toUpperCase())}</span>
    <span>Địa chỉ giao</span><span style="grid-column: span 3">${esc([order.speaddress, order.ward, order.city].filter(Boolean).join(", "))}</span>
    <span>NV xác nhận</span><span>${esc(order.staffName ?? "-")}</span>
    <span>Ngày xác nhận</span><span>${order.confirmedAt ? formatDateTime(order.confirmedAt) : "-"}</span>
  </div>
  <table>
    <thead><tr><th>STT</th><th>Tên sách</th><th>SL</th><th>Đơn giá</th><th>Giảm / cuốn</th><th>Tiền trước giảm</th><th>Thành tiền</th></tr></thead>
    <tbody>${rows}</tbody>
    <tfoot>
      <tr><td colspan="6" class="r">Cộng tiền hàng (SL × Đơn giá)</td><td class="r">${money(order.subtotal)}</td></tr>
      <tr><td colspan="6" class="r">Giảm giá</td><td class="r">-${money(order.discountTotal)}</td></tr>
      <tr class="total"><td colspan="6" class="r">TỔNG THANH TOÁN</td><td class="r">${money(order.total)}</td></tr>
    </tfoot>
  </table>
  <div class="sign">
    <div><b>Khách hàng</b><br><i>(Ký, ghi rõ họ tên)</i></div>
    <div><b>Nhân viên bán hàng</b><br><i>(Ký, ghi rõ họ tên)</i><br><br><br>${esc(order.staffName ?? "")}</div>
  </div>
  <p class="noprint" style="text-align:center;margin-top:24px"><button onclick="window.print()">In hóa đơn</button></p>
  <script>window.onload = () => setTimeout(() => window.print(), 300);</script>
</body></html>`;

  const win = window.open("", "_blank", "width=900,height=1000");
  if (!win) return;
  win.document.open();
  win.document.write(html);
  win.document.close();
}
