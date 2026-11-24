package com.bookstore.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.bookstore.backend.dto.ImportReceiptDTO;
import com.bookstore.backend.dto.OrderDTO;
import com.bookstore.backend.service.StatisticsService.GroupBy;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StatisticsServiceTest {

  @Mock private OrderService orderService;
  @Mock private ImportReceiptService importReceiptService;

  @InjectMocks private StatisticsService statisticsService;

  private static OrderDTO order(
      String code, int status, double total, String date, String staffId, int qty) {
    return OrderDTO.builder()
        .orderCode(code)
        .status(status)
        .subtotal(total + 1000)
        .discountTotal(1000d)
        .total(total)
        .totalQuantity(qty)
        .createdAt(LocalDate.parse(date).atTime(10, 0))
        .staffId(staffId)
        .staffName(staffId == null ? null : "NV " + staffId)
        .build();
  }

  private static ImportReceiptDTO receipt(String date, double amount) {
    return ImportReceiptDTO.builder()
        .receiptCode("PN" + date)
        .totalAmount(amount)
        .totalQuantity(10)
        .createdAt(LocalDateTime.parse(date + "T08:00:00"))
        .build();
  }

  @BeforeEach
  void setup() {
    when(orderService.getCachedOrders())
        .thenReturn(
            List.of(
                order("O1", 3, 100_000, "2025-01-15", "s1", 1),
                order("O2", 3, 300_000, "2025-02-20", "s2", 3),
                order("O3", 4, 999_000, "2025-02-21", "s1", 9), // đã hủy: không tính doanh thu
                order("O4", 3, 500_000, "2025-05-01", "s1", 5),
                order("O5", 0, 700_000, "2025-05-02", null, 7), // chờ xác nhận
                order("O6", 3, 200_000, "2025-11-30", null, 2)));
    when(importReceiptService.getCachedReceipts())
        .thenReturn(List.of(receipt("2025-01-05", 150_000), receipt("2025-05-10", 100_000)));
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> rows(Map<String, Object> result) {
    return (List<Map<String, Object>>) result.get("rows");
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> summary(Map<String, Object> result) {
    return (Map<String, Object>) result.get("summary");
  }

  // Thống kê theo quý: chỉ tính đơn giao thành công, có đủ các quý trong khoảng
  @Test
  void revenueByQuarter_shouldGroupDeliveredOrders() {
    Map<String, Object> result =
        statisticsService.getRevenue(
            LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31), GroupBy.QUARTER);

    List<Map<String, Object>> rows = rows(result);
    assertEquals(4, rows.size());
    assertEquals("2025-Q1", rows.get(0).get("period"));
    assertEquals(400_000d, rows.get(0).get("revenue"));
    assertEquals(2, rows.get(0).get("orderCount"));
    assertEquals(500_000d, rows.get(1).get("revenue"));
    assertEquals(0d, rows.get(2).get("revenue"));
    assertEquals(200_000d, rows.get(3).get("revenue"));
    assertEquals(1_100_000d, summary(result).get("revenue"));

    // quý bán tốt nhất
    assertEquals("2025-Q2", ((Map<?, ?>) result.get("best")).get("period"));
  }

  @Test
  void revenueByDay_shouldRespectDateRange() {
    Map<String, Object> result =
        statisticsService.getRevenue(
            LocalDate.of(2025, 2, 1), LocalDate.of(2025, 2, 28), GroupBy.DAY);

    assertEquals(28, rows(result).size());
    assertEquals(300_000d, summary(result).get("revenue"));
    assertEquals(3L, summary(result).get("totalQuantity"));
  }

  // Lợi nhuận = tổng thu (hóa đơn) - tổng chi (phiếu nhập)
  @Test
  void profitByMonth_shouldSubtractImportCost() {
    Map<String, Object> result =
        statisticsService.getProfit(
            LocalDate.of(2025, 1, 1), LocalDate.of(2025, 6, 30), GroupBy.MONTH);

    List<Map<String, Object>> rows = rows(result);
    assertEquals(6, rows.size());
    assertEquals(100_000d, rows.get(0).get("revenue"));
    assertEquals(150_000d, rows.get(0).get("importCost"));
    assertEquals(-50_000d, rows.get(0).get("profit"));
    assertEquals(400_000d, rows.get(4).get("profit"));

    Map<String, Object> summary = summary(result);
    assertEquals(900_000d, summary.get("revenue"));
    assertEquals(250_000d, summary.get("importCost"));
    assertEquals(650_000d, summary.get("profit"));
  }

  // Tổng tiền hóa đơn theo nhân viên xác nhận
  @Test
  void revenueByStaff_shouldRankStaffByRevenue() {
    Map<String, Object> result =
        statisticsService.getRevenueByStaff(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31));

    List<Map<String, Object>> rows = rows(result);
    assertEquals("s1", rows.get(0).get("staffId"));
    assertEquals(600_000d, rows.get(0).get("revenue"));
    assertEquals(3, rows.get(0).get("confirmedCount"));
    assertEquals(2, rows.get(0).get("deliveredCount"));
    assertEquals(1L, rows.get(0).get("cancelledCount"));
    assertEquals("s2", rows.get(1).get("staffId"));
    // đơn chưa ghi nhận nhân viên vẫn được liệt kê để tổng khớp với doanh thu
    assertEquals(null, rows.get(2).get("staffId"));
    assertEquals(1_100_000d, summary(result).get("revenue"));
  }

  @Test
  void overview_shouldComputeTotalsFromCache() {
    Map<String, Object> overview = statisticsService.getOverview();

    assertEquals(1_100_000d, overview.get("totalRevenue"));
    assertEquals(11L, overview.get("totalSoldQuantity"));
    assertEquals(250_000d, overview.get("totalImportCost"));
    assertEquals(850_000d, overview.get("totalProfit"));
    assertEquals(12, ((List<?>) overview.get("monthlyRevenue")).size());
  }

  @Test
  void invalidInput_shouldThrow() {
    assertThrows(IllegalArgumentException.class, () -> GroupBy.from("week"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            statisticsService.getRevenue(
                LocalDate.of(2025, 2, 1), LocalDate.of(2025, 1, 1), GroupBy.MONTH));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            statisticsService.getRevenue(
                LocalDate.of(2020, 1, 1), LocalDate.of(2025, 1, 1), GroupBy.DAY));
  }
}
