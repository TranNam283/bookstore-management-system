package com.bookstore.backend.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Một dòng nhân viên / quản trị trong trang quản trị, kèm số liệu công việc tính từ cache. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StaffSummaryDTO {
  private String id;
  private String fullname;
  private String email;
  private Integer role; // 0 quản trị viên, 1 nhân viên bán hàng, 2 nhân viên kho
  private String roleName;
  private Integer status;
  private LocalDateTime createdAt;
  private long confirmedCount; // số hóa đơn nhân viên đã xác nhận (xử lý)
  private long deliveredCount; // trong đó giao thành công
  private long cancelledCount; // trong đó bị hủy / trả hàng
  private double revenue; // tổng tiền các hóa đơn giao thành công do nhân viên xử lý
  private long importReceiptCount; // số phiếu nhập đã lập (còn hiệu lực)
  private double importAmount; // tổng tiền phiếu nhập
  private long exportReceiptCount; // số phiếu xuất đã lập
  private LocalDateTime lastActivityAt; // lần xử lý chứng từ gần nhất
}
