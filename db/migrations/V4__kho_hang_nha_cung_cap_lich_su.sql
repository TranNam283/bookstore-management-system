-- =====================================================================
-- V4: Quản lý kho chi tiết
--   - Nhà cung cấp (supplier), phiếu nhập gắn nhà cung cấp + trạng thái (hủy phiếu)
--   - Phiếu xuất kho (export_receipt): xuất bán hàng, xuất hư hỏng, trả nhà cung cấp, khác
--   - Thẻ kho (stock_movement): mọi lần tăng / giảm tồn kho của từng cuốn sách
--   - Lịch sử trạng thái hóa đơn (order_status_history): ai đổi, lúc nào
-- Chạy sau V2, V3:
--   mysql -u root -p bookstore_dev -e "source db/migrations/V4__kho_hang_nha_cung_cap_lich_su.sql"
-- =====================================================================
SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 1. Nhà cung cấp
-- ---------------------------------------------------------------------
CREATE TABLE `supplier` (
  `id` VARCHAR(255) NOT NULL,
  `code` VARCHAR(20) NOT NULL,
  `name` VARCHAR(150) NOT NULL,
  `contact_name` VARCHAR(100) NULL,
  `phone` VARCHAR(15) NULL,
  `email` VARCHAR(100) NULL,
  `address` VARCHAR(255) NULL,
  `tax_code` VARCHAR(20) NULL,
  `status` INT NOT NULL DEFAULT 1 COMMENT '1 đang hợp tác, 0 ngừng',
  `created_at` DATETIME(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_supplier_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO `supplier` (`id`, `code`, `name`, `contact_name`, `phone`, `email`, `address`, `tax_code`, `status`, `created_at`) VALUES
  ('5eed00d1-0000-4000-8000-000000000001', 'NCC001', 'Công ty CP Phát hành sách TP.HCM (Fahasa)', 'Nguyễn Văn Hùng', '02838225796', 'kinhdoanh@fahasa.vn', '60-62 Lê Lợi, Phường Bến Thành, Hồ Chí Minh', '0304132047', 1, '2024-12-01 08:00:00'),
  ('5eed00d1-0000-4000-8000-000000000002', 'NCC002', 'Công ty TNHH Sách Phương Nam', 'Trần Thị Mai', '02838207200', 'sach@phuongnam.vn', '940 Ba Tháng Hai, Phường Phú Thọ, Hồ Chí Minh', '0302105271', 1, '2024-12-01 08:00:00'),
  ('5eed00d1-0000-4000-8000-000000000003', 'NCC003', 'Công ty CP Sách Alpha', 'Lê Quốc Bảo', '02437224060', 'info@alphabooks.vn', '176 Thái Hà, Phường Đống Đa, Hà Nội', '0101602925', 1, '2024-12-01 08:00:00'),
  ('5eed00d1-0000-4000-8000-000000000004', 'NCC004', 'Công ty CP Văn hóa và Truyền thông Nhã Nam', 'Phạm Thu Hà', '02435146875', 'lienhe@nhanam.vn', '59 Đỗ Quang, Phường Cầu Giấy, Hà Nội', '0101856521', 1, '2024-12-01 08:00:00'),
  ('5eed00d1-0000-4000-8000-000000000005', 'NCC005', 'Công ty CP Sách và Thiết bị Đà Nẵng', 'Võ Minh Tuấn', '02363822113', 'sachdanang@gmail.com', '78 Bạch Đằng, Phường Hải Châu, Đà Nẵng', '0400101394', 0, '2024-12-01 08:00:00');

-- ---------------------------------------------------------------------
-- 2. Phiếu nhập: nhà cung cấp + trạng thái (1 hoàn tất, 0 đã hủy)
-- ---------------------------------------------------------------------
ALTER TABLE `import_receipt`
  ADD COLUMN `supplier_id` VARCHAR(255) NULL COMMENT 'Nhà cung cấp' AFTER `receipt_code`,
  ADD COLUMN `status` INT NOT NULL DEFAULT 1 COMMENT '1 hoàn tất, 0 đã hủy',
  ADD COLUMN `cancelled_at` DATETIME(6) NULL,
  ADD COLUMN `cancel_reason` VARCHAR(255) NULL,
  ADD KEY `idx_import_receipt_supplier` (`supplier_id`),
  ADD CONSTRAINT `fk_import_receipt_supplier` FOREIGN KEY (`supplier_id`) REFERENCES `supplier` (`id`) ON DELETE SET NULL;

-- gán nhà cung cấp cho phiếu nhập cũ (phân bổ đều theo mã phiếu)
UPDATE `import_receipt`
SET `supplier_id` = CONCAT('5eed00d1-0000-4000-8000-00000000000', 1 + (CRC32(`receipt_code`) % 4))
WHERE `supplier_id` IS NULL;

-- ---------------------------------------------------------------------
-- 3. Phiếu xuất kho
--    type: SALE (xuất bán theo hóa đơn - tồn kho đã trừ khi đặt hàng),
--          DAMAGED (hư hỏng / mất), RETURN_SUPPLIER (trả nhà cung cấp), OTHER (khác)
-- ---------------------------------------------------------------------
CREATE TABLE `export_receipt` (
  `id` VARCHAR(255) NOT NULL,
  `receipt_code` VARCHAR(15) NOT NULL,
  `type` VARCHAR(20) NOT NULL,
  `order_id` VARCHAR(255) NULL COMMENT 'Hóa đơn (khi xuất bán)',
  `supplier_id` VARCHAR(255) NULL COMMENT 'Nhà cung cấp (khi trả hàng)',
  `staff_id` VARCHAR(255) NULL COMMENT 'Nhân viên lập phiếu',
  `reason` VARCHAR(255) NULL,
  `total_quantity` INT NOT NULL DEFAULT 0,
  `total_amount` DOUBLE NOT NULL DEFAULT 0 COMMENT 'Tổng giá trị xuất',
  `created_at` DATETIME(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_export_receipt_code` (`receipt_code`),
  UNIQUE KEY `uk_export_receipt_order` (`order_id`),
  KEY `idx_export_receipt_created` (`created_at`),
  KEY `idx_export_receipt_type` (`type`),
  CONSTRAINT `fk_export_receipt_order` FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_export_receipt_supplier` FOREIGN KEY (`supplier_id`) REFERENCES `supplier` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_export_receipt_staff` FOREIGN KEY (`staff_id`) REFERENCES `user` (`id`) ON DELETE SET NULL,
  CONSTRAINT `chk_export_receipt_type` CHECK (`type` IN ('SALE', 'DAMAGED', 'RETURN_SUPPLIER', 'OTHER'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `export_receipt_detail` (
  `id` VARCHAR(255) NOT NULL,
  `receipt_id` VARCHAR(255) NOT NULL,
  `book_id` VARCHAR(255) NOT NULL,
  `quantity` INT NOT NULL,
  `unit_price` DOUBLE NOT NULL COMMENT 'Đơn giá xuất',
  `line_total` DOUBLE NOT NULL COMMENT 'Thành tiền = quantity * unit_price',
  PRIMARY KEY (`id`),
  KEY `idx_erd_receipt` (`receipt_id`),
  KEY `idx_erd_book` (`book_id`),
  CONSTRAINT `fk_erd_receipt` FOREIGN KEY (`receipt_id`) REFERENCES `export_receipt` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_erd_book` FOREIGN KEY (`book_id`) REFERENCES `book` (`id`),
  CONSTRAINT `chk_erd_quantity` CHECK (`quantity` > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Phiếu xuất bán hàng cho các đơn đã giao đi (đang giao / giao thành công / trả hàng)
INSERT INTO `export_receipt` (`id`, `receipt_code`, `type`, `order_id`, `staff_id`, `reason`, `total_quantity`, `total_amount`, `created_at`)
SELECT CONCAT('5eed00e1-', SUBSTRING(o.`id`, 10)),
       CONCAT('PX', o.`order_code`),
       'SALE', o.`id`, o.`staff_id`,
       CONCAT('Xuất bán theo hóa đơn ', o.`order_code`),
       (SELECT COALESCE(SUM(d.`quantity`), 0) FROM `orderdetail` d WHERE d.`order_id` = o.`id`),
       o.`total`,
       DATE_ADD(COALESCE(o.`confirmed_at`, o.`created_at`), INTERVAL 1 DAY)
FROM `orders` o
WHERE o.`status` IN (2, 3, 5);

INSERT INTO `export_receipt_detail` (`id`, `receipt_id`, `book_id`, `quantity`, `unit_price`, `line_total`)
SELECT CONCAT('5eed00e2-', SUBSTRING(d.`id`, 10)),
       CONCAT('5eed00e1-', SUBSTRING(d.`order_id`, 10)),
       d.`book_id`, d.`quantity`, d.`price` - d.`discount`, d.`line_total`
FROM `orderdetail` d
JOIN `orders` o ON o.`id` = d.`order_id`
WHERE o.`status` IN (2, 3, 5);

-- ---------------------------------------------------------------------
-- 3b. Sửa dữ liệu mẫu: đơn sỉ UHDXI3 (đã hủy) đặt vượt tồn kho tại thời điểm đặt
-- ---------------------------------------------------------------------
UPDATE `orderdetail` d JOIN `orders` o ON o.`id` = d.`order_id`
SET d.`quantity` = 6, d.`subtotal` = 6 * d.`price`, d.`discount_amount` = 6 * d.`discount`,
    d.`line_total` = 6 * (d.`price` - d.`discount`)
WHERE o.`order_code` = 'UHDXI3' AND d.`quantity` = 11;
UPDATE `orders` o
JOIN (SELECT `order_id`, SUM(`subtotal`) s, SUM(`discount_amount`) dc, SUM(`line_total`) t
      FROM `orderdetail` GROUP BY `order_id`) x ON x.`order_id` = o.`id`
SET o.`subtotal` = x.s, o.`discount_total` = x.dc, o.`total` = x.t
WHERE o.`order_code` = 'UHDXI3';
UPDATE `payment` p JOIN `orders` o ON o.`id` = p.`order_id`
SET p.`amount` = o.`total` WHERE o.`order_code` = 'UHDXI3';

-- ---------------------------------------------------------------------
-- 4. Thẻ kho: mỗi dòng là một lần tồn kho thay đổi
--    type: OPENING (tồn đầu kỳ), IMPORT (nhập), SALE (bán), CANCEL_RETURN (hủy / trả hàng),
--          EXPORT (xuất thủ công), IMPORT_CANCEL (hủy phiếu nhập), ADJUST (điều chỉnh)
-- ---------------------------------------------------------------------
CREATE TABLE `stock_movement` (
  `id` VARCHAR(255) NOT NULL,
  `book_id` VARCHAR(255) NOT NULL,
  `type` VARCHAR(20) NOT NULL,
  `quantity_change` INT NOT NULL COMMENT '+ tăng tồn, - giảm tồn',
  `stock_after` INT NOT NULL COMMENT 'Tồn kho sau khi thay đổi',
  `ref_type` VARCHAR(20) NULL COMMENT 'ORDER / IMPORT / EXPORT / BOOK',
  `ref_id` VARCHAR(255) NULL,
  `ref_code` VARCHAR(20) NULL,
  `staff_id` VARCHAR(255) NULL,
  `note` VARCHAR(255) NULL,
  `created_at` DATETIME(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_stock_movement_book_time` (`book_id`, `created_at`),
  KEY `idx_stock_movement_created` (`created_at`),
  CONSTRAINT `fk_stock_movement_book` FOREIGN KEY (`book_id`) REFERENCES `book` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_stock_movement_staff` FOREIGN KEY (`staff_id`) REFERENCES `user` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Dựng lại lịch sử từ dữ liệu có sẵn (stock_after tính ở bước sau)
INSERT INTO `stock_movement` (`id`, `book_id`, `type`, `quantity_change`, `stock_after`, `ref_type`, `ref_id`, `ref_code`, `staff_id`, `note`, `created_at`)
-- nhập hàng
SELECT CONCAT('5eed00f1-', SUBSTRING(d.`id`, 10)), d.`book_id`, 'IMPORT', d.`quantity`, 0,
       'IMPORT', r.`id`, r.`receipt_code`, r.`staff_id`, 'Nhập hàng', r.`created_at`
FROM `import_receipt_detail` d JOIN `import_receipt` r ON r.`id` = d.`receipt_id`
UNION ALL
-- bán hàng (tồn kho trừ khi đặt hàng)
SELECT CONCAT('5eed00f2-', SUBSTRING(d.`id`, 10)), d.`book_id`, 'SALE', -d.`quantity`, 0,
       'ORDER', o.`id`, o.`order_code`, NULL, 'Bán hàng', o.`created_at`
FROM `orderdetail` d JOIN `orders` o ON o.`id` = d.`order_id`
WHERE o.`status` >= 0
UNION ALL
-- hủy / trả hàng: hoàn kho
SELECT CONCAT('5eed00f3-', SUBSTRING(d.`id`, 10)), d.`book_id`, 'CANCEL_RETURN', d.`quantity`, 0,
       'ORDER', o.`id`, o.`order_code`, o.`staff_id`,
       IF(o.`status` = 4, 'Hủy đơn - hoàn kho', 'Trả hàng - hoàn kho'),
       DATE_ADD(COALESCE(o.`confirmed_at`, o.`created_at`), INTERVAL IF(o.`status` = 4, 2, 120) HOUR)
FROM `orderdetail` d JOIN `orders` o ON o.`id` = d.`order_id`
WHERE o.`status` IN (4, 5);

-- Tồn đầu kỳ = tồn hiện tại - tổng biến động đã ghi nhận
INSERT INTO `stock_movement` (`id`, `book_id`, `type`, `quantity_change`, `stock_after`, `ref_type`, `ref_id`, `ref_code`, `note`, `created_at`)
SELECT CONCAT('5eed00f0-', SUBSTRING(b.`id`, 10)), b.`id`, 'OPENING',
       b.`stock` - COALESCE((SELECT SUM(m.`quantity_change`) FROM `stock_movement` m WHERE m.`book_id` = b.`id`), 0),
       0, 'BOOK', b.`id`, NULL, 'Tồn đầu kỳ', LEAST(b.`created_at`, '2024-12-31 00:00:00')
FROM `book` b;

-- stock_after = tổng lũy kế theo thời gian
UPDATE `stock_movement` m
JOIN (
  SELECT `id`,
         SUM(`quantity_change`) OVER (PARTITION BY `book_id` ORDER BY `created_at`, `id`
                                      ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS running
  FROM `stock_movement`
) x ON x.`id` = m.`id`
SET m.`stock_after` = x.running;

-- ---------------------------------------------------------------------
-- 5. Lịch sử trạng thái hóa đơn
-- ---------------------------------------------------------------------
CREATE TABLE `order_status_history` (
  `id` VARCHAR(255) NOT NULL,
  `order_id` VARCHAR(255) NOT NULL,
  `from_status` INT NULL,
  `to_status` INT NOT NULL,
  `staff_id` VARCHAR(255) NULL,
  `note` VARCHAR(255) NULL,
  `created_at` DATETIME(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_osh_order` (`order_id`, `created_at`),
  CONSTRAINT `fk_osh_order` FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_osh_staff` FOREIGN KEY (`staff_id`) REFERENCES `user` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO `order_status_history` (`id`, `order_id`, `from_status`, `to_status`, `staff_id`, `note`, `created_at`)
-- tạo đơn
SELECT CONCAT('5eed0a01-', SUBSTRING(o.`id`, 10)), o.`id`, NULL, 0, NULL, 'Khách hàng đặt hàng', o.`created_at`
FROM `orders` o WHERE o.`status` >= 0
UNION ALL
-- xác nhận
SELECT CONCAT('5eed0a02-', SUBSTRING(o.`id`, 10)), o.`id`, 0, 1, o.`staff_id`, 'Xác nhận đơn hàng', o.`confirmed_at`
FROM `orders` o WHERE o.`confirmed_at` IS NOT NULL
UNION ALL
-- bàn giao vận chuyển
SELECT CONCAT('5eed0a03-', SUBSTRING(o.`id`, 10)), o.`id`, 1, 2, o.`staff_id`, 'Bàn giao vận chuyển',
       DATE_ADD(o.`confirmed_at`, INTERVAL 1 DAY)
FROM `orders` o WHERE o.`confirmed_at` IS NOT NULL AND o.`status` IN (2, 3, 5)
UNION ALL
-- giao thành công
SELECT CONCAT('5eed0a04-', SUBSTRING(o.`id`, 10)), o.`id`, 2, 3, o.`staff_id`, 'Giao hàng thành công',
       DATE_ADD(o.`confirmed_at`, INTERVAL 3 DAY)
FROM `orders` o WHERE o.`confirmed_at` IS NOT NULL AND o.`status` IN (3, 5)
UNION ALL
-- trả hàng
SELECT CONCAT('5eed0a05-', SUBSTRING(o.`id`, 10)), o.`id`, 3, 5, o.`staff_id`, 'Khách trả hàng',
       DATE_ADD(o.`confirmed_at`, INTERVAL 5 DAY)
FROM `orders` o WHERE o.`confirmed_at` IS NOT NULL AND o.`status` = 5
UNION ALL
-- hủy
SELECT CONCAT('5eed0a06-', SUBSTRING(o.`id`, 10)), o.`id`, IF(o.`confirmed_at` IS NULL, 0, 1), 4, o.`staff_id`, 'Hủy đơn hàng',
       DATE_ADD(COALESCE(o.`confirmed_at`, o.`created_at`), INTERVAL 2 HOUR)
FROM `orders` o WHERE o.`status` = 4;

-- ---------------------------------------------------------------------
-- 6. View báo cáo
-- ---------------------------------------------------------------------
-- Tổng chi chỉ tính phiếu nhập chưa hủy
CREATE OR REPLACE SQL SECURITY INVOKER VIEW `vw_monthly_import_cost` AS
SELECT DATE_FORMAT(`created_at`, '%Y-%m') AS report_month,
       COUNT(*) AS receipt_count,
       COALESCE(SUM(`total_quantity`), 0) AS total_quantity,
       COALESCE(SUM(`total_amount`), 0) AS import_cost
FROM `import_receipt`
WHERE `status` = 1
GROUP BY DATE_FORMAT(`created_at`, '%Y-%m');

-- Tồn kho hiện tại + giá trị tồn
CREATE OR REPLACE SQL SECURITY INVOKER VIEW `vw_inventory` AS
SELECT b.`id` AS book_id, b.`title`, b.`stock`, b.`price`,
       b.`stock` * b.`price` AS stock_value,
       COALESCE((SELECT SUM(m.`quantity_change`) FROM `stock_movement` m WHERE m.`book_id` = b.`id` AND m.`quantity_change` > 0 AND m.`type` <> 'OPENING'), 0) AS total_in,
       COALESCE((SELECT -SUM(m.`quantity_change`) FROM `stock_movement` m WHERE m.`book_id` = b.`id` AND m.`quantity_change` < 0), 0) AS total_out
FROM `book` b;
