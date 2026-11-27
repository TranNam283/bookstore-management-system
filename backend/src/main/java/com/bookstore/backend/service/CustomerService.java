package com.bookstore.backend.service;

import com.bookstore.backend.cache.CacheNames;
import com.bookstore.backend.cache.DataCacheRegistry;
import com.bookstore.backend.cache.ListCache;
import com.bookstore.backend.dto.CustomerSummaryDTO;
import com.bookstore.backend.dto.OrderDTO;
import com.bookstore.backend.dto.search.AdvancedSearchRequest;
import com.bookstore.backend.dto.search.SearchResponse;
import com.bookstore.backend.entities.Address;
import com.bookstore.backend.entities.User;
import com.bookstore.backend.repository.AddressRepository;
import com.bookstore.backend.repository.UserRepository;
import com.bookstore.backend.utils.AdvancedFilter;
import jakarta.persistence.EntityNotFoundException;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

/**
 * Danh sách khách hàng cho trang quản trị: thông tin tài khoản + thành phố (sổ địa chỉ) được cache
 * trên RAM, số đơn / tổng chi tiêu được tính từ cache đơn hàng.
 */
@Service
public class CustomerService {

  private record CustomerBase(
      String id,
      String fullname,
      String email,
      String phone,
      String address,
      Integer status,
      LocalDateTime createdAt,
      List<String> cities,
      List<Map<String, String>> addresses) {}

  private final UserRepository userRepository;
  private final AddressRepository addressRepository;
  private final OrderService orderService;
  private final ListCache<CustomerBase> customerCache;
  private final AdvancedFilter<CustomerSummaryDTO> customerFilter = buildFilter();

  public CustomerService(
      UserRepository userRepository,
      AddressRepository addressRepository,
      OrderService orderService,
      DataCacheRegistry cacheRegistry) {
    this.userRepository = userRepository;
    this.addressRepository = addressRepository;
    this.orderService = orderService;
    this.customerCache =
        cacheRegistry.register(CacheNames.CUSTOMERS, this::loadCustomersFromDatabase);
  }

  private List<CustomerBase> loadCustomersFromDatabase() {
    List<Address> allAddresses = addressRepository.findAll();
    Map<String, List<String>> citiesByUser =
        allAddresses.stream()
            .filter(a -> a.getUser() != null && a.getCity() != null)
            .collect(
                Collectors.groupingBy(
                    a -> a.getUser().getId(),
                    Collectors.mapping(Address::getCity, Collectors.toList())));
    Map<String, List<Map<String, String>>> addressesByUser =
        allAddresses.stream()
            .filter(a -> a.getUser() != null)
            .collect(
                Collectors.groupingBy(
                    a -> a.getUser().getId(),
                    Collectors.mapping(
                        a -> {
                          Map<String, String> m = new LinkedHashMap<>();
                          m.put("fullname", a.getFullname());
                          m.put("phone", a.getPhone());
                          m.put("speaddress", a.getSpeaddress());
                          m.put("ward", a.getWard());
                          m.put("city", a.getCity());
                          return m;
                        },
                        Collectors.toList())));

    List<User> customers =
        userRepository.findByRoleIn(List.of(3), Sort.by("createdAt").descending());
    return customers.stream()
        .map(
            u -> {
              List<Map<String, String>> addresses =
                  addressesByUser.getOrDefault(u.getId(), List.of());
              Map<String, String> first = addresses.isEmpty() ? Map.of() : addresses.get(0);
              String address =
                  Stream.of(first.get("speaddress"), first.get("ward"), first.get("city"))
                      .filter(Objects::nonNull)
                      .filter(s -> !s.isBlank())
                      .collect(Collectors.joining(", "));
              return new CustomerBase(
                  u.getId(),
                  u.getFullname(),
                  u.getEmail(),
                  first.get("phone"),
                  address.isEmpty() ? null : address,
                  u.getStatus(),
                  u.getCreatedAt(),
                  citiesByUser.getOrDefault(u.getId(), List.of()),
                  addresses);
            })
        .collect(Collectors.toList());
  }

  /** Ghép dữ liệu khách hàng (RAM) với số liệu đơn hàng (RAM), không truy vấn database. */
  public List<CustomerSummaryDTO> getCustomerSummaries() {
    Map<String, List<OrderDTO>> ordersByEmail =
        orderService.getCachedOrders().stream()
            .filter(o -> o.getAccountEmail() != null && o.getStatus() != null)
            .filter(o -> o.getStatus() >= 0)
            .collect(Collectors.groupingBy(OrderDTO::getAccountEmail));

    List<CustomerSummaryDTO> result = new ArrayList<>();
    for (CustomerBase base : customerCache.get()) {
      List<OrderDTO> orders = ordersByEmail.getOrDefault(base.email(), List.of());
      List<OrderDTO> delivered = orders.stream().filter(o -> o.getStatus() == 3).toList();

      Set<String> cities = new LinkedHashSet<>(base.cities());
      orders.stream().map(OrderDTO::getCity).filter(Objects::nonNull).forEach(cities::add);
      OrderDTO latest =
          orders.stream()
              .filter(o -> o.getCreatedAt() != null)
              .max(Comparator.comparing(OrderDTO::getCreatedAt))
              .orElse(null);
      String phone =
          base.phone() != null ? base.phone() : latest == null ? null : latest.getPhone();

      result.add(
          CustomerSummaryDTO.builder()
              .id(base.id())
              .fullname(base.fullname())
              .email(base.email())
              .phone(phone)
              .address(base.address())
              .status(base.status())
              .createdAt(base.createdAt())
              .cities(new ArrayList<>(cities))
              .orderCount(orders.size())
              .deliveredCount(delivered.size())
              .cancelledCount(
                  orders.stream().filter(o -> o.getStatus() == 4 || o.getStatus() == 5).count())
              .totalSpent(
                  delivered.stream()
                      .mapToDouble(o -> o.getTotal() == null ? 0 : o.getTotal())
                      .sum())
              .firstOrderAt(
                  orders.stream()
                      .map(OrderDTO::getCreatedAt)
                      .filter(Objects::nonNull)
                      .min(Comparator.naturalOrder())
                      .orElse(null))
              .lastOrderAt(latest == null ? null : latest.getCreatedAt())
              .build());
    }
    return result;
  }

  /** Hồ sơ chi tiết khách hàng: thông tin, sổ địa chỉ, lịch sử hóa đơn, sách mua nhiều nhất. */
  public Map<String, Object> getDetail(String id) {
    CustomerBase base =
        customerCache.get().stream()
            .filter(c -> c.id().equals(id))
            .findFirst()
            .orElseThrow(() -> new EntityNotFoundException("Khách hàng không tìm thấy"));
    CustomerSummaryDTO summary =
        getCustomerSummaries().stream().filter(c -> c.getId().equals(id)).findFirst().orElse(null);

    List<OrderDTO> orders =
        orderService.getCachedOrders().stream()
            .filter(o -> base.email().equals(o.getAccountEmail()))
            .sorted(Comparator.comparing(OrderDTO::getCreatedAt).reversed())
            .toList();

    Map<String, Integer> boughtQty = new LinkedHashMap<>();
    Map<String, Double> boughtAmount = new LinkedHashMap<>();
    orders.stream()
        .filter(o -> o.getStatus() != null && o.getStatus() == 3)
        .flatMap(o -> o.getItems().stream())
        .forEach(
            item -> {
              boughtQty.merge(item.getTitle(), item.getQuantity(), Integer::sum);
              boughtAmount.merge(
                  item.getTitle(),
                  item.getLineTotal() == null ? 0 : item.getLineTotal(),
                  Double::sum);
            });
    List<Map<String, Object>> topBooks =
        boughtQty.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(5)
            .map(
                e -> {
                  Map<String, Object> m = new LinkedHashMap<>();
                  m.put("title", e.getKey());
                  m.put("quantity", e.getValue());
                  m.put("amount", boughtAmount.get(e.getKey()));
                  return m;
                })
            .toList();

    Map<Integer, Long> byStatus =
        orders.stream().collect(Collectors.groupingBy(OrderDTO::getStatus, Collectors.counting()));

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("customer", summary);
    result.put("addresses", base.addresses());
    result.put("orders", orders);
    result.put("ordersByStatus", byStatus);
    result.put("topBooks", topBooks);
    return result;
  }

  private static AdvancedFilter<CustomerSummaryDTO> buildFilter() {
    return new AdvancedFilter<CustomerSummaryDTO>()
        .text("fullname", CustomerSummaryDTO::getFullname)
        .text("email", CustomerSummaryDTO::getEmail)
        .text("phone", CustomerSummaryDTO::getPhone)
        .text("city", CustomerSummaryDTO::getCities)
        .number("status", CustomerSummaryDTO::getStatus)
        .number("orderCount", CustomerSummaryDTO::getOrderCount)
        .number("deliveredCount", CustomerSummaryDTO::getDeliveredCount)
        .number("cancelledCount", CustomerSummaryDTO::getCancelledCount)
        .number("totalSpent", CustomerSummaryDTO::getTotalSpent)
        .date("createdAt", CustomerSummaryDTO::getCreatedAt)
        .date("lastOrderAt", CustomerSummaryDTO::getLastOrderAt)
        .keyword(
            CustomerSummaryDTO::getFullname,
            CustomerSummaryDTO::getEmail,
            CustomerSummaryDTO::getPhone);
  }

  public SearchResponse<CustomerSummaryDTO> search(AdvancedSearchRequest request) {
    return customerFilter.search(
        getCustomerSummaries(),
        request,
        "createdAt",
        customers -> {
          Map<String, Object> summary = new LinkedHashMap<>();
          YearMonth thisMonth = YearMonth.now();
          summary.put("count", customers.size());
          summary.put(
              "activeCount",
              customers.stream().filter(c -> Objects.equals(c.getStatus(), 1)).count());
          summary.put(
              "blockedCount",
              customers.stream().filter(c -> !Objects.equals(c.getStatus(), 1)).count());
          summary.put("buyerCount", customers.stream().filter(c -> c.getOrderCount() > 0).count());
          summary.put(
              "newThisMonth",
              customers.stream()
                  .filter(c -> c.getCreatedAt() != null)
                  .filter(c -> YearMonth.from(c.getCreatedAt()).equals(thisMonth))
                  .count());
          summary.put(
              "orderCount", customers.stream().mapToLong(CustomerSummaryDTO::getOrderCount).sum());
          summary.put(
              "totalSpent",
              customers.stream().mapToDouble(CustomerSummaryDTO::getTotalSpent).sum());
          return summary;
        },
        customerCache.getLoadedAt());
  }
}
