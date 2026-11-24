-- =====================================================================
-- V2: Lưu thành tiền từng dòng hóa đơn, nhân viên xác nhận đơn,
--     phiếu nhập hàng và các view thống kê.
-- Chạy MỘT LẦN trên database đang có (bản db.sql mới đã gồm sẵn thay đổi này):
--   mysql -u root -p bookstore_dev < db/migrations/V2__thanh_tien_nhan_vien_phieu_nhap.sql
-- =====================================================================
SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 1. Chi tiết hóa đơn: lưu tiền trước giảm, tiền giảm và thành tiền
--    subtotal        = quantity * price          (tiền trước giảm giá)
--    discount_amount = quantity * discount       (discount = tiền giảm / 1 cuốn)
--    line_total      = subtotal - discount_amount (thành tiền sau giảm)
-- ---------------------------------------------------------------------
ALTER TABLE `orderdetail`
  ADD COLUMN `subtotal` DOUBLE NOT NULL DEFAULT 0 COMMENT 'Tiền trước giảm = quantity * price',
  ADD COLUMN `discount_amount` DOUBLE NOT NULL DEFAULT 0 COMMENT 'Tiền giảm = quantity * discount',
  ADD COLUMN `line_total` DOUBLE NOT NULL DEFAULT 0 COMMENT 'Thành tiền = subtotal - discount_amount',
  ADD CONSTRAINT `chk_orderdetail_quantity` CHECK (`quantity` > 0),
  ADD CONSTRAINT `chk_orderdetail_price` CHECK (`price` >= 0 AND `discount` >= 0);

UPDATE `orderdetail`
SET `subtotal`        = `quantity` * `price`,
    `discount_amount` = `quantity` * `discount`,
    `line_total`      = `quantity` * (`price` - `discount`);

-- ---------------------------------------------------------------------
-- 2. Hóa đơn: tổng tiền trước giảm, tổng giảm, nhân viên xác nhận
-- ---------------------------------------------------------------------
ALTER TABLE `orders`
  ADD COLUMN `subtotal` DOUBLE NOT NULL DEFAULT 0 COMMENT 'Tổng tiền trước giảm',
  ADD COLUMN `discount_total` DOUBLE NOT NULL DEFAULT 0 COMMENT 'Tổng tiền giảm',
  ADD COLUMN `staff_id` VARCHAR(255) NULL COMMENT 'Nhân viên xác nhận đơn',
  ADD COLUMN `confirmed_at` DATETIME(6) NULL COMMENT 'Thời điểm xác nhận',
  ADD KEY `idx_orders_created_status` (`created_at`, `status`),
  ADD KEY `idx_orders_staff` (`staff_id`),
  ADD CONSTRAINT `fk_orders_staff` FOREIGN KEY (`staff_id`) REFERENCES `user` (`id`) ON DELETE SET NULL;

UPDATE `orders` o
JOIN (
  SELECT `order_id`,
         SUM(`subtotal`)        AS s,
         SUM(`discount_amount`) AS d,
         SUM(`line_total`)      AS t
  FROM `orderdetail`
  GROUP BY `order_id`
) x ON x.`order_id` = o.`id`
SET o.`subtotal`       = x.s,
    o.`discount_total` = x.d,
    o.`total`          = x.t;

-- ---------------------------------------------------------------------
-- 3. Phiếu nhập hàng (tổng chi) và chi tiết phiếu nhập
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `import_receipt_detail`;
DROP TABLE IF EXISTS `import_receipt`;

CREATE TABLE `import_receipt` (
  `id` VARCHAR(255) NOT NULL,
  `receipt_code` VARCHAR(15) NOT NULL,
  `publisher_id` VARCHAR(255) NULL COMMENT 'Nhà cung cấp (nhà xuất bản)',
  `staff_id` VARCHAR(255) NULL COMMENT 'Nhân viên lập phiếu',
  `note` VARCHAR(255) NULL,
  `total_quantity` INT NOT NULL DEFAULT 0,
  `total_amount` DOUBLE NOT NULL DEFAULT 0 COMMENT 'Tổng tiền nhập = SUM(line_total)',
  `created_at` DATETIME(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_import_receipt_code` (`receipt_code`),
  KEY `idx_import_receipt_created` (`created_at`),
  KEY `idx_import_receipt_publisher` (`publisher_id`),
  KEY `idx_import_receipt_staff` (`staff_id`),
  CONSTRAINT `fk_import_receipt_publisher` FOREIGN KEY (`publisher_id`) REFERENCES `publisher` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_import_receipt_staff` FOREIGN KEY (`staff_id`) REFERENCES `user` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `import_receipt_detail` (
  `id` VARCHAR(255) NOT NULL,
  `receipt_id` VARCHAR(255) NOT NULL,
  `book_id` VARCHAR(255) NOT NULL,
  `quantity` INT NOT NULL,
  `import_price` DOUBLE NOT NULL COMMENT 'Giá nhập / 1 cuốn',
  `line_total` DOUBLE NOT NULL COMMENT 'Thành tiền = quantity * import_price',
  PRIMARY KEY (`id`),
  KEY `idx_ird_receipt` (`receipt_id`),
  KEY `idx_ird_book` (`book_id`),
  CONSTRAINT `fk_ird_receipt` FOREIGN KEY (`receipt_id`) REFERENCES `import_receipt` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_ird_book` FOREIGN KEY (`book_id`) REFERENCES `book` (`id`),
  CONSTRAINT `chk_ird_quantity` CHECK (`quantity` > 0),
  CONSTRAINT `chk_ird_price` CHECK (`import_price` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------
-- 4. View thống kê (dùng để minh họa / đối chiếu bằng SQL).
--    Doanh thu chỉ tính đơn trạng thái 3 = Giao thành công.
-- ---------------------------------------------------------------------
CREATE OR REPLACE SQL SECURITY INVOKER VIEW `vw_order_status_statistics` AS
SELECT `status`,
       COUNT(*) AS order_count,
       COALESCE(SUM(`total`), 0) AS total_amount
FROM `orders`
GROUP BY `status`;

CREATE OR REPLACE SQL SECURITY INVOKER VIEW `vw_monthly_revenue_statistics` AS
SELECT DATE_FORMAT(`created_at`, '%Y-%m') AS report_month,
       COUNT(*) AS successful_order_count,
       COALESCE(SUM(`subtotal`), 0) AS gross_amount,
       COALESCE(SUM(`discount_total`), 0) AS discount_amount,
       COALESCE(SUM(`total`), 0) AS revenue
FROM `orders`
WHERE `status` = 3
GROUP BY DATE_FORMAT(`created_at`, '%Y-%m');

CREATE OR REPLACE SQL SECURITY INVOKER VIEW `vw_quarter_revenue` AS
SELECT YEAR(`created_at`) AS report_year,
       QUARTER(`created_at`) AS report_quarter,
       CONCAT(YEAR(`created_at`), '-Q', QUARTER(`created_at`)) AS period,
       COUNT(*) AS order_count,
       COALESCE(SUM(`total`), 0) AS revenue
FROM `orders`
WHERE `status` = 3
GROUP BY report_year, report_quarter, period;

CREATE OR REPLACE SQL SECURITY INVOKER VIEW `vw_staff_revenue` AS
SELECT u.`id` AS staff_id,
       u.`fullname` AS staff_name,
       u.`email` AS staff_email,
       COUNT(o.`id`) AS order_count,
       COALESCE(SUM(o.`total`), 0) AS revenue
FROM `user` u
LEFT JOIN `orders` o ON o.`staff_id` = u.`id` AND o.`status` = 3
WHERE u.`role` IN (0, 1, 2)
GROUP BY u.`id`, u.`fullname`, u.`email`;

CREATE OR REPLACE SQL SECURITY INVOKER VIEW `vw_monthly_import_cost` AS
SELECT DATE_FORMAT(`created_at`, '%Y-%m') AS report_month,
       COUNT(*) AS receipt_count,
       COALESCE(SUM(`total_quantity`), 0) AS total_quantity,
       COALESCE(SUM(`total_amount`), 0) AS import_cost
FROM `import_receipt`
GROUP BY DATE_FORMAT(`created_at`, '%Y-%m');

CREATE OR REPLACE SQL SECURITY INVOKER VIEW `vw_monthly_profit` AS
SELECT m.report_month,
       COALESCE(r.revenue, 0) AS revenue,
       COALESCE(c.import_cost, 0) AS import_cost,
       COALESCE(r.revenue, 0) - COALESCE(c.import_cost, 0) AS profit
FROM (
  SELECT DATE_FORMAT(`created_at`, '%Y-%m') AS report_month FROM `orders` WHERE `status` = 3
  UNION
  SELECT DATE_FORMAT(`created_at`, '%Y-%m') FROM `import_receipt`
) m
LEFT JOIN `vw_monthly_revenue_statistics` r ON r.report_month = m.report_month
LEFT JOIN `vw_monthly_import_cost` c ON c.report_month = m.report_month;
