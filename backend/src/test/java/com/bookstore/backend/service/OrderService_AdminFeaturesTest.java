package com.bookstore.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookstore.backend.cache.DataCacheRegistry;
import com.bookstore.backend.dto.OrderDTO;
import com.bookstore.backend.dto.OrderDetailDTO;
import com.bookstore.backend.dto.search.AdvancedSearchRequest;
import com.bookstore.backend.dto.search.SearchCondition;
import com.bookstore.backend.dto.search.SearchGroup;
import com.bookstore.backend.dto.search.SearchResponse;
import com.bookstore.backend.entities.Book;
import com.bookstore.backend.entities.Order;
import com.bookstore.backend.entities.OrderDetail;
import com.bookstore.backend.entities.User;
import com.bookstore.backend.repository.BookRepository;
import com.bookstore.backend.repository.CartRepository;
import com.bookstore.backend.repository.OrderRepository;
import com.bookstore.backend.repository.PaymentRepository;
import com.bookstore.backend.repository.UserRepository;
import com.bookstore.backend.utils.ValidationUtils;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderService_AdminFeaturesTest {

  @Mock private OrderRepository orderRepository;
  @Mock private UserRepository userRepository;
  @Mock private BookRepository bookRepository;
  @Mock private CartRepository cartRepository;
  @Mock private PaymentRepository paymentRepository;
  @Mock private MomoService momoService;
  @Spy private DataCacheRegistry cacheRegistry = new DataCacheRegistry();

  @InjectMocks private OrderService orderService;

  private static Order order(String code, String city, double total, int status) {
    User customer = User.builder().id("c-" + code).email(code + "@mail.com").build();
    return Order.builder()
        .id("id-" + code)
        .orderCode(code)
        .fullname("Khách " + code)
        .city(city)
        .paymethod("cod")
        .status(status)
        .subtotal(total)
        .discountTotal(0d)
        .total(total)
        .user(customer)
        .createdAt(LocalDateTime.of(2025, 6, 1, 10, 0))
        .items(new ArrayList<>())
        .build();
  }

  // Đơn giá & giảm giá lấy từ database, lưu thành tiền từng dòng
  @Test
  void createOrder_shouldUseBookPriceFromDatabaseAndStoreLineTotals() {
    User user = User.builder().id("u1").email("u1@mail.com").build();
    Book book =
        Book.builder().id("b1").title("Sách A").price(160_000d).discount(25_000d).stock(10).build();
    when(userRepository.findById("u1")).thenReturn(Optional.of(user));
    when(bookRepository.findById("b1")).thenReturn(Optional.of(book));
    when(cartRepository.findByUserId("u1")).thenReturn(Optional.empty());
    when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

    OrderDetailDTO item = new OrderDetailDTO();
    item.setBookId("b1");
    item.setQuantity(3);
    item.setPrice(1d); // giá client gửi lên bị bỏ qua
    item.setDiscount(0d);
    OrderDTO request = new OrderDTO();
    request.setFullname("A");
    request.setPhone("0901234567");
    request.setSpeaddress("1");
    request.setCity("Hà Nội");
    request.setWard("Phường Ba Đình");
    request.setPaymethod("cod");
    request.setItems(List.of(item));

    OrderDTO result;
    try (MockedStatic<ValidationUtils> mocked = Mockito.mockStatic(ValidationUtils.class)) {
      mocked.when(() -> ValidationUtils.validatePhone(anyString())).thenReturn(true);
      result = orderService.createOrder(request, "u1");
    }

    OrderDetailDTO line = result.getItems().get(0);
    assertEquals(160_000d, line.getPrice());
    assertEquals(480_000d, line.getSubtotal());
    assertEquals(75_000d, line.getDiscountAmount());
    assertEquals(405_000d, line.getLineTotal());
    assertEquals(480_000d, result.getSubtotal());
    assertEquals(75_000d, result.getDiscountTotal());
    assertEquals(405_000d, result.getTotal());
    assertEquals(3, result.getTotalQuantity());
    assertEquals(7, book.getStock());
  }

  // Nhân viên xác nhận đơn (0 -> 1) được lưu để thống kê theo nhân viên
  @Test
  void confirmOrder_shouldRecordStaff() {
    Order order = order("A1", "Hà Nội", 100_000, 0);
    User staff = User.builder().id("s1").fullname("Nhân viên A").role(1).build();
    when(orderRepository.findById("id-A1")).thenReturn(Optional.of(order));
    when(userRepository.findById("s1")).thenReturn(Optional.of(staff));
    when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

    OrderDTO result = orderService.updateOrderStatus("id-A1", 1, "s1");

    assertEquals("s1", result.getStaffId());
    assertEquals("Nhân viên A", result.getStaffName());
    assertNotNull(result.getConfirmedAt());
  }

  @Test
  void confirmOrder_shouldIgnoreCustomerAccount() {
    Order order = order("A2", "Hà Nội", 100_000, 0);
    User customer = User.builder().id("c1").fullname("Khách").role(3).build();
    when(orderRepository.findById("id-A2")).thenReturn(Optional.of(order));
    when(userRepository.findById("c1")).thenReturn(Optional.of(customer));
    when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

    OrderDTO result = orderService.updateOrderStatus("id-A2", 1, "c1");

    assertNull(result.getStaffId());
  }

  // Tìm kiếm nâng cao chạy trên danh sách RAM: database chỉ đọc 1 lần
  @Test
  void searchOrders_shouldFilterCachedListWithAndOrGroups() {
    when(orderRepository.findAll(any(Sort.class)))
        .thenReturn(
            List.of(
                order("O1", "Hồ Chí Minh", 1_500_000, 3),
                order("O2", "Hà Nội", 2_500_000, 3),
                order("O3", "Đà Nẵng", 1_200_000, 3),
                order("O4", "Hà Nội", 1_000_000, 0),
                order("O5", "Hồ Chí Minh", 900_000, 3)));

    AdvancedSearchRequest request = new AdvancedSearchRequest();
    request.setGroups(
        List.of(
            new SearchGroup(
                "AND",
                List.of(
                    new SearchCondition("total", ">=", "1000000"),
                    new SearchCondition("total", "<=", "2000000"))),
            new SearchGroup(
                "OR",
                List.of(
                    new SearchCondition("city", "=", "Hồ Chí Minh"),
                    new SearchCondition("city", "=", "Hà Nội")))));
    request.setSortBy("orderCode");
    request.setSortDir("asc");

    SearchResponse<OrderDTO> result = orderService.searchOrders(request);
    orderService.searchOrders(request);
    Map<Integer, Long> counts = orderService.getOrderCountByStatus(List.of(0, 3, 4));

    assertEquals(
        List.of("O1", "O4"), result.getItems().stream().map(OrderDTO::getOrderCode).toList());
    assertEquals(2_500_000d, result.getSummary().get("total"));
    assertEquals(4L, counts.get(3));
    assertEquals(0L, counts.get(4));
    verify(orderRepository, times(1)).findAll(any(Sort.class));
  }

  // Có thay đổi dữ liệu thì cache bị xóa và lần sau đọc lại database
  @Test
  void updateStatus_shouldInvalidateCache() {
    Order order = order("A3", "Hà Nội", 100_000, 0);
    when(orderRepository.findAll(any(Sort.class))).thenReturn(List.of(order));
    when(orderRepository.findById("id-A3")).thenReturn(Optional.of(order));
    when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

    orderService.getCachedOrders();
    orderService.updateOrderStatus("id-A3", 1, null);
    orderService.getCachedOrders();

    verify(orderRepository, times(2)).findAll(any(Sort.class));
  }

  @Test
  void orderDetail_calculateAmounts() {
    OrderDetail detail = OrderDetail.builder().quantity(2).price(50_000d).discount(5_000d).build();
    detail.calculateAmounts();

    assertEquals(100_000d, detail.getSubtotal());
    assertEquals(10_000d, detail.getDiscountAmount());
    assertEquals(90_000d, detail.getLineTotal());
  }
}
