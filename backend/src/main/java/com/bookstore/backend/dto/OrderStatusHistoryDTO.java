package com.bookstore.backend.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderStatusHistoryDTO {
  private Integer fromStatus;
  private Integer toStatus;
  private String staffName;
  private String note;
  private LocalDateTime createdAt;
}
