package com.bookstore.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExportReceiptDetailDTO {
  private String bookId;
  private String title;
  private String image;
  private Integer quantity;
  private Double unitPrice; // đơn giá xuất
  private Double lineTotal; // thành tiền
}
