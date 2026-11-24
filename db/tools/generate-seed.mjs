// Sinh dữ liệu mẫu cho thống kê (nhân viên, khách hàng, ~230 đơn hàng 01/2025 - 11/2026, phiếu nhập).
// Dữ liệu dựa trên ảnh chụp db/tools/seed-base.json (sách, user, địa chỉ có sẵn).
// Seed ngẫu nhiên cố định => chạy lại luôn ra cùng một kết quả.
//
//   node db/tools/generate-seed.mjs > db/migrations/V3__du_lieu_mau_thong_ke.sql
//
// Mọi bản ghi sinh ra có id bắt đầu bằng "5eed" nên file SQL chạy lại được (xóa seed cũ trước).
import { readFileSync } from "node:fs";

const base = JSON.parse(readFileSync(new URL("./seed-base.json", import.meta.url), "utf8"));

// ------------------------------------------------------------------ ngẫu nhiên có seed
function mulberry32(seed) {
  let a = seed;
  return () => {
    a |= 0;
    a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}
const rand = mulberry32(20251125);
const randint = (min, max) => min + Math.floor(rand() * (max - min + 1));
const pick = (arr) => arr[Math.floor(rand() * arr.length)];
const weighted = (items) => {
  const total = items.reduce((s, [, w]) => s + w, 0);
  let r = rand() * total;
  for (const [value, w] of items) {
    r -= w;
    if (r < 0) return value;
  }
  return items[items.length - 1][0];
};

let idCounter = 0;
const seedId = (type) => {
  idCounter += 1;
  return `5eed${type}-0000-4000-8000-${idCounter.toString(16).padStart(12, "0")}`;
};
const CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
const usedCodes = new Set(base.orderCodes);
const newCode = (prefix, len) => {
  let code;
  do {
    code = prefix;
    for (let i = 0; i < len; i++) code += CODE_CHARS[Math.floor(rand() * CODE_CHARS.length)];
  } while (usedCodes.has(code));
  usedCodes.add(code);
  return code;
};

// ------------------------------------------------------------------ thời gian (UTC thuần)
const DAY = 86400000;
const HOUR = 3600000;
const date = (y, m, d, h = 0, mi = 0, s = 0) => Date.UTC(y, m - 1, d, h, mi, s);
const fmt = (ts) => {
  const d = new Date(ts);
  const p = (n, l = 2) => String(n).padStart(l, "0");
  return `${d.getUTCFullYear()}-${p(d.getUTCMonth() + 1)}-${p(d.getUTCDate())} ${p(
    d.getUTCHours(),
  )}:${p(d.getUTCMinutes())}:${p(d.getUTCSeconds())}.000000`;
};
const TODAY = date(2026, 11, 25);
const LAST_ORDER_DAY = date(2026, 11, 24, 23, 59);
const START = { y: 2025, m: 1 };
const END = { y: 2026, m: 11 };
const months = [];
for (let y = START.y, m = START.m; y < END.y || (y === END.y && m <= END.m); ) {
  months.push({ y, m });
  m += 1;
  if (m > 12) {
    m = 1;
    y += 1;
  }
}
const daysInMonth = (y, m) => new Date(Date.UTC(y, m, 0)).getUTCDate();

const sql = (v) => {
  if (v === null || v === undefined) return "NULL";
  if (typeof v === "number") return Number.isInteger(v) ? String(v) : v.toFixed(2);
  return `'${String(v).replace(/\\/g, "\\\\").replace(/'/g, "''")}'`;
};

// ------------------------------------------------------------------ dữ liệu nền
const PASSWORD_123456 = "$2a$10$CfJc3NCQnXuzQx/hUtyxROkWJVBQYeL0wTT/u6Eh9DEkB24LevZ66";
const CITIES = [
  ["Hồ Chí Minh", 30],
  ["Hà Nội", 25],
  ["Đà Nẵng", 12],
  ["Cần Thơ", 7],
  ["Hải Phòng", 7],
  ["Huế", 5],
  ["Khánh Hòa", 4],
  ["Đồng Nai", 4],
  ["Bắc Ninh", 3],
  ["Quảng Ninh", 3],
];
const WARDS = {
  "Hồ Chí Minh": ["Phường Sài Gòn", "Phường Bến Thành", "Phường Tân Định", "Phường Gia Định"],
  "Hà Nội": ["Phường Ba Đình", "Phường Hoàn Kiếm", "Phường Cửa Nam", "Phường Đống Đa"],
  "Đà Nẵng": ["Phường Hải Châu", "Phường Thanh Khê", "Phường Sơn Trà"],
  "Cần Thơ": ["Phường Ninh Kiều", "Phường Cái Khế"],
  "Hải Phòng": ["Phường Hồng Bàng", "Phường Lê Chân"],
  Huế: ["Phường Thuận Hóa", "Phường Phú Xuân"],
  "Khánh Hòa": ["Phường Nha Trang", "Phường Bắc Nha Trang"],
  "Đồng Nai": ["Phường Biên Hòa", "Phường Trấn Biên"],
  "Bắc Ninh": ["Phường Kinh Bắc", "Phường Võ Cường"],
  "Quảng Ninh": ["Phường Hạ Long", "Phường Bãi Cháy"],
};
const STREETS = [
  "Lê Lợi",
  "Nguyễn Huệ",
  "Trần Hưng Đạo",
  "Hai Bà Trưng",
  "Lý Thường Kiệt",
  "Điện Biên Phủ",
  "Nguyễn Trãi",
  "Phan Đình Phùng",
  "Lê Duẩn",
  "Quang Trung",
];
const phone = () => `0${pick(["3", "5", "7", "8", "9"])}${String(randint(10000000, 99999999))}`;
const street = () => `${randint(1, 250)} ${pick(STREETS)}`;

// Nhân viên mới (role 1) + nhân viên có sẵn
const NEW_STAFF = [
  ["Nguyễn Thị Lan", "lan.nguyen@bookstore.vn"],
  ["Trần Văn Minh", "minh.tran@bookstore.vn"],
  ["Lê Thị Hoa", "hoa.le@bookstore.vn"],
];
const users = [];
const staff = NEW_STAFF.map(([fullname, email]) => {
  const u = { id: seedId("00a1"), fullname, email, role: 1, createdAt: date(2024, 12, 1, 8) };
  users.push(u);
  return u;
});
const existingAdmin = base.users.find((u) => u.role === 0);
const existingStaff1 = base.users.find((u) => u.email === "admin456789@gmail.com");
const STAFF_WEIGHTS = [
  [staff[0].id, 32],
  [staff[1].id, 26],
  [staff[2].id, 20],
  ...(existingStaff1 ? [[existingStaff1.id, 14]] : []),
  ...(existingAdmin ? [[existingAdmin.id, 8]] : []),
];
const RECEIPT_STAFF = [
  [staff[2].id, 40],
  [staff[1].id, 25],
  ...(existingAdmin ? [[existingAdmin.id, 20]] : []),
  ...(existingStaff1 ? [[existingStaff1.id, 15]] : []),
];

// Khách hàng mới
const CUSTOMER_NAMES = [
  ["Phạm Minh Anh", "minhanh.pham"],
  ["Võ Thanh Bình", "thanhbinh.vo"],
  ["Đặng Thu Trang", "thutrang.dang"],
  ["Hoàng Gia Huy", "giahuy.hoang"],
  ["Bùi Ngọc Mai", "ngocmai.bui"],
  ["Ngô Quốc Bảo", "quocbao.ngo"],
  ["Đỗ Khánh Linh", "khanhlinh.do"],
  ["Huỳnh Tấn Phát", "tanphat.huynh"],
  ["Lý Mỹ Duyên", "myduyen.ly"],
  ["Trương Hoài Nam", "hoainam.truong"],
  ["Phan Thảo Vy", "thaovy.phan"],
  ["Vũ Đức Thắng", "ducthang.vu"],
  ["Mai Phương Thảo", "phuongthao.mai"],
  ["Tô Minh Khoa", "minhkhoa.to"],
  ["Lâm Bảo Ngọc", "baongoc.lam"],
  ["Dương Văn Hậu", "vanhau.duong"],
  ["Cao Thùy Dung", "thuydung.cao"],
  ["Hà Anh Tuấn", "anhtuan.ha"],
  ["Châu Kim Ngân", "kimngan.chau"],
  ["Kiều Trọng Nghĩa", "trongnghia.kieu"],
];
const addresses = [];
const customers = CUSTOMER_NAMES.map(([fullname, slug], i) => {
  const createdAt = date(2024, 11, 1, 9) + randint(0, 210) * DAY + randint(0, 12) * HOUR;
  const u = {
    id: seedId("00a1"),
    fullname,
    email: `${slug}${String(i + 1).padStart(2, "0")}@gmail.com`,
    role: 3,
    createdAt,
    addresses: [],
  };
  users.push(u);
  const count = rand() < 0.3 ? 2 : 1;
  const mainCity = weighted(CITIES);
  for (let k = 0; k < count; k++) {
    const city = k === 0 || rand() < 0.6 ? mainCity : weighted(CITIES);
    const a = {
      id: seedId("00a2"),
      userId: u.id,
      fullname,
      phone: phone(),
      speaddress: street(),
      ward: pick(WARDS[city]),
      city,
      createdAt: createdAt + (k + 1) * HOUR,
    };
    addresses.push(a);
    u.addresses.push(a);
  }
  return u;
});
// khách hàng có sẵn (đặt hàng sau ngày tạo tài khoản)
for (const u of base.users.filter((x) => x.role === 3)) {
  const own = base.addresses.filter((a) => a.userId === u.id);
  const [y, m, d] = u.createdAt.slice(0, 10).split("-").map(Number);
  customers.push({
    id: u.id,
    fullname: u.fullname,
    email: u.email,
    createdAt: date(y, m, d) + DAY,
    addresses: own.length
      ? own
      : [
          {
            fullname: u.fullname,
            phone: phone(),
            speaddress: street(),
            ward: "Phường Hải Châu",
            city: "Đà Nẵng",
          },
        ],
  });
}

// Sách: độ phổ biến cố định cho từng cuốn
const books = base.books.map((b) => ({
  ...b,
  popularity: randint(1, 6),
  importRate: randint(55, 66) / 100,
}));
const bookWeights = books.map((b) => [b, b.popularity]);

// ------------------------------------------------------------------ đơn hàng
const SEASON = { 1: 0.8, 2: 0.6, 3: 0.7, 4: 0.8, 5: 0.9, 6: 1.0, 7: 1.0, 8: 1.1, 9: 1.2, 10: 1.1, 11: 1.3, 12: 1.5 };
const GROWTH = { 2025: 1.0, 2026: 1.25 };
const BASE_PER_MONTH = 9;

const orders = [];
const details = [];
const payments = [];
const soldByBookMonth = new Map(); // bookId -> Map(monthIndex -> qty)

months.forEach(({ y, m }, monthIndex) => {
  let count = BASE_PER_MONTH * SEASON[m] * GROWTH[y] * (0.85 + rand() * 0.3);
  if (y === 2026 && m === 11) count *= 24 / 30;
  count = Math.round(count);

  for (let i = 0; i < count; i++) {
    const day = randint(1, daysInMonth(y, m));
    let createdAt = date(y, m, day, randint(8, 21), randint(0, 59), randint(0, 59));
    if (createdAt > LAST_ORDER_DAY) createdAt = LAST_ORDER_DAY - randint(1, 72) * HOUR;

    const eligible = customers.filter((c) => c.createdAt < createdAt);
    if (eligible.length === 0) continue;
    const customer = pick(eligible);
    const address = rand() < 0.85 ? customer.addresses[0] : pick(customer.addresses);

    // dòng sách
    const bulk = rand() < 0.05;
    const lineCount = bulk ? randint(1, 2) : weighted([[1, 45], [2, 30], [3, 17], [4, 8]]);
    const chosen = new Set();
    while (chosen.size < lineCount) chosen.add(weighted(bookWeights));

    const orderId = seedId("00b1");
    let subtotal = 0;
    let discountTotal = 0;
    const lines = [...chosen].map((book) => {
      const quantity = bulk ? randint(6, 12) : weighted([[1, 60], [2, 28], [3, 12]]);
      const price = book.price;
      const discount = book.discount;
      const lineSubtotal = quantity * price;
      const discountAmount = quantity * discount;
      subtotal += lineSubtotal;
      discountTotal += discountAmount;
      return {
        id: seedId("00b2"),
        orderId,
        book,
        quantity,
        price,
        discount,
        subtotal: lineSubtotal,
        discountAmount,
        lineTotal: lineSubtotal - discountAmount,
      };
    });
    const total = subtotal - discountTotal;

    // trạng thái
    const ageDays = (TODAY - createdAt) / DAY;
    const status =
      ageDays > 12
        ? weighted([[3, 82], [4, 12], [5, 6]])
        : weighted([[0, 25], [1, 25], [2, 25], [3, 20], [4, 5]]);
    const confirmed = [1, 2, 3, 5].includes(status) || (status === 4 && rand() < 0.5);
    const staffId = confirmed ? weighted(STAFF_WEIGHTS) : null;
    const confirmedAt = confirmed ? createdAt + randint(1, 20) * HOUR : null;
    const paymethod = rand() < 0.68 ? "cod" : "momo";

    orders.push({
      id: orderId,
      code: newCode("", 6),
      createdAt,
      customer,
      address,
      status,
      paymethod,
      subtotal,
      discountTotal,
      total,
      staffId,
      confirmedAt,
    });
    details.push(...lines);

    if (paymethod === "momo") {
      const transactionId = String(randint(1000000000, 4294967295));
      payments.push({
        id: seedId("00b3"),
        orderId,
        amount: total,
        createdAt: createdAt + randint(1, 5) * 60000,
        status: 1,
        transactionId,
      });
      if (status === 4 || status === 5) {
        payments.push({
          id: seedId("00b3"),
          orderId,
          amount: total,
          createdAt: createdAt + randint(1, 5) * DAY,
          status: 0,
          transactionId,
        });
      }
    }

    // hủy / trả hàng thì hoàn kho -> không tính vào lượng bán
    if (status !== 4 && status !== 5) {
      for (const line of lines) {
        const byMonth = soldByBookMonth.get(line.book.id) ?? new Map();
        byMonth.set(monthIndex, (byMonth.get(monthIndex) ?? 0) + line.quantity);
        soldByBookMonth.set(line.book.id, byMonth);
      }
    }
  }
});

// ------------------------------------------------------------------ phiếu nhập (gom theo nhà xuất bản khi sắp hết hàng)
const stock = new Map(books.map((b) => [b.id, b.stock]));
const sales = (book, monthIndex) => soldByBookMonth.get(book.id)?.get(monthIndex) ?? 0;
const booksByPublisher = new Map();
for (const book of books) {
  if (!booksByPublisher.has(book.publisherId)) booksByPublisher.set(book.publisherId, []);
  booksByPublisher.get(book.publisherId).push(book);
}
const reorders = []; // {monthIndex, publisherId, lines: [{book, quantity}]}
months.forEach((_, monthIndex) => {
  for (const [publisherId, list] of booksByPublisher) {
    const urgent = list.some((b) => stock.get(b.id) - sales(b, monthIndex) < 5);
    if (!urgent) continue;
    // nhập luôn các sách cùng nhà xuất bản sắp hết trong 2 tháng tới
    const lines = list
      .filter((b) => stock.get(b.id) - sales(b, monthIndex) - sales(b, monthIndex + 1) < 12)
      .map((b) => {
        const need = Math.max(0, sales(b, monthIndex) + sales(b, monthIndex + 1) + 5 - stock.get(b.id));
        const quantity = Math.ceil((need + randint(8, 15)) / 5) * 5;
        stock.set(b.id, stock.get(b.id) + quantity);
        return { book: b, quantity };
      });
    reorders.push({ monthIndex, publisherId, lines });
  }
  for (const b of books) stock.set(b.id, stock.get(b.id) - sales(b, monthIndex));
});
const finalStock = stock;

const receipts = [];
const receiptDetails = [];
for (const { monthIndex, publisherId, lines } of reorders) {
  const { y, m } = months[monthIndex];
  const createdAt = date(y, m, randint(1, 4), randint(8, 11), randint(0, 59));
  const id = seedId("00c1");
  let totalQuantity = 0;
  let totalAmount = 0;
  for (const { book, quantity } of lines) {
    const importPrice =
      Math.round((book.price * book.importRate * (0.98 + rand() * 0.04)) / 1000) * 1000;
    const lineTotal = importPrice * quantity;
    totalQuantity += quantity;
    totalAmount += lineTotal;
    receiptDetails.push({ id: seedId("00c2"), receiptId: id, book, quantity, importPrice, lineTotal });
  }
  receipts.push({
    id,
    code: newCode("PN", 6),
    publisherId,
    staffId: weighted(RECEIPT_STAFF),
    note:
      lines.length > 1
        ? `Nhập hàng định kỳ tháng ${String(m).padStart(2, "0")}/${y}`
        : `Bổ sung tồn kho: ${lines[0].book.title}`.slice(0, 250),
    totalQuantity,
    totalAmount,
    createdAt,
  });
}

// thống kê nhanh (in ra stderr để kiểm tra)
{
  const revenue = orders.filter((o) => o.status === 3).reduce((s, o) => s + o.total, 0);
  const cost = receipts.reduce((s, r) => s + r.totalAmount, 0);
  const inRange = orders.filter((o) => o.total >= 1e6 && o.total <= 2e6).length;
  process.stderr.write(
    `orders=${orders.length} revenue=${revenue} importCost=${cost} profit=${revenue - cost} ` +
      `orders1to2tr=${inRange} receipts=${receipts.length} lines=${receiptDetails.length} ` +
      `minStock=${Math.min(...finalStock.values())}
`,
  );
}

// ------------------------------------------------------------------ xuất SQL
const out = [];
const insert = (table, columns, rows) => {
  for (let i = 0; i < rows.length; i += 200) {
    out.push(`INSERT INTO \`${table}\` (${columns.map((c) => `\`${c}\``).join(", ")}) VALUES`);
    out.push(rows.slice(i, i + 200).map((r) => `  (${r.map(sql).join(", ")})`).join(",\n") + ";");
  }
};

const delivered = orders.filter((o) => o.status === 3);
out.push(`-- =====================================================================
-- V3: Dữ liệu mẫu cho thống kê (sinh bởi db/tools/generate-seed.mjs - KHÔNG sửa tay)
--   ${users.filter((u) => u.role === 1).length} nhân viên mới, ${CUSTOMER_NAMES.length} khách hàng mới, ${addresses.length} địa chỉ
--   ${orders.length} đơn hàng (${delivered.length} giao thành công), ${details.length} dòng chi tiết, ${payments.length} thanh toán
--   ${receipts.length} phiếu nhập, ${receiptDetails.length} dòng chi tiết phiếu nhập
-- Tài khoản mới đều có mật khẩu: 123456
-- Chạy sau V2:  mysql -u root -p bookstore_dev < db/migrations/V3__du_lieu_mau_thong_ke.sql
-- Chạy lại được: các bản ghi seed (id bắt đầu bằng '5eed') được xóa trước khi thêm.
-- =====================================================================
SET NAMES utf8mb4;
START TRANSACTION;

DELETE FROM \`payment\` WHERE \`id\` LIKE '5eed%';
DELETE FROM \`orderdetail\` WHERE \`id\` LIKE '5eed%';
DELETE FROM \`orders\` WHERE \`id\` LIKE '5eed%';
DELETE FROM \`import_receipt_detail\` WHERE \`id\` LIKE '5eed%';
DELETE FROM \`import_receipt\` WHERE \`id\` LIKE '5eed%';
DELETE FROM \`address\` WHERE \`id\` LIKE '5eed%';
DELETE FROM \`user\` WHERE \`id\` LIKE '5eed%';
`);

out.push("-- Nhân viên & khách hàng");
insert(
  "user",
  ["id", "created_at", "email", "password", "role", "status", "fullname"],
  users.map((u) => [u.id, fmt(u.createdAt), u.email, PASSWORD_123456, u.role, 1, u.fullname]),
);
insert(
  "address",
  ["id", "city", "created_at", "fullname", "phone", "speaddress", "ward", "user_id"],
  addresses.map((a) => [a.id, a.city, fmt(a.createdAt), a.fullname, a.phone, a.speaddress, a.ward, a.userId]),
);

out.push("\n-- Đơn hàng (tiền trước giảm, tiền giảm, thành tiền, nhân viên xác nhận)");
insert(
  "orders",
  [
    "id", "city", "created_at", "fullname", "order_code", "paymethod", "phone", "speaddress",
    "status", "total", "ward", "user_id", "subtotal", "discount_total", "staff_id", "confirmed_at",
  ],
  orders.map((o) => [
    o.id, o.address.city, fmt(o.createdAt), o.address.fullname, o.code, o.paymethod, o.address.phone,
    o.address.speaddress, o.status, o.total, o.address.ward, o.customer.id, o.subtotal, o.discountTotal,
    o.staffId, o.confirmedAt ? fmt(o.confirmedAt) : null,
  ]),
);
insert(
  "orderdetail",
  ["id", "discount", "price", "quantity", "book_id", "order_id", "subtotal", "discount_amount", "line_total"],
  details.map((d) => [d.id, d.discount, d.price, d.quantity, d.book.id, d.orderId, d.subtotal, d.discountAmount, d.lineTotal]),
);
insert(
  "payment",
  ["id", "amount", "created_at", "paymethod", "status", "transaction_id", "order_id"],
  payments.map((p) => [p.id, p.amount, fmt(p.createdAt), "momo", p.status, p.transactionId, p.orderId]),
);

out.push("\n-- Phiếu nhập hàng (tổng chi)");
insert(
  "import_receipt",
  ["id", "receipt_code", "publisher_id", "staff_id", "note", "total_quantity", "total_amount", "created_at"],
  receipts.map((r) => [r.id, r.code, r.publisherId, r.staffId, r.note, r.totalQuantity, r.totalAmount, fmt(r.createdAt)]),
);
insert(
  "import_receipt_detail",
  ["id", "receipt_id", "book_id", "quantity", "import_price", "line_total"],
  receiptDetails.map((d) => [d.id, d.receiptId, d.book.id, d.quantity, d.importPrice, d.lineTotal]),
);

out.push("\n-- Đơn hàng cũ (trước khi có cột nhân viên): gán nhân viên xác nhận");
if (existingStaff1) {
  out.push(`UPDATE \`orders\` SET \`staff_id\` = ${sql(existingStaff1.id)}, \`confirmed_at\` = DATE_ADD(\`created_at\`, INTERVAL 2 HOUR)
WHERE \`id\` NOT LIKE '5eed%' AND \`status\` IN (1, 2, 3, 5) AND \`staff_id\` IS NULL;`);
}

out.push("\n-- Tồn kho sau khi bán và nhập hàng");
for (const book of books) {
  out.push(`UPDATE \`book\` SET \`stock\` = ${finalStock.get(book.id)} WHERE \`id\` = ${sql(book.id)};`);
}
out.push("\nCOMMIT;\n");

process.stdout.write(out.join("\n"));
