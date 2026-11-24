package com.bookstore.backend.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bookstore.backend.dto.OrderDTO;
import com.bookstore.backend.service.OrderService;
import com.bookstore.backend.service.StatisticsService;
import com.bookstore.backend.utils.CurrentUserResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@WebMvcTest(OrderController.class)
class OrderControllerTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @MockBean private OrderService orderService;

  @MockBean private StatisticsService statisticsService;

  @MockBean private CurrentUserResolver currentUserResolver;

  private OrderDTO orderDTO;

  @BeforeEach
  void setUp() {
    orderDTO =
        OrderDTO.builder()
            .id("o1")
            .orderCode("ORD001")
            .fullname("Nguyễn Văn A")
            .phone("0901234567")
            .speaddress("123 Nguyễn Trãi")
            .city("Hà Nội")
            .ward("Đống Đa")
            .paymethod("MOMO")
            .status(0)
            .total(150000d)
            .accountEmail("test@gmail.com")
            .createdAt(LocalDateTime.now())
            .items(List.of())
            .build();
  }

  // lấy tất cả đơn hàng
  @Test
  @WithMockUser
  void getAllOrders_shouldReturnOrders() throws Exception {
    Page<OrderDTO> page = new PageImpl<>(List.of(orderDTO));

    Mockito.when(
            orderService.getAllOrders(
                Mockito.eq(1),
                Mockito.eq(12),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any()))
        .thenReturn(page);

    Mockito.when(orderService.getOrderCountByStatus(Mockito.any())).thenReturn(Map.of(0, 5L));

    mockMvc
        .perform(get("/api/order").param("page", "1").param("limit", "12"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.orders[0].orderCode").value("ORD001"))
        .andExpect(jsonPath("$.total").value(1))
        .andExpect(jsonPath("$.totalByStatus[\"0\"]").value(5));
  }

  // lấy đơn hàng theo id
  @Test
  @WithMockUser
  void getOrderById_shouldReturnOrder() throws Exception {

    Mockito.when(orderService.getOrderById("o1")).thenReturn(orderDTO);

    mockMvc
        .perform(get("/api/order/{id}", "o1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.orderCode").value("ORD001"));
  }

  @Test
  @WithMockUser
  void updateOrderStatus_shouldUpdateStatus() throws Exception {

    orderDTO.setStatus(3);

    Mockito.when(currentUserResolver.resolveUserId(Mockito.any())).thenReturn(null);
    Mockito.when(orderService.updateOrderStatus("o1", 3, null)).thenReturn(orderDTO);

    mockMvc
        .perform(put("/api/order/{id}", "o1").with(csrf()).param("status", "3"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value(3));
  }

  // nhân viên xác nhận đơn: id nhân viên lấy từ token
  @Test
  @WithMockUser
  void updateOrderStatus_shouldPassStaffIdFromToken() throws Exception {
    orderDTO.setStatus(1);
    orderDTO.setStaffId("staff1");

    Mockito.when(currentUserResolver.resolveUserId("Bearer token")).thenReturn("staff1");
    Mockito.when(orderService.updateOrderStatus("o1", 1, "staff1")).thenReturn(orderDTO);

    mockMvc
        .perform(
            put("/api/order/{id}", "o1")
                .with(csrf())
                .header("Authorization", "Bearer token")
                .param("status", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.staffId").value("staff1"));
  }

  // tìm kiếm nâng cao
  @Test
  @WithMockUser
  void searchOrders_shouldReturnSearchResponse() throws Exception {
    Mockito.when(orderService.searchOrders(Mockito.any()))
        .thenReturn(
            com.bookstore.backend.dto.search.SearchResponse.<OrderDTO>builder()
                .items(List.of(orderDTO))
                .total(1)
                .totalPages(1)
                .page(1)
                .limit(20)
                .summary(Map.of("total", 150000d))
                .build());

    mockMvc
        .perform(
            post("/api/order/search")
                .with(csrf())
                .contentType("application/json")
                .content(
                    "{\"groups\":[{\"logic\":\"AND\",\"conditions\":"
                        + "[{\"field\":\"total\",\"operator\":\">=\",\"value\":\"100000\"}]}]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].orderCode").value("ORD001"))
        .andExpect(jsonPath("$.summary.total").value(150000d));
  }

  // tạo đơn hàng
  @Test
  @WithMockUser
  void createOrder_shouldReturnCreatedOrder() throws Exception {

    Mockito.when(orderService.createOrder(Mockito.any(), Mockito.eq("user1"))).thenReturn(orderDTO);

    mockMvc
        .perform(
            post("/api/order/user/{userId}", "user1")
                .with(csrf())
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(orderDTO)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.orderCode").value("ORD001"));
  }

  @Test
  @WithMockUser
  void getOrderByUserAndCode_shouldReturnOrder() throws Exception {

    Mockito.when(orderService.getOrderByUserAndCode("user1", "ORD001")).thenReturn(orderDTO);

    mockMvc
        .perform(get("/api/order/user/{userId}/{code}", "user1", "ORD001"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.orderCode").value("ORD001"));
  }

  @Test
  @WithMockUser
  void getOrdersByUser_shouldReturnOrders() throws Exception {

    Page<OrderDTO> page = new PageImpl<>(List.of(orderDTO));

    Mockito.when(orderService.getOrdersByUserAndStatus("user1", 1, 12, null)).thenReturn(page);

    mockMvc
        .perform(get("/api/order/user/{userId}", "user1").param("page", "1").param("limit", "12"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.orders[0].orderCode").value("ORD001"));
  }

  // Thống kê từ đơn hàng
  @Test
  @WithMockUser
  void getOrderStats_shouldReturnStats() throws Exception {

    Mockito.when(statisticsService.getOverview())
        .thenReturn(
            Map.of(
                "totalRevenue",
                1000000d,
                "todayRevenue",
                250000d,
                "totalSoldQuantity",
                200L,
                "todaySoldQuantity",
                50L));

    mockMvc
        .perform(get("/api/order/statistics"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalRevenue").value(1000000d))
        .andExpect(jsonPath("$.todayRevenue").value(250000d))
        .andExpect(jsonPath("$.totalSoldQuantity").value(200))
        .andExpect(jsonPath("$.todaySoldQuantity").value(50));
  }
}
