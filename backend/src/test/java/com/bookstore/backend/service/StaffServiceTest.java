package com.bookstore.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookstore.backend.cache.DataCacheRegistry;
import com.bookstore.backend.dto.ExportReceiptDTO;
import com.bookstore.backend.dto.ImportReceiptDTO;
import com.bookstore.backend.dto.OrderDTO;
import com.bookstore.backend.dto.StaffSummaryDTO;
import com.bookstore.backend.dto.search.AdvancedSearchRequest;
import com.bookstore.backend.dto.search.SearchCondition;
import com.bookstore.backend.dto.search.SearchGroup;
import com.bookstore.backend.dto.search.SearchResponse;
import com.bookstore.backend.entities.User;
import com.bookstore.backend.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StaffServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private OrderService orderService;
  @Mock private ImportReceiptService importReceiptService;
  @Mock private ExportReceiptService exportReceiptService;

  private StaffService staffService;

  private static User user(String id, String name, int role, int status) {
    User u = new User();
    u.setId(id);
    u.setFullname(name);
    u.setEmail(id + "@bookstore.vn");
    u.setRole(role);
    u.setStatus(status);
    u.setCreatedAt(LocalDateTime.of(2025, 1, 1, 8, 0));
    return u;
  }

  private static OrderDTO order(String code, int status, double total, String staffId) {
    return OrderDTO.builder()
        .id(code)
        .orderCode(code)
        .status(status)
        .total(total)
        .staffId(staffId)
        .createdAt(LocalDateTime.of(2025, 3, 1, 9, 0))
        .confirmedAt(LocalDateTime.of(2025, 3, 1, 10, 0))
        .build();
  }

  @BeforeEach
  void setup() {
    when(userRepository.findByRoleIn(anyList(), any(Sort.class)))
        .thenReturn(
            List.of(
                user("admin", "Quản trị", 0, 1),
                user("lan", "Nguyễn Thị Lan", 1, 1),
                user("kho", "Phạm Thu", 2, 0)));
    when(orderService.getCachedOrders())
        .thenReturn(
            List.of(
                order("O1", 3, 200_000, "lan"),
                order("O2", 3, 300_000, "lan"),
                order("O3", 4, 999_000, "lan"),
                order("O4", 1, 50_000, "admin"),
                order("O5", 0, 70_000, null)));
    when(importReceiptService.getCachedReceipts())
        .thenReturn(
            List.of(
                ImportReceiptDTO.builder()
                    .receiptCode("PN1")
                    .staffId("kho")
                    .status(1)
                    .totalAmount(1_000_000d)
                    .createdAt(LocalDateTime.of(2025, 4, 1, 8, 0))
                    .build(),
                ImportReceiptDTO.builder()
                    .receiptCode("PN2")
                    .staffId("kho")
                    .status(0) // đã hủy: không tính
                    .totalAmount(5_000_000d)
                    .createdAt(LocalDateTime.of(2025, 5, 1, 8, 0))
                    .build()));
    when(exportReceiptService.getCachedReceipts())
        .thenReturn(
            List.of(
                ExportReceiptDTO.builder()
                    .receiptCode("PX1")
                    .staffId("lan")
                    .createdAt(LocalDateTime.of(2025, 6, 1, 8, 0))
                    .build()));
    staffService =
        new StaffService(
            userRepository,
            orderService,
            importReceiptService,
            exportReceiptService,
            new DataCacheRegistry());
  }

  private StaffSummaryDTO find(String id) {
    return staffService.getStaffSummaries().stream()
        .filter(s -> s.getId().equals(id))
        .findFirst()
        .orElseThrow();
  }

  @Test
  void summaries_shouldCountWorkOfEachStaff() {
    StaffSummaryDTO lan = find("lan");
    assertEquals("Nhân viên bán hàng", lan.getRoleName());
    assertEquals(3, lan.getConfirmedCount());
    assertEquals(2, lan.getDeliveredCount());
    assertEquals(1, lan.getCancelledCount());
    assertEquals(500_000d, lan.getRevenue());
    assertEquals(1, lan.getExportReceiptCount());
    assertEquals(LocalDateTime.of(2025, 6, 1, 8, 0), lan.getLastActivityAt());

    StaffSummaryDTO kho = find("kho");
    assertEquals("Nhân viên kho", kho.getRoleName());
    assertEquals(1, kho.getImportReceiptCount());
    assertEquals(1_000_000d, kho.getImportAmount());

    assertEquals("Quản trị viên hệ thống", find("admin").getRoleName());
  }

  @Test
  void search_shouldFilterByRoleAndSummarize() {
    AdvancedSearchRequest all = new AdvancedSearchRequest();
    SearchResponse<StaffSummaryDTO> response = staffService.search(all);
    assertEquals(3, response.getTotal());
    Map<String, Object> summary = response.getSummary();
    assertEquals(1L, summary.get("adminCount"));
    assertEquals(1L, summary.get("salesCount"));
    assertEquals(1L, summary.get("warehouseCount"));
    assertEquals(1L, summary.get("lockedCount"));

    AdvancedSearchRequest sales = new AdvancedSearchRequest();
    sales.setGroups(List.of(new SearchGroup("OR", List.of(new SearchCondition("role", "=", "1")))));
    SearchResponse<StaffSummaryDTO> salesOnly = staffService.search(sales);
    assertEquals(1, salesOnly.getTotal());
    assertEquals("lan", salesOnly.getItems().get(0).getId());

    // danh sách tài khoản được cache: gọi nhiều lần chỉ đọc database 1 lần
    staffService.search(all);
    verify(userRepository, times(1)).findByRoleIn(anyList(), any(Sort.class));
  }

  @Test
  void detail_shouldListHandledOrdersAndReceipts() {
    Map<String, Object> detail = staffService.getDetail("lan");
    assertEquals(3, ((List<?>) detail.get("orders")).size());
    assertEquals(1, ((List<?>) detail.get("exportReceipts")).size());
    assertEquals(0, ((List<?>) detail.get("importReceipts")).size());

    assertThrows(EntityNotFoundException.class, () -> staffService.getDetail("nobody"));
  }
}
