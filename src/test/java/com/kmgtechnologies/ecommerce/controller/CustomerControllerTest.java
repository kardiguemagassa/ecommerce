package com.kmgtechnologies.ecommerce.controller;

import org.springframework.test.context.ActiveProfiles;
import com.kmgtechnologies.ecommerce.dto.CustomerRequest;
import com.kmgtechnologies.ecommerce.dto.CustomerResponse;
import com.kmgtechnologies.ecommerce.exception.DuplicateResourceException;
import com.kmgtechnologies.ecommerce.exception.ResourceNotFoundException;
import com.kmgtechnologies.ecommerce.service.CustomerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CustomerController.class)
@ActiveProfiles("test")
class CustomerControllerTest {

    private static final String ALICE_JSON = """
            {"email": "alice@example.com", "fullName": "Alice"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CustomerService customerService;

    @Test
    void create_returns201WithLocation() throws Exception {
        when(customerService.create(any(CustomerRequest.class)))
                .thenReturn(new CustomerResponse(1L, "alice@example.com", "Alice"));

        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ALICE_JSON))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/customers/1"))
                .andExpect(jsonPath("$.email").value("alice@example.com"));
    }

    @Test
    void create_returns400WhenEmailIsInvalid() throws Exception {
        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "not-an-email", "fullName": ""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").value("Email must be valid"))
                .andExpect(jsonPath("$.errors.fullName").value("Full name is required"));

        verifyNoInteractions(customerService);
    }

    @Test
    void create_returns409WhenEmailAlreadyExists() throws Exception {
        when(customerService.create(any(CustomerRequest.class)))
                .thenThrow(new DuplicateResourceException("A customer with email 'alice@example.com' already exists"));

        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ALICE_JSON))
                .andExpect(status().isConflict());
    }

    @Test
    void findById_returnsCustomer() throws Exception {
        when(customerService.findById(1L)).thenReturn(new CustomerResponse(1L, "alice@example.com", "Alice"));

        mockMvc.perform(get("/api/customers/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Alice"));
    }

    @Test
    void findById_returns404WhenCustomerDoesNotExist() throws Exception {
        when(customerService.findById(99L)).thenThrow(new ResourceNotFoundException("Customer", 99L));

        mockMvc.perform(get("/api/customers/99"))
                .andExpect(status().isNotFound());
    }
}