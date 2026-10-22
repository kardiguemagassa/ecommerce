package com.kmgtechnologies.ecommerce.controller;

import com.kmgtechnologies.ecommerce.dto.OrderItemResponse;
import com.kmgtechnologies.ecommerce.dto.OrderResponse;
import com.kmgtechnologies.ecommerce.dto.OrderSummaryResponse;
import com.kmgtechnologies.ecommerce.entity.OrderStatus;
import com.kmgtechnologies.ecommerce.exception.EmptyCartException;
import com.kmgtechnologies.ecommerce.exception.InsufficientStockException;
import com.kmgtechnologies.ecommerce.exception.ResourceNotFoundException;
import com.kmgtechnologies.ecommerce.service.OrderService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    private static final Instant NOW = Instant.parse("2026-10-22T10:00:00Z");

    private static final OrderResponse ORDER = new OrderResponse(
            7L, 1L, OrderStatus.PENDING, new BigDecimal("99.80"), NOW,
            List.of(new OrderItemResponse(10L, "Keyboard", new BigDecimal("49.90"), 2, new BigDecimal("99.80"))));

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    @Test
    void placeOrder_returns201WithLocation() throws Exception {
        when(orderService.placeOrder(1L)).thenReturn(ORDER);

        mockMvc.perform(post("/api/customers/1/orders"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/customers/1/orders/7"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.totalAmount").value(99.80))
                .andExpect(jsonPath("$.items[0].productName").value("Keyboard"));
    }

    @Test
    void placeOrder_returns409WhenCartIsEmpty() throws Exception {
        when(orderService.placeOrder(1L)).thenThrow(new EmptyCartException(1L));

        mockMvc.perform(post("/api/customers/1/orders"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Empty cart"));
    }

    @Test
    void placeOrder_returns409WhenStockIsInsufficient() throws Exception {
        when(orderService.placeOrder(1L)).thenThrow(new InsufficientStockException("Keyboard", 2, 1));

        mockMvc.perform(post("/api/customers/1/orders"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Insufficient stock"));
    }

    @Test
    void listOrders_returnsNewestFirstByDefault() throws Exception {
        when(orderService.listOrders(eq(1L), any(Pageable.class)))
                .thenAnswer(invocation -> new PageImpl<>(
                        List.of(new OrderSummaryResponse(7L, OrderStatus.CONFIRMED, new BigDecimal("99.80"), NOW)),
                        invocation.getArgument(1), 1));

        mockMvc.perform(get("/api/customers/1/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("CONFIRMED"))
                .andExpect(jsonPath("$.page.totalElements").value(1));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(orderService).listOrders(eq(1L), captor.capture());
        assertThat(captor.getValue().getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    @Test
    void listOrders_rejectsUnknownSortProperty() throws Exception {
        mockMvc.perform(get("/api/customers/1/orders").param("sort", "customer.email"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(orderService);
    }

    @Test
    void getOrder_returnsOrder() throws Exception {
        when(orderService.getOrder(1L, 7L)).thenReturn(ORDER);

        mockMvc.perform(get("/api/customers/1/orders/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.createdAt").value("2026-10-22T10:00:00Z"));
    }

    @Test
    void getOrder_returns404WhenOrderDoesNotBelongToCustomer() throws Exception {
        when(orderService.getOrder(2L, 7L)).thenThrow(new ResourceNotFoundException("Order", 7L));

        mockMvc.perform(get("/api/customers/2/orders/7"))
                .andExpect(status().isNotFound());
    }
}