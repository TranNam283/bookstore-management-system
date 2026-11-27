package com.bookstore.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookstore.backend.cache.DataCacheRegistry;
import com.bookstore.backend.dto.ExportReceiptDTO;
import com.bookstore.backend.dto.ExportReceiptDetailDTO;
import com.bookstore.backend.entities.Book;
import com.bookstore.backend.entities.ExportReceipt;
import com.bookstore.backend.entities.Order;
import com.bookstore.backend.entities.OrderDetail;
import com.bookstore.backend.entities.Supplier;
import com.bookstore.backend.repository.BookRepository;
import com.bookstore.backend.repository.ExportReceiptRepository;
import com.bookstore.backend.repository.SupplierRepository;
import com.bookstore.backend.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExportReceiptServiceTest {

  @Mock private ExportReceiptRepository exportReceiptRepository;
  @Mock private BookRepository bookRepository;
  @Mock private SupplierRepository supplierRepository;
  @Mock private UserRepository userRepository;
  @Mock private StockService stockService;
  @Spy private DataCacheRegistry cacheRegistry = new DataCacheRegistry();

  @InjectMocks private ExportReceiptService exportReceiptService;

  private Book book;

  @BeforeEach
  void setup() {
    book = Book.builder().id("b1").title("Sách A").price(100_000d).stock(10).build();
    when(bookRepository.findById("b1")).thenReturn(Optional.of(book));
    when(exportReceiptRepository.existsByReceiptCode(anyString())).thenReturn(false);
    when(exportReceiptRepository.save(any(ExportReceipt.class)))
        .thenAnswer(inv -> inv.getArgument(0));
  }

  private static ExportReceiptDTO request(String type, int qty) {
    return ExportReceiptDTO.builder()
        .type(type)
        .reason("Sách rách bìa")
        .items(List.of(ExportReceiptDetailDTO.builder().bookId("b1").quantity(qty).build()))
        .build();
  }

  // Xuất hư hỏng: trừ tồn kho, ghi thẻ kho, đơn giá mặc định = giá bán
  @Test
  void createManual_shouldDeductStock() {
    ExportReceiptDTO result = exportReceiptService.createManual(request("DAMAGED", 4), null);

    assertEquals(6, book.getStock());
    assertEquals(4, result.getTotalQuantity());
    assertEquals(400_000d, result.getTotalAmount());
    verify(stockService)
        .record(eq(book), eq(-4), eq(StockService.EXPORT), any(), any(), any(), any(), any());
  }

  @Test
  void createManual_shouldRejectInvalidRequests() {
    assertThrows(
        IllegalArgumentException.class,
        () -> exportReceiptService.createManual(request("DAMAGED", 11), null));
    assertThrows(
        IllegalArgumentException.class,
        () -> exportReceiptService.createManual(request("SALE", 1), null));
    assertThrows(
        IllegalArgumentException.class,
        () -> exportReceiptService.createManual(request("RETURN_SUPPLIER", 1), null));
    assertEquals(10, book.getStock());
  }

  @Test
  void createManual_returnSupplierNeedsSupplier() {
    when(supplierRepository.findById("s1"))
        .thenReturn(Optional.of(Supplier.builder().id("s1").name("NCC").build()));
    ExportReceiptDTO req = request("RETURN_SUPPLIER", 2);
    req.setSupplierId("s1");

    ExportReceiptDTO result = exportReceiptService.createManual(req, null);

    assertEquals("NCC", result.getSupplierName());
    assertEquals(8, book.getStock());
  }

  // Phiếu xuất bán hàng theo hóa đơn: không trừ tồn kho lần nữa
  @Test
  void createForOrder_shouldNotTouchStock() {
    Order order = Order.builder().id("o1").orderCode("ABC123").items(new ArrayList<>()).build();
    order
        .getItems()
        .add(
            OrderDetail.builder()
                .order(order)
                .book(book)
                .quantity(2)
                .price(100_000d)
                .discount(10_000d)
                .build());

    exportReceiptService.createForOrder(order, null);

    ArgumentCaptor<ExportReceipt> captor = ArgumentCaptor.forClass(ExportReceipt.class);
    verify(exportReceiptRepository).save(captor.capture());
    assertEquals("PXABC123", captor.getValue().getReceiptCode());
    assertEquals(180_000d, captor.getValue().getTotalAmount());
    assertEquals(10, book.getStock());
    verify(stockService, never())
        .record(any(), any(Integer.class), any(), any(), any(), any(), any(), any());
  }

  @Test
  void createForOrder_shouldSkipWhenAlreadyExists() {
    when(exportReceiptRepository.existsByOrder_Id("o1")).thenReturn(true);
    Order order = Order.builder().id("o1").orderCode("ABC123").items(new ArrayList<>()).build();

    exportReceiptService.createForOrder(order, null);

    verify(exportReceiptRepository, never()).save(any());
  }
}
