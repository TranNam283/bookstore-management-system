package com.bookstore.backend.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Một dòng sách trong phiếu nhập. */
@Entity
@Table(name = "import_receipt_detail")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImportReceiptDetail {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;

  @ManyToOne
  @JoinColumn(name = "receiptId", nullable = false)
  private ImportReceipt receipt;

  @ManyToOne
  @JoinColumn(name = "bookId", nullable = false)
  private Book book;

  @Column(nullable = false)
  private Integer quantity;

  // Giá nhập / 1 cuốn
  @Column(nullable = false)
  private Double importPrice;

  // Thành tiền = quantity * importPrice
  @Builder.Default
  @Column(nullable = false)
  private Double lineTotal = 0.0;

  @PrePersist
  @PreUpdate
  public void calculateAmounts() {
    int qty = quantity == null ? 0 : quantity;
    double price = importPrice == null ? 0 : importPrice;
    this.lineTotal = qty * price;
  }
}
