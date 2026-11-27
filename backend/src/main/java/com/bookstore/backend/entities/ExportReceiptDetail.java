package com.bookstore.backend.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Một dòng sách trong phiếu xuất. */
@Entity
@Table(name = "export_receipt_detail")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExportReceiptDetail {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;

  @ManyToOne
  @JoinColumn(name = "receiptId", nullable = false)
  private ExportReceipt receipt;

  @ManyToOne
  @JoinColumn(name = "bookId", nullable = false)
  private Book book;

  @Column(nullable = false)
  private Integer quantity;

  // Đơn giá xuất
  @Column(nullable = false)
  private Double unitPrice;

  // Thành tiền = quantity * unitPrice
  @Builder.Default
  @Column(nullable = false)
  private Double lineTotal = 0.0;

  @PrePersist
  @PreUpdate
  public void calculateAmounts() {
    this.lineTotal = (quantity == null ? 0 : quantity) * (unitPrice == null ? 0 : unitPrice);
  }
}
