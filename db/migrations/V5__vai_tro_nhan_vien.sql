-- =====================================================================
-- V5: Vai trò nhân viên
--   Hệ thống có 3 vai trò cho trang quản trị (cột user.role):
--     0 = Quản trị viên hệ thống : toàn quyền, quản lý tài khoản nhân viên / khách hàng
--     1 = Nhân viên bán hàng     : xác nhận, xử lý hóa đơn
--     2 = Nhân viên kho          : lập phiếu nhập / xuất, kiểm kho
--   (3 = Khách hàng mua trên website)
--
--   - Thêm tài khoản quản trị viên dùng để demo: quanly@bookstore.vn / 123456
--   - Thêm nhân viên kho: kho.pham@bookstore.vn / 123456
--   - Phiếu nhập mẫu do nhân viên bán hàng lập (V3) chuyển sang cho nhân viên kho
--     (kèm dòng thẻ kho tương ứng) để mỗi vai trò có dữ liệu công việc riêng.
-- Chạy sau V2, V3, V4 (chạy lại nhiều lần không bị trùng):
--   mysql -u root -p bookstore_dev -e "source db/migrations/V5__vai_tro_nhan_vien.sql"
-- =====================================================================
SET NAMES utf8mb4;

-- Mật khẩu 123456 (bcrypt, giống các tài khoản mẫu ở V3)
INSERT IGNORE INTO `user` (`id`, `created_at`, `email`, `password`, `role`, `status`, `fullname`) VALUES
  ('5eed00a1-0000-4000-8000-000000000010', '2024-11-15 08:00:00.000000', 'quanly@bookstore.vn',
   '$2a$10$CfJc3NCQnXuzQx/hUtyxROkWJVBQYeL0wTT/u6Eh9DEkB24LevZ66', 0, 1, 'Võ Minh Quân'),
  ('5eed00a1-0000-4000-8000-000000000011', '2024-12-01 08:00:00.000000', 'kho.pham@bookstore.vn',
   '$2a$10$CfJc3NCQnXuzQx/hUtyxROkWJVBQYeL0wTT/u6Eh9DEkB24LevZ66', 2, 1, 'Phạm Thị Thu');

-- Phiếu nhập do nhân viên bán hàng mẫu (Trần Văn Minh, Lê Thị Hoa) lập -> nhân viên kho
UPDATE `stock_movement` sm
JOIN `import_receipt` ir ON ir.id = sm.ref_id AND sm.ref_type = 'IMPORT'
SET sm.staff_id = '5eed00a1-0000-4000-8000-000000000011'
WHERE ir.staff_id IN ('5eed00a1-0000-4000-8000-000000000002', '5eed00a1-0000-4000-8000-000000000003');

UPDATE `import_receipt`
SET staff_id = '5eed00a1-0000-4000-8000-000000000011'
WHERE staff_id IN ('5eed00a1-0000-4000-8000-000000000002', '5eed00a1-0000-4000-8000-000000000003');

-- Kiểm tra: số tài khoản theo vai trò
SELECT role,
       CASE role WHEN 0 THEN 'Quản trị viên hệ thống' WHEN 1 THEN 'Nhân viên bán hàng'
                 WHEN 2 THEN 'Nhân viên kho' ELSE 'Khách hàng' END AS vai_tro,
       COUNT(*) AS so_tai_khoan
FROM `user` GROUP BY role ORDER BY role;
