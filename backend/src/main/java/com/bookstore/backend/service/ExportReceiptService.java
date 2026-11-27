package com.bookstore.backend.service;

import com.bookstore.backend.cache.CacheNames;
import com.bookstore.backend.cache.DataCacheRegistry;
import com.bookstore.backend.cache.ListCache;
import com.bookstore.backend.dto.ExportReceiptDTO;
import com.bookstore.backend.dto.ExportReceiptDetailDTO;
import com.bookstore.backend.dto.search.AdvancedSearchRequest;
import com.bookstore.backend.dto.search.SearchResponse;
import com.bookstore.backend.entities.Book;
import com.bookstore.backend.entities.ExportReceipt;
import com.bookstore.backend.entities.ExportReceiptDetail;
import com.bookstore.backend.entities.Order;
import com.bookstore.backend.entities.OrderDetail;
import com.bookstore.backend.entities.Supplier;
import com.bookstore.backend.entities.User;
import com.bookstore.backend.repository.BookRepository;
import com.bookstore.backend.repository.ExportReceiptRepository;
import com.bookstore.backend.repository.SupplierRepository;
import com.bookstore.backend.repository.UserRepository;
import com.bookstore.backend.utils.AdvancedFilter;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.time.LocalDateTime;
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

/**
 * Phiếu xuất kho.
 *
 * <ul>
 *   <li>SALE: tự tạo khi hóa đơn chuyển sang "Đang giao" (tồn kho đã trừ lúc đặt hàng nên không trừ
 *       lại).
 *   <li>DAMAGED / RETURN_SUPPLIER / OTHER: nhân viên lập thủ công, trừ tồn kho và ghi thẻ kho.
 * </ul>
 */
@Service
public class ExportReceiptService {

  public static final List<String> MANUAL_TYPES = List.of("DAMAGED", "RETURN_SUPPLIER", "OTHER");

  private final ExportReceiptRepository exportReceiptRepository;
  private final BookRepository bookRepository;
  private final SupplierRepository supplierRepository;
  private final UserRepository userRepository;
  private final StockService stockService;
  private final DataCacheRegistry cacheRegistry;
  private final ListCache<ExportReceiptDTO> receiptCache;
  private final AdvancedFilter<ExportReceiptDTO> filter = buildFilter();
  private final Random random = new Random();

  public ExportReceiptService(
      ExportReceiptRepository exportReceiptRepository,
      BookRepository bookRepository,
      SupplierRepository supplierRepository,
      UserRepository userRepository,
      StockService stockService,
      DataCacheRegistry cacheRegistry) {
    this.exportReceiptRepository = exportReceiptRepository;
    this.bookRepository = bookRepository;
    this.supplierRepository = supplierRepository;
    this.userRepository = userRepository;
    this.stockService = stockService;
    this.cacheRegistry = cacheRegistry;
    this.receiptCache = cacheRegistry.register(CacheNames.EXPORT_RECEIPTS, this::loadFromDatabase);
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

  private ExportReceiptDTO toDTO(ExportReceipt r) {
    return ExportReceiptDTO.builder()
        .id(r.getId())
        .receiptCode(r.getReceiptCode())
        .type(r.getType())
        .orderId(r.getOrder() != null ? r.getOrder().getId() : null)
        .orderCode(r.getOrder() != null ? r.getOrder().getOrderCode() : null)
        .supplierId(r.getSupplier() != null ? r.getSupplier().getId() : null)
        .supplierName(r.getSupplier() != null ? r.getSupplier().getName() : null)
        .staffId(r.getStaff() != null ? r.getStaff().getId() : null)
        .staffName(r.getStaff() != null ? r.getStaff().getFullname() : null)
        .reason(r.getReason())
        .totalQuantity(r.getTotalQuantity())
        .totalAmount(r.getTotalAmount())
        .createdAt(r.getCreatedAt())
        .items(
            r.getItems() == null
                ? List.of()
                : r.getItems().stream()
                    .map(
                        d ->
                            ExportReceiptDetailDTO.builder()
                                .bookId(d.getBook().getId())
                                .title(d.getBook().getTitle())
                                .image(firstImage(d.getBook()))
                                .quantity(d.getQuantity())
                                .unitPrice(d.getUnitPrice())
                                .lineTotal(d.getLineTotal())
                                .build())
                    .collect(Collectors.toList()))
        .build();
  }

  private List<ExportReceiptDTO> loadFromDatabase() {
    return exportReceiptRepository.findAll(Sort.by("createdAt").descending()).stream()
        .map(this::toDTO)
        .collect(Collectors.toList());
  }

  public List<ExportReceiptDTO> getCachedReceipts() {
    return receiptCache.get();
  }

  public Instant getCachedAt() {
    return receiptCache.getLoadedAt();
  }

  private static AdvancedFilter<ExportReceiptDTO> buildFilter() {
    return new AdvancedFilter<ExportReceiptDTO>()
        .text("receiptCode", ExportReceiptDTO::getReceiptCode)
        .text("type", ExportReceiptDTO::getType)
        .text("orderCode", ExportReceiptDTO::getOrderCode)
        .text("supplierId", ExportReceiptDTO::getSupplierId)
        .text("staffName", ExportReceiptDTO::getStaffName)
        .text("reason", ExportReceiptDTO::getReason)
        .text(
            "bookTitle",
            r ->
                r.getItems() == null
                    ? List.of()
                    : r.getItems().stream().map(ExportReceiptDetailDTO::getTitle).toList())
        .number("totalQuantity", ExportReceiptDTO::getTotalQuantity)
        .number("totalAmount", ExportReceiptDTO::getTotalAmount)
        .date("createdAt", ExportReceiptDTO::getCreatedAt)
        .keyword(
            ExportReceiptDTO::getReceiptCode,
            ExportReceiptDTO::getOrderCode,
            ExportReceiptDTO::getStaffName,
            ExportReceiptDTO::getReason);
  }

  public SearchResponse<ExportReceiptDTO> search(AdvancedSearchRequest request) {
    return filter.search(
        getCachedReceipts(),
        request,
        "createdAt",
        list -> {
          Map<String, Object> summary = new LinkedHashMap<>();
          summary.put("count", list.size());
          summary.put(
              "totalQuantity",
              list.stream()
                  .mapToInt(r -> r.getTotalQuantity() == null ? 0 : r.getTotalQuantity())
                  .sum());
          summary.put(
              "totalAmount",
              list.stream()
                  .mapToDouble(r -> r.getTotalAmount() == null ? 0 : r.getTotalAmount())
                  .sum());
          return summary;
        },
        getCachedAt());
  }

  public ExportReceiptDTO getById(String id) {
    return getCachedReceipts().stream()
        .filter(r -> r.getId().equals(id))
        .findFirst()
        .orElseThrow(() -> new EntityNotFoundException("Phiếu xuất không tìm thấy"));
  }

  private String generateCode() {
    String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    String code;
    do {
      StringBuilder sb = new StringBuilder("PX");
      for (int i = 0; i < 6; i++) {
        sb.append(chars.charAt(random.nextInt(chars.length())));
      }
      code = sb.toString();
    } while (exportReceiptRepository.existsByReceiptCode(code));
    return code;
  }

  private User findStaff(String staffId) {
    if (staffId == null) {
      return null;
    }
    return userRepository
        .findById(staffId)
        .filter(u -> u.getRole() != null && u.getRole() >= 0 && u.getRole() <= 2)
        .orElse(null);
  }

  /** Phiếu xuất bán hàng cho hóa đơn (không trừ tồn kho lần nữa). */
  public void createForOrder(Order order, User staff) {
    if (order == null || order.getId() == null || order.getItems() == null) {
      return;
    }
    if (exportReceiptRepository.existsByOrder_Id(order.getId())) {
      return;
    }
    ExportReceipt receipt =
        ExportReceipt.builder()
            .receiptCode("PX" + order.getOrderCode())
            .type("SALE")
            .order(order)
            .staff(staff)
            .reason("Xuất bán theo hóa đơn " + order.getOrderCode())
            .createdAt(LocalDateTime.now())
            .build();
    for (OrderDetail d : order.getItems()) {
      double unit =
          (d.getPrice() == null ? 0 : d.getPrice())
              - (d.getDiscount() == null ? 0 : d.getDiscount());
      receipt
          .getItems()
          .add(
              ExportReceiptDetail.builder()
                  .receipt(receipt)
                  .book(d.getBook())
                  .quantity(d.getQuantity())
                  .unitPrice(unit)
                  .build());
    }
    receipt.recalculateTotals();
    exportReceiptRepository.save(receipt);
    cacheRegistry.invalidate(CacheNames.EXPORT_RECEIPTS);
  }

  /** Lập phiếu xuất thủ công: hư hỏng, trả nhà cung cấp, xuất khác. Trừ tồn kho. */
  @Transactional
  public ExportReceiptDTO createManual(ExportReceiptDTO request, String staffId) {
    if (request == null || request.getType() == null || !MANUAL_TYPES.contains(request.getType())) {
      throw new IllegalArgumentException(
          "Loại phiếu xuất không hợp lệ (DAMAGED, RETURN_SUPPLIER, OTHER)");
    }
    if (request.getItems() == null || request.getItems().isEmpty()) {
      throw new IllegalArgumentException("Phiếu xuất phải có ít nhất một sách");
    }
    if (request.getReason() == null || request.getReason().isBlank()) {
      throw new IllegalArgumentException("Vui lòng nhập lý do xuất kho");
    }
    Supplier supplier = null;
    if ("RETURN_SUPPLIER".equals(request.getType())) {
      if (request.getSupplierId() == null || request.getSupplierId().isBlank()) {
        throw new IllegalArgumentException("Phiếu trả hàng phải chọn nhà cung cấp");
      }
      supplier =
          supplierRepository
              .findById(request.getSupplierId())
              .orElseThrow(() -> new EntityNotFoundException("Nhà cung cấp không tìm thấy"));
    }

    User staff = findStaff(staffId);
    ExportReceipt receipt =
        ExportReceipt.builder()
            .receiptCode(generateCode())
            .type(request.getType())
            .supplier(supplier)
            .staff(staff)
            .reason(request.getReason().trim())
            .createdAt(LocalDateTime.now())
            .build();

    Set<String> bookIds = new HashSet<>();
    for (ExportReceiptDetailDTO item : request.getItems()) {
      if (item.getBookId() == null || !bookIds.add(item.getBookId())) {
        throw new IllegalArgumentException("Sách trong phiếu xuất bị trống hoặc bị trùng");
      }
      if (item.getQuantity() == null || item.getQuantity() <= 0) {
        throw new IllegalArgumentException("Số lượng xuất phải lớn hơn 0");
      }
      Book book =
          bookRepository
              .findById(item.getBookId())
              .orElseThrow(() -> new EntityNotFoundException("Sách không tìm thấy"));
      if (item.getQuantity() > book.getStock()) {
        throw new IllegalArgumentException(
            book.getTitle() + " chỉ còn " + book.getStock() + " cuốn trong kho");
      }
      double unit =
          item.getUnitPrice() != null && item.getUnitPrice() >= 0
              ? item.getUnitPrice()
              : (book.getPrice() == null ? 0 : book.getPrice());
      receipt
          .getItems()
          .add(
              ExportReceiptDetail.builder()
                  .receipt(receipt)
                  .book(book)
                  .quantity(item.getQuantity())
                  .unitPrice(unit)
                  .build());
      book.setStock(book.getStock() - item.getQuantity());
    }
    receipt.recalculateTotals();
    ExportReceipt saved = exportReceiptRepository.save(receipt);

    for (ExportReceiptDetail d : saved.getItems()) {
      bookRepository.save(d.getBook());
      stockService.record(
          d.getBook(),
          -d.getQuantity(),
          StockService.EXPORT,
          "EXPORT",
          saved.getId(),
          saved.getReceiptCode(),
          staff,
          saved.getReason());
    }
    cacheRegistry.invalidate(CacheNames.EXPORT_RECEIPTS, CacheNames.BOOKS);
    return toDTO(saved);
  }
}
