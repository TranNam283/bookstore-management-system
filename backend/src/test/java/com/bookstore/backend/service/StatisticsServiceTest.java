package com.bookstore.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.bookstore.backend.dto.BookDTO;
import com.bookstore.backend.dto.CategoryDTO;
import com.bookstore.backend.dto.ImportReceiptDTO;
import com.bookstore.backend.dto.OrderDTO;
import com.bookstore.backend.dto.OrderDetailDTO;
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
  @Mock private BookService bookService;

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

  // ------------------------------------------------------------ hàng bán chạy

  private static BookDTO book(String id, String title, String category, int stock) {
    BookDTO book = new BookDTO();
    book.setId(id);
    book.setTitle(title);
    book.setPrice(100_000);
    book.setStock(stock);
    CategoryDTO c = new CategoryDTO();
    c.setName(category);
    book.setCategory(c);
    return book;
  }

  private static OrderDetailDTO line(String bookId, int qty) {
    return OrderDetailDTO.builder()
        .bookId(bookId)
        .title("t-" + bookId)
        .quantity(qty)
        .lineTotal(qty * 100_000d)
        .build();
  }

  private static OrderDTO sale(String code, int status, LocalDate date, OrderDetailDTO... items) {
    return OrderDTO.builder()
        .id(code)
        .orderCode(code)
        .status(status)
        .createdAt(date.atTime(9, 0))
        .items(List.of(items))
        .build();
  }

  private void givenSales(OrderDTO... orders) {
    when(orderService.getCachedOrders()).thenReturn(List.of(orders));
    when(bookService.getCachedBooks())
        .thenReturn(
            List.of(
                book("hp", "Harry Potter", "Fantasy", 3),
                book("it", "IT", "Horror", 50),
                book("gg", "Gone Girl", "Thriller", 0)));
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> list(Map<String, Object> result, String key) {
    return (List<Map<String, Object>>) result.get(key);
  }

  // Top bán chạy của 1 quý: xếp hạng sách + thể loại, so sánh với quý trước, bỏ đơn hủy / chờ
  @Test
  void topBooksByQuarter_shouldRankBooksAndCategories() {
    givenSales(
        sale("A", 3, LocalDate.of(2025, 7, 5), line("hp", 5), line("it", 1)),
        sale("B", 1, LocalDate.of(2025, 8, 9), line("hp", 2), line("gg", 3)),
        sale("C", 4, LocalDate.of(2025, 8, 10), line("it", 40)), // đã hủy
        sale("D", 0, LocalDate.of(2025, 9, 1), line("it", 40)), // chờ xác nhận
        sale("E", 3, LocalDate.of(2025, 5, 1), line("hp", 1), line("gg", 4))); // quý trước

    Map<String, Object> result =
        statisticsService.getTopBooks(
            GroupBy.QUARTER, LocalDate.of(2025, 8, 15), null, null, 10, false);

    assertEquals(LocalDate.of(2025, 7, 1), result.get("from"));
    assertEquals(LocalDate.of(2025, 9, 30), result.get("to"));
    assertEquals("Quý 3/2025", result.get("label"));
    assertEquals("Quý 2/2025", result.get("previousLabel"));

    List<Map<String, Object>> books = list(result, "books");
    assertEquals(3, books.size());
    assertEquals(1, books.get(0).get("rank"));
    assertEquals("Harry Potter", books.get(0).get("title"));
    assertEquals(7L, books.get(0).get("quantity"));
    assertEquals(2L, books.get(0).get("orderCount"));
    assertEquals(1L, books.get(0).get("previousQuantity"));
    assertEquals(6L, books.get(0).get("change"));
    assertEquals("Gone Girl", books.get(1).get("title"));

    List<Map<String, Object>> categories = list(result, "categories");
    assertEquals("Fantasy", categories.get(0).get("name"));
    assertEquals("Harry Potter", categories.get(0).get("topBook"));
    assertEquals("Thriller", categories.get(1).get("name"));
    assertEquals("Horror", categories.get(2).get("name"));
    assertEquals(11L, summary(result).get("quantity"));
    assertEquals(5L, summary(result).get("previousQuantity"));

    // chỉ tính đơn giao thành công
    Map<String, Object> delivered =
        statisticsService.getTopBooks(
            GroupBy.QUARTER, LocalDate.of(2025, 8, 15), null, null, 10, true);
    assertEquals(6L, summary(delivered).get("quantity"));
  }

  @Test
  void topBooksByWeek_shouldUseMondayToSunday() {
    givenSales(
        sale("A", 3, LocalDate.of(2026, 11, 23), line("it", 2)),
        sale("B", 2, LocalDate.of(2026, 11, 29), line("hp", 1)),
        sale("C", 3, LocalDate.of(2026, 11, 30), line("hp", 9))); // tuần sau

    Map<String, Object> result =
        statisticsService.getTopBooks(
            GroupBy.WEEK, LocalDate.of(2026, 11, 26), null, null, 1, false);

    assertEquals(LocalDate.of(2026, 11, 23), result.get("from"));
    assertEquals(LocalDate.of(2026, 11, 29), result.get("to"));
    assertEquals("Tuần 48/2026 (23/11 – 29/11/2026)", result.get("label"));
    List<Map<String, Object>> books = list(result, "books");
    assertEquals(1, books.size()); // limit = 1
    assertEquals("IT", books.get(0).get("title"));
  }

  // Mỗi quý một bảng xếp hạng riêng
  @Test
  void bestsellersByQuarter_shouldRankEachPeriod() {
    givenSales(
        sale("A", 3, LocalDate.of(2025, 2, 1), line("it", 4), line("hp", 1)),
        sale("B", 3, LocalDate.of(2025, 8, 1), line("hp", 6)),
        sale("C", 3, LocalDate.of(2025, 8, 2), line("gg", 2)));

    Map<String, Object> result =
        statisticsService.getBestsellersByPeriod(
            LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31), GroupBy.QUARTER, 5, false);

    List<Map<String, Object>> rows = rows(result);
    assertEquals(4, rows.size());
    assertEquals("IT", rows.get(0).get("topBook"));
    assertEquals("Horror", rows.get(0).get("topCategory"));
    assertEquals(null, rows.get(1).get("topBook"));
    assertEquals("Harry Potter", rows.get(2).get("topBook"));
    assertEquals("Fantasy", rows.get(2).get("topCategory"));
    assertEquals(8L, rows.get(2).get("quantity"));
    assertEquals("2025-Q3", ((Map<?, ?>) result.get("best")).get("period"));

    @SuppressWarnings("unchecked")
    Map<String, Object> overall = (Map<String, Object>) result.get("overall");
    assertEquals("Harry Potter", list(overall, "books").get(0).get("title"));
    assertEquals(13L, overall.get("quantity"));
  }

  // Mùa vụ: quý 3 qua các năm -> dự báo = trung bình các năm đã kết thúc, đề xuất = dự báo - tồn
  @Test
  void seasonalQuarter_shouldForecastAndSuggestImport() {
    int y1 = LocalDate.now().getYear() - 2;
    int y2 = LocalDate.now().getYear() - 1;
    givenSales(
        sale("A", 3, LocalDate.of(y1, 1, 10), line("it", 1)), // quý 1: không thuộc mùa vụ
        sale("B", 3, LocalDate.of(y1, 8, 1), line("hp", 10), line("it", 2)),
        sale("C", 3, LocalDate.of(y2, 7, 20), line("hp", 20)),
        sale("D", 3, LocalDate.of(y2, 9, 30), line("gg", 4)));

    Map<String, Object> result = statisticsService.getSeasonal(GroupBy.QUARTER, 3, 10, false);

    assertEquals("Quý 3", result.get("label"));
    @SuppressWarnings("unchecked")
    List<Integer> forecastYears = (List<Integer>) summary(result).get("forecastYears");
    int n = forecastYears.size();
    assertEquals(y1, forecastYears.get(0));

    List<Map<String, Object>> books = list(result, "books");
    assertEquals("Harry Potter", books.get(0).get("title"));
    assertEquals(30L, books.get(0).get("quantity"));
    assertEquals(30d / n, books.get(0).get("forecast"));
    long expectedImport = Math.max(0, (long) Math.ceil(30d / n) - 3); // tồn kho 3
    assertEquals(expectedImport, books.get(0).get("suggestedImport"));
    // xu hướng = năm gần nhất - năm liền trước (trong các năm đã kết thúc)
    Map<Integer, Long> hpByYear = Map.of(y1, 10L, y2, 20L);
    long expectedTrend =
        hpByYear.getOrDefault(forecastYears.get(n - 1), 0L)
            - hpByYear.getOrDefault(forecastYears.get(n - 2), 0L);
    assertEquals(expectedTrend, books.get(0).get("trend"));
    assertEquals(
        Map.of(String.valueOf(y1), 10L, String.valueOf(y2), 20L), books.get(0).get("byYear"));

    List<Map<String, Object>> categories = list(result, "categories");
    assertEquals("Fantasy", categories.get(0).get("name"));
    assertEquals(36L, summary(result).get("quantity"));

    assertThrows(
        IllegalArgumentException.class,
        () -> statisticsService.getSeasonal(GroupBy.QUARTER, 5, 10, false));
    assertThrows(
        IllegalArgumentException.class,
        () -> statisticsService.getSeasonal(GroupBy.YEAR, 1, 10, false));
  }

  @Test
  void invalidInput_shouldThrow() {
    assertEquals(GroupBy.WEEK, GroupBy.from("week"));
    assertThrows(IllegalArgumentException.class, () -> GroupBy.from("hour"));
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
