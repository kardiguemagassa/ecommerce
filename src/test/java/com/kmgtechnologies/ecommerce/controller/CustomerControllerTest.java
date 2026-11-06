package com.kmgtechnologies.ecommerce.controller;

import com.kmgtechnologies.ecommerce.dto.CustomerResponse;
import com.kmgtechnologies.ecommerce.dto.OrderSummaryResponse;
import com.kmgtechnologies.ecommerce.entity.OrderStatus;
import com.kmgtechnologies.ecommerce.exception.ImageTooLargeException;
import com.kmgtechnologies.ecommerce.exception.ResourceNotFoundException;
import com.kmgtechnologies.ecommerce.security.CurrentCustomer;
import com.kmgtechnologies.ecommerce.security.BffTestConfiguration;
import com.kmgtechnologies.ecommerce.security.SecurityConfig;
import com.kmgtechnologies.ecommerce.service.CustomerService;
import com.kmgtechnologies.ecommerce.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CustomerController.class)
@Import({SecurityConfig.class, BffTestConfiguration.class})
@ActiveProfiles("test")
class CustomerControllerTest {

    private static final CustomerResponse ALICE = new CustomerResponse(1L, "alice@example.com", "Alice Martin", null);

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
    void admin_searchesTheCustomers_byNameByDefault() throws Exception {
        when(customerService.search(eq("martin"), any(Pageable.class)))
                .thenAnswer(invocation -> new PageImpl<>(List.of(ALICE), invocation.getArgument(1), 1));

        mockMvc.perform(get("/api/admin/customers").param("q", "martin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].fullName").value("Alice Martin"))
                .andExpect(jsonPath("$.page.totalElements").value(1));

        verify(customerService).search(eq("martin"), argThat(pageable ->
                pageable.getPageSize() == 20 && pageable.getSort().equals(Sort.by("fullName"))));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void admin_customerSearch_rejectsAnUnknownSortProperty() throws Exception {
        mockMvc.perform(get("/api/admin/customers").param("sort", "externalId"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(customerService);
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void customerSearch_isForbiddenToCustomers() throws Exception {
        mockMvc.perform(get("/api/admin/customers"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verifyNoInteractions(customerService);
    }

    @Test
    void customerSearch_needsAuthentication() throws Exception {
        mockMvc.perform(get("/api/admin/customers"))
                .andExpect(status().isUnauthorized());
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

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void replaceAvatar_storesThePhotoOfTheSignedInCustomer() throws Exception {
        MockMultipartFile photo = new MockMultipartFile("file", "me.png", "image/png", new byte[]{1});
        when(currentCustomer.id()).thenReturn(1L);
        when(customerService.replaceAvatar(eq(1L), any())).thenReturn(
                new CustomerResponse(1L, "alice@example.com", "Alice Martin", "http://media.test/avatars/a.jpg"));

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/me/avatar").file(photo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarUrl").value("http://media.test/avatars/a.jpg"));
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void replaceAvatar_returns413ForATooHeavyPhoto() throws Exception {
        MockMultipartFile photo = new MockMultipartFile("file", "me.png", "image/png", new byte[]{1});
        when(currentCustomer.id()).thenReturn(1L);
        when(customerService.replaceAvatar(eq(1L), any())).thenThrow(new ImageTooLargeException(1024 * 1024));

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/me/avatar").file(photo))
                .andExpect(status().is(413))
                .andExpect(jsonPath("$.code").value("IMAGE_TOO_LARGE"));
    }

    @Test
    void replaceAvatar_requiresAuthentication() throws Exception {
        mockMvc.perform(multipart(HttpMethod.PUT, "/api/me/avatar")
                        .file(new MockMultipartFile("file", new byte[]{1})))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(customerService);
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void deleteAvatar_deletesThePhotoOfTheSignedInCustomer() throws Exception {
        when(currentCustomer.id()).thenReturn(1L);

        mockMvc.perform(delete("/api/me/avatar"))
                .andExpect(status().isNoContent());

        verify(customerService).deleteAvatar(1L);
    }
}
