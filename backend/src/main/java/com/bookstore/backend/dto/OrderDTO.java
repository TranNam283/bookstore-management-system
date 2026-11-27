package com.bookstore.backend.dto;

import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderDTO {
  private String id;
  private String orderCode;
  private String fullname;
  private String phone;
  private String speaddress;
  private String city;
  private String ward;
  private String paymethod;
  private Integer status;
  private Double total; // tổng thanh toán (sau giảm)
  private Double subtotal; // tổng tiền trước giảm
  private Double discountTotal; // tổng tiền giảm
  private Integer totalQuantity; // tổng số cuốn
  private String staffId; // nhân viên xác nhận
  private String staffName;
  private LocalDateTime confirmedAt;
  private String accountEmail;
  private LocalDateTime createdAt;
  private List<OrderDetailDTO> items;
  private List<OrderStatusHistoryDTO> history; // lịch sử trạng thái
  private String exportReceiptCode; // phiếu xuất kho bán hàng
}
