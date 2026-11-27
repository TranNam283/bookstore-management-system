package com.bookstore.backend.controller;

import com.bookstore.backend.dto.SupplierDTO;
import com.bookstore.backend.service.SupplierService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Quản lý nhà cung cấp. */
@RestController
@RequestMapping("/api/supplier")
@CrossOrigin(origins = "*")
public class SupplierController {

  private final SupplierService supplierService;

  public SupplierController(SupplierService supplierService) {
    this.supplierService = supplierService;
  }

  @GetMapping
  public ResponseEntity<List<SupplierDTO>> getAll() {
    return ResponseEntity.ok(supplierService.getAll());
  }

  @GetMapping("/{id}")
  public ResponseEntity<SupplierDTO> getById(@PathVariable String id) {
    return ResponseEntity.ok(supplierService.getById(id));
  }

  @PostMapping
  public ResponseEntity<SupplierDTO> create(@RequestBody SupplierDTO dto) {
    return ResponseEntity.ok(supplierService.create(dto));
  }

  @PutMapping("/{id}")
  public ResponseEntity<SupplierDTO> update(@PathVariable String id, @RequestBody SupplierDTO dto) {
    return ResponseEntity.ok(supplierService.update(id, dto));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable String id) {
    supplierService.delete(id);
    return ResponseEntity.noContent().build();
  }
}
