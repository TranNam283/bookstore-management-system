package com.bookstore.backend.controller;

import com.bookstore.backend.dto.StockMovementDTO;
import com.bookstore.backend.dto.search.AdvancedSearchRequest;
import com.bookstore.backend.dto.search.SearchResponse;
import com.bookstore.backend.service.BookService;
import com.bookstore.backend.service.StockService;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Tồn kho: thẻ kho và báo cáo nhập - xuất - tồn (tính trên dữ liệu RAM). */
@RestController
@RequestMapping("/api/inventory")
@CrossOrigin(origins = "*")
public class InventoryController {

  private final StockService stockService;
  private final BookService bookService;

  public InventoryController(StockService stockService, BookService bookService) {
    this.stockService = stockService;
    this.bookService = bookService;
  }

  // thẻ kho (lọc theo sách, loại, ngày...)
  @PostMapping("/movements/search")
  public ResponseEntity<SearchResponse<StockMovementDTO>> movements(
      @RequestBody(required = false) AdvancedSearchRequest request) {
    return ResponseEntity.ok(stockService.search(request));
  }

  // báo cáo nhập - xuất - tồn từ ngày A đến ngày B
  @GetMapping("/report")
  public ResponseEntity<Map<String, Object>> report(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    return ResponseEntity.ok(
        stockService.getInventoryReport(bookService.getCachedBooks(), from, to));
  }
}
