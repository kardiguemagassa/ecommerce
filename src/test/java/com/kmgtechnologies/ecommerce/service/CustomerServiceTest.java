package com.kmgtechnologies.ecommerce.service;

import com.kmgtechnologies.ecommerce.dto.CustomerRequest;
import com.kmgtechnologies.ecommerce.dto.CustomerResponse;
import com.kmgtechnologies.ecommerce.entity.Customer;
import com.kmgtechnologies.ecommerce.exception.DuplicateResourceException;
import com.kmgtechnologies.ecommerce.exception.ResourceNotFoundException;
import com.kmgtechnologies.ecommerce.repository.CustomerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @InjectMocks
    private CustomerService customerService;

    @Test
    void create_normalizesEmailAndName() {
        when(customerRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CustomerResponse response = customerService.create(new CustomerRequest("  Alice@Example.COM ", " Alice "));

        assertThat(response.email()).isEqualTo("alice@example.com");
        assertThat(response.fullName()).isEqualTo("Alice");
    }

    @Test
    void create_rejectsDuplicateEmail() {
        when(customerRepository.existsByEmail("alice@example.com")).thenReturn(true);

        assertThatThrownBy(() -> customerService.create(new CustomerRequest("ALICE@example.com", "Alice")))
                .isInstanceOf(DuplicateResourceException.class);
        verify(customerRepository, never()).save(any());
    }

    @Test
    void findById_throwsWhenCustomerDoesNotExist() {
        when(customerRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Customer with id 99 not found");
    }
}