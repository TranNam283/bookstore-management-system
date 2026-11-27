package com.bookstore.backend.entities;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Một dòng thẻ kho: tồn kho của một cuốn sách tăng / giảm bao nhiêu, vì sao, còn lại bao nhiêu. */
@Entity
@Table(name = "stock_movement")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockMovement {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "bookId", nullable = false)
  private Book book;

  // OPENING, IMPORT, SALE, CANCEL_RETURN, EXPORT, IMPORT_CANCEL, ADJUST
  @Column(nullable = false, length = 20)
  private String type;

  // + tăng tồn, - giảm tồn
  @Column(nullable = false)
  private Integer quantityChange;

  // tồn kho sau khi thay đổi
  @Column(nullable = false)
  private Integer stockAfter;

  @Column(length = 20)
  private String refType; // ORDER, IMPORT, EXPORT, BOOK

  private String refId;

  @Column(length = 20)
  private String refCode;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "staffId")
  private User staff;

  @Column(length = 255)
  private String note;

  @Builder.Default
  @Column(nullable = false)
  private LocalDateTime createdAt = LocalDateTime.now();
}
