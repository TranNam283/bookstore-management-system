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
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;

  @Column(nullable = false, unique = true, length = 15)
  private String orderCode;

  @Column(nullable = false, length = 50)
  private String fullname;

  @Column(nullable = false, length = 15)
  private String phone;

  @Column(nullable = false, length = 70)
  private String speaddress;

  @Column(nullable = false, length = 70)
  private String city;

  @Column(nullable = false, length = 70)
  private String ward;

  @Column(nullable = false, length = 15)
  private String paymethod;

  @Column(nullable = false)
  private Integer status;

  @Column(nullable = false)
  private Double total; // tổng thanh toán = subtotal - discountTotal

  // Tổng tiền trước giảm giá
  @Builder.Default
  @Column(nullable = false)
  private Double subtotal = 0.0;

  // Tổng tiền giảm giá
  @Builder.Default
  @Column(nullable = false)
  private Double discountTotal = 0.0;

  // Nhân viên xác nhận đơn (dùng cho thống kê theo nhân viên)
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "staffId")
  private User staff;

  private LocalDateTime confirmedAt;

  @Builder.Default
  @Column(nullable = false)
  private LocalDateTime createdAt = LocalDateTime.now();

  @ManyToOne
  @JoinColumn(name = "userId", nullable = false)
  private User user;

  @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
  private List<OrderDetail> items;

  @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
  private List<Payment> payments;

  /** Tính lại các tổng tiền của hóa đơn từ các dòng chi tiết. */
  public void recalculateTotals() {
    double sub = 0;
    double disc = 0;
    if (items != null) {
      for (OrderDetail item : items) {
        item.calculateAmounts();
        sub += item.getSubtotal();
        disc += item.getDiscountAmount();
      }
    }
    this.subtotal = sub;
    this.discountTotal = disc;
    this.total = sub - disc;
  }
}
