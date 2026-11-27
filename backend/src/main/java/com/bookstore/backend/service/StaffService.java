package com.bookstore.backend.service;

import com.bookstore.backend.cache.CacheNames;
import com.bookstore.backend.cache.DataCacheRegistry;
import com.bookstore.backend.cache.ListCache;
import com.bookstore.backend.dto.ExportReceiptDTO;
import com.bookstore.backend.dto.ImportReceiptDTO;
import com.bookstore.backend.dto.OrderDTO;
import com.bookstore.backend.dto.StaffSummaryDTO;
import com.bookstore.backend.dto.search.AdvancedSearchRequest;
import com.bookstore.backend.dto.search.SearchResponse;
import com.bookstore.backend.entities.User;
import com.bookstore.backend.repository.UserRepository;
import com.bookstore.backend.utils.AdvancedFilter;
import jakarta.persistence.EntityNotFoundException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

/**
 * Danh sách nhân viên / quản trị: tài khoản được cache trên RAM, số liệu công việc (hóa đơn đã xử
 * lý, doanh thu, phiếu nhập / xuất) tính từ cache đơn hàng và phiếu kho.
 */
@Service
public class StaffService {

  public static final List<Integer> STAFF_ROLES = List.of(0, 1, 2);

  private record StaffBase(
      String id,
      String fullname,
      String email,
      Integer role,
      Integer status,
      LocalDateTime createdAt) {}

  private final UserRepository userRepository;
  private final OrderService orderService;
  private final ImportReceiptService importReceiptService;
  private final ExportReceiptService exportReceiptService;
  private final ListCache<StaffBase> staffCache;
  private final AdvancedFilter<StaffSummaryDTO> staffFilter = buildFilter();

  public StaffService(
      UserRepository userRepository,
      OrderService orderService,
      ImportReceiptService importReceiptService,
      ExportReceiptService exportReceiptService,
      DataCacheRegistry cacheRegistry) {
    this.userRepository = userRepository;
    this.orderService = orderService;
    this.importReceiptService = importReceiptService;
    this.exportReceiptService = exportReceiptService;
    this.staffCache = cacheRegistry.register(CacheNames.STAFF, this::loadStaffFromDatabase);
  }

  /** Tên vai trò hiển thị trên giao diện. */
  public static String roleName(Integer role) {
    if (role == null) {
      return "Không xác định";
    }
    return switch (role) {
      case 0 -> "Quản trị viên hệ thống";
      case 1 -> "Nhân viên bán hàng";
      case 2 -> "Nhân viên kho";
      case 3 -> "Khách hàng";
      default -> "Không xác định";
    };
  }

  private List<StaffBase> loadStaffFromDatabase() {
    List<User> users = userRepository.findByRoleIn(STAFF_ROLES, Sort.by("createdAt").ascending());
    return users.stream()
        .map(
            u ->
                new StaffBase(
                    u.getId(),
                    u.getFullname(),
                    u.getEmail(),
                    u.getRole(),
                    u.getStatus(),
                    u.getCreatedAt()))
        .collect(Collectors.toList());
  }

  /** Ghép tài khoản nhân viên (RAM) với số liệu công việc (RAM), không truy vấn database. */
  public List<StaffSummaryDTO> getStaffSummaries() {
    Map<String, List<OrderDTO>> ordersByStaff =
        orderService.getCachedOrders().stream()
            .filter(o -> o.getStaffId() != null)
            .collect(Collectors.groupingBy(OrderDTO::getStaffId));
    Map<String, List<ImportReceiptDTO>> importsByStaff =
        importReceiptService.getCachedReceipts().stream()
            .filter(r -> r.getStaffId() != null)
            .filter(r -> r.getStatus() == null || r.getStatus() == 1)
            .collect(Collectors.groupingBy(ImportReceiptDTO::getStaffId));
    Map<String, List<ExportReceiptDTO>> exportsByStaff =
        exportReceiptService.getCachedReceipts().stream()
            .filter(r -> r.getStaffId() != null)
            .collect(Collectors.groupingBy(ExportReceiptDTO::getStaffId));

    List<StaffSummaryDTO> result = new ArrayList<>();
    for (StaffBase base : staffCache.get()) {
      List<OrderDTO> orders = ordersByStaff.getOrDefault(base.id(), List.of());
      List<ImportReceiptDTO> imports = importsByStaff.getOrDefault(base.id(), List.of());
      List<ExportReceiptDTO> exports = exportsByStaff.getOrDefault(base.id(), List.of());
      List<OrderDTO> delivered =
          orders.stream().filter(o -> Objects.equals(o.getStatus(), 3)).toList();

      LocalDateTime lastActivity =
          Stream.of(
                  orders.stream()
                      .map(o -> o.getConfirmedAt() != null ? o.getConfirmedAt() : o.getCreatedAt()),
                  imports.stream().map(ImportReceiptDTO::getCreatedAt),
                  exports.stream().map(ExportReceiptDTO::getCreatedAt))
              .flatMap(s -> s)
              .filter(Objects::nonNull)
              .max(Comparator.naturalOrder())
              .orElse(null);

      result.add(
          StaffSummaryDTO.builder()
              .id(base.id())
              .fullname(base.fullname())
              .email(base.email())
              .role(base.role())
              .roleName(roleName(base.role()))
              .status(base.status())
              .createdAt(base.createdAt())
              .confirmedCount(orders.size())
              .deliveredCount(delivered.size())
              .cancelledCount(
                  orders.stream()
                      .filter(o -> o.getStatus() != null)
                      .filter(o -> o.getStatus() == 4 || o.getStatus() == 5)
                      .count())
              .revenue(delivered.stream().mapToDouble(o -> nz(o.getTotal())).sum())
              .importReceiptCount(imports.size())
              .importAmount(imports.stream().mapToDouble(r -> nz(r.getTotalAmount())).sum())
              .exportReceiptCount(exports.size())
              .lastActivityAt(lastActivity)
              .build());
    }
    return result;
  }

  /** Hồ sơ nhân viên: thông tin, hóa đơn đã xử lý, phiếu nhập / xuất đã lập. */
  public Map<String, Object> getDetail(String id) {
    StaffSummaryDTO summary =
        getStaffSummaries().stream()
            .filter(s -> s.getId().equals(id))
            .findFirst()
            .orElseThrow(() -> new EntityNotFoundException("Nhân viên không tìm thấy"));

    List<OrderDTO> orders =
        orderService.getCachedOrders().stream()
            .filter(o -> id.equals(o.getStaffId()))
            .sorted(
                Comparator.comparing(
                        OrderDTO::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                    .reversed())
            .toList();
    List<ImportReceiptDTO> imports =
        importReceiptService.getCachedReceipts().stream()
            .filter(r -> id.equals(r.getStaffId()))
            .sorted(
                Comparator.comparing(
                        ImportReceiptDTO::getCreatedAt,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                    .reversed())
            .toList();
    List<ExportReceiptDTO> exports =
        exportReceiptService.getCachedReceipts().stream()
            .filter(r -> id.equals(r.getStaffId()))
            .sorted(
                Comparator.comparing(
                        ExportReceiptDTO::getCreatedAt,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                    .reversed())
            .toList();

    Map<Integer, Long> byStatus =
        orders.stream()
            .filter(o -> o.getStatus() != null)
            .collect(Collectors.groupingBy(OrderDTO::getStatus, Collectors.counting()));

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("staff", summary);
    result.put("orders", orders);
    result.put("ordersByStatus", byStatus);
    result.put("importReceipts", imports);
    result.put("exportReceipts", exports);
    return result;
  }

  private static AdvancedFilter<StaffSummaryDTO> buildFilter() {
    return new AdvancedFilter<StaffSummaryDTO>()
        .text("fullname", StaffSummaryDTO::getFullname)
        .text("email", StaffSummaryDTO::getEmail)
        .number("role", StaffSummaryDTO::getRole)
        .number("status", StaffSummaryDTO::getStatus)
        .number("confirmedCount", StaffSummaryDTO::getConfirmedCount)
        .number("deliveredCount", StaffSummaryDTO::getDeliveredCount)
        .number("cancelledCount", StaffSummaryDTO::getCancelledCount)
        .number("revenue", StaffSummaryDTO::getRevenue)
        .number("importReceiptCount", StaffSummaryDTO::getImportReceiptCount)
        .number("importAmount", StaffSummaryDTO::getImportAmount)
        .number("exportReceiptCount", StaffSummaryDTO::getExportReceiptCount)
        .date("createdAt", StaffSummaryDTO::getCreatedAt)
        .date("lastActivityAt", StaffSummaryDTO::getLastActivityAt)
        .keyword(StaffSummaryDTO::getFullname, StaffSummaryDTO::getEmail);
  }

  public SearchResponse<StaffSummaryDTO> search(AdvancedSearchRequest request) {
    return staffFilter.search(
        getStaffSummaries(),
        request,
        "createdAt",
        StaffService::summarize,
        staffCache.getLoadedAt());
  }

  private static Map<String, Object> summarize(List<StaffSummaryDTO> staff) {
    Map<String, Object> summary = new LinkedHashMap<>();
    summary.put("count", staff.size());
    summary.put("adminCount", staff.stream().filter(s -> Objects.equals(s.getRole(), 0)).count());
    summary.put("salesCount", staff.stream().filter(s -> Objects.equals(s.getRole(), 1)).count());
    summary.put(
        "warehouseCount", staff.stream().filter(s -> Objects.equals(s.getRole(), 2)).count());
    summary.put(
        "activeCount", staff.stream().filter(s -> Objects.equals(s.getStatus(), 1)).count());
    summary.put(
        "lockedCount", staff.stream().filter(s -> !Objects.equals(s.getStatus(), 1)).count());
    summary.put(
        "confirmedCount", staff.stream().mapToLong(StaffSummaryDTO::getConfirmedCount).sum());
    summary.put("revenue", staff.stream().mapToDouble(StaffSummaryDTO::getRevenue).sum());
    summary.put(
        "importReceiptCount",
        staff.stream().mapToLong(StaffSummaryDTO::getImportReceiptCount).sum());
    return summary;
  }

  private static double nz(Double value) {
    return value == null ? 0 : value;
  }
}
