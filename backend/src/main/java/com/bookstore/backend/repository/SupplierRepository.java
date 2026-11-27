package com.bookstore.backend.repository;

import com.bookstore.backend.entities.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupplierRepository extends JpaRepository<Supplier, String> {
  boolean existsByCode(String code);

  boolean existsByCodeAndIdNot(String code, String id);
}
