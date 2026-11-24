package com.bookstore.backend.controller;

import com.bookstore.backend.dto.CustomerSummaryDTO;
import com.bookstore.backend.dto.search.AdvancedSearchRequest;
import com.bookstore.backend.dto.search.SearchResponse;
import com.bookstore.backend.service.CustomerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Tìm kiếm nâng cao khách hàng cho trang quản trị (dữ liệu RAM). */
@RestController
@RequestMapping("/api/customer")
@CrossOrigin(origins = "*")
public class CustomerController {

  private final CustomerService customerService;

  public CustomerController(CustomerService customerService) {
    this.customerService = customerService;
  }

  @PostMapping("/search")
  public ResponseEntity<SearchResponse<CustomerSummaryDTO>> search(
      @RequestBody(required = false) AdvancedSearchRequest request) {
    return ResponseEntity.ok(customerService.search(request));
  }
}
