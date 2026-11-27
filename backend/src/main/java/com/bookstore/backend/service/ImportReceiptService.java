package com.bookstore.backend.service;

import com.bookstore.backend.cache.CacheNames;
import com.bookstore.backend.cache.DataCacheRegistry;
import com.bookstore.backend.cache.ListCache;
import com.bookstore.backend.dto.ImportReceiptDTO;
import com.bookstore.backend.dto.ImportReceiptDetailDTO;
import com.bookstore.backend.dto.search.AdvancedSearchRequest;
import com.bookstore.backend.dto.search.SearchResponse;
import com.bookstore.backend.entities.Book;
import com.bookstore.backend.entities.ImportReceipt;
import com.bookstore.backend.entities.ImportReceiptDetail;
import com.bookstore.backend.entities.Publisher;
import com.bookstore.backend.entities.Supplier;
import com.bookstore.backend.entities.User;
import com.bookstore.backend.repository.BookRepository;
import com.bookstore.backend.repository.ImportReceiptRepository;
import com.bookstore.backend.repository.PublisherRepository;
import com.bookstore.backend.repository.SupplierRepository;
import com.bookstore.backend.repository.UserRepository;
import com.bookstore.backend.utils.AdvancedFilter;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Phiếu nhập hàng: tạo phiếu sẽ cộng tồn kho sách và là nguồn "tổng chi" cho thống kê. */
@Service
public class ImportReceiptService {

  private final ImportReceiptRepository importReceiptRepository;
  private final BookRepository bookRepository;
  private final PublisherRepository publisherRepository;
  private final UserRepository userRepository;
  private final SupplierRepository supplierRepository;
  private final StockService stockService;
  private final DataCacheRegistry cacheRegistry;
  private final ListCache<ImportReceiptDTO> receiptCache;
  private final AdvancedFilter<ImportReceiptDTO> receiptFilter = buildFilter();
  private final Random random = new Random();

  public ImportReceiptService(
      ImportReceiptRepository importReceiptRepository,
      BookRepository bookRepository,
      PublisherRepository publisherRepository,
      UserRepository userRepository,
      SupplierRepository supplierRepository,
      StockService stockService,
      DataCacheRegistry cacheRegistry) {
    this.importReceiptRepository = importReceiptRepository;
    this.bookRepository = bookRepository;
    this.publisherRepository = publisherRepository;
    this.userRepository = userRepository;
    this.supplierRepository = supplierRepository;
    this.stockService = stockService;
    this.cacheRegistry = cacheRegistry;
    this.receiptCache =
        cacheRegistry.register(CacheNames.IMPORT_RECEIPTS, this::loadReceiptsFromDatabase);
  }

  private ImportReceiptDTO convertToDTO(ImportReceipt receipt) {
    List<ImportReceiptDetailDTO> items =
        receipt.getItems() == null
            ? List.of()
            : receipt.getItems().stream()
                .map(
                    item ->
                        ImportReceiptDetailDTO.builder()
                            .bookId(item.getBook().getId())
                            .title(item.getBook().getTitle())
                            .image(firstImage(item.getBook()))
                            .quantity(item.getQuantity())
                            .importPrice(item.getImportPrice())
                            .lineTotal(item.getLineTotal())
                            .build())
                .collect(Collectors.toList());

    return ImportReceiptDTO.builder()
        .id(receipt.getId())
        .receiptCode(receipt.getReceiptCode())
        .supplierId(receipt.getSupplier() != null ? receipt.getSupplier().getId() : null)
        .supplierName(receipt.getSupplier() != null ? receipt.getSupplier().getName() : null)
        .status(receipt.getStatus())
        .cancelledAt(receipt.getCancelledAt())
        .cancelReason(receipt.getCancelReason())
        .publisherId(receipt.getPublisher() != null ? receipt.getPublisher().getId() : null)
        .publisherName(receipt.getPublisher() != null ? receipt.getPublisher().getName() : null)
        .staffId(receipt.getStaff() != null ? receipt.getStaff().getId() : null)
        .staffName(receipt.getStaff() != null ? receipt.getStaff().getFullname() : null)
        .note(receipt.getNote())
        .totalQuantity(receipt.getTotalQuantity())
        .totalAmount(receipt.getTotalAmount())
        .createdAt(receipt.getCreatedAt())
        .items(items)
        .build();
  }

  private static String firstImage(Book book) {
    if (book.getImages() == null || book.getImages().isEmpty()) {
      return null;
    }
    return book.getImages().stream()
        .sorted(Comparator.comparing(img -> img.getCreatedAt()))
        .map(img -> img.getImage())
        .findFirst()
        .orElse(null);
  }

  // ===================================================== cache & tìm kiếm

  private List<ImportReceiptDTO> loadReceiptsFromDatabase() {
    return importReceiptRepository.findAll(Sort.by("createdAt").descending()).stream()
        .map(this::convertToDTO)
        .collect(Collectors.toList());
  }

  public List<ImportReceiptDTO> getCachedReceipts() {
    return receiptCache.get();
  }

  public Instant getReceiptsCachedAt() {
    return receiptCache.getLoadedAt();
  }

  private static AdvancedFilter<ImportReceiptDTO> buildFilter() {
    return new AdvancedFilter<ImportReceiptDTO>()
        .text("receiptCode", ImportReceiptDTO::getReceiptCode)
        .text("supplierId", ImportReceiptDTO::getSupplierId)
        .text("supplierName", ImportReceiptDTO::getSupplierName)
        .number("status", ImportReceiptDTO::getStatus)
        .text("publisherId", ImportReceiptDTO::getPublisherId)
        .text("publisherName", ImportReceiptDTO::getPublisherName)
        .text("staffId", ImportReceiptDTO::getStaffId)
        .text("staffName", ImportReceiptDTO::getStaffName)
        .text("note", ImportReceiptDTO::getNote)
        .text(
            "bookTitle",
            receipt ->
                receipt.getItems() == null
                    ? List.of()
                    : receipt.getItems().stream().map(ImportReceiptDetailDTO::getTitle).toList())
        .number("totalQuantity", ImportReceiptDTO::getTotalQuantity)
        .number("totalAmount", ImportReceiptDTO::getTotalAmount)
        .date("createdAt", ImportReceiptDTO::getCreatedAt)
        .keyword(
            ImportReceiptDTO::getReceiptCode,
            ImportReceiptDTO::getPublisherName,
            ImportReceiptDTO::getStaffName,
            ImportReceiptDTO::getNote);
  }

  public SearchResponse<ImportReceiptDTO> search(AdvancedSearchRequest request) {
    return receiptFilter.search(
        getCachedReceipts(),
        request,
        "createdAt",
        all -> {
          // tổng chỉ tính phiếu còn hiệu lực (chưa hủy)
          List<ImportReceiptDTO> receipts =
              all.stream().filter(r -> r.getStatus() == null || r.getStatus() == 1).toList();
          Map<String, Object> summary = new LinkedHashMap<>();
          summary.put("count", all.size());
          summary.put("cancelledCount", all.size() - receipts.size());
          summary.put(
              "totalQuantity",
              receipts.stream()
                  .mapToInt(r -> r.getTotalQuantity() == null ? 0 : r.getTotalQuantity())
                  .sum());
          summary.put(
              "totalAmount",
              receipts.stream()
                  .mapToDouble(r -> r.getTotalAmount() == null ? 0 : r.getTotalAmount())
                  .sum());
          return summary;
        },
        getReceiptsCachedAt());
  }

  public ImportReceiptDTO getById(String id) {
    return getCachedReceipts().stream()
        .filter(receipt -> receipt.getId().equals(id))
        .findFirst()
        .orElseGet(
            () ->
                importReceiptRepository
                    .findById(id)
                    .map(this::convertToDTO)
                    .orElseThrow(() -> new EntityNotFoundException("Phiếu nhập không tìm thấy")));
  }

  // ===================================================== tạo phiếu nhập

  @Transactional
  public ImportReceiptDTO create(ImportReceiptDTO request, String staffId) {
    if (request == null || request.getItems() == null || request.getItems().isEmpty()) {
      throw new IllegalArgumentException("Phiếu nhập phải có ít nhất một sách");
    }

    if (request.getSupplierId() == null || request.getSupplierId().isBlank()) {
      throw new IllegalArgumentException("Vui lòng chọn nhà cung cấp");
    }
    Supplier supplier =
        supplierRepository
            .findById(request.getSupplierId())
            .orElseThrow(() -> new EntityNotFoundException("Nhà cung cấp không tìm thấy"));
    if (supplier.getStatus() != null && supplier.getStatus() == 0) {
      throw new IllegalArgumentException("Nhà cung cấp đã ngừng hợp tác");
    }

    Publisher publisher = null;
    if (request.getPublisherId() != null && !request.getPublisherId().isBlank()) {
      publisher =
          publisherRepository
              .findById(request.getPublisherId())
              .orElseThrow(() -> new EntityNotFoundException("Nhà xuất bản không tìm thấy"));
    }

    User staff = null;
    if (staffId != null) {
      staff =
          userRepository
              .findById(staffId)
              .filter(user -> user.getRole() != null && user.getRole() >= 0 && user.getRole() <= 2)
              .orElse(null);
    }

    ImportReceipt receipt =
        ImportReceipt.builder()
            .receiptCode(generateReceiptCode())
            .supplier(supplier)
            .publisher(publisher)
            .staff(staff)
            .note(request.getNote() != null ? request.getNote().trim() : null)
            .createdAt(LocalDateTime.now())
            .build();

    List<ImportReceiptDetail> details = new ArrayList<>();
    Set<String> bookIds = new HashSet<>();
    for (ImportReceiptDetailDTO item : request.getItems()) {
      if (item.getBookId() == null || !bookIds.add(item.getBookId())) {
        throw new IllegalArgumentException("Sách trong phiếu nhập bị trống hoặc bị trùng");
      }
      if (item.getQuantity() == null || item.getQuantity() <= 0) {
        throw new IllegalArgumentException("Số lượng nhập phải lớn hơn 0");
      }
      if (item.getImportPrice() == null || item.getImportPrice() < 0) {
        throw new IllegalArgumentException("Giá nhập không hợp lệ");
      }

      Book book =
          bookRepository
              .findById(item.getBookId())
              .orElseThrow(() -> new EntityNotFoundException("Sách không tìm thấy"));

      // nhập hàng -> cộng tồn kho
      book.setStock(book.getStock() + item.getQuantity());
      bookRepository.save(book);

      ImportReceiptDetail detail =
          ImportReceiptDetail.builder()
              .receipt(receipt)
              .book(book)
              .quantity(item.getQuantity())
              .importPrice(item.getImportPrice())
              .build();
      detail.calculateAmounts();
      details.add(detail);
    }

    receipt.setItems(details);
    receipt.recalculateTotals();
    ImportReceipt saved = importReceiptRepository.save(receipt);

    // thẻ kho
    for (ImportReceiptDetail d : saved.getItems()) {
      stockService.record(
          d.getBook(),
          d.getQuantity(),
          StockService.IMPORT,
          "IMPORT",
          saved.getId(),
          saved.getReceiptCode(),
          staff,
          "Nhập hàng từ " + supplier.getName());
    }

    cacheRegistry.invalidate(CacheNames.IMPORT_RECEIPTS, CacheNames.BOOKS, CacheNames.SUPPLIERS);
    return convertToDTO(saved);
  }

  /**
   * Hủy phiếu nhập (nhập sai): trừ lại tồn kho các sách trong phiếu. Không hủy được nếu số sách đó
   * đã bán bớt và tồn kho không còn đủ.
   */
  @Transactional
  public ImportReceiptDTO cancel(String id, String reason, String staffId) {
    ImportReceipt receipt =
        importReceiptRepository
            .findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Phiếu nhập không tìm thấy"));
    if (receipt.getStatus() != null && receipt.getStatus() == 0) {
      throw new IllegalStateException("Phiếu nhập đã bị hủy trước đó");
    }
    if (reason == null || reason.isBlank()) {
      throw new IllegalArgumentException("Vui lòng nhập lý do hủy phiếu");
    }
    for (ImportReceiptDetail d : receipt.getItems()) {
      if (d.getBook().getStock() < d.getQuantity()) {
        throw new IllegalStateException(
            "Không thể hủy: "
                + d.getBook().getTitle()
                + " chỉ còn "
                + d.getBook().getStock()
                + " cuốn (phiếu nhập "
                + d.getQuantity()
                + ")");
      }
    }
    User staff =
        staffId == null
            ? null
            : userRepository
                .findById(staffId)
                .filter(u -> u.getRole() != null && u.getRole() >= 0 && u.getRole() <= 2)
                .orElse(null);
    for (ImportReceiptDetail d : receipt.getItems()) {
      Book book = d.getBook();
      book.setStock(book.getStock() - d.getQuantity());
      bookRepository.save(book);
      stockService.record(
          book,
          -d.getQuantity(),
          StockService.IMPORT_CANCEL,
          "IMPORT",
          receipt.getId(),
          receipt.getReceiptCode(),
          staff,
          "Hủy phiếu nhập: " + reason.trim());
    }
    receipt.setStatus(0);
    receipt.setCancelledAt(LocalDateTime.now());
    receipt.setCancelReason(reason.trim());
    ImportReceipt saved = importReceiptRepository.save(receipt);
    cacheRegistry.invalidate(CacheNames.IMPORT_RECEIPTS, CacheNames.BOOKS, CacheNames.SUPPLIERS);
    return convertToDTO(saved);
  }

  private String generateReceiptCode() {
    String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    String code;
    do {
      StringBuilder sb = new StringBuilder("PN");
      for (int i = 0; i < 6; i++) {
        sb.append(chars.charAt(random.nextInt(chars.length())));
      }
      code = sb.toString();
    } while (importReceiptRepository.existsByReceiptCode(code));
    return code;
  }
}
