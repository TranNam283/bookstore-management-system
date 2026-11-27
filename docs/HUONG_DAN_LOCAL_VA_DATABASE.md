# Hướng dẫn chạy local, quản lý database và demo đồ án

## Chạy nhanh mỗi ngày (đã cài đặt xong)

Mở PowerShell tại thư mục dự án và chạy:

```powershell
cd D:\Downloads\bookstore-ecommerce
powershell -ExecutionPolicy Bypass -File .\chay-local.ps1
```

Script kiểm tra MySQL, rồi mở 2 cửa sổ **BACKEND** (cổng 8082) và **FRONTEND** (cổng 5173). Đợi 30–60 giây rồi vào http://localhost:5173/admin/login. Muốn tắt thì bấm `Ctrl + C` hoặc đóng hai cửa sổ đó. Các mục bên dưới dành cho lần cài đặt đầu tiên hoặc khi cần làm thủ công.

## 1. Yêu cầu cài đặt

| Phần mềm | Phiên bản | Ghi chú |
|---|---|---|
| Java JDK | 17 trở lên (máy hiện dùng 21) | chạy backend Spring Boot |
| Node.js | 20 trở lên | chạy frontend Vite/React |
| MySQL Server | 8.x | service `MySQL84`, cổng 3306 |
| MySQL Workbench | 8.0 | mở/xem database, chạy SQL |
| Docker Desktop | tùy chọn | chạy toàn bộ hệ thống bằng 1 lệnh |

## 2. Chạy local (không dùng Docker)

### 2.1. Database

```powershell
# Kiểm tra MySQL đang chạy (nếu Stopped: mở PowerShell quyền Admin -> Start-Service MySQL84)
Get-Service MySQL84

# Thêm MySQL vào PATH cho phiên PowerShell hiện tại
$env:Path += ";C:\Program Files\MySQL\MySQL Server 8.4\bin"
```

**Máy mới / database trống** – tạo database và nhập file `db/db.sql` (đã gồm bảng mới, view và dữ liệu mẫu):

```powershell
mysql -u root -p -e "CREATE DATABASE bookstore_dev CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
mysql -u root -p bookstore_dev -e "source D:/Downloads/bookstore-ecommerce/db/db.sql"
```

**Database cũ (trước khi nâng cấp)** – chạy lần lượt các file migration còn thiếu, mỗi file một lần:

```powershell
mysql -u root -p bookstore_dev -e "source D:/Downloads/bookstore-ecommerce/db/migrations/V2__thanh_tien_nhan_vien_phieu_nhap.sql"
mysql -u root -p bookstore_dev -e "source D:/Downloads/bookstore-ecommerce/db/migrations/V3__du_lieu_mau_thong_ke.sql"
mysql -u root -p bookstore_dev -e "source D:/Downloads/bookstore-ecommerce/db/migrations/V4__kho_hang_nha_cung_cap_lich_su.sql"
mysql -u root -p bookstore_dev -e "source D:/Downloads/bookstore-ecommerce/db/migrations/V5__vai_tro_nhan_vien.sql"
```

| File | Nội dung |
|---|---|
| V2 | Thành tiền từng dòng hóa đơn, nhân viên xác nhận đơn, phiếu nhập, view thống kê |
| V3 | Dữ liệu mẫu (227 đơn 01/2025 – 11/2026, 34 phiếu nhập, nhân viên, khách hàng) |
| V4 | Nhà cung cấp, phiếu xuất kho, thẻ kho (`stock_movement`), lịch sử trạng thái hóa đơn, hủy phiếu nhập |
| V5 | Vai trò nhân viên (0 quản trị, 1 bán hàng, 2 kho), thêm tài khoản quản trị và nhân viên kho để demo |

> PowerShell không hỗ trợ `mysql ... < file.sql`, vì vậy dùng `-e "source đường/dẫn.sql"` (đường dẫn dùng dấu `/`).

### 2.2. Backend (Spring Boot)

1. Tạo file `backend/.env.local` (file này **không** được commit, đã có trong `.gitignore`):

   ```properties
   SERVER_PORT=8082
   BACKEND_URL=http://localhost:8082
   FRONTEND_URL=http://localhost:5173
   DATASOURCE_URL=jdbc:mysql://localhost:3306/bookstore_dev?useSSL=false&serverTimezone=Asia/Ho_Chi_Minh&allowPublicKeyRetrieval=true
   DATASOURCE_USERNAME=root
   DATASOURCE_PASSWORD=<mật khẩu MySQL của bạn>
   JWT_SECRET=<chuỗi bí mật, xem docker-compose.yml>
   MOMO_PARTNERCODE=MOMO
   MOMO_ACCESSKEY=<xem docker-compose.yml>
   MOMO_SECRETKEY=<xem docker-compose.yml>
   MOMO_URL=https://test-payment.momo.vn/v2/gateway/api/create
   MOMO_REFUND_URL=https://test-payment.momo.vn/v2/gateway/api/refund
   ```

   Trên máy này cổng 8080 đang bị chương trình khác (`AgentService`) chiếm nên backend chạy cổng **8082**.

2. Chạy:

   ```powershell
   cd backend
   .\mvnw.cmd spring-boot:run
   ```

   Kiểm tra: mở http://localhost:8082/api/cache/info

### 2.3. Frontend (React)

1. File `frontend/.env.development.local` (không commit) trỏ tới backend:

   ```properties
   VITE_BACKEND_URL=http://localhost:8082
   ```

2. Chạy:

   ```powershell
   cd frontend
   npm install
   npm run dev
   ```

   - Trang khách hàng: http://localhost:5173
   - Trang quản trị: http://localhost:5173/admin/login

### 2.4. Tài khoản demo

| Vai trò (`user.role`) | Email | Mật khẩu |
|---|---|---|
| 0 – Quản trị viên hệ thống | `quanly@bookstore.vn` | `123456` |
| 1 – Nhân viên bán hàng | `lan.nguyen@bookstore.vn`, `minh.tran@bookstore.vn`, `hoa.le@bookstore.vn` | `123456` |
| 2 – Nhân viên kho | `kho.pham@bookstore.vn` | `123456` |
| 3 – Khách hàng (20 tài khoản mẫu) | ví dụ `minhanh.pham01@gmail.com` | `123456` |
| 0 – Quản trị viên (tài khoản cũ của nhóm) | `admin456@gmail.com` | mật khẩu cũ của nhóm |

Phân quyền trang quản trị:

| Chức năng | Quản trị viên (0) | Bán hàng (1) / Kho (2) |
|---|---|---|
| Xem danh sách + hồ sơ nhân viên, khách hàng | ✔ | ✔ (chỉ đọc) |
| Thêm / sửa / khóa / xóa nhân viên, khách hàng | ✔ | ✘ – hiện trang “Bạn không có quyền” |
| Các chức năng còn lại (hóa đơn, kho, sách, thống kê) | ✔ | ✔ |

## 3. Chạy bằng Docker (tùy chọn)

```powershell
docker compose up --build        # MySQL cổng 3307, backend 8080, frontend 5173
docker compose down -v           # xóa database Docker để lần sau nhập lại db/db.sql
```

Docker tự nhập `db/db.sql` khi tạo database lần đầu (chỉ các file ở thư mục `db/`, không chạy thư mục `db/migrations/`).

## 4. Quản lý database

### 4.1. Sao lưu và khôi phục

```powershell
# Sao lưu (file nằm trong db/backup/, không commit)
mysqldump -u root -p --single-transaction --routines bookstore_dev --result-file=db/backup/bookstore_dev_YYYYMMDD.sql

# Khôi phục
mysql -u root -p bookstore_dev -e "source D:/Downloads/bookstore-ecommerce/db/backup/bookstore_dev_YYYYMMDD.sql"
```

Bản sao lưu trước khi nâng cấp: `db/backup/bookstore_dev_before_v2.sql`.

### 4.2. Mở database cho giảng viên xem (MySQL Workbench)

1. Mở **MySQL Workbench** → chọn kết nối *Local instance MySQL84* (user `root`).
2. Khung **Schemas** bên trái → mở `bookstore_dev` → **Tables** / **Views**.
3. Xem dữ liệu: chuột phải vào bảng → **Select Rows - Limit 1000**.
4. Xem cấu trúc (khóa chính, khóa ngoại, ràng buộc CHECK): chuột phải vào bảng → **Alter Table...** (tab *Columns*, *Foreign Keys*).
5. Vẽ sơ đồ ERD: menu **Database → Reverse Engineer...** → chọn kết nối → chọn `bookstore_dev` → Next đến hết → Workbench vẽ sơ đồ quan hệ. Có thể **File → Export → Export as PNG** để đưa vào báo cáo.

Các bảng/cột quan trọng để giải thích:

| Bảng | Cột | Ý nghĩa |
|---|---|---|
| `orderdetail` | `price`, `discount` | đơn giá và tiền giảm / 1 cuốn tại thời điểm bán |
| | `subtotal` | tiền trước giảm = `quantity × price` |
| | `discount_amount` | tiền giảm = `quantity × discount` |
| | `line_total` | thành tiền = `subtotal − discount_amount` |
| `orders` | `subtotal`, `discount_total`, `total` | tổng trước giảm, tổng giảm, tổng thanh toán |
| | `staff_id`, `confirmed_at` | nhân viên xác nhận đơn (thống kê theo nhân viên) |
| `import_receipt` | `total_amount` | tổng tiền phiếu nhập = **tổng chi** |
| `import_receipt_detail` | `quantity`, `import_price`, `line_total` | từng sách nhập |
| `supplier` | `code`, `name`, `status` | nhà cung cấp; phiếu nhập bắt buộc chọn nhà cung cấp đang hợp tác |
| `import_receipt` | `supplier_id`, `status`, `cancel_reason` | phiếu nhập gắn nhà cung cấp; hủy phiếu thì trừ lại tồn kho |
| `export_receipt` | `type` | SALE (xuất bán theo hóa đơn), DAMAGED, RETURN_SUPPLIER, OTHER |
| `export_receipt_detail` | `quantity`, `unit_price`, `line_total` | từng sách xuất |
| `stock_movement` | `type`, `quantity_change`, `stock_after`, `ref_code` | **thẻ kho**: mỗi lần tồn kho thay đổi (nhập, bán, hủy/trả, xuất, điều chỉnh) |
| `order_status_history` | `from_status`, `to_status`, `staff_id` | lịch sử xử lý hóa đơn: ai đổi trạng thái, lúc nào |
| View `vw_quarter_revenue`, `vw_staff_revenue`, `vw_monthly_profit`, `vw_inventory`, … | | thống kê viết sẵn bằng SQL để đối chiếu |

### 4.3. Khi giảng viên yêu cầu viết query

- Trong Workbench: **Ctrl + T** mở tab SQL mới, viết câu lệnh, **Ctrl + Enter** chạy câu đang đặt con trỏ (Ctrl + Shift + Enter chạy tất cả).
- Bộ câu mẫu có chú thích: **`db/demo_queries.sql`** (File → Open SQL Script). Gồm: AND/OR với khoảng giá trị và tập hợp, BETWEEN/IN/LIKE, JOIN nhiều bảng, GROUP BY/HAVING, thống kê theo ngày/tháng/quý/năm, theo nhân viên, thu–chi–lợi nhuận, truy vấn con.
- Khi phải thêm/sửa/xóa để demo: bọc trong `START TRANSACTION; ... ROLLBACK;` để dữ liệu không bị hỏng.
- Nhớ dấu ngoặc khi trộn AND và OR: `WHERE (total >= 1000000 AND total <= 2000000) AND (city = 'Hồ Chí Minh' OR city = 'Hà Nội')`.

## 5. Ánh xạ yêu cầu đồ án → chức năng

| Yêu cầu | Ở đâu |
|---|---|
| Cache danh sách trên RAM, phiên 15 phút | Backend: `cache/ListCache`, `cache/DataCacheRegistry`; header admin hiện *"Phiên dữ liệu RAM 15': còn X phút"*, bấm để xem số lần đọc database; nút **Làm mới dữ liệu** |
| Tìm kiếm không query lại database | Tìm kiếm, sắp xếp, phân trang, thống kê đều chạy trên danh sách RAM; thêm/sửa dữ liệu thì danh sách liên quan tự nạp lại |
| Tìm kiếm nâng cao >, <, =, ≥, ≤ + AND/OR | Khung **Tìm kiếm nâng cao** ở trang Đơn hàng, Sách, Khách hàng, Phiếu nhập (nhóm nối bằng AND, trong nhóm chọn AND/OR); có nút mẫu *"(1tr–2tr) AND (HCM OR HN)"* |
| Giao diện tập trung dữ liệu | Bảng gọn, nhiều dòng (20/50/100), cột số căn phải, dòng tổng cuối bảng, bấm tiêu đề cột để sắp xếp |
| Main–sub form hóa đơn | Trang **Đơn hàng**: danh sách bên trái, chi tiết bên phải; phím ↑/↓ để chuyển đơn; đổi trạng thái ngay tại chỗ. Trang **Phiếu nhập** cùng bố cục |
| Thống kê theo thời gian (ngày/tuần/tháng/quý/năm) | **Báo cáo thống kê → Tổng tiền hóa đơn theo thời gian** (đánh dấu kỳ bán tốt nhất) |
| Doanh thu 12 tháng gần nhất | **Tổng quan**: trục ghi rõ tháng + năm (T12 / 2025 …), vạch chia năm, tổng từng năm |
| Top bán chạy | **Tổng quan → Top bán chạy**: lọc theo Ngày / Tuần / Tháng / Quý / Năm, nút ◀ ▶ chuyển kỳ, so với kỳ trước, cảnh báo tồn kho; xếp hạng thể loại bên cạnh |
| Mặt hàng & thể loại bán chạy theo từng thời điểm | **Báo cáo thống kê → Hàng bán chạy theo kỳ**: mỗi tháng / quý / năm một bảng xếp hạng sách + thể loại (bấm 1 kỳ để xem) |
| Biết trước mùa nào bán gì để nhập hàng | **Báo cáo thống kê → Mùa vụ & gợi ý nhập hàng**: cùng một quý (hoặc tháng) qua các năm, dự báo số cuốn bán, tồn kho, **đề xuất số lượng nhập** |
| Tổng thu – tổng chi – lợi nhuận | **Báo cáo thống kê → Thu – Chi – Lợi nhuận** |
| Theo nhân viên | **Báo cáo thống kê → Theo nhân viên** (nhân viên xác nhận đơn) |
| Lưu thành tiền từng dòng | Bảng `orderdetail` (`subtotal`, `discount_amount`, `line_total`) |
| Biểu đồ | Recharts trong trang Báo cáo thống kê và Tổng quan |
| Quản lý nhập hàng | **Kho hàng → Nhập hàng**: phiếu nhập theo nhà cung cấp, chi tiết từng sách, hủy phiếu (trừ lại tồn kho) |
| Quản lý xuất hàng | **Kho hàng → Xuất hàng**: phiếu xuất bán tự lập khi hóa đơn chuyển “Đang giao”; lập phiếu xuất hư hỏng / trả nhà cung cấp / khác |
| Tồn kho | **Kho hàng → Tồn kho & thẻ kho**: tồn từng sách, cảnh báo sắp hết, thẻ kho chi tiết; tab **Báo cáo nhập – xuất – tồn** theo khoảng ngày, xuất Excel (CSV) |
| Nhà cung cấp | **Kho hàng → Nhà cung cấp**: thêm / sửa / ngừng hợp tác, số phiếu và tổng tiền đã nhập |
| Hóa đơn chi tiết | Trang **Hóa đơn**: từng món (SL × đơn giá − giảm = thành tiền), phiếu xuất kho, **lịch sử xử lý**, nút **In hóa đơn** |
| Khách hàng | Trang **Khách hàng**: tổng số khách, đang hoạt động / bị chặn, khách mới; mỗi khách có SĐT, địa chỉ, ngày tạo tài khoản, tổng đơn, đơn thành công / hủy, tổng chi tiêu. Bấm 1 khách → từng hóa đơn đã mua (bấm hóa đơn xem từng món) và **nhân viên xử lý hóa đơn** |
| Nhân viên | Trang **Nhân viên**: tổng nhân sự, số người theo **vai trò**; mỗi nhân viên có số hóa đơn xử lý, doanh thu, phiếu nhập / xuất. Bấm 1 nhân viên → hóa đơn đã xử lý, phiếu nhập, phiếu xuất |

**Chứng minh cache khi demo:** bật log SQL (`spring.jpa.show-sql=true` đang bật sẵn), tìm kiếm/thống kê nhiều lần → console backend không in thêm câu `select ... from orders`; bấm **Làm mới dữ liệu** hoặc đổi trạng thái đơn → in lại đúng một lượt.

**Nếu sửa trực tiếp trong database** (Workbench) thì web vẫn hiện số cũ tối đa 15 phút, vì đó là cơ chế phiên RAM theo yêu cầu. Bấm **Làm mới dữ liệu** để đọc lại ngay.

## 6. Kiểm thử

```powershell
cd backend;  .\mvnw.cmd spotless:apply; .\mvnw.cmd test     # 447 test
cd frontend; npm run lint; npm run build
```

CI trên GitHub chạy `mvn spotless:check`, nên trước khi commit code Java luôn chạy `.\mvnw.cmd spotless:apply`.

## 7. Sinh lại dữ liệu mẫu

```powershell
node db/tools/generate-seed.mjs > db/migrations/V3__du_lieu_mau_thong_ke.sql
```

Script dùng seed cố định nên chạy lại vẫn ra cùng dữ liệu. File V3 chạy lại được: nó xóa các bản ghi mẫu cũ (id bắt đầu bằng `5eed`) trước khi thêm.
