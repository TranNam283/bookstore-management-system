package com.bookstore.backend.entities;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Phiếu xuất kho. type: SALE (xuất bán theo hóa đơn), DAMAGED (hư hỏng / mất), RETURN_SUPPLIER (trả
 * nhà cung cấp), OTHER (khác).
 */
@Entity
@Table(name = "export_receipt")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExportReceipt {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;

  @Column(nullable = false, unique = true, length = 15)
  private String receiptCode;

  @Column(nullable = false, length = 20)
  private String type;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "orderId", unique = true)
  private Order order;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "supplierId")
  private Supplier supplier;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "staffId")
  private User staff;

  @Column(length = 255)
  private String reason;

  @Builder.Default
  @Column(nullable = false)
  private Integer totalQuantity = 0;

  @Builder.Default
  @Column(nullable = false)
  private Double totalAmount = 0.0;

  @Builder.Default
  @Column(nullable = false)
  private LocalDateTime createdAt = LocalDateTime.now();

  @Builder.Default
  @OneToMany(mappedBy = "receipt", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<ExportReceiptDetail> items = new ArrayList<>();

  /** Tính lại tổng số lượng và tổng giá trị xuất. */
  public void recalculateTotals() {
    int qty = 0;
    double amount = 0;
    for (ExportReceiptDetail item : items) {
      item.calculateAmounts();
      qty += item.getQuantity();
      amount += item.getLineTotal();
    }
    this.totalQuantity = qty;
    this.totalAmount = amount;
  }
}
