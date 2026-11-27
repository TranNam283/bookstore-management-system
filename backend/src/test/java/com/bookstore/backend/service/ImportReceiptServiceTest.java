package com.bookstore.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookstore.backend.cache.DataCacheRegistry;
import com.bookstore.backend.dto.ImportReceiptDTO;
import com.bookstore.backend.dto.ImportReceiptDetailDTO;
import com.bookstore.backend.dto.search.AdvancedSearchRequest;
import com.bookstore.backend.entities.Book;
import com.bookstore.backend.entities.ImportReceipt;
import com.bookstore.backend.entities.Publisher;
import com.bookstore.backend.entities.User;
import com.bookstore.backend.repository.BookRepository;
import com.bookstore.backend.repository.ImportReceiptRepository;
import com.bookstore.backend.repository.PublisherRepository;
import com.bookstore.backend.repository.SupplierRepository;
import com.bookstore.backend.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ImportReceiptServiceTest {

  @Mock private ImportReceiptRepository importReceiptRepository;
  @Mock private BookRepository bookRepository;
  @Mock private PublisherRepository publisherRepository;
  @Mock private UserRepository userRepository;
  @Mock private SupplierRepository supplierRepository;

  @Mock private StockService stockService;

  @Spy private DataCacheRegistry cacheRegistry = new DataCacheRegistry();

  @InjectMocks private ImportReceiptService importReceiptService;

  private Book book1;
  private Book book2;

  @BeforeEach
  void setup() {
    book1 = Book.builder().id("b1").title("Sách 1").price(100_000d).stock(5).build();
    book2 = Book.builder().id("b2").title("Sách 2").price(80_000d).stock(0).build();
    when(bookRepository.findById("b1")).thenReturn(Optional.of(book1));
    when(bookRepository.findById("b2")).thenReturn(Optional.of(book2));
    when(importReceiptRepository.existsByReceiptCode(anyString())).thenReturn(false);
    when(supplierRepository.findById("sup1"))
        .thenReturn(
            Optional.of(
                com.bookstore.backend.entities.Supplier.builder()
                    .id("sup1")
                    .code("NCC001")
                    .name("Fahasa")
                    .status(1)
                    .build()));
    when(importReceiptRepository.save(any(ImportReceipt.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
  }

  private static ImportReceiptDetailDTO line(String bookId, Integer qty, Double price) {
    return ImportReceiptDetailDTO.builder().bookId(bookId).quantity(qty).importPrice(price).build();
  }

  // Tạo phiếu nhập: cộng tồn kho và tính thành tiền
  @Test
  void create_shouldIncreaseStockAndComputeTotals() {
    Publisher publisher = Publisher.builder().id("p1").name("NXB Trẻ").build();
    User staff = User.builder().id("s1").fullname("Nhân viên A").role(1).build();
    when(publisherRepository.findById("p1")).thenReturn(Optional.of(publisher));
    when(userRepository.findById("s1")).thenReturn(Optional.of(staff));

    ImportReceiptDTO request =
        ImportReceiptDTO.builder()
            .supplierId("sup1")
            .publisherId("p1")
            .note(" nhập đầu tháng ")
            .items(List.of(line("b1", 10, 60_000d), line("b2", 5, 50_000d)))
            .build();

    ImportReceiptDTO result = importReceiptService.create(request, "s1");

    assertEquals(15, book1.getStock());
    assertEquals(5, book2.getStock());
    assertEquals(15, result.getTotalQuantity());
    assertEquals(850_000d, result.getTotalAmount());
    assertEquals(600_000d, result.getItems().get(0).getLineTotal());
    assertEquals("NXB Trẻ", result.getPublisherName());
    assertEquals("Nhân viên A", result.getStaffName());
    assertEquals("nhập đầu tháng", result.getNote());
    assertEquals(8, result.getReceiptCode().length());
    verify(bookRepository, times(2)).save(any(Book.class));
  }

  @Test
  void create_shouldRejectInvalidLines() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            importReceiptService.create(
                ImportReceiptDTO.builder().supplierId("sup1").items(List.of()).build(), null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            importReceiptService.create(
                ImportReceiptDTO.builder()
                    .supplierId("sup1")
                    .items(List.of(line("b1", 0, 1000d)))
                    .build(),
                null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            importReceiptService.create(
                ImportReceiptDTO.builder()
                    .supplierId("sup1")
                    .items(List.of(line("b1", 1, -1d)))
                    .build(),
                null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            importReceiptService.create(
                ImportReceiptDTO.builder()
                    .supplierId("sup1")
                    .items(List.of(line("b1", 1, 1000d), line("b1", 2, 1000d)))
                    .build(),
                null));
  }

  @Test
  void create_shouldFailWhenBookMissing() {
    when(bookRepository.findById("x")).thenReturn(Optional.empty());

    assertThrows(
        EntityNotFoundException.class,
        () ->
            importReceiptService.create(
                ImportReceiptDTO.builder()
                    .supplierId("sup1")
                    .items(List.of(line("x", 1, 1000d)))
                    .build(),
                null));
  }

  // Danh sách phiếu nhập được đọc từ database một lần rồi dùng lại trên RAM
  @Test
  void search_shouldReuseCachedList() {
    when(importReceiptRepository.findAll(any(Sort.class))).thenReturn(List.of());

    importReceiptService.search(new AdvancedSearchRequest());
    importReceiptService.search(new AdvancedSearchRequest());

    verify(importReceiptRepository, times(1)).findAll(any(Sort.class));
  }

  // Hủy phiếu nhập: trừ lại tồn kho, ghi thẻ kho, chuyển trạng thái
  @Test
  void cancel_shouldReverseStock() {
    ImportReceipt receipt =
        ImportReceipt.builder().id("r1").receiptCode("PN000001").status(1).build();
    receipt
        .getItems()
        .add(
            com.bookstore.backend.entities.ImportReceiptDetail.builder()
                .receipt(receipt)
                .book(book1)
                .quantity(3)
                .importPrice(50_000d)
                .build());
    when(importReceiptRepository.findById("r1")).thenReturn(Optional.of(receipt));

    ImportReceiptDTO result = importReceiptService.cancel("r1", "Nhập nhầm", null);

    assertEquals(2, book1.getStock());
    assertEquals(0, result.getStatus());
    assertEquals("Nhập nhầm", result.getCancelReason());
    verify(stockService)
        .record(
            org.mockito.ArgumentMatchers.eq(book1),
            org.mockito.ArgumentMatchers.eq(-3),
            org.mockito.ArgumentMatchers.eq(StockService.IMPORT_CANCEL),
            any(),
            any(),
            any(),
            any(),
            any());
  }

  @Test
  void cancel_shouldFailWhenStockAlreadySold() {
    ImportReceipt receipt =
        ImportReceipt.builder().id("r2").receiptCode("PN000002").status(1).build();
    receipt
        .getItems()
        .add(
            com.bookstore.backend.entities.ImportReceiptDetail.builder()
                .receipt(receipt)
                .book(book1)
                .quantity(50)
                .importPrice(50_000d)
                .build());
    when(importReceiptRepository.findById("r2")).thenReturn(Optional.of(receipt));

    assertThrows(
        IllegalStateException.class, () -> importReceiptService.cancel("r2", "Nhập nhầm", null));
    assertEquals(5, book1.getStock());
  }

  @Test
  void create_shouldRequireSupplier() {
    ImportReceiptDTO request =
        ImportReceiptDTO.builder().items(List.of(line("b1", 1, 1000d))).build();
    assertThrows(IllegalArgumentException.class, () -> importReceiptService.create(request, null));
  }
}
