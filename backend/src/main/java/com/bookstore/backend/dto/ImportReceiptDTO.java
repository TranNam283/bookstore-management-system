package com.bookstore.backend.dto;

import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImportReceiptDTO {
  private String id;
  private String receiptCode;
  private String supplierId;
  private String supplierName;
  private Integer status; // 1 hoàn tất, 0 đã hủy
  private LocalDateTime cancelledAt;
  private String cancelReason;
  private String publisherId;
  private String publisherName;
  private String staffId;
  private String staffName;
  private String note;
  private Integer totalQuantity;
  private Double totalAmount;
  private LocalDateTime createdAt;
  private List<ImportReceiptDetailDTO> items;
}
