package com.kmgtechnologies.ecommerce.controller;

import com.kmgtechnologies.ecommerce.dto.CustomerResponse;
import com.kmgtechnologies.ecommerce.dto.OrderSummaryResponse;
import com.kmgtechnologies.ecommerce.entity.OrderStatus;
import com.kmgtechnologies.ecommerce.exception.ResourceNotFoundException;
import com.kmgtechnologies.ecommerce.security.CurrentCustomer;
import com.kmgtechnologies.ecommerce.security.SecurityConfig;
import com.kmgtechnologies.ecommerce.service.CustomerService;
import com.kmgtechnologies.ecommerce.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CustomerController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class CustomerControllerTest {

    private static final CustomerResponse ALICE = new CustomerResponse(1L, "alice@example.com", "Alice Martin");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CurrentCustomer currentCustomer;

    @MockitoBean
    private CustomerService customerService;

    @MockitoBean
    private OrderService orderService;

    /** Requests are authenticated by @WithMockUser: no token to decode, no Keycloak. */
    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void me_returnsTheProfileOfTheSignedInCustomer() throws Exception {
        when(currentCustomer.profile()).thenReturn(ALICE);

        mockMvc.perform(get("/api/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.fullName").value("Alice Martin"));
    }

    @Test
    void me_requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verifyNoInteractions(currentCustomer);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void admin_getsAnyCustomer() throws Exception {
        when(customerService.findById(1L)).thenReturn(ALICE);

        mockMvc.perform(get("/api/admin/customers/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("alice@example.com"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void admin_gets404ForUnknownCustomer() throws Exception {
        when(customerService.findById(99L)).thenThrow(new ResourceNotFoundException("Customer", 99L));

        mockMvc.perform(get("/api/admin/customers/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void admin_listsTheOrdersOfACustomer() throws Exception {
        when(orderService.listOrders(eq(1L), any(Pageable.class)))
                .thenAnswer(invocation -> new PageImpl<>(
                        List.of(new OrderSummaryResponse(7L, OrderStatus.CONFIRMED, new BigDecimal("99.80"),
                                Instant.parse("2026-10-22T10:00:00Z"))),
                        invocation.getArgument(1), 1));

        mockMvc.perform(get("/api/admin/customers/1/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(7));
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void customerSupport_isForbiddenToCustomers() throws Exception {
        mockMvc.perform(get("/api/admin/customers/1/orders"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verifyNoInteractions(customerService, orderService);
    }
}
