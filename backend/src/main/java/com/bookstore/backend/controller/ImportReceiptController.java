package com.bookstore.backend.controller;

import com.bookstore.backend.dto.ImportReceiptDTO;
import com.bookstore.backend.dto.search.AdvancedSearchRequest;
import com.bookstore.backend.dto.search.SearchResponse;
import com.bookstore.backend.service.ImportReceiptService;
import com.bookstore.backend.utils.CurrentUserResolver;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/import-receipt")
@CrossOrigin(origins = "*")
public class ImportReceiptController {

  private final ImportReceiptService importReceiptService;
  private final CurrentUserResolver currentUserResolver;

  public ImportReceiptController(
      ImportReceiptService importReceiptService, CurrentUserResolver currentUserResolver) {
    this.importReceiptService = importReceiptService;
    this.currentUserResolver = currentUserResolver;
  }

  @GetMapping
  public ResponseEntity<SearchResponse<ImportReceiptDTO>> getAll(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int limit,
      @RequestParam(required = false) String q) {
    AdvancedSearchRequest request = new AdvancedSearchRequest();
    request.setKeyword(q);
    request.setPage(page);
    request.setLimit(limit);
    return ResponseEntity.ok(importReceiptService.search(request));
  }

  // tìm kiếm nâng cao trên danh sách RAM
  @PostMapping("/search")
  public ResponseEntity<SearchResponse<ImportReceiptDTO>> search(
      @RequestBody(required = false) AdvancedSearchRequest request) {
    return ResponseEntity.ok(importReceiptService.search(request));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ImportReceiptDTO> getById(@PathVariable String id) {
    return ResponseEntity.ok(importReceiptService.getById(id));
  }

  @PostMapping
  public ResponseEntity<ImportReceiptDTO> create(
      @RequestBody ImportReceiptDTO request,
      @RequestHeader(value = "Authorization", required = false) String authHeader) {
    String staffId = currentUserResolver.resolveUserId(authHeader);
    return ResponseEntity.ok(importReceiptService.create(request, staffId));
  }

  // hủy phiếu nhập (trừ lại tồn kho)
  @PatchMapping("/{id}/cancel")
  public ResponseEntity<ImportReceiptDTO> cancel(
      @PathVariable String id,
      @RequestBody Map<String, String> body,
      @RequestHeader(value = "Authorization", required = false) String authHeader) {
    return ResponseEntity.ok(
        importReceiptService.cancel(
            id, body.get("reason"), currentUserResolver.resolveUserId(authHeader)));
  }
}
