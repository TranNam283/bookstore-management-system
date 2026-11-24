package com.bookstore.backend.service;

import com.bookstore.backend.cache.CacheNames;
import com.bookstore.backend.cache.DataCacheRegistry;
import com.bookstore.backend.cache.ListCache;
import com.bookstore.backend.dto.OrderDTO;
import com.bookstore.backend.dto.OrderDetailDTO;
import com.bookstore.backend.dto.search.AdvancedSearchRequest;
import com.bookstore.backend.dto.search.SearchCondition;
import com.bookstore.backend.dto.search.SearchGroup;
import com.bookstore.backend.dto.search.SearchResponse;
import com.bookstore.backend.entities.Book;
import com.bookstore.backend.entities.Cart;
import com.bookstore.backend.entities.Order;
import com.bookstore.backend.entities.OrderDetail;
import com.bookstore.backend.entities.Payment;
import com.bookstore.backend.entities.User;
import com.bookstore.backend.repository.BookRepository;
import com.bookstore.backend.repository.CartRepository;
import com.bookstore.backend.repository.OrderRepository;
import com.bookstore.backend.repository.PaymentRepository;
import com.bookstore.backend.repository.UserRepository;
import com.bookstore.backend.utils.AdvancedFilter;
import com.bookstore.backend.utils.ValidationUtils;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

  private final OrderRepository orderRepository;
  private final UserRepository userRepository;
  private final BookRepository bookRepository;
  private final CartRepository cartRepository;
  private final PaymentRepository paymentRepository;
  private final MomoService momoService;
  private final DataCacheRegistry cacheRegistry;

  // Danh sách đơn hàng trên RAM (phiên 15 phút) dùng cho tìm kiếm & thống kê
  private final ListCache<OrderDTO> orderCache;
  private final AdvancedFilter<OrderDTO> orderFilter = buildOrderFilter();

  public OrderService(
      OrderRepository orderRepository,
      UserRepository userRepository,
      BookRepository bookRepository,
      CartRepository cartRepository,
      PaymentRepository paymentRepository,
      MomoService momoService,
      DataCacheRegistry cacheRegistry) {
    this.orderRepository = orderRepository;
    this.userRepository = userRepository;
    this.bookRepository = bookRepository;
    this.cartRepository = cartRepository;
    this.paymentRepository = paymentRepository;
    this.momoService = momoService;
    this.cacheRegistry = cacheRegistry;
    this.orderCache = cacheRegistry.register(CacheNames.ORDERS, this::loadOrdersFromDatabase);
  }

  // tạo order code
  private String generateOrderCode() {
    String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    Random random = new Random();
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 6; i++) {
      sb.append(chars.charAt(random.nextInt(chars.length())));
    }
    return sb.toString();
  }

  private OrderDetailDTO convertToDetailDTO(OrderDetail detail) {
    OrderDetailDTO dto = new OrderDetailDTO();
    dto.setBookId(detail.getBook().getId());
    dto.setTitle(detail.getBook().getTitle());

    if (detail.getBook().getImages() != null && !detail.getBook().getImages().isEmpty()) {
      List<String> images =
          detail.getBook().getImages().stream()
              .sorted(Comparator.comparing(img -> img.getCreatedAt()))
              .map(img -> img.getImage())
              .collect(Collectors.toList());
      dto.setImages(images);
    } else {
      dto.setImages(Collections.emptyList());
    }

    dto.setQuantity(detail.getQuantity());
    dto.setPrice(detail.getPrice());
    dto.setDiscount(detail.getDiscount());

    int qty = detail.getQuantity() == null ? 0 : detail.getQuantity();
    double price = detail.getPrice() == null ? 0 : detail.getPrice();
    double discount = detail.getDiscount() == null ? 0 : detail.getDiscount();
    dto.setSubtotal(detail.getSubtotal() != null ? detail.getSubtotal() : qty * price);
    dto.setDiscountAmount(
        detail.getDiscountAmount() != null ? detail.getDiscountAmount() : qty * discount);
    dto.setLineTotal(
        detail.getLineTotal() != null ? detail.getLineTotal() : qty * (price - discount));

    return dto;
  }

  private OrderDTO convertToDTO(Order order) {
    OrderDTO dto = new OrderDTO();
    dto.setId(order.getId());
    dto.setOrderCode(order.getOrderCode());
    dto.setFullname(order.getFullname());
    dto.setPhone(order.getPhone());
    dto.setSpeaddress(order.getSpeaddress());
    dto.setCity(order.getCity());
    dto.setWard(order.getWard());
    dto.setPaymethod(order.getPaymethod());
    dto.setStatus(order.getStatus());
    dto.setTotal(order.getTotal());
    dto.setSubtotal(order.getSubtotal());
    dto.setDiscountTotal(order.getDiscountTotal());
    dto.setCreatedAt(order.getCreatedAt());
    dto.setConfirmedAt(order.getConfirmedAt());
    dto.setAccountEmail(order.getUser() != null ? order.getUser().getEmail() : null);

    if (order.getStaff() != null) {
      dto.setStaffId(order.getStaff().getId());
      dto.setStaffName(order.getStaff().getFullname());
    }

    if (order.getItems() != null && !order.getItems().isEmpty()) {
      List<OrderDetailDTO> detailDTOs =
          order.getItems().stream().map(this::convertToDetailDTO).collect(Collectors.toList());
      dto.setItems(detailDTOs);
    } else {
      dto.setItems(Collections.emptyList());
    }
    dto.setTotalQuantity(
        dto.getItems().stream()
            .mapToInt(item -> item.getQuantity() == null ? 0 : item.getQuantity())
            .sum());

    return dto;
  }

  // ===================================================== cache & tìm kiếm (admin)

  private List<OrderDTO> loadOrdersFromDatabase() {
    return orderRepository.findAll(Sort.by("createdAt").descending()).stream()
        .map(this::convertToDTO)
        .collect(Collectors.toList());
  }

  /** Danh sách đơn hàng trên RAM, chỉ đọc lại database khi hết phiên hoặc có thay đổi. */
  public List<OrderDTO> getCachedOrders() {
    return orderCache.get();
  }

  public Instant getOrdersCachedAt() {
    return orderCache.getLoadedAt();
  }

  private void invalidateCaches() {
    // đơn hàng thay đổi -> tồn kho / số lượng bán của sách cũng thay đổi
    cacheRegistry.invalidate(CacheNames.ORDERS, CacheNames.BOOKS);
  }

  private static AdvancedFilter<OrderDTO> buildOrderFilter() {
    return new AdvancedFilter<OrderDTO>()
        .text("orderCode", OrderDTO::getOrderCode)
        .text("fullname", OrderDTO::getFullname)
        .text("accountEmail", OrderDTO::getAccountEmail)
        .text("phone", OrderDTO::getPhone)
        .text("city", OrderDTO::getCity)
        .text("paymethod", OrderDTO::getPaymethod)
        .text("staffId", OrderDTO::getStaffId)
        .text("staffName", OrderDTO::getStaffName)
        .text(
            "bookTitle",
            order ->
                order.getItems() == null
                    ? List.of()
                    : order.getItems().stream().map(OrderDetailDTO::getTitle).toList())
        .number("status", OrderDTO::getStatus)
        .number("total", OrderDTO::getTotal)
        .number("subtotal", OrderDTO::getSubtotal)
        .number("discountTotal", OrderDTO::getDiscountTotal)
        .number("totalQuantity", OrderDTO::getTotalQuantity)
        .date("createdAt", OrderDTO::getCreatedAt)
        .date("confirmedAt", OrderDTO::getConfirmedAt)
        .keyword(
            OrderDTO::getOrderCode,
            OrderDTO::getFullname,
            OrderDTO::getAccountEmail,
            OrderDTO::getPhone);
  }

  /** Tìm kiếm nâng cao trên danh sách RAM: (điều kiện AND/OR) + sắp xếp + phân trang. */
  public SearchResponse<OrderDTO> searchOrders(AdvancedSearchRequest request) {
    return orderFilter.search(
        getCachedOrders(), request, "createdAt", OrderService::summarize, getOrdersCachedAt());
  }

  private static Map<String, Object> summarize(List<OrderDTO> orders) {
    Map<String, Object> summary = new LinkedHashMap<>();
    summary.put("count", orders.size());
    summary.put("subtotal", orders.stream().mapToDouble(o -> nz(o.getSubtotal())).sum());
    summary.put("discountTotal", orders.stream().mapToDouble(o -> nz(o.getDiscountTotal())).sum());
    summary.put("total", orders.stream().mapToDouble(o -> nz(o.getTotal())).sum());
    summary.put(
        "totalQuantity",
        orders.stream()
            .mapToInt(o -> o.getTotalQuantity() == null ? 0 : o.getTotalQuantity())
            .sum());
    return summary;
  }

  private static double nz(Double value) {
    return value == null ? 0 : value;
  }

  /** Giá trị gợi ý cho bộ lọc (thành phố, phương thức thanh toán, nhân viên). */
  public Map<String, Object> getSearchOptions() {
    List<OrderDTO> orders = getCachedOrders();
    Map<String, String> staffs = new LinkedHashMap<>();
    orders.stream()
        .filter(o -> o.getStaffId() != null)
        .forEach(o -> staffs.putIfAbsent(o.getStaffId(), o.getStaffName()));

    Map<String, Object> options = new LinkedHashMap<>();
    options.put(
        "cities",
        orders.stream()
            .map(OrderDTO::getCity)
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(TreeSet::new)));
    options.put(
        "paymethods",
        orders.stream()
            .map(OrderDTO::getPaymethod)
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(TreeSet::new)));
    options.put(
        "staffs",
        staffs.entrySet().stream()
            .map(e -> Map.of("id", e.getKey(), "name", String.valueOf(e.getValue())))
            .toList());
    return options;
  }

  // order cho admin

  // lấy tất cả orders (lọc đơn giản theo mã đơn, trạng thái, khoảng ngày) - dùng cache RAM
  public Page<OrderDTO> getAllOrders(
      int page, int limit, String orderCode, Integer status, LocalDate start, LocalDate end) {
    List<SearchCondition> conditions = new ArrayList<>();
    if (status != null) {
      conditions.add(new SearchCondition("status", "=", String.valueOf(status)));
    }
    if (start != null) {
      conditions.add(new SearchCondition("createdAt", ">=", start.toString()));
    }
    if (end != null) {
      conditions.add(new SearchCondition("createdAt", "<=", end.toString()));
    }
    AdvancedSearchRequest request = new AdvancedSearchRequest();
    request.setKeyword(orderCode);
    request.setGroups(List.of(new SearchGroup("AND", conditions)));
    request.setPage(page);
    request.setLimit(limit);

    SearchResponse<OrderDTO> result = searchOrders(request);
    Pageable pageable = PageRequest.of(Math.max(page - 1, 0), Math.max(limit, 1));
    return new PageImpl<>(result.getItems(), pageable, result.getTotal());
  }

  // đếm số lượng order có status đó (tính trên danh sách RAM)
  public Map<Integer, Long> getOrderCountByStatus(List<Integer> statuses) {
    Map<Integer, Long> counts =
        getCachedOrders().stream()
            .filter(o -> o.getStatus() != null && statuses.contains(o.getStatus()))
            .collect(Collectors.groupingBy(OrderDTO::getStatus, Collectors.counting()));
    Map<Integer, Long> map = new HashMap<>(counts);
    for (Integer s : statuses) {
      map.putIfAbsent(s, 0L);
    }
    return map;
  }

  // lấy 1 order theo id
  public OrderDTO getOrderById(String id) {
    Order order =
        orderRepository
            .findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Đơn hàng không tìm thấy"));
    return convertToDTO(order);
  }

  // cập nhật status của order
  @Transactional
  public OrderDTO updateOrderStatus(String orderId, Integer status) {
    return updateOrderStatus(orderId, status, null);
  }

  // cập nhật status của order, ghi nhận nhân viên xác nhận đơn (0 -> 1)
  @Transactional
  public OrderDTO updateOrderStatus(String orderId, Integer status, String staffId) {
    Order order =
        orderRepository
            .findById(orderId)
            .orElseThrow(() -> new EntityNotFoundException("Đơn hàng không tìm thấy"));

    if ((status == 4 || status == 5) && order.getStatus() != 4 && order.getStatus() != 5) {
      // trả tồn kho
      if (order.getItems() != null) {
        for (OrderDetail detail : order.getItems()) {
          Book book = detail.getBook();
          book.setStock(book.getStock() + detail.getQuantity());
          bookRepository.save(book);
        }
      }

      // hoàn tiền momo
      if ("momo".equalsIgnoreCase(order.getPaymethod())) {
        Payment payment =
            paymentRepository
                .findFirstByOrder_OrderCode(order.getOrderCode())
                .orElseThrow(() -> new EntityNotFoundException("Thanh toán không tìm thấy"));

        Map<String, Object> payload = new HashMap<>();
        payload.put("orderId", order.getOrderCode());
        payload.put("amount", order.getTotal().intValue());
        payload.put("transId", payment.getTransactionId());

        try {
          momoService.refundPayment(payload);
        } catch (Exception e) {
          throw new RuntimeException("Hoàn tiền Momo thất bại " + e.getMessage(), e);
        }

        Payment refundPayment =
            Payment.builder()
                .order(order)
                .paymethod("momo")
                .amount(order.getTotal())
                .transactionId(payment.getTransactionId())
                .status(0)
                .build();

        paymentRepository.save(refundPayment);
      }
    }

    // nhân viên xác nhận đơn
    if (status == 1 && order.getStaff() == null && staffId != null && !staffId.isBlank()) {
      userRepository
          .findById(staffId)
          .filter(user -> user.getRole() != null && user.getRole() >= 0 && user.getRole() <= 2)
          .ifPresent(
              staff -> {
                order.setStaff(staff);
                order.setConfirmedAt(LocalDateTime.now());
              });
    }

    order.setStatus(status);
    Order updatedOrder = orderRepository.save(order);
    invalidateCaches();
    return convertToDTO(updatedOrder);
  }

  // order cho customer

  // tạo đơn hàng mặc định status = 0
  public OrderDTO createOrder(OrderDTO orderDTO, String userId) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new EntityNotFoundException("Người dùng không tìm thấy"));

    if (!ValidationUtils.validatePhone(orderDTO.getPhone())) {
      throw new IllegalArgumentException("Số điện thoại không hợp lệ");
    }

    // cod -> status = 0 (chờ xác nhận)
    // momo -> status = -1 (chờ thanh toán)
    int status = "cod".equalsIgnoreCase(orderDTO.getPaymethod()) ? 0 : -1;

    Order order =
        Order.builder()
            .orderCode(generateOrderCode())
            .fullname(orderDTO.getFullname())
            .phone(orderDTO.getPhone())
            .speaddress(orderDTO.getSpeaddress())
            .city(orderDTO.getCity())
            .ward(orderDTO.getWard())
            .paymethod(orderDTO.getPaymethod())
            .status(status)
            .user(user)
            .createdAt(LocalDateTime.now())
            .build();

    if (orderDTO.getItems() != null && !orderDTO.getItems().isEmpty()) {
      List<OrderDetail> details =
          orderDTO.getItems().stream()
              .map(
                  d -> {
                    Book book =
                        bookRepository
                            .findById(d.getBookId())
                            .orElseThrow(() -> new EntityNotFoundException("Sách không tìm thấy"));

                    if (d.getQuantity() == null || d.getQuantity() <= 0) {
                      throw new IllegalArgumentException("Số lượng không hợp lệ");
                    }

                    // Kiểm tra số lượng
                    if (d.getQuantity() > book.getStock()) {
                      throw new IllegalArgumentException(book.getTitle() + " không đủ số lượng");
                    }

                    // Nếu thanh toán COD trừ số lượng, thanh toán Momo không trừ tồn kho
                    if ("cod".equalsIgnoreCase(orderDTO.getPaymethod())) {
                      book.setStock(book.getStock() - d.getQuantity());
                      bookRepository.save(book);
                    }

                    // Đơn giá & giảm giá lấy từ sách trong database (không tin giá client gửi)
                    double price = book.getPrice() != null ? book.getPrice() : 0;
                    double discount = book.getDiscount() != null ? book.getDiscount() : 0;
                    discount = Math.max(0, Math.min(discount, price));

                    OrderDetail detail =
                        OrderDetail.builder()
                            .book(book)
                            .order(order)
                            .quantity(d.getQuantity())
                            .price(price)
                            .discount(discount)
                            .build();
                    detail.calculateAmounts();
                    return detail;
                  })
              .collect(Collectors.toList());

      // Tính tổng tiền: tiền trước giảm, tổng giảm, thành tiền
      order.setItems(details);
      order.recalculateTotals();

      // xóa giỏ hàng cho phương thức thanh toán COD
      if ("cod".equalsIgnoreCase(orderDTO.getPaymethod())) {
        Cart cart = cartRepository.findByUserId(userId).orElse(null);
        if (cart != null) {
          cartRepository.delete(cart);
        }
      }
    } else {
      order.setTotal(0.0);
    }

    Order savedOrder = orderRepository.save(order);
    invalidateCaches();

    return convertToDTO(savedOrder);
  }

  // láy order của customer theo user id và order code
  public OrderDTO getOrderByUserAndCode(String userId, String orderCode) {
    Order order =
        orderRepository
            .findByUserIdAndOrderCode(userId, orderCode)
            .orElseThrow(() -> new EntityNotFoundException("Đơn hàng không tìm thấy"));
    return convertToDTO(order);
  }

  // lấy các orders theo user id và lọc theo status
  public Page<OrderDTO> getOrdersByUserAndStatus(
      String userId, int page, int limit, Integer status) {
    Pageable pageable = PageRequest.of(page - 1, limit, Sort.by("createdAt").descending());
    Page<Order> orderPage;

    // không lấy order có status = -1
    if (status != null && status >= 0) {
      orderPage = orderRepository.findByUserIdAndStatus(userId, status, pageable);
    } else {
      orderPage = orderRepository.findByUserIdAndStatusGreaterThanEqual(userId, 0, pageable);
    }

    return orderPage.map(this::convertToDTO);
  }

  public OrderDTO getOrderByOrderCode(String orderCode) {
    Order order =
        orderRepository
            .findByOrderCode(orderCode)
            .orElseThrow(() -> new EntityNotFoundException("Đơn hàng không tìm thấy"));
    return convertToDTO(order);
  }

  // Tổng doanh thu của tất cả orders có status = 3
  public double getTotalRevenue() {
    Double total = orderRepository.sumTotalByStatus(3);
    return total != null ? total : 0.0;
  }

  // Doanh thu của orders có status = 3 trong ngày
  public double getTodayRevenue() {
    LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
    LocalDateTime endOfDay = LocalDate.now().atTime(23, 59, 59);
    Double total = orderRepository.sumTotalByStatusAndCreatedAtBetween(3, startOfDay, endOfDay);
    return total != null ? total : 0.0;
  }

  // Tổng số lượng sản phẩm đã bán của orders có status = 3
  public long getTotalSoldQuantity() {
    Long totalQty = orderRepository.sumQuantityByStatus(3);
    return totalQty != null ? totalQty : 0L;
  }

  // Tổng số lượng sản phẩm đã bán của orders có status = 3 trong ngày
  public long getTodaySoldQuantity() {
    LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
    LocalDateTime endOfDay = LocalDate.now().atTime(23, 59, 59);
    Long totalQty = orderRepository.sumQuantityByStatusAndCreatedAtBetween(3, startOfDay, endOfDay);
    return totalQty != null ? totalQty : 0L;
  }

  // Chạy mỗi 30 phút để xóa order status -1 chưa thanh toán quá 90 phút
  @Scheduled(fixedRate = 30 * 60 * 1000)
  public void cleanupUnpaidOrders() {
    LocalDateTime cutoff = LocalDateTime.now().minusMinutes(90);

    // Tìm các đơn có status = -1 (chưa thanh toán) và quá 90 phút
    List<Order> expiredOrders = orderRepository.findByStatusAndCreatedAtBefore(-1, cutoff);

    if (!expiredOrders.isEmpty()) {
      orderRepository.deleteAll(expiredOrders);
      invalidateCaches();
    }
  }
}
