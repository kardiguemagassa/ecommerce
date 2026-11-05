package com.kmgtechnologies.ecommerce.service;

import com.kmgtechnologies.ecommerce.dto.CustomerResponse;
import com.kmgtechnologies.ecommerce.entity.Customer;
import com.kmgtechnologies.ecommerce.exception.ResourceNotFoundException;
import com.kmgtechnologies.ecommerce.repository.CustomerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    private static final String SUBJECT = "6f1c2a3b-0000-4000-8000-000000000001";

    @Mock
    private CustomerRepository customerRepository;

    @InjectMocks
    private CustomerService customerService;

    private void saveReturnsItsArgument() {
        when(customerRepository.saveAndFlush(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void provision_createsTheCustomerOnTheFirstCall_withNormalizedProfile() {
        when(customerRepository.findByExternalId(SUBJECT)).thenReturn(Optional.empty());
        when(customerRepository.findByEmailAndExternalIdIsNull("alice@example.com")).thenReturn(Optional.empty());
        saveReturnsItsArgument();

        CustomerResponse response = customerService.provision(
                new CustomerIdentity(SUBJECT, "  Alice@Example.COM ", true, " Alice Martin "));

        ArgumentCaptor<Customer> saved = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getExternalId()).isEqualTo(SUBJECT);
        assertThat(response.email()).isEqualTo("alice@example.com");
        assertThat(response.fullName()).isEqualTo("Alice Martin");
    }

    @Test
    void provision_keepsTheProfileOfALinkedCustomerInSyncWithKeycloak() {
        Customer alice = new Customer(SUBJECT, "alice@old.example.com", "Alice");
        when(customerRepository.findByExternalId(SUBJECT)).thenReturn(Optional.of(alice));
        saveReturnsItsArgument();

        customerService.provision(new CustomerIdentity(SUBJECT, "alice@example.com", true, "Alice Martin"));

        assertThat(alice.getEmail()).isEqualTo("alice@example.com");
        assertThat(alice.getFullName()).isEqualTo("Alice Martin");
        verify(customerRepository, never()).findByEmailAndExternalIdIsNull(anyString());
    }

    @Test
    void provision_linksACustomerCreatedBeforeAuthentication_whenKeycloakVerifiedTheEmail() {
        Customer legacy = new Customer("alice@example.com", "Alice");
        when(customerRepository.findByExternalId(SUBJECT)).thenReturn(Optional.empty());
        when(customerRepository.findByEmailAndExternalIdIsNull("alice@example.com")).thenReturn(Optional.of(legacy));
        saveReturnsItsArgument();

        customerService.provision(new CustomerIdentity(SUBJECT, "alice@example.com", true, "Alice Martin"));

        assertThat(legacy.getExternalId()).isEqualTo(SUBJECT);
    }

    @Test
    void provision_neverLinksAnExistingCustomerThroughAnUnverifiedEmail() {
        when(customerRepository.findByExternalId(SUBJECT)).thenReturn(Optional.empty());
        saveReturnsItsArgument();

        customerService.provision(new CustomerIdentity(SUBJECT, "alice@example.com", false, "Mallory"));

        verify(customerRepository, never()).findByEmailAndExternalIdIsNull(anyString());
        ArgumentCaptor<Customer> saved = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getExternalId()).isEqualTo(SUBJECT);
    }

    @Test
    void provision_truncatesNamesLongerThanTheColumn() {
        when(customerRepository.findByExternalId(SUBJECT)).thenReturn(Optional.empty());
        when(customerRepository.findByEmailAndExternalIdIsNull("alice@example.com")).thenReturn(Optional.empty());
        saveReturnsItsArgument();

        CustomerResponse response = customerService.provision(
                new CustomerIdentity(SUBJECT, "alice@example.com", true, "a".repeat(150)));

        assertThat(response.fullName()).hasSize(CustomerService.MAX_NAME_LENGTH);
    }

    @Test
    void findById_throwsWhenCustomerDoesNotExist() {
        when(customerRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Customer with id 99 not found");
    }
}
