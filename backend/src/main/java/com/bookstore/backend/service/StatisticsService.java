package com.bookstore.backend.service;

import com.bookstore.backend.dto.ImportReceiptDTO;
import com.bookstore.backend.dto.OrderDTO;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Thống kê kinh doanh, tính hoàn toàn trên danh sách đơn hàng và phiếu nhập đã cache trên RAM.
 *
 * <p>Doanh thu (tổng thu) = tổng tiền các hóa đơn ở trạng thái 3 (Giao thành công). Tổng chi = tổng
 * tiền các phiếu nhập hàng. Lợi nhuận = tổng thu - tổng chi.
 */
@Service
public class StatisticsService {

  public static final int STATUS_DELIVERED = 3;
  private static final int MAX_DAY_PERIODS = 1100;

  public enum GroupBy {
    DAY,
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
            "Kiểu thống kê không hợp lệ (day, month, quarter, year): " + value);
      }
    }
  }

  private final OrderService orderService;
  private final ImportReceiptService importReceiptService;

  public StatisticsService(OrderService orderService, ImportReceiptService importReceiptService) {
    this.orderService = orderService;
    this.importReceiptService = importReceiptService;
  }

  // ===================================================== tổng quan (dashboard)

  public Map<String, Object> getOverview() {
    List<OrderDTO> delivered = deliveredOrders(orderService.getCachedOrders());
    List<ImportReceiptDTO> receipts = importReceiptService.getCachedReceipts();
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
            .map(row -> Map.of("month", row.get("period"), "revenue", row.get("revenue")))
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
    List<ImportReceiptDTO> receipts = importReceiptService.getCachedReceipts();
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

  // ===================================================== tiện ích

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
      case MONTH -> date.withDayOfMonth(1);
      case QUARTER ->
          date.withMonth((date.get(IsoFields.QUARTER_OF_YEAR) - 1) * 3 + 1).withDayOfMonth(1);
      case YEAR -> date.withDayOfYear(1);
    };
  }

  static String periodKey(LocalDate date, GroupBy groupBy) {
    if (date == null) {
      return "";
    }
    return switch (groupBy) {
      case DAY -> date.toString();
      case MONTH -> YearMonth.from(date).toString();
      case QUARTER -> date.getYear() + "-Q" + date.get(IsoFields.QUARTER_OF_YEAR);
      case YEAR -> String.valueOf(date.getYear());
    };
  }

  private static String periodLabel(LocalDate date, GroupBy groupBy) {
    return switch (groupBy) {
      case DAY ->
          String.format("%02d/%02d/%d", date.getDayOfMonth(), date.getMonthValue(), date.getYear());
      case MONTH -> String.format("%02d/%d", date.getMonthValue(), date.getYear());
      case QUARTER -> "Quý " + date.get(IsoFields.QUARTER_OF_YEAR) + "/" + date.getYear();
      case YEAR -> "Năm " + date.getYear();
    };
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
