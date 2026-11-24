package com.bookstore.backend.controller;

import com.bookstore.backend.dto.OrderDTO;
import com.bookstore.backend.dto.search.AdvancedSearchRequest;
import com.bookstore.backend.dto.search.SearchResponse;
import com.bookstore.backend.service.OrderService;
import com.bookstore.backend.service.StatisticsService;
import com.bookstore.backend.utils.CurrentUserResolver;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/order")
@CrossOrigin(origins = "*")
public class OrderController {

  private final OrderService orderService;
  private final StatisticsService statisticsService;
  private final CurrentUserResolver currentUserResolver;

  public OrderController(
      OrderService orderService,
      StatisticsService statisticsService,
      CurrentUserResolver currentUserResolver) {
    this.orderService = orderService;
    this.statisticsService = statisticsService;
    this.currentUserResolver = currentUserResolver;
  }

  // order cho admin
  @GetMapping
  public ResponseEntity<?> getAllOrders(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "12") int limit,
      @RequestParam(required = false) String q,
      @RequestParam(required = false) Integer status,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate start,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate end) {
    Page<OrderDTO> orderPage = orderService.getAllOrders(page, limit, q, status, start, end);

    List<Integer> statuses = Arrays.asList(0, 3, 4, 5);
    Map<Integer, Long> totalByStatus = orderService.getOrderCountByStatus(statuses);

    return ResponseEntity.ok(
        Map.of(
            "orders", orderPage.getContent(),
            "totalPages", orderPage.getTotalPages(),
            "total", orderPage.getTotalElements(),
            "totalByStatus", totalByStatus));
  }

  // tìm kiếm nâng cao trên danh sách RAM: nhóm điều kiện AND/OR với >, <, =, >=, <=
  @PostMapping("/search")
  public ResponseEntity<SearchResponse<OrderDTO>> searchOrders(
      @RequestBody(required = false) AdvancedSearchRequest request) {
    return ResponseEntity.ok(orderService.searchOrders(request));
  }

  @GetMapping("/search-options")
  public ResponseEntity<Map<String, Object>> getSearchOptions() {
    return ResponseEntity.ok(orderService.getSearchOptions());
  }

  @GetMapping("/status-count")
  public ResponseEntity<Map<Integer, Long>> getStatusCount() {
    return ResponseEntity.ok(
        orderService.getOrderCountByStatus(Arrays.asList(-1, 0, 1, 2, 3, 4, 5)));
  }

  @GetMapping("/{id}")
  public ResponseEntity<OrderDTO> getOrderById(@PathVariable String id) {
    OrderDTO orderDTO = orderService.getOrderById(id);
    return ResponseEntity.ok(orderDTO);
  }

  @PutMapping("/{id}")
  public ResponseEntity<OrderDTO> updateOrderStatus(
      @PathVariable("id") String orderId,
      @RequestParam Integer status,
      @RequestHeader(value = "Authorization", required = false) String authHeader) {
    String staffId = currentUserResolver.resolveUserId(authHeader);
    OrderDTO updatedOrder = orderService.updateOrderStatus(orderId, status, staffId);
    return ResponseEntity.ok(updatedOrder);
  }

  // order cho customer
  @PostMapping("/user/{userId}")
  public ResponseEntity<OrderDTO> createOrder(
      @RequestBody OrderDTO orderDTO, @PathVariable String userId) {
    OrderDTO createdOrder = orderService.createOrder(orderDTO, userId);
    return ResponseEntity.ok(createdOrder);
  }

  @GetMapping("/user/{userId}/{orderCode}")
  public ResponseEntity<OrderDTO> getOrderByUserAndCode(
      @PathVariable String userId, @PathVariable String orderCode) {
    OrderDTO order = orderService.getOrderByUserAndCode(userId, orderCode);
    return ResponseEntity.ok(order);
  }

  @GetMapping("/user/{userId}")
  public ResponseEntity<?> getOrdersByUser(
      @PathVariable String userId,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "12") int limit,
      @RequestParam(required = false) Integer status) {
    Page<OrderDTO> orderPage = orderService.getOrdersByUserAndStatus(userId, page, limit, status);

    return ResponseEntity.ok(
        Map.of(
            "orders", orderPage.getContent(),
            "totalPages", orderPage.getTotalPages(),
            "total", orderPage.getTotalElements()));
  }

  @GetMapping("/statistics")
  public ResponseEntity<Map<String, Object>> getOrderStats() {
    return ResponseEntity.ok(statisticsService.getOverview());
  }
}
