package com.bookstore.backend.controller;

import com.bookstore.backend.service.StatisticsService;
import com.bookstore.backend.service.StatisticsService.GroupBy;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Các bảng thống kê kinh doanh (tính trên dữ liệu RAM). */
@RestController
@RequestMapping("/api/statistics")
@CrossOrigin(origins = "*")
public class StatisticsController {

  private final StatisticsService statisticsService;

  public StatisticsController(StatisticsService statisticsService) {
    this.statisticsService = statisticsService;
  }

  @GetMapping("/overview")
  public ResponseEntity<Map<String, Object>> overview() {
    return ResponseEntity.ok(statisticsService.getOverview());
  }

  // tổng tiền hóa đơn từ ngày A đến ngày B, theo ngày / tháng / quý / năm
  @GetMapping("/revenue")
  public ResponseEntity<Map<String, Object>> revenue(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(defaultValue = "month") String groupBy) {
    return ResponseEntity.ok(statisticsService.getRevenue(from, to, GroupBy.from(groupBy)));
  }

  // tổng thu (hóa đơn) - tổng chi (phiếu nhập) - lợi nhuận
  @GetMapping("/profit")
  public ResponseEntity<Map<String, Object>> profit(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(defaultValue = "month") String groupBy) {
    return ResponseEntity.ok(statisticsService.getProfit(from, to, GroupBy.from(groupBy)));
  }

  // tổng tiền hóa đơn theo nhân viên
  @GetMapping("/by-staff")
  public ResponseEntity<Map<String, Object>> byStaff(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    return ResponseEntity.ok(statisticsService.getRevenueByStaff(from, to));
  }
}
