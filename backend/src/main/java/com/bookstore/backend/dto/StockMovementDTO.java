package com.bookstore.backend.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Một dòng thẻ kho. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockMovementDTO {
  private String id;
  private String bookId;
  private String bookTitle;
  private String type; // OPENING, IMPORT, SALE, CANCEL_RETURN, EXPORT, IMPORT_CANCEL, ADJUST
  private Integer quantityChange;
  private Integer stockAfter;
  private String refType;
  private String refId;
  private String refCode;
  private String staffName;
  private String note;
  private LocalDateTime createdAt;
}
