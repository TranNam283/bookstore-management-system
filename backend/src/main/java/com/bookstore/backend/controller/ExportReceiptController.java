package com.bookstore.backend.controller;

import com.bookstore.backend.dto.ExportReceiptDTO;
import com.bookstore.backend.dto.search.AdvancedSearchRequest;
import com.bookstore.backend.dto.search.SearchResponse;
import com.bookstore.backend.service.ExportReceiptService;
import com.bookstore.backend.utils.CurrentUserResolver;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Phiếu xuất kho. */
@RestController
@RequestMapping("/api/export-receipt")
@CrossOrigin(origins = "*")
public class ExportReceiptController {

  private final ExportReceiptService exportReceiptService;
  private final CurrentUserResolver currentUserResolver;

  public ExportReceiptController(
      ExportReceiptService exportReceiptService, CurrentUserResolver currentUserResolver) {
    this.exportReceiptService = exportReceiptService;
    this.currentUserResolver = currentUserResolver;
  }

  @PostMapping("/search")
  public ResponseEntity<SearchResponse<ExportReceiptDTO>> search(
      @RequestBody(required = false) AdvancedSearchRequest request) {
    return ResponseEntity.ok(exportReceiptService.search(request));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ExportReceiptDTO> getById(@PathVariable String id) {
    return ResponseEntity.ok(exportReceiptService.getById(id));
  }

  // lập phiếu xuất thủ công (hư hỏng, trả nhà cung cấp, khác)
  @PostMapping
  public ResponseEntity<ExportReceiptDTO> create(
      @RequestBody ExportReceiptDTO request,
      @RequestHeader(value = "Authorization", required = false) String authHeader) {
    return ResponseEntity.ok(
        exportReceiptService.createManual(request, currentUserResolver.resolveUserId(authHeader)));
  }
}
