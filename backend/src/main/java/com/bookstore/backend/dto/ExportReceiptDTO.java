package com.bookstore.backend.dto;

import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Phiếu xuất kho. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExportReceiptDTO {
  private String id;
  private String receiptCode;
  private String type; // SALE, DAMAGED, RETURN_SUPPLIER, OTHER
  private String orderId;
  private String orderCode;
  private String supplierId;
  private String supplierName;
  private String staffId;
  private String staffName;
  private String reason;
  private Integer totalQuantity;
  private Double totalAmount;
  private LocalDateTime createdAt;
  private List<ExportReceiptDetailDTO> items;
}
