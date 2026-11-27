package com.bookstore.backend.service;

import com.bookstore.backend.cache.CacheNames;
import com.bookstore.backend.cache.DataCacheRegistry;
import com.bookstore.backend.cache.ListCache;
import com.bookstore.backend.dto.BookDTO;
import com.bookstore.backend.dto.StockMovementDTO;
import com.bookstore.backend.dto.search.AdvancedSearchRequest;
import com.bookstore.backend.dto.search.SearchResponse;
import com.bookstore.backend.entities.Book;
import com.bookstore.backend.entities.StockMovement;
import com.bookstore.backend.entities.User;
import com.bookstore.backend.repository.StockMovementRepository;
import com.bookstore.backend.utils.AdvancedFilter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

/**
 * Thẻ kho: ghi nhận mọi lần tồn kho thay đổi (nhập, bán, hủy/trả, xuất, điều chỉnh) và lập báo cáo
 * nhập - xuất - tồn. Danh sách thẻ kho được cache trên RAM như các danh sách khác.
 */
@Service
public class StockService {

  public static final String OPENING = "OPENING";
  public static final String IMPORT = "IMPORT";
  public static final String SALE = "SALE";
  public static final String CANCEL_RETURN = "CANCEL_RETURN";
  public static final String EXPORT = "EXPORT";
  public static final String IMPORT_CANCEL = "IMPORT_CANCEL";
  public static final String ADJUST = "ADJUST";

  private final StockMovementRepository stockMovementRepository;
  private final DataCacheRegistry cacheRegistry;
  private final ListCache<StockMovementDTO> movementCache;
  private final AdvancedFilter<StockMovementDTO> filter = buildFilter();

  public StockService(
      StockMovementRepository stockMovementRepository, DataCacheRegistry cacheRegistry) {
    this.stockMovementRepository = stockMovementRepository;
    this.cacheRegistry = cacheRegistry;
    this.movementCache = cacheRegistry.register(CacheNames.STOCK_MOVEMENTS, this::loadFromDatabase);
  }

  /**
   * Ghi một dòng thẻ kho. Tồn kho của sách phải được cập nhật trước khi gọi (stockAfter lấy từ
   * book.getStock()).
   */
  public void record(
      Book book,
      int quantityChange,
      String type,
      String refType,
      String refId,
      String refCode,
      User staff,
      String note) {
    if (book == null || quantityChange == 0) {
      return;
    }
    stockMovementRepository.save(
        StockMovement.builder()
            .book(book)
            .type(type)
            .quantityChange(quantityChange)
            .stockAfter(book.getStock() == null ? 0 : book.getStock())
            .refType(refType)
            .refId(refId)
            .refCode(refCode)
            .staff(staff)
            .note(note)
            .createdAt(LocalDateTime.now())
            .build());
    cacheRegistry.invalidate(CacheNames.STOCK_MOVEMENTS);
  }

  private StockMovementDTO toDTO(StockMovement m) {
    return StockMovementDTO.builder()
        .id(m.getId())
        .bookId(m.getBook().getId())
        .bookTitle(m.getBook().getTitle())
        .type(m.getType())
        .quantityChange(m.getQuantityChange())
        .stockAfter(m.getStockAfter())
        .refType(m.getRefType())
        .refId(m.getRefId())
        .refCode(m.getRefCode())
        .staffName(m.getStaff() != null ? m.getStaff().getFullname() : null)
        .note(m.getNote())
        .createdAt(m.getCreatedAt())
        .build();
  }

  private List<StockMovementDTO> loadFromDatabase() {
    return stockMovementRepository
        .findAll(Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id")))
        .stream()
        .map(this::toDTO)
        .collect(Collectors.toList());
  }

  public List<StockMovementDTO> getCachedMovements() {
    return movementCache.get();
  }

  private static AdvancedFilter<StockMovementDTO> buildFilter() {
    return new AdvancedFilter<StockMovementDTO>()
        .text("bookId", StockMovementDTO::getBookId)
        .text("bookTitle", StockMovementDTO::getBookTitle)
        .text("type", StockMovementDTO::getType)
        .text("refCode", StockMovementDTO::getRefCode)
        .text("refType", StockMovementDTO::getRefType)
        .text("staffName", StockMovementDTO::getStaffName)
        .number("quantityChange", StockMovementDTO::getQuantityChange)
        .number("stockAfter", StockMovementDTO::getStockAfter)
        .date("createdAt", StockMovementDTO::getCreatedAt)
        .keyword(
            StockMovementDTO::getBookTitle,
            StockMovementDTO::getRefCode,
            StockMovementDTO::getNote);
  }

  /** Tìm kiếm thẻ kho (ví dụ thẻ kho của một cuốn sách: bookId = ...). */
  public SearchResponse<StockMovementDTO> search(AdvancedSearchRequest request) {
    return filter.search(
        getCachedMovements(),
        request,
        "createdAt",
        list -> {
          Map<String, Object> summary = new LinkedHashMap<>();
          summary.put("count", list.size());
          summary.put(
              "totalIn",
              list.stream()
                  .filter(m -> m.getQuantityChange() > 0 && !OPENING.equals(m.getType()))
                  .mapToInt(StockMovementDTO::getQuantityChange)
                  .sum());
          summary.put(
              "totalOut",
              -list.stream()
                  .filter(m -> m.getQuantityChange() < 0)
                  .mapToInt(StockMovementDTO::getQuantityChange)
                  .sum());
          return summary;
        },
        movementCache.getLoadedAt());
  }

  /**
   * Báo cáo nhập - xuất - tồn của từng đầu sách trong khoảng [from, to]: tồn đầu kỳ, nhập từ nhà
   * cung cấp, hàng trả / hủy nhập lại, bán ra, xuất khác, tồn cuối kỳ.
   */
  public Map<String, Object> getInventoryReport(List<BookDTO> books, LocalDate from, LocalDate to) {
    LocalDate end = to != null ? to : LocalDate.now();
    LocalDate start = from != null ? from : end.withDayOfMonth(1);
    if (start.isAfter(end)) {
      throw new IllegalArgumentException("Ngày bắt đầu phải trước hoặc bằng ngày kết thúc");
    }
    LocalDateTime startAt = start.atStartOfDay();
    LocalDateTime endAt = end.plusDays(1).atStartOfDay();

    Map<String, List<StockMovementDTO>> byBook =
        getCachedMovements().stream().collect(Collectors.groupingBy(StockMovementDTO::getBookId));

    List<Map<String, Object>> rows = new ArrayList<>();
    for (BookDTO book : books) {
      List<StockMovementDTO> list = byBook.getOrDefault(book.getId(), List.of());
      int opening = 0;
      int imported = 0;
      int returned = 0;
      int sold = 0;
      int exported = 0;
      for (StockMovementDTO m : list) {
        if (m.getCreatedAt().isBefore(startAt)) {
          opening += m.getQuantityChange();
        } else if (m.getCreatedAt().isBefore(endAt)) {
          switch (m.getType()) {
            case OPENING -> opening += m.getQuantityChange();
            case IMPORT -> imported += m.getQuantityChange();
            case CANCEL_RETURN -> returned += m.getQuantityChange();
            case SALE -> sold -= m.getQuantityChange();
            default -> exported -= m.getQuantityChange(); // EXPORT, IMPORT_CANCEL, ADJUST
          }
        }
      }
      int closing = opening + imported + returned - sold - exported;
      double price = book.getPrice();

      Map<String, Object> row = new LinkedHashMap<>();
      row.put("bookId", book.getId());
      row.put("title", book.getTitle());
      row.put("price", price);
      row.put("opening", opening);
      row.put("imported", imported);
      row.put("returned", returned);
      row.put("sold", sold);
      row.put("exported", exported);
      row.put("closing", closing);
      row.put("closingValue", closing * price);
      row.put("currentStock", book.getStock());
      rows.add(row);
    }

    Map<String, Object> summary = new LinkedHashMap<>();
    for (String key : List.of("opening", "imported", "returned", "sold", "exported", "closing")) {
      summary.put(key, rows.stream().mapToInt(r -> (Integer) r.get(key)).sum());
    }
    summary.put(
        "closingValue", rows.stream().mapToDouble(r -> (Double) r.get("closingValue")).sum());

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("from", start);
    result.put("to", end);
    result.put(
        "rows",
        rows.stream()
            .sorted(Comparator.comparing((Map<String, Object> r) -> -((Integer) r.get("sold"))))
            .collect(Collectors.toList()));
    result.put("summary", summary);
    result.put("cachedAt", movementCache.getLoadedAt());
    return result;
  }
}
