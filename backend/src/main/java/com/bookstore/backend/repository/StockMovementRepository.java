package com.bookstore.backend.repository;

import com.bookstore.backend.entities.StockMovement;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockMovementRepository extends JpaRepository<StockMovement, String> {}
