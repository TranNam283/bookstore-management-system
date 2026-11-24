package com.bookstore.backend.dto;

import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Một dòng khách hàng trong trang quản trị, kèm số liệu mua hàng tính từ cache đơn hàng. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerSummaryDTO {
  private String id;
  private String fullname;
  private String email;
  private Integer status;
  private LocalDateTime createdAt;
  private List<String> cities; // các thành phố trong sổ địa chỉ / đơn hàng
  private long orderCount; // tổng số đơn (không tính đơn chờ thanh toán)
  private long deliveredCount; // số đơn giao thành công
  private double totalSpent; // tổng tiền các đơn giao thành công
  private LocalDateTime lastOrderAt;
}
