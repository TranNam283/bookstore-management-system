package com.bookstore.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookstore.backend.cache.DataCacheRegistry;
import com.bookstore.backend.dto.BookDTO;
import com.bookstore.backend.entities.Book;
import com.bookstore.backend.entities.StockMovement;
import com.bookstore.backend.repository.StockMovementRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StockServiceTest {

  @Mock private StockMovementRepository stockMovementRepository;
  @Spy private DataCacheRegistry cacheRegistry = new DataCacheRegistry();

  @InjectMocks private StockService stockService;

  private final Book book =
      Book.builder().id("b1").title("Sách A").price(100_000d).stock(7).build();

  private StockMovement move(String type, int change, int after, String date) {
    return StockMovement.builder()
        .id(type + date)
        .book(book)
        .type(type)
        .quantityChange(change)
        .stockAfter(after)
        .createdAt(LocalDate.parse(date).atTime(10, 0))
        .build();
  }

  @Test
  void record_shouldSaveStockAfter() {
    stockService.record(book, -3, StockService.SALE, "ORDER", "o1", "ABC123", null, "Bán hàng");

    ArgumentCaptor<StockMovement> captor = ArgumentCaptor.forClass(StockMovement.class);
    verify(stockMovementRepository).save(captor.capture());
    assertEquals(-3, captor.getValue().getQuantityChange());
    assertEquals(7, captor.getValue().getStockAfter());
    assertEquals("ABC123", captor.getValue().getRefCode());
  }

  @Test
  void record_shouldIgnoreZeroChange() {
    stockService.record(book, 0, StockService.ADJUST, null, null, null, null, null);
    verify(stockMovementRepository, never()).save(any());
  }

  // Báo cáo nhập - xuất - tồn: tồn đầu + nhập + trả - bán - xuất = tồn cuối
  @Test
  void inventoryReport_shouldBalance() {
    when(stockMovementRepository.findAll(any(Sort.class)))
        .thenReturn(
            List.of(
                move(StockService.OPENING, 10, 10, "2025-01-01"),
                move(StockService.SALE, -4, 6, "2025-01-20"), // trước kỳ
                move(StockService.IMPORT, 20, 26, "2025-02-03"),
                move(StockService.SALE, -5, 21, "2025-02-10"),
                move(StockService.CANCEL_RETURN, 2, 23, "2025-02-11"),
                move(StockService.EXPORT, -1, 22, "2025-02-15"),
                move(StockService.SALE, -9, 13, "2025-03-05"))); // sau kỳ
    BookDTO dto = new BookDTO();
    dto.setId("b1");
    dto.setTitle("Sách A");
    dto.setPrice(100_000d);
    dto.setStock(13);

    Map<String, Object> report =
        stockService.getInventoryReport(
            List.of(dto), LocalDate.of(2025, 2, 1), LocalDate.of(2025, 2, 28));

    @SuppressWarnings("unchecked")
    Map<String, Object> row = ((List<Map<String, Object>>) report.get("rows")).get(0);
    assertEquals(6, row.get("opening"));
    assertEquals(20, row.get("imported"));
    assertEquals(2, row.get("returned"));
    assertEquals(5, row.get("sold"));
    assertEquals(1, row.get("exported"));
    assertEquals(22, row.get("closing"));
    assertEquals(2_200_000d, row.get("closingValue"));
  }

  @Test
  void inventoryReport_shouldRejectInvertedRange() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            stockService.getInventoryReport(
                List.of(), LocalDate.of(2025, 3, 1), LocalDate.of(2025, 2, 1)));
  }

  @Test
  void movements_shouldBeCached() {
    when(stockMovementRepository.findAll(any(Sort.class))).thenReturn(List.of());
    stockService.getCachedMovements();
    stockService.getCachedMovements();
    verify(stockMovementRepository, org.mockito.Mockito.times(1)).findAll(any(Sort.class));
  }
}
