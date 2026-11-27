package com.bookstore.backend.repository;

import com.bookstore.backend.entities.ImportReceipt;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportReceiptRepository extends JpaRepository<ImportReceipt, String> {
  boolean existsByReceiptCode(String receiptCode);

  boolean existsBySupplier_Id(String supplierId);
}
