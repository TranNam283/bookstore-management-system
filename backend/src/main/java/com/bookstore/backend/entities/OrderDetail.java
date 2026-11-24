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

@Entity
@Table(name = "orderdetail")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderDetail {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;

  @Column(nullable = false)
  private Integer quantity;

  @Column(nullable = false)
  private Double price;

  @Column(nullable = false)
  private Double discount; // tiền giảm / 1 cuốn tại thời điểm đặt

  // Tiền trước giảm = quantity * price
  @Builder.Default
  @Column(nullable = false)
  private Double subtotal = 0.0;

  // Tiền giảm = quantity * discount
  @Builder.Default
  @Column(nullable = false)
  private Double discountAmount = 0.0;

  // Thành tiền = subtotal - discountAmount
  @Builder.Default
  @Column(nullable = false)
  private Double lineTotal = 0.0;

  @ManyToOne
  @JoinColumn(name = "bookId", nullable = false)
  private Book book;

  @ManyToOne
  @JoinColumn(name = "orderId", nullable = false)
  private Order order;

  /** Tính lại tiền trước giảm, tiền giảm và thành tiền của dòng. */
  @PrePersist
  @PreUpdate
  public void calculateAmounts() {
    int qty = quantity == null ? 0 : quantity;
    double unitPrice = price == null ? 0 : price;
    double unitDiscount = discount == null ? 0 : discount;
    this.subtotal = qty * unitPrice;
    this.discountAmount = qty * unitDiscount;
    this.lineTotal = this.subtotal - this.discountAmount;
  }
}
