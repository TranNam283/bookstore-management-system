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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
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
      Integer status,
      LocalDateTime createdAt,
      List<String> cities) {}

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
    Map<String, List<String>> citiesByUser =
        addressRepository.findAll().stream()
            .filter(a -> a.getUser() != null && a.getCity() != null)
            .collect(
                Collectors.groupingBy(
                    a -> a.getUser().getId(),
                    Collectors.mapping(Address::getCity, Collectors.toList())));

    List<User> customers =
        userRepository.findByRoleIn(List.of(3), Sort.by("createdAt").descending());
    return customers.stream()
        .map(
            u ->
                new CustomerBase(
                    u.getId(),
                    u.getFullname(),
                    u.getEmail(),
                    u.getStatus(),
                    u.getCreatedAt(),
                    citiesByUser.getOrDefault(u.getId(), List.of())))
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

      result.add(
          CustomerSummaryDTO.builder()
              .id(base.id())
              .fullname(base.fullname())
              .email(base.email())
              .status(base.status())
              .createdAt(base.createdAt())
              .cities(new ArrayList<>(cities))
              .orderCount(orders.size())
              .deliveredCount(delivered.size())
              .totalSpent(
                  delivered.stream()
                      .mapToDouble(o -> o.getTotal() == null ? 0 : o.getTotal())
                      .sum())
              .lastOrderAt(
                  orders.stream()
                      .map(OrderDTO::getCreatedAt)
                      .filter(Objects::nonNull)
                      .max(Comparator.naturalOrder())
                      .orElse(null))
              .build());
    }
    return result;
  }

  private static AdvancedFilter<CustomerSummaryDTO> buildFilter() {
    return new AdvancedFilter<CustomerSummaryDTO>()
        .text("fullname", CustomerSummaryDTO::getFullname)
        .text("email", CustomerSummaryDTO::getEmail)
        .text("city", CustomerSummaryDTO::getCities)
        .number("status", CustomerSummaryDTO::getStatus)
        .number("orderCount", CustomerSummaryDTO::getOrderCount)
        .number("deliveredCount", CustomerSummaryDTO::getDeliveredCount)
        .number("totalSpent", CustomerSummaryDTO::getTotalSpent)
        .date("createdAt", CustomerSummaryDTO::getCreatedAt)
        .date("lastOrderAt", CustomerSummaryDTO::getLastOrderAt)
        .keyword(CustomerSummaryDTO::getFullname, CustomerSummaryDTO::getEmail);
  }

  public SearchResponse<CustomerSummaryDTO> search(AdvancedSearchRequest request) {
    return customerFilter.search(
        getCustomerSummaries(),
        request,
        "createdAt",
        customers -> {
          Map<String, Object> summary = new LinkedHashMap<>();
          summary.put("count", customers.size());
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
