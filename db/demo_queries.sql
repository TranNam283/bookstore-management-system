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
