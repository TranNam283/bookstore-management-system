-- =====================================================================
-- BỘ CÂU SQL MẪU ĐỂ DEMO / TRẢ LỜI CÂU HỎI CỦA GIẢNG VIÊN
-- Mở trong MySQL Workbench (schema bookstore_dev), đặt con trỏ vào một câu
-- rồi bấm Ctrl + Enter để chạy riêng câu đó.
-- Trạng thái đơn: -1 chờ thanh toán, 0 chờ xác nhận, 1 xác nhận, 2 đang giao,
--                  3 giao thành công, 4 đã hủy, 5 trả hàng.
-- Doanh thu (tổng thu) chỉ tính đơn status = 3.
-- =====================================================================
USE bookstore_dev;

-- ---------------------------------------------------------------------
-- 1. Xem cấu trúc bảng (khóa chính, khóa ngoại, kiểu dữ liệu)
-- ---------------------------------------------------------------------
SHOW TABLES;
DESCRIBE orders;
DESCRIBE orderdetail;
SHOW CREATE TABLE import_receipt;

-- Danh sách khóa ngoại của database
SELECT table_name, column_name, referenced_table_name, referenced_column_name
FROM information_schema.key_column_usage
WHERE table_schema = 'bookstore_dev' AND referenced_table_name IS NOT NULL
ORDER BY table_name;

-- ---------------------------------------------------------------------
-- 2. Tìm kiếm nâng cao: phép toán quan hệ + AND / OR
-- ---------------------------------------------------------------------
-- 2a. Tổng tiền từ 1 triệu đến 2 triệu (khoảng giá trị -> AND)
SELECT order_code, fullname, city, total, created_at
FROM orders
WHERE total >= 1000000 AND total <= 2000000
ORDER BY total DESC;

-- 2b. Khách ở TP.HCM hoặc Hà Nội (gom tập hợp -> OR)
SELECT order_code, fullname, city, total
FROM orders
WHERE city = 'Hồ Chí Minh' OR city = 'Hà Nội';

-- 2c. Kết hợp: (khoảng tiền) AND (tập thành phố) - chú ý dấu ngoặc!
SELECT order_code, fullname, city, total
FROM orders
WHERE (total >= 1000000 AND total <= 2000000)
  AND (city = 'Hồ Chí Minh' OR city = 'Hà Nội');

-- 2d. Viết gọn bằng BETWEEN / IN (kết quả giống 2c)
SELECT order_code, fullname, city, total
FROM orders
WHERE total BETWEEN 1000000 AND 2000000
  AND city IN ('Hồ Chí Minh', 'Hà Nội');

-- 2e. Sách giá > 150.000 và còn ít hàng (<= 10), hoặc đang giảm giá
SELECT title, price, discount, stock
FROM book
WHERE (price > 150000 AND stock <= 10) OR discount > 0;

-- 2f. LIKE: đơn có người nhận họ "Nguyễn"
SELECT order_code, fullname FROM orders WHERE fullname LIKE 'Nguyễn%';

-- ---------------------------------------------------------------------
-- 3. Chi tiết hóa đơn: thành tiền từng dòng (Số lượng × Đơn giá − Giảm giá)
-- ---------------------------------------------------------------------
SELECT o.order_code, b.title,
       d.quantity, d.price AS don_gia, d.discount AS giam_moi_cuon,
       d.subtotal AS tien_truoc_giam,        -- quantity * price
       d.discount_amount AS tien_giam,       -- quantity * discount
       d.line_total AS thanh_tien            -- subtotal - discount_amount
FROM orderdetail d
JOIN orders o ON o.id = d.order_id
JOIN book b ON b.id = d.book_id
WHERE o.order_code = (SELECT order_code FROM orders ORDER BY created_at DESC LIMIT 1);

-- Kiểm tra dữ liệu nhất quán: tổng thành tiền các dòng = tổng tiền hóa đơn (kết quả rỗng là đúng)
SELECT o.order_code, o.total, SUM(d.line_total) AS tong_dong
FROM orders o JOIN orderdetail d ON d.order_id = o.id
GROUP BY o.id, o.order_code, o.total
HAVING ABS(SUM(d.line_total) - o.total) > 0.01;

-- ---------------------------------------------------------------------
-- 4. Thống kê tổng tiền hóa đơn theo thời gian (từ ngày A đến ngày B)
-- ---------------------------------------------------------------------
-- 4a. Theo ngày
SELECT DATE(created_at) AS ngay, COUNT(*) AS so_hd, SUM(total) AS tong_tien
FROM orders
WHERE status = 3 AND created_at >= '2026-10-01' AND created_at < '2026-11-01'
GROUP BY DATE(created_at)
ORDER BY ngay;

-- 4b. Theo tháng
SELECT DATE_FORMAT(created_at, '%Y-%m') AS thang, COUNT(*) AS so_hd,
       SUM(subtotal) AS truoc_giam, SUM(discount_total) AS giam_gia, SUM(total) AS tong_tien
FROM orders
WHERE status = 3 AND created_at BETWEEN '2025-01-01' AND '2026-12-31 23:59:59'
GROUP BY thang
ORDER BY thang;

-- 4c. Theo quý - quý nào bán tốt nhất?
SELECT YEAR(created_at) AS nam, QUARTER(created_at) AS quy, COUNT(*) AS so_hd, SUM(total) AS tong_tien
FROM orders
WHERE status = 3
GROUP BY nam, quy
ORDER BY tong_tien DESC;

-- 4d. Theo năm
SELECT YEAR(created_at) AS nam, COUNT(*) AS so_hd, SUM(total) AS tong_tien
FROM orders WHERE status = 3
GROUP BY nam ORDER BY nam;

-- ---------------------------------------------------------------------
-- 5. Tổng thu (hóa đơn) - Tổng chi (phiếu nhập) - Lợi nhuận
-- ---------------------------------------------------------------------
SELECT
  (SELECT COALESCE(SUM(total), 0) FROM orders WHERE status = 3
     AND created_at BETWEEN '2025-01-01' AND '2025-12-31 23:59:59') AS tong_thu,
  (SELECT COALESCE(SUM(total_amount), 0) FROM import_receipt
     WHERE created_at BETWEEN '2025-01-01' AND '2025-12-31 23:59:59') AS tong_chi,
  (SELECT COALESCE(SUM(total), 0) FROM orders WHERE status = 3
     AND created_at BETWEEN '2025-01-01' AND '2025-12-31 23:59:59')
  - (SELECT COALESCE(SUM(total_amount), 0) FROM import_receipt
     WHERE created_at BETWEEN '2025-01-01' AND '2025-12-31 23:59:59') AS loi_nhuan;

-- Theo tháng (dùng view đã tạo sẵn)
SELECT * FROM vw_monthly_profit ORDER BY report_month;

-- ---------------------------------------------------------------------
-- 6. Tổng tiền hóa đơn theo nhân viên (nhân viên xác nhận đơn)
-- ---------------------------------------------------------------------
SELECT u.fullname AS nhan_vien, COUNT(o.id) AS so_hd, COALESCE(SUM(o.total), 0) AS tong_tien
FROM user u
LEFT JOIN orders o ON o.staff_id = u.id AND o.status = 3
WHERE u.role IN (0, 1, 2)
GROUP BY u.id, u.fullname
ORDER BY tong_tien DESC;

-- Nhân viên có doanh thu > 10 triệu (HAVING lọc sau khi gom nhóm)
SELECT u.fullname, SUM(o.total) AS tong_tien
FROM orders o JOIN user u ON u.id = o.staff_id
WHERE o.status = 3
GROUP BY u.id, u.fullname
HAVING SUM(o.total) > 10000000;

-- ---------------------------------------------------------------------
-- 7. Các câu thường gặp khác
-- ---------------------------------------------------------------------
-- Top 5 sách bán chạy (không tính đơn hủy / trả)
SELECT b.title, SUM(d.quantity) AS da_ban, SUM(d.line_total) AS doanh_thu
FROM orderdetail d
JOIN orders o ON o.id = d.order_id AND o.status NOT IN (4, 5)
JOIN book b ON b.id = d.book_id
GROUP BY b.id, b.title
ORDER BY da_ban DESC
LIMIT 5;

-- Khách hàng chi tiêu nhiều nhất
SELECT u.fullname, u.email, COUNT(o.id) AS so_don, SUM(o.total) AS tong_chi_tieu
FROM user u JOIN orders o ON o.user_id = u.id AND o.status = 3
WHERE u.role = 3
GROUP BY u.id, u.fullname, u.email
ORDER BY tong_chi_tieu DESC
LIMIT 10;

-- Truy vấn con: sách có giá cao hơn giá trung bình
SELECT title, price FROM book WHERE price > (SELECT AVG(price) FROM book) ORDER BY price DESC;

-- Chi tiết một phiếu nhập
SELECT r.receipt_code, p.name AS nha_xuat_ban, u.fullname AS nhan_vien,
       b.title, d.quantity, d.import_price, d.line_total
FROM import_receipt r
LEFT JOIN publisher p ON p.id = r.publisher_id
LEFT JOIN user u ON u.id = r.staff_id
JOIN import_receipt_detail d ON d.receipt_id = r.id
JOIN book b ON b.id = d.book_id
ORDER BY r.created_at DESC
LIMIT 10;

-- ---------------------------------------------------------------------
-- 8. Thêm / sửa / xóa AN TOÀN khi demo: chạy trong transaction rồi ROLLBACK
-- ---------------------------------------------------------------------
START TRANSACTION;
UPDATE book SET price = price * 1.1 WHERE title LIKE 'Mưa Đỏ%';
SELECT title, price FROM book WHERE title LIKE 'Mưa Đỏ%';   -- thấy giá mới
ROLLBACK;                                                     -- hoàn tác, dữ liệu như cũ
SELECT title, price FROM book WHERE title LIKE 'Mưa Đỏ%';

-- Lưu ý: nếu sửa trực tiếp trong database (COMMIT), trang admin vẫn hiển thị dữ liệu cũ
-- tối đa 15 phút (phiên cache RAM). Bấm nút "Làm mới dữ liệu" trên thanh header admin
-- để hệ thống đọc lại database ngay.

-- =====================================================================
-- 9. KHO HÀNG (V4): nhà cung cấp, phiếu xuất, thẻ kho, lịch sử hóa đơn
-- =====================================================================
-- Tổng tiền nhập theo nhà cung cấp (bỏ phiếu đã hủy)
SELECT s.code, s.name, COUNT(r.id) AS so_phieu, COALESCE(SUM(r.total_amount), 0) AS tong_nhap
FROM supplier s
LEFT JOIN import_receipt r ON r.supplier_id = s.id AND r.status = 1
GROUP BY s.id, s.code, s.name
ORDER BY tong_nhap DESC;

-- Phiếu xuất theo loại
SELECT type, COUNT(*) AS so_phieu, SUM(total_quantity) AS so_luong, SUM(total_amount) AS gia_tri
FROM export_receipt GROUP BY type;

-- Thẻ kho của một cuốn sách (mỗi dòng là một lần tồn kho thay đổi)
SELECT m.created_at, m.type, m.quantity_change, m.stock_after, m.ref_code, u.fullname AS nguoi_thuc_hien, m.note
FROM stock_movement m
JOIN book b ON b.id = m.book_id
LEFT JOIN user u ON u.id = m.staff_id
WHERE b.title LIKE 'Mưa Đỏ%'
ORDER BY m.created_at;

-- Kiểm tra: tồn cuối trên thẻ kho = tồn kho hiện tại (kết quả rỗng là đúng)
SELECT b.title, b.stock,
       (SELECT m.stock_after FROM stock_movement m WHERE m.book_id = b.id
        ORDER BY m.created_at DESC, m.id DESC LIMIT 1) AS ton_the_kho
FROM book b
HAVING b.stock <> ton_the_kho;

-- Báo cáo nhập - xuất - tồn tháng 10/2026
SELECT b.title,
       COALESCE(SUM(CASE WHEN m.created_at < '2026-10-01' THEN m.quantity_change END), 0) AS ton_dau,
       COALESCE(SUM(CASE WHEN m.created_at >= '2026-10-01' AND m.created_at < '2026-11-01' AND m.quantity_change > 0 THEN m.quantity_change END), 0) AS nhap,
       COALESCE(-SUM(CASE WHEN m.created_at >= '2026-10-01' AND m.created_at < '2026-11-01' AND m.quantity_change < 0 THEN m.quantity_change END), 0) AS xuat,
       COALESCE(SUM(CASE WHEN m.created_at < '2026-11-01' THEN m.quantity_change END), 0) AS ton_cuoi
FROM book b LEFT JOIN stock_movement m ON m.book_id = b.id
GROUP BY b.id, b.title
ORDER BY xuat DESC;

-- Lịch sử xử lý một hóa đơn
SELECT h.created_at, h.from_status, h.to_status, u.fullname AS nhan_vien, h.note
FROM order_status_history h
JOIN orders o ON o.id = h.order_id
LEFT JOIN user u ON u.id = h.staff_id
WHERE o.order_code = (SELECT order_code FROM orders WHERE status = 3 ORDER BY created_at DESC LIMIT 1)
ORDER BY h.created_at;

-- Tồn kho hiện tại + giá trị tồn (view)
SELECT * FROM vw_inventory ORDER BY stock;

-- =====================================================================
-- 10. HÀNG BÁN CHẠY THEO THỜI ĐIỂM - để quản lý biết nhập hàng gì, khi nào
--     (tính trên đơn đã xác nhận / đang giao / giao thành công: status IN (1,2,3))
-- =====================================================================

-- 10a. Top 5 sách bán chạy của MỖI quý (ROW_NUMBER = xếp hạng trong từng quý)
SELECT ky, hang, sach, the_loai, so_cuon, doanh_thu
FROM (
  SELECT CONCAT('Quý ', QUARTER(o.created_at), '/', YEAR(o.created_at)) AS ky,
         YEAR(o.created_at) AS y, QUARTER(o.created_at) AS q,
         b.title AS sach, TRIM(c.name) AS the_loai,
         SUM(d.quantity) AS so_cuon, SUM(d.line_total) AS doanh_thu,
         ROW_NUMBER() OVER (PARTITION BY YEAR(o.created_at), QUARTER(o.created_at)
                            ORDER BY SUM(d.quantity) DESC, SUM(d.line_total) DESC) AS hang
  FROM orders o
  JOIN orderdetail d ON d.order_id = o.id
  JOIN book b ON b.id = d.book_id
  JOIN category c ON c.id = b.category_id
  WHERE o.status IN (1, 2, 3)
  GROUP BY y, q, ky, b.id, b.title, c.name
) t
WHERE hang <= 5
ORDER BY y, q, hang;

-- 10b. Xếp hạng THỂ LOẠI bán chạy trong quý 3/2026 (từ trên xuống)
SELECT RANK() OVER (ORDER BY SUM(d.quantity) DESC) AS hang,
       TRIM(c.name) AS the_loai,
       SUM(d.quantity) AS so_cuon,
       SUM(d.line_total) AS doanh_thu,
       COUNT(DISTINCT b.id) AS so_dau_sach,
       ROUND(100 * SUM(d.quantity) / SUM(SUM(d.quantity)) OVER (), 1) AS ty_trong_phan_tram
FROM orders o
JOIN orderdetail d ON d.order_id = o.id
JOIN book b ON b.id = d.book_id
JOIN category c ON c.id = b.category_id
WHERE o.status IN (1, 2, 3)
  AND o.created_at >= '2026-07-01' AND o.created_at < '2026-10-01'
GROUP BY c.id, c.name
ORDER BY hang;

-- 10c. Mùa vụ: quý 3 các năm - sách nào bán chạy, tồn kho hiện tại, đề xuất nhập trước quý 3
SELECT b.title AS sach,
       SUM(CASE WHEN YEAR(o.created_at) = 2025 THEN d.quantity ELSE 0 END) AS q3_2025,
       SUM(CASE WHEN YEAR(o.created_at) = 2026 THEN d.quantity ELSE 0 END) AS q3_2026,
       CEIL(SUM(d.quantity) / 2) AS du_bao_q3,
       b.stock AS ton_kho,
       GREATEST(CEIL(SUM(d.quantity) / 2) - b.stock, 0) AS de_xuat_nhap
FROM orders o
JOIN orderdetail d ON d.order_id = o.id
JOIN book b ON b.id = d.book_id
WHERE o.status IN (1, 2, 3) AND QUARTER(o.created_at) = 3
  AND YEAR(o.created_at) IN (2025, 2026)
GROUP BY b.id, b.title, b.stock
ORDER BY du_bao_q3 DESC
LIMIT 10;

-- 10d. Top bán chạy TUẦN này (thứ 2 - chủ nhật), so với tuần trước
SELECT b.title,
       SUM(CASE WHEN YEARWEEK(o.created_at, 3) = YEARWEEK(CURDATE(), 3) THEN d.quantity ELSE 0 END) AS tuan_nay,
       SUM(CASE WHEN YEARWEEK(o.created_at, 3) = YEARWEEK(CURDATE() - INTERVAL 7 DAY, 3) THEN d.quantity ELSE 0 END) AS tuan_truoc
FROM orders o
JOIN orderdetail d ON d.order_id = o.id
JOIN book b ON b.id = d.book_id
WHERE o.status IN (1, 2, 3) AND o.created_at >= CURDATE() - INTERVAL 14 DAY
GROUP BY b.id, b.title
ORDER BY tuan_nay DESC, tuan_truoc DESC;

-- =====================================================================
-- 11. NGƯỜI DÙNG: vai trò nhân viên, khách hàng & hóa đơn của họ
-- =====================================================================

-- 11a. Số tài khoản theo vai trò
SELECT role,
       CASE role WHEN 0 THEN 'Quản trị viên hệ thống' WHEN 1 THEN 'Nhân viên bán hàng'
                 WHEN 2 THEN 'Nhân viên kho' ELSE 'Khách hàng' END AS vai_tro,
       COUNT(*) AS so_tai_khoan,
       SUM(status = 1) AS dang_hoat_dong
FROM user GROUP BY role ORDER BY role;

-- 11b. Công việc của từng nhân viên: hóa đơn xử lý, doanh thu, phiếu nhập
SELECT u.fullname,
       CASE u.role WHEN 0 THEN 'Quản trị viên hệ thống' WHEN 1 THEN 'Nhân viên bán hàng' ELSE 'Nhân viên kho' END AS vai_tro,
       (SELECT COUNT(*) FROM orders o WHERE o.staff_id = u.id) AS hoa_don_xu_ly,
       (SELECT COALESCE(SUM(o.total), 0) FROM orders o WHERE o.staff_id = u.id AND o.status = 3) AS doanh_thu,
       (SELECT COUNT(*) FROM import_receipt r WHERE r.staff_id = u.id AND r.status = 1) AS phieu_nhap
FROM user u
WHERE u.role IN (0, 1, 2)
ORDER BY doanh_thu DESC;

-- 11c. Danh sách khách hàng: ngày tạo tài khoản, tổng đơn, tổng chi tiêu
SELECT u.fullname, u.email, DATE(u.created_at) AS ngay_tao,
       COUNT(o.id) AS tong_don,
       SUM(o.status = 3) AS giao_thanh_cong,
       COALESCE(SUM(CASE WHEN o.status = 3 THEN o.total END), 0) AS tong_chi_tieu
FROM user u
LEFT JOIN orders o ON o.user_id = u.id AND o.status >= 0
WHERE u.role = 3
GROUP BY u.id, u.fullname, u.email, u.created_at
ORDER BY tong_chi_tieu DESC;

-- 11d. Hóa đơn của 1 khách + nhân viên xử lý + từng món
SELECT o.order_code, o.created_at, o.status, o.total,
       nv.fullname AS nhan_vien_xu_ly,
       b.title, d.quantity, d.price, d.line_total
FROM orders o
LEFT JOIN user nv ON nv.id = o.staff_id
JOIN orderdetail d ON d.order_id = o.id
JOIN book b ON b.id = d.book_id
WHERE o.user_id = (SELECT u.id FROM user u WHERE u.role = 3 AND EXISTS (SELECT 1 FROM orders x WHERE x.user_id = u.id) ORDER BY u.created_at LIMIT 1)
ORDER BY o.created_at DESC, b.title;
