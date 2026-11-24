package com.bookstore.backend.entities;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Phiếu nhập hàng: dùng để tính tổng chi trong thống kê thu - chi - lợi nhuận. */
@Entity
@Table(name = "import_receipt")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImportReceipt {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;

  @Column(nullable = false, unique = true, length = 15)
  private String receiptCode;

  // Nhà cung cấp (nhà xuất bản)
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "publisherId")
  private Publisher publisher;

  // Nhân viên lập phiếu
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "staffId")
  private User staff;

  @Column(length = 255)
  private String note;

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
  private List<ImportReceiptDetail> items = new ArrayList<>();

  /** Tính lại tổng số lượng và tổng tiền nhập từ các dòng chi tiết. */
  public void recalculateTotals() {
    int qty = 0;
    double amount = 0;
    for (ImportReceiptDetail item : items) {
      item.calculateAmounts();
      qty += item.getQuantity();
      amount += item.getLineTotal();
    }
    this.totalQuantity = qty;
    this.totalAmount = amount;
  }
}
