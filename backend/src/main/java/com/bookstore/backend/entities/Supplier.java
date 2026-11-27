package com.bookstore.backend.entities;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Nhà cung cấp sách (đối tác nhập hàng). */
@Entity
@Table(name = "supplier")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Supplier {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;

  @Column(nullable = false, unique = true, length = 20)
  private String code;

  @Column(nullable = false, length = 150)
  private String name;

  @Column(length = 100)
  private String contactName;

  @Column(length = 15)
  private String phone;

  @Column(length = 100)
  private String email;

  @Column(length = 255)
  private String address;

  @Column(length = 20)
  private String taxCode;

  @Builder.Default
  @Column(nullable = false)
  private Integer status = 1; // 1 đang hợp tác, 0 ngừng

  @Builder.Default
  @Column(nullable = false)
  private LocalDateTime createdAt = LocalDateTime.now();
}
