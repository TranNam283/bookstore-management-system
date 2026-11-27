package com.bookstore.backend.repository;

import com.bookstore.backend.entities.ExportReceipt;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExportReceiptRepository extends JpaRepository<ExportReceipt, String> {
  boolean existsByReceiptCode(String receiptCode);

  boolean existsByOrder_Id(String orderId);
}
