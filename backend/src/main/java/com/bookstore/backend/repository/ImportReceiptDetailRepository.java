package com.bookstore.backend.repository;

import com.bookstore.backend.entities.Book;
import com.bookstore.backend.entities.ImportReceiptDetail;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportReceiptDetailRepository extends JpaRepository<ImportReceiptDetail, String> {
  boolean existsByBook(Book book);
}
