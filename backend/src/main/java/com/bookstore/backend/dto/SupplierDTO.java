package com.bookstore.backend.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Nhà cung cấp kèm số liệu nhập hàng. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SupplierDTO {
  private String id;
  private String code;
  private String name;
  private String contactName;
  private String phone;
  private String email;
  private String address;
  private String taxCode;
  private Integer status; // 1 đang hợp tác, 0 ngừng
  private LocalDateTime createdAt;
  private long receiptCount; // số phiếu nhập
  private double totalImported; // tổng tiền đã nhập
  private LocalDateTime lastImportAt;
}
