package com.bookstore.backend.service;

import com.bookstore.backend.cache.CacheNames;
import com.bookstore.backend.cache.DataCacheRegistry;
import com.bookstore.backend.cache.ListCache;
import com.bookstore.backend.dto.ImportReceiptDTO;
import com.bookstore.backend.dto.SupplierDTO;
import com.bookstore.backend.entities.Supplier;
import com.bookstore.backend.repository.ImportReceiptRepository;
import com.bookstore.backend.repository.SupplierRepository;
import com.bookstore.backend.utils.ValidationUtils;
import jakarta.persistence.EntityNotFoundException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

/** Quản lý nhà cung cấp; số liệu nhập hàng tính từ cache phiếu nhập. */
@Service
public class SupplierService {

  private final SupplierRepository supplierRepository;
  private final ImportReceiptRepository importReceiptRepository;
  private final ImportReceiptService importReceiptService;
  private final DataCacheRegistry cacheRegistry;
  private final ListCache<Supplier> supplierCache;

  public SupplierService(
      SupplierRepository supplierRepository,
      ImportReceiptRepository importReceiptRepository,
      ImportReceiptService importReceiptService,
      DataCacheRegistry cacheRegistry) {
    this.supplierRepository = supplierRepository;
    this.importReceiptRepository = importReceiptRepository;
    this.importReceiptService = importReceiptService;
    this.cacheRegistry = cacheRegistry;
    this.supplierCache =
        cacheRegistry.register(
            CacheNames.SUPPLIERS, () -> supplierRepository.findAll(Sort.by("code")));
  }

  /** Danh sách nhà cung cấp kèm số phiếu nhập, tổng tiền đã nhập (không tính phiếu đã hủy). */
  public List<SupplierDTO> getAll() {
    Map<String, List<ImportReceiptDTO>> receiptsBySupplier =
        importReceiptService.getCachedReceipts().stream()
            .filter(r -> r.getSupplierId() != null)
            .filter(r -> r.getStatus() == null || r.getStatus() == 1)
            .collect(Collectors.groupingBy(ImportReceiptDTO::getSupplierId));

    List<SupplierDTO> result = new ArrayList<>();
    for (Supplier s : supplierCache.get()) {
      List<ImportReceiptDTO> receipts = receiptsBySupplier.getOrDefault(s.getId(), List.of());
      result.add(
          SupplierDTO.builder()
              .id(s.getId())
              .code(s.getCode())
              .name(s.getName())
              .contactName(s.getContactName())
              .phone(s.getPhone())
              .email(s.getEmail())
              .address(s.getAddress())
              .taxCode(s.getTaxCode())
              .status(s.getStatus())
              .createdAt(s.getCreatedAt())
              .receiptCount(receipts.size())
              .totalImported(
                  receipts.stream()
                      .mapToDouble(r -> r.getTotalAmount() == null ? 0 : r.getTotalAmount())
                      .sum())
              .lastImportAt(
                  receipts.stream()
                      .map(ImportReceiptDTO::getCreatedAt)
                      .filter(Objects::nonNull)
                      .max(LocalDateTime::compareTo)
                      .orElse(null))
              .build());
    }
    return result;
  }

  public SupplierDTO getById(String id) {
    return getAll().stream()
        .filter(s -> s.getId().equals(id))
        .findFirst()
        .orElseThrow(() -> new EntityNotFoundException("Nhà cung cấp không tìm thấy"));
  }

  private void validate(SupplierDTO dto, String currentId) {
    if (dto.getCode() == null || dto.getCode().isBlank()) {
      throw new IllegalArgumentException("Mã nhà cung cấp không được để trống");
    }
    if (dto.getName() == null || dto.getName().isBlank()) {
      throw new IllegalArgumentException("Tên nhà cung cấp không được để trống");
    }
    String code = dto.getCode().trim().toUpperCase();
    boolean duplicated =
        currentId == null
            ? supplierRepository.existsByCode(code)
            : supplierRepository.existsByCodeAndIdNot(code, currentId);
    if (duplicated) {
      throw new IllegalArgumentException("Mã nhà cung cấp đã tồn tại");
    }
    if (dto.getEmail() != null
        && !dto.getEmail().isBlank()
        && !ValidationUtils.validateEmail(dto.getEmail())) {
      throw new IllegalArgumentException("Email không hợp lệ");
    }
    if (dto.getPhone() != null
        && !dto.getPhone().isBlank()
        && !dto.getPhone().matches("\\d{9,11}")) {
      throw new IllegalArgumentException("Số điện thoại phải gồm 9 - 11 chữ số");
    }
  }

  private void apply(Supplier s, SupplierDTO dto) {
    s.setCode(dto.getCode().trim().toUpperCase());
    s.setName(dto.getName().trim());
    s.setContactName(blankToNull(dto.getContactName()));
    s.setPhone(blankToNull(dto.getPhone()));
    s.setEmail(blankToNull(dto.getEmail()));
    s.setAddress(blankToNull(dto.getAddress()));
    s.setTaxCode(blankToNull(dto.getTaxCode()));
    if (dto.getStatus() != null) {
      s.setStatus(dto.getStatus());
    }
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  public SupplierDTO create(SupplierDTO dto) {
    validate(dto, null);
    Supplier supplier = new Supplier();
    apply(supplier, dto);
    supplier.setCreatedAt(LocalDateTime.now());
    Supplier saved = supplierRepository.save(supplier);
    cacheRegistry.invalidate(CacheNames.SUPPLIERS);
    return SupplierDTO.builder()
        .id(saved.getId())
        .code(saved.getCode())
        .name(saved.getName())
        .build();
  }

  public SupplierDTO update(String id, SupplierDTO dto) {
    Supplier supplier =
        supplierRepository
            .findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Nhà cung cấp không tìm thấy"));
    validate(dto, id);
    apply(supplier, dto);
    supplierRepository.save(supplier);
    // tên nhà cung cấp hiển thị trên phiếu nhập / phiếu xuất
    cacheRegistry.invalidate(
        CacheNames.SUPPLIERS, CacheNames.IMPORT_RECEIPTS, CacheNames.EXPORT_RECEIPTS);
    return SupplierDTO.builder().id(id).code(supplier.getCode()).name(supplier.getName()).build();
  }

  public void delete(String id) {
    if (!supplierRepository.existsById(id)) {
      throw new EntityNotFoundException("Nhà cung cấp không tìm thấy");
    }
    if (importReceiptRepository.existsBySupplier_Id(id)) {
      throw new IllegalStateException(
          "Nhà cung cấp đã có phiếu nhập nên không thể xóa, hãy chuyển sang trạng thái ngừng hợp tác");
    }
    supplierRepository.deleteById(id);
    cacheRegistry.invalidate(CacheNames.SUPPLIERS);
  }
}
