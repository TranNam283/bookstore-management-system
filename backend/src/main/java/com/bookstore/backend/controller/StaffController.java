package com.bookstore.backend.controller;

import com.bookstore.backend.dto.StaffSummaryDTO;
import com.bookstore.backend.dto.search.AdvancedSearchRequest;
import com.bookstore.backend.dto.search.SearchResponse;
import com.bookstore.backend.service.StaffService;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Danh sách & hồ sơ nhân viên / quản trị cho trang quản trị (dữ liệu RAM). */
@RestController
@RequestMapping("/api/staff")
@CrossOrigin(origins = "*")
public class StaffController {

  private final StaffService staffService;

  public StaffController(StaffService staffService) {
    this.staffService = staffService;
  }

  @PostMapping("/search")
  public ResponseEntity<SearchResponse<StaffSummaryDTO>> search(
      @RequestBody(required = false) AdvancedSearchRequest request) {
    return ResponseEntity.ok(staffService.search(request));
  }

  // hồ sơ nhân viên: thông tin, hóa đơn đã xử lý, phiếu nhập / xuất đã lập
  @GetMapping("/{id}")
  public ResponseEntity<Map<String, Object>> detail(@PathVariable String id) {
    return ResponseEntity.ok(staffService.getDetail(id));
  }
}
