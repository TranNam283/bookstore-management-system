package com.bookstore.backend.service;

import com.bookstore.backend.dto.BookDTO;
import com.bookstore.backend.dto.ImportReceiptDTO;
import com.bookstore.backend.dto.OrderDTO;
import com.bookstore.backend.dto.OrderDetailDTO;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Thống kê kinh doanh, tính hoàn toàn trên danh sách đơn hàng và phiếu nhập đã cache trên RAM.
 *
 * <p>Doanh thu (tổng thu) = tổng tiền các hóa đơn ở trạng thái 3 (Giao thành công). Tổng chi = tổng
 * tiền các phiếu nhập hàng. Lợi nhuận = tổng thu - tổng chi.
 *
 * <p>Hàng bán chạy = số cuốn trên các hóa đơn đã bán (đã xác nhận / đang giao / giao thành công;
 * không tính đơn chờ xác nhận, đã hủy, trả hàng), gộp theo sách và theo thể loại.
 */
@Service
public class StatisticsService {

  public static final int STATUS_DELIVERED = 3;
  public static final Set<Integer> SOLD_STATUSES = Set.of(1, 2, 3);
  private static final int MAX_DAY_PERIODS = 1100;
  private static final String NO_CATEGORY = "(Chưa phân loại)";

  public enum GroupBy {
    DAY,
    WEEK,
    MONTH,
    QUARTER,
    YEAR;

    public static GroupBy from(String value) {
      if (value == null || value.isBlank()) {
        return MONTH;
      }
      try {
        return GroupBy.valueOf(value.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException(
            "Kiểu thống kê không hợp lệ (day, week, month, quarter, year): " + value);
      }
    }
  }

  private final OrderService orderService;
  private final ImportReceiptService importReceiptService;
  private final BookService bookService;

  public StatisticsService(
      OrderService orderService,
      ImportReceiptService importReceiptService,
      BookService bookService) {
    this.orderService = orderService;
    this.importReceiptService = importReceiptService;
    this.bookService = bookService;
  }

  // ===================================================== tổng quan (dashboard)

  public Map<String, Object> getOverview() {
    List<OrderDTO> delivered = deliveredOrders(orderService.getCachedOrders());
    List<ImportReceiptDTO> receipts = activeReceipts();
    LocalDate today = LocalDate.now();

    double totalRevenue = sum(delivered, OrderDTO::getTotal);
    double totalImportCost = receipts.stream().mapToDouble(r -> nz(r.getTotalAmount())).sum();
    List<OrderDTO> todayOrders =
        delivered.stream().filter(o -> today.equals(dateOf(o.getCreatedAt()))).toList();

    Map<String, Object> stats = new LinkedHashMap<>();
    stats.put("totalRevenue", totalRevenue);
    stats.put("todayRevenue", sum(todayOrders, OrderDTO::getTotal));
    stats.put("totalSoldQuantity", quantity(delivered));
    stats.put("todaySoldQuantity", quantity(todayOrders));
    stats.put("totalImportCost", totalImportCost);
    stats.put("totalProfit", totalRevenue - totalImportCost);
    stats.put("deliveredOrderCount", delivered.size());

    LocalDate from = YearMonth.now().minusMonths(11).atDay(1);
    stats.put(
        "monthlyRevenue",
        revenueRows(delivered, from, today, GroupBy.MONTH).stream()
            .map(
                row -> {
                  Map<String, Object> month = new LinkedHashMap<>();
                  month.put("month", row.get("period"));
                  month.put("label", row.get("label"));
                  month.put("orderCount", row.get("orderCount"));
                  month.put("revenue", row.get("revenue"));
                  return month;
                })
            .toList());
    stats.put("cachedAt", cachedAt());
    return stats;
  }

  // ===================================================== tổng tiền hóa đơn theo thời gian

  public Map<String, Object> getRevenue(LocalDate from, LocalDate to, GroupBy groupBy) {
    List<OrderDTO> orders = orderService.getCachedOrders();
    LocalDate[] range = resolveRange(from, to, orders);
    List<OrderDTO> delivered = inRange(deliveredOrders(orders), range[0], range[1]);
    List<Map<String, Object>> rows = revenueRows(delivered, range[0], range[1], groupBy);

    Map<String, Object> summary = new LinkedHashMap<>();
    summary.put("orderCount", delivered.size());
    summary.put("totalQuantity", quantity(delivered));
    summary.put("subtotal", sum(delivered, OrderDTO::getSubtotal));
    summary.put("discountTotal", sum(delivered, OrderDTO::getDiscountTotal));
    summary.put("revenue", sum(delivered, OrderDTO::getTotal));
    summary.put(
        "averageOrderValue",
        delivered.isEmpty() ? 0 : sum(delivered, OrderDTO::getTotal) / delivered.size());

    return result(range, groupBy, rows, summary, best(rows, "revenue"));
  }

  private List<Map<String, Object>> revenueRows(
      List<OrderDTO> delivered, LocalDate from, LocalDate to, GroupBy groupBy) {
    Map<String, List<OrderDTO>> byPeriod =
        delivered.stream()
            .collect(Collectors.groupingBy(o -> periodKey(dateOf(o.getCreatedAt()), groupBy)));

    List<Map<String, Object>> rows = new ArrayList<>();
    for (LocalDate start : periods(from, to, groupBy)) {
      String key = periodKey(start, groupBy);
      List<OrderDTO> list = byPeriod.getOrDefault(key, List.of());
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("period", key);
      row.put("label", periodLabel(start, groupBy));
      row.put("orderCount", list.size());
      row.put("totalQuantity", quantity(list));
      row.put("subtotal", sum(list, OrderDTO::getSubtotal));
      row.put("discountTotal", sum(list, OrderDTO::getDiscountTotal));
      row.put("revenue", sum(list, OrderDTO::getTotal));
      rows.add(row);
    }
    return rows;
  }

  // ===================================================== tổng thu - tổng chi - lợi nhuận

  public Map<String, Object> getProfit(LocalDate from, LocalDate to, GroupBy groupBy) {
    List<OrderDTO> orders = orderService.getCachedOrders();
    List<ImportReceiptDTO> receipts = activeReceipts();
    LocalDate[] range = resolveRange(from, to, orders);

    List<OrderDTO> delivered = inRange(deliveredOrders(orders), range[0], range[1]);
    List<ImportReceiptDTO> receiptsInRange =
        receipts.stream()
            .filter(r -> within(dateOf(r.getCreatedAt()), range[0], range[1]))
            .toList();

    Map<String, List<OrderDTO>> ordersByPeriod =
        delivered.stream()
            .collect(Collectors.groupingBy(o -> periodKey(dateOf(o.getCreatedAt()), groupBy)));
    Map<String, List<ImportReceiptDTO>> receiptsByPeriod =
        receiptsInRange.stream()
            .collect(Collectors.groupingBy(r -> periodKey(dateOf(r.getCreatedAt()), groupBy)));

    List<Map<String, Object>> rows = new ArrayList<>();
    for (LocalDate start : periods(range[0], range[1], groupBy)) {
      String key = periodKey(start, groupBy);
      List<OrderDTO> periodOrders = ordersByPeriod.getOrDefault(key, List.of());
      List<ImportReceiptDTO> periodReceipts = receiptsByPeriod.getOrDefault(key, List.of());
      double revenue = sum(periodOrders, OrderDTO::getTotal);
      double cost = periodReceipts.stream().mapToDouble(r -> nz(r.getTotalAmount())).sum();

      Map<String, Object> row = new LinkedHashMap<>();
      row.put("period", key);
      row.put("label", periodLabel(start, groupBy));
      row.put("orderCount", periodOrders.size());
      row.put("receiptCount", periodReceipts.size());
      row.put("revenue", revenue);
      row.put("importCost", cost);
      row.put("profit", revenue - cost);
      rows.add(row);
    }

    double totalRevenue = sum(delivered, OrderDTO::getTotal);
    double totalCost = receiptsInRange.stream().mapToDouble(r -> nz(r.getTotalAmount())).sum();
    Map<String, Object> summary = new LinkedHashMap<>();
    summary.put("orderCount", delivered.size());
    summary.put("receiptCount", receiptsInRange.size());
    summary.put("revenue", totalRevenue);
    summary.put("importCost", totalCost);
    summary.put("profit", totalRevenue - totalCost);
    summary.put("margin", totalRevenue == 0 ? 0 : (totalRevenue - totalCost) / totalRevenue);

    return result(range, groupBy, rows, summary, best(rows, "profit"));
  }

  // ===================================================== tổng tiền hóa đơn theo nhân viên

  public Map<String, Object> getRevenueByStaff(LocalDate from, LocalDate to) {
    List<OrderDTO> orders = orderService.getCachedOrders();
    LocalDate[] range = resolveRange(from, to, orders);
    List<OrderDTO> inRange =
        inRange(
            orders.stream().filter(o -> o.getStatus() != null && o.getStatus() >= 1).toList(),
            range[0],
            range[1]);

    Map<String, List<OrderDTO>> byStaff =
        inRange.stream()
            .collect(
                Collectors.groupingBy(
                    o -> o.getStaffId() == null ? "" : o.getStaffId(),
                    LinkedHashMap::new,
                    Collectors.toList()));

    List<Map<String, Object>> rows = new ArrayList<>();
    for (Map.Entry<String, List<OrderDTO>> entry : byStaff.entrySet()) {
      List<OrderDTO> list = entry.getValue();
      List<OrderDTO> delivered = deliveredOrders(list);
      double revenue = sum(delivered, OrderDTO::getTotal);

      Map<String, Object> row = new LinkedHashMap<>();
      row.put("staffId", entry.getKey().isEmpty() ? null : entry.getKey());
      row.put(
          "staffName",
          entry.getKey().isEmpty()
              ? "(Chưa ghi nhận nhân viên)"
              : Objects.requireNonNullElse(list.get(0).getStaffName(), entry.getKey()));
      row.put("confirmedCount", list.size());
      row.put("deliveredCount", delivered.size());
      row.put(
          "cancelledCount",
          list.stream().filter(o -> o.getStatus() == 4 || o.getStatus() == 5).count());
      row.put("totalQuantity", quantity(delivered));
      row.put("revenue", revenue);
      row.put("averageOrderValue", delivered.isEmpty() ? 0 : revenue / delivered.size());
      rows.add(row);
    }
    rows.sort(
        Comparator.comparing((Map<String, Object> row) -> (Double) row.get("revenue")).reversed());

    Map<String, Object> summary = new LinkedHashMap<>();
    summary.put("staffCount", rows.stream().filter(r -> r.get("staffId") != null).count());
    summary.put("confirmedCount", inRange.size());
    summary.put("deliveredCount", deliveredOrders(inRange).size());
    summary.put("revenue", sum(deliveredOrders(inRange), OrderDTO::getTotal));

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("from", range[0]);
    result.put("to", range[1]);
    result.put("rows", rows);
    result.put("summary", summary);
    result.put(
        "best", rows.stream().filter(r -> r.get("staffId") != null).findFirst().orElse(null));
    result.put("cachedAt", cachedAt());
    return result;
  }

  // ===================================================== hàng bán chạy

  /** Một dòng sách đã bán trên hóa đơn, kèm thể loại lấy từ danh sách sách (RAM). */
  private record SoldLine(
      LocalDate date,
      String orderId,
      String bookId,
      String title,
      String image,
      String category,
      int quantity,
      double amount) {}

  /**
   * Top sách & thể loại bán chạy trong một kỳ (ngày / tuần / tháng / quý / năm chứa ngày {@code
   * date}) hoặc trong khoảng {@code from} - {@code to}; so sánh với kỳ liền trước.
   */
  public Map<String, Object> getTopBooks(
      GroupBy period,
      LocalDate date,
      LocalDate from,
      LocalDate to,
      int limit,
      boolean deliveredOnly) {
    LocalDate start;
    LocalDate end;
    LocalDate prevStart;
    LocalDate prevEnd;
    String label;
    String prevLabel;
    if (from != null || to != null) {
      LocalDate[] range = resolveRange(from, to, orderService.getCachedOrders());
      start = range[0];
      end = range[1];
      long days = ChronoUnit.DAYS.between(start, end) + 1;
      prevEnd = start.minusDays(1);
      prevStart = prevEnd.minusDays(days - 1);
      label = formatDay(start) + " – " + formatDay(end);
      prevLabel = formatDay(prevStart) + " – " + formatDay(prevEnd);
    } else {
      LocalDate anchor = date != null ? date : LocalDate.now();
      start = periodStart(anchor, period);
      end = periodEnd(start, period);
      prevStart = periodStart(start.minusDays(1), period);
      prevEnd = start.minusDays(1);
      label = periodTitle(start, period);
      prevLabel = periodTitle(prevStart, period);
    }

    List<SoldLine> all = soldLines(deliveredOnly);
    List<SoldLine> current = linesBetween(all, start, end);
    List<SoldLine> previous = linesBetween(all, prevStart, prevEnd);

    Map<String, Object> summary = salesSummary(current);
    summary.put("previousQuantity", totalQuantity(previous));
    summary.put("previousRevenue", totalAmount(previous));

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("period", period.name().toLowerCase(Locale.ROOT));
    result.put("from", start);
    result.put("to", end);
    result.put("label", label);
    result.put("previousFrom", prevStart);
    result.put("previousTo", prevEnd);
    result.put("previousLabel", prevLabel);
    result.put("deliveredOnly", deliveredOnly);
    result.put("books", rankBooks(current, previous, limit));
    result.put("categories", rankCategories(current, previous));
    result.put("summary", summary);
    result.put("cachedAt", cachedAt());
    return result;
  }

  /**
   * Hàng bán chạy theo từng kỳ trong khoảng thời gian: mỗi kỳ có bảng xếp hạng sách và thể loại
   * riêng (ví dụ quý 3 bán chạy sách nào, thể loại nào), cộng thêm bảng xếp hạng cả khoảng.
   */
  public Map<String, Object> getBestsellersByPeriod(
      LocalDate from, LocalDate to, GroupBy groupBy, int limit, boolean deliveredOnly) {
    LocalDate[] range = resolveRange(from, to, orderService.getCachedOrders());
    List<SoldLine> all = soldLines(deliveredOnly);
    Map<String, List<SoldLine>> byPeriod =
        all.stream().collect(Collectors.groupingBy(l -> periodKey(l.date(), groupBy)));

    List<Map<String, Object>> rows = new ArrayList<>();
    for (LocalDate start : periods(range[0], range[1], groupBy)) {
      String key = periodKey(start, groupBy);
      List<SoldLine> lines =
          linesBetween(byPeriod.getOrDefault(key, List.of()), range[0], range[1]);
      List<SoldLine> previous =
          byPeriod.getOrDefault(periodKey(start.minusDays(1), groupBy), List.of());
      List<Map<String, Object>> books = rankBooks(lines, previous, limit);
      List<Map<String, Object>> categories = rankCategories(lines, previous);

      Map<String, Object> row = salesSummary(lines);
      row.put("period", key);
      row.put("label", periodLabel(start, groupBy));
      row.put("from", start.isBefore(range[0]) ? range[0] : start);
      LocalDate end = periodEnd(start, groupBy);
      row.put("to", end.isAfter(range[1]) ? range[1] : end);
      row.put("topBook", books.isEmpty() ? null : books.get(0).get("title"));
      row.put("topCategory", categories.isEmpty() ? null : categories.get(0).get("name"));
      row.put("books", books);
      row.put("categories", categories);
      rows.add(row);
    }

    List<SoldLine> inRange = linesBetween(all, range[0], range[1]);
    Map<String, Object> overall = salesSummary(inRange);
    overall.put("label", "Cả khoảng " + formatDay(range[0]) + " – " + formatDay(range[1]));
    overall.put("books", rankBooks(inRange, List.of(), limit));
    overall.put("categories", rankCategories(inRange, List.of()));

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("from", range[0]);
    result.put("to", range[1]);
    result.put("groupBy", groupBy.name().toLowerCase(Locale.ROOT));
    result.put("deliveredOnly", deliveredOnly);
    result.put("rows", rows);
    result.put("overall", overall);
    result.put("best", best(rows, "quantity"));
    result.put("cachedAt", cachedAt());
    return result;
  }

  /**
   * Mùa vụ: cùng một quý (hoặc tháng) qua các năm, sách / thể loại nào bán chạy. Dự báo = trung
   * bình số cuốn bán ra trong kỳ đó của các năm đã kết thúc; đề xuất nhập = dự báo - tồn kho.
   */
  public Map<String, Object> getSeasonal(
      GroupBy unit, int index, int limit, boolean deliveredOnly) {
    if (unit != GroupBy.QUARTER && unit != GroupBy.MONTH) {
      throw new IllegalArgumentException("Mùa vụ chỉ tính theo quý hoặc tháng");
    }
    int max = unit == GroupBy.QUARTER ? 4 : 12;
    if (index < 1 || index > max) {
      throw new IllegalArgumentException(
          (unit == GroupBy.QUARTER ? "Quý" : "Tháng") + " phải từ 1 đến " + max);
    }

    List<SoldLine> all = soldLines(deliveredOnly);
    LocalDate today = LocalDate.now();
    LocalDate firstSale = all.stream().map(SoldLine::date).min(LocalDate::compareTo).orElse(today);
    int lastYear =
        Math.max(today.getYear(), all.stream().mapToInt(l -> l.date().getYear()).max().orElse(0));

    // các năm đã có kỳ này (đã bắt đầu) trong dữ liệu
    List<Map<String, Object>> years = new ArrayList<>();
    Set<Integer> completeYears = new HashSet<>();
    Set<Integer> knownYears = new HashSet<>();
    for (int year = firstSale.getYear(); year <= lastYear; year++) {
      LocalDate start =
          unit == GroupBy.QUARTER
              ? LocalDate.of(year, (index - 1) * 3 + 1, 1)
              : LocalDate.of(year, index, 1);
      LocalDate end = periodEnd(start, unit);
      if (start.isAfter(today) || end.isBefore(firstSale)) {
        continue;
      }
      boolean partial = end.isAfter(today); // kỳ của năm nay chưa kết thúc
      knownYears.add(year);
      if (!partial) {
        completeYears.add(year);
      }
      Map<String, Object> y = new LinkedHashMap<>();
      y.put("year", year);
      y.put("label", periodTitle(start, unit));
      y.put("from", start);
      y.put("to", end);
      y.put("partial", partial);
      years.add(y);
    }
    Set<Integer> forecastYears = completeYears.isEmpty() ? knownYears : completeYears;

    List<SoldLine> season =
        all.stream()
            .filter(l -> knownYears.contains(l.date().getYear()))
            .filter(l -> seasonIndex(l.date(), unit) == index)
            .toList();
    for (Map<String, Object> y : years) {
      int year = (Integer) y.get("year");
      List<SoldLine> lines = season.stream().filter(l -> l.date().getYear() == year).toList();
      y.put("quantity", totalQuantity(lines));
      y.put("revenue", totalAmount(lines));
    }

    Map<String, BookDTO> books = bookMap();
    List<Map<String, Object>> bookRows = new ArrayList<>();
    for (Map<String, Object> row : seasonalRows(season, SoldLine::bookId, forecastYears)) {
      if (bookRows.size() >= Math.max(limit, 1)) {
        break;
      }
      SoldLine sample = (SoldLine) row.remove("sample");
      BookDTO book = books.get(sample.bookId());
      int stock = book == null ? 0 : book.getStock();
      long forecast = (long) Math.ceil((Double) row.get("forecast"));
      row.put("bookId", sample.bookId());
      row.put("title", sample.title());
      row.put("image", sample.image());
      row.put("category", sample.category());
      row.put("stock", stock);
      row.put("suggestedImport", Math.max(0, forecast - stock));
      bookRows.add(row);
    }
    rankRows(bookRows);

    long seasonQuantity = totalQuantity(season);
    List<Map<String, Object>> categoryRows = new ArrayList<>();
    for (Map<String, Object> row : seasonalRows(season, SoldLine::category, forecastYears)) {
      SoldLine sample = (SoldLine) row.remove("sample");
      row.put("name", sample.category());
      row.put(
          "bookCount",
          season.stream()
              .filter(l -> l.category().equals(sample.category()))
              .map(SoldLine::bookId)
              .distinct()
              .count());
      row.put(
          "share",
          seasonQuantity == 0 ? 0 : ((Number) row.get("quantity")).doubleValue() / seasonQuantity);
      categoryRows.add(row);
    }
    rankRows(categoryRows);

    Map<String, Object> summary = salesSummary(season);
    summary.put(
        "forecastQuantity", bookRows.stream().mapToDouble(r -> (Double) r.get("forecast")).sum());
    summary.put(
        "suggestedImport",
        bookRows.stream().mapToLong(r -> ((Number) r.get("suggestedImport")).longValue()).sum());
    summary.put("forecastYears", forecastYears.stream().sorted().toList());

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("unit", unit.name().toLowerCase(Locale.ROOT));
    result.put("index", index);
    result.put("label", (unit == GroupBy.QUARTER ? "Quý " : "Tháng ") + index);
    result.put("deliveredOnly", deliveredOnly);
    result.put("years", years);
    result.put("books", bookRows);
    result.put("categories", categoryRows);
    result.put("summary", summary);
    result.put("cachedAt", cachedAt());
    return result;
  }

  private static int seasonIndex(LocalDate date, GroupBy unit) {
    return unit == GroupBy.QUARTER ? date.get(IsoFields.QUARTER_OF_YEAR) : date.getMonthValue();
  }

  /** Gộp các dòng theo khóa (sách / thể loại): số cuốn từng năm, tổng, dự báo, xu hướng. */
  private static List<Map<String, Object>> seasonalRows(
      List<SoldLine> lines, Function<SoldLine, String> key, Set<Integer> forecastYears) {
    Map<String, List<SoldLine>> grouped =
        lines.stream().collect(Collectors.groupingBy(key, LinkedHashMap::new, Collectors.toList()));
    List<Integer> sortedYears = forecastYears.stream().sorted().toList();

    List<Map<String, Object>> rows = new ArrayList<>();
    for (List<SoldLine> group : grouped.values()) {
      Map<String, Long> byYear = new TreeMap<>();
      group.forEach(
          l -> byYear.merge(String.valueOf(l.date().getYear()), (long) l.quantity(), Long::sum));
      long forecastTotal =
          sortedYears.stream().mapToLong(y -> byYear.getOrDefault(String.valueOf(y), 0L)).sum();
      double forecast = sortedYears.isEmpty() ? 0 : forecastTotal / (double) sortedYears.size();
      Long trend = null;
      if (sortedYears.size() >= 2) {
        String last = String.valueOf(sortedYears.get(sortedYears.size() - 1));
        String before = String.valueOf(sortedYears.get(sortedYears.size() - 2));
        trend = byYear.getOrDefault(last, 0L) - byYear.getOrDefault(before, 0L);
      }
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("sample", group.get(0));
      row.put("byYear", byYear);
      row.put("quantity", totalQuantity(group));
      row.put("revenue", totalAmount(group));
      row.put("forecast", forecast);
      row.put("trend", trend);
      rows.add(row);
    }
    rows.sort(
        Comparator.comparingDouble((Map<String, Object> r) -> (Double) r.get("forecast"))
            .thenComparingLong(r -> ((Number) r.get("quantity")).longValue())
            .reversed());
    return rows;
  }

  private List<SoldLine> soldLines(boolean deliveredOnly) {
    Map<String, BookDTO> books = bookMap();
    Set<Integer> statuses = deliveredOnly ? Set.of(STATUS_DELIVERED) : SOLD_STATUSES;
    List<SoldLine> lines = new ArrayList<>();
    for (OrderDTO order : orderService.getCachedOrders()) {
      LocalDate date = dateOf(order.getCreatedAt());
      if (date == null
          || order.getStatus() == null
          || !statuses.contains(order.getStatus())
          || order.getItems() == null) {
        continue;
      }
      String orderId = Objects.requireNonNullElse(order.getId(), order.getOrderCode());
      for (OrderDetailDTO item : order.getItems()) {
        int qty = item.getQuantity() == null ? 0 : item.getQuantity();
        if (qty <= 0) {
          continue;
        }
        BookDTO book = item.getBookId() == null ? null : books.get(item.getBookId());
        String bookId = item.getBookId() != null ? item.getBookId() : item.getTitle();
        String title = book != null ? book.getTitle() : item.getTitle();
        String category =
            book != null && book.getCategory() != null && book.getCategory().getName() != null
                ? book.getCategory().getName().trim()
                : NO_CATEGORY;
        String image = null;
        if (book != null && book.getImages() != null && !book.getImages().isEmpty()) {
          image = book.getImages().get(0).getImage();
        } else if (item.getImages() != null && !item.getImages().isEmpty()) {
          image = item.getImages().get(0);
        }
        double amount =
            item.getLineTotal() != null
                ? item.getLineTotal()
                : qty * (nz(item.getPrice()) - nz(item.getDiscount()));
        lines.add(new SoldLine(date, orderId, bookId, title, image, category, qty, amount));
      }
    }
    return lines;
  }

  private Map<String, BookDTO> bookMap() {
    Map<String, BookDTO> map = new HashMap<>();
    if (bookService != null) {
      bookService.getCachedBooks().forEach(b -> map.put(b.getId(), b));
    }
    return map;
  }

  /** Xếp hạng sách theo số cuốn bán ra (bằng nhau thì theo doanh thu). */
  private List<Map<String, Object>> rankBooks(
      List<SoldLine> lines, List<SoldLine> previous, int limit) {
    Map<String, BookDTO> books = bookMap();
    Map<String, Long> previousQty = new HashMap<>();
    previous.forEach(l -> previousQty.merge(l.bookId(), (long) l.quantity(), Long::sum));
    long total = totalQuantity(lines);

    List<Map<String, Object>> rows = new ArrayList<>();
    Map<String, List<SoldLine>> grouped =
        lines.stream()
            .collect(
                Collectors.groupingBy(SoldLine::bookId, LinkedHashMap::new, Collectors.toList()));
    for (Map.Entry<String, List<SoldLine>> entry : grouped.entrySet()) {
      List<SoldLine> group = entry.getValue();
      SoldLine sample = group.get(0);
      BookDTO book = books.get(entry.getKey());
      long qty = totalQuantity(group);
      long prev = previousQty.getOrDefault(entry.getKey(), 0L);
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("bookId", entry.getKey());
      row.put("title", sample.title());
      row.put("image", sample.image());
      row.put("category", sample.category());
      row.put(
          "author",
          book != null && book.getAuthor() != null ? book.getAuthor().getFullname() : null);
      row.put("price", book == null ? null : book.getPrice() - book.getDiscount());
      row.put("stock", book == null ? null : book.getStock());
      row.put("quantity", qty);
      row.put("revenue", totalAmount(group));
      row.put("orderCount", group.stream().map(SoldLine::orderId).distinct().count());
      row.put("share", total == 0 ? 0 : qty / (double) total);
      row.put("previousQuantity", prev);
      row.put("change", qty - prev);
      rows.add(row);
    }
    rows.sort(SALES_ORDER);
    List<Map<String, Object>> top =
        new ArrayList<>(rows.subList(0, Math.min(Math.max(limit, 1), rows.size())));
    rankRows(top);
    return top;
  }

  /** Xếp hạng thể loại theo số cuốn bán ra, kèm sách bán chạy nhất của thể loại. */
  private static List<Map<String, Object>> rankCategories(
      List<SoldLine> lines, List<SoldLine> previous) {
    Map<String, Long> previousQty = new HashMap<>();
    previous.forEach(l -> previousQty.merge(l.category(), (long) l.quantity(), Long::sum));
    long total = totalQuantity(lines);

    List<Map<String, Object>> rows = new ArrayList<>();
    Map<String, List<SoldLine>> grouped =
        lines.stream()
            .collect(
                Collectors.groupingBy(SoldLine::category, LinkedHashMap::new, Collectors.toList()));
    for (Map.Entry<String, List<SoldLine>> entry : grouped.entrySet()) {
      List<SoldLine> group = entry.getValue();
      Map<String, Long> qtyByBook = new LinkedHashMap<>();
      group.forEach(l -> qtyByBook.merge(l.title(), (long) l.quantity(), Long::sum));
      long qty = totalQuantity(group);
      long prev = previousQty.getOrDefault(entry.getKey(), 0L);
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("name", entry.getKey());
      row.put("quantity", qty);
      row.put("revenue", totalAmount(group));
      row.put("bookCount", group.stream().map(SoldLine::bookId).distinct().count());
      row.put("orderCount", group.stream().map(SoldLine::orderId).distinct().count());
      row.put("share", total == 0 ? 0 : qty / (double) total);
      row.put(
          "topBook",
          qtyByBook.entrySet().stream()
              .max(Map.Entry.comparingByValue())
              .map(Map.Entry::getKey)
              .orElse(null));
      row.put("previousQuantity", prev);
      row.put("change", qty - prev);
      rows.add(row);
    }
    rows.sort(SALES_ORDER);
    rankRows(rows);
    return rows;
  }

  private static final Comparator<Map<String, Object>> SALES_ORDER =
      Comparator.comparingLong((Map<String, Object> r) -> ((Number) r.get("quantity")).longValue())
          .thenComparingDouble(r -> ((Number) r.get("revenue")).doubleValue())
          .reversed();

  private static void rankRows(List<Map<String, Object>> rows) {
    for (int i = 0; i < rows.size(); i++) {
      Map<String, Object> ranked = new LinkedHashMap<>();
      ranked.put("rank", i + 1);
      ranked.putAll(rows.get(i));
      rows.set(i, ranked);
    }
  }

  private static Map<String, Object> salesSummary(List<SoldLine> lines) {
    Map<String, Object> summary = new LinkedHashMap<>();
    summary.put("quantity", totalQuantity(lines));
    summary.put("revenue", totalAmount(lines));
    summary.put("orderCount", lines.stream().map(SoldLine::orderId).distinct().count());
    summary.put("bookCount", lines.stream().map(SoldLine::bookId).distinct().count());
    return summary;
  }

  private static List<SoldLine> linesBetween(List<SoldLine> lines, LocalDate from, LocalDate to) {
    return lines.stream().filter(l -> within(l.date(), from, to)).toList();
  }

  private static long totalQuantity(List<SoldLine> lines) {
    return lines.stream().mapToLong(SoldLine::quantity).sum();
  }

  private static double totalAmount(List<SoldLine> lines) {
    return lines.stream().mapToDouble(SoldLine::amount).sum();
  }

  // ===================================================== tiện ích

  /** Phiếu nhập còn hiệu lực (bỏ phiếu đã hủy) - nguồn của tổng chi. */
  private List<ImportReceiptDTO> activeReceipts() {
    return importReceiptService.getCachedReceipts().stream()
        .filter(r -> r.getStatus() == null || r.getStatus() == 1)
        .toList();
  }

  private Map<String, Object> result(
      LocalDate[] range,
      GroupBy groupBy,
      List<Map<String, Object>> rows,
      Map<String, Object> summary,
      Map<String, Object> best) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("from", range[0]);
    result.put("to", range[1]);
    result.put("groupBy", groupBy.name().toLowerCase(Locale.ROOT));
    result.put("rows", rows);
    result.put("summary", summary);
    result.put("best", best);
    result.put("cachedAt", cachedAt());
    return result;
  }

  /** Kỳ có giá trị lớn nhất (ví dụ quý bán hàng tốt nhất). */
  private static Map<String, Object> best(List<Map<String, Object>> rows, String field) {
    return rows.stream()
        .filter(row -> ((Number) row.get(field)).doubleValue() > 0)
        .max(Comparator.comparingDouble(row -> ((Number) row.get(field)).doubleValue()))
        .orElse(null);
  }

  private Instant cachedAt() {
    Instant orders = orderService.getOrdersCachedAt();
    Instant receipts = importReceiptService.getReceiptsCachedAt();
    if (orders == null) {
      return receipts;
    }
    if (receipts == null) {
      return orders;
    }
    return orders.isBefore(receipts) ? orders : receipts;
  }

  private static LocalDate[] resolveRange(LocalDate from, LocalDate to, List<OrderDTO> orders) {
    LocalDate end = to != null ? to : LocalDate.now();
    LocalDate start = from;
    if (start == null) {
      start =
          orders.stream()
              .map(o -> dateOf(o.getCreatedAt()))
              .filter(Objects::nonNull)
              .min(LocalDate::compareTo)
              .orElse(end.withDayOfYear(1));
    }
    if (start.isAfter(end)) {
      throw new IllegalArgumentException("Ngày bắt đầu phải trước hoặc bằng ngày kết thúc");
    }
    return new LocalDate[] {start, end};
  }

  static List<LocalDate> periods(LocalDate from, LocalDate to, GroupBy groupBy) {
    if (groupBy == GroupBy.DAY && ChronoUnit.DAYS.between(from, to) > MAX_DAY_PERIODS) {
      throw new IllegalArgumentException(
          "Khoảng thời gian quá dài để thống kê theo ngày, hãy chọn theo tháng / quý / năm");
    }
    List<LocalDate> result = new ArrayList<>();
    LocalDate cursor = periodStart(from, groupBy);
    while (!cursor.isAfter(to)) {
      result.add(cursor);
      cursor =
          switch (groupBy) {
            case DAY -> cursor.plusDays(1);
            case WEEK -> cursor.plusWeeks(1);
            case MONTH -> cursor.plusMonths(1);
            case QUARTER -> cursor.plusMonths(3);
            case YEAR -> cursor.plusYears(1);
          };
    }
    return result;
  }

  private static LocalDate periodStart(LocalDate date, GroupBy groupBy) {
    return switch (groupBy) {
      case DAY -> date;
      case WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
      case MONTH -> date.withDayOfMonth(1);
      case QUARTER ->
          date.withMonth((date.get(IsoFields.QUARTER_OF_YEAR) - 1) * 3 + 1).withDayOfMonth(1);
      case YEAR -> date.withDayOfYear(1);
    };
  }

  /** Ngày cuối của kỳ chứa {@code date}. */
  static LocalDate periodEnd(LocalDate date, GroupBy groupBy) {
    LocalDate begin = periodStart(date, groupBy);
    LocalDate next =
        switch (groupBy) {
          case DAY -> begin.plusDays(1);
          case WEEK -> begin.plusWeeks(1);
          case MONTH -> begin.plusMonths(1);
          case QUARTER -> begin.plusMonths(3);
          case YEAR -> begin.plusYears(1);
        };
    return next.minusDays(1);
  }

  static String periodKey(LocalDate date, GroupBy groupBy) {
    if (date == null) {
      return "";
    }
    return switch (groupBy) {
      case DAY -> date.toString();
      case WEEK ->
          String.format(
              "%d-W%02d",
              date.get(IsoFields.WEEK_BASED_YEAR), date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
      case MONTH -> YearMonth.from(date).toString();
      case QUARTER -> date.getYear() + "-Q" + date.get(IsoFields.QUARTER_OF_YEAR);
      case YEAR -> String.valueOf(date.getYear());
    };
  }

  /** Tiêu đề đầy đủ của kỳ: "Ngày 28/11/2026", "Tháng 11/2026", "Quý 4/2026"... */
  private static String periodTitle(LocalDate date, GroupBy groupBy) {
    return switch (groupBy) {
      case DAY -> "Ngày " + formatDay(date);
      case MONTH -> "Tháng " + periodLabel(date, groupBy);
      default -> periodLabel(date, groupBy);
    };
  }

  private static String periodLabel(LocalDate date, GroupBy groupBy) {
    return switch (groupBy) {
      case DAY ->
          String.format("%02d/%02d/%d", date.getDayOfMonth(), date.getMonthValue(), date.getYear());
      case WEEK -> {
        LocalDate monday = periodStart(date, GroupBy.WEEK);
        yield String.format(
            "Tuần %d/%d (%02d/%02d – %s)",
            monday.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR),
            monday.get(IsoFields.WEEK_BASED_YEAR),
            monday.getDayOfMonth(),
            monday.getMonthValue(),
            formatDay(monday.plusDays(6)));
      }
      case MONTH -> String.format("%02d/%d", date.getMonthValue(), date.getYear());
      case QUARTER -> "Quý " + date.get(IsoFields.QUARTER_OF_YEAR) + "/" + date.getYear();
      case YEAR -> "Năm " + date.getYear();
    };
  }

  private static String formatDay(LocalDate date) {
    return String.format(
        "%02d/%02d/%d", date.getDayOfMonth(), date.getMonthValue(), date.getYear());
  }

  private static List<OrderDTO> deliveredOrders(List<OrderDTO> orders) {
    return orders.stream()
        .filter(o -> o.getStatus() != null && o.getStatus() == STATUS_DELIVERED)
        .toList();
  }

  private static List<OrderDTO> inRange(List<OrderDTO> orders, LocalDate from, LocalDate to) {
    return orders.stream().filter(o -> within(dateOf(o.getCreatedAt()), from, to)).toList();
  }

  private static boolean within(LocalDate date, LocalDate from, LocalDate to) {
    return date != null && !date.isBefore(from) && !date.isAfter(to);
  }

  private static LocalDate dateOf(LocalDateTime dateTime) {
    return dateTime == null ? null : dateTime.toLocalDate();
  }

  private static double sum(List<OrderDTO> orders, Function<OrderDTO, Double> field) {
    return orders.stream().mapToDouble(o -> nz(field.apply(o))).sum();
  }

  private static long quantity(List<OrderDTO> orders) {
    return orders.stream()
        .mapToLong(o -> o.getTotalQuantity() == null ? 0 : o.getTotalQuantity())
        .sum();
  }

  private static double nz(Double value) {
    return value == null ? 0 : value;
  }
}
