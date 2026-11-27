package com.bookstore.backend.entities;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Lịch sử chuyển trạng thái hóa đơn. */
@Entity
@Table(name = "order_status_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderStatusHistory {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "orderId", nullable = false)
  private Order order;

  private Integer fromStatus;

  @Column(nullable = false)
  private Integer toStatus;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "staffId")
  private User staff;

  @Column(length = 255)
  private String note;

  @Builder.Default
  @Column(nullable = false)
  private LocalDateTime createdAt = LocalDateTime.now();
}
