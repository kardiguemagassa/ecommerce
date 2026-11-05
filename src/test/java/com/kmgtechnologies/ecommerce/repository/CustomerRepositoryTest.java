package com.kmgtechnologies.ecommerce.repository;

import com.kmgtechnologies.ecommerce.entity.Customer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(showSql = false)
@ActiveProfiles("test")
class CustomerRepositoryTest {

    @Autowired
    private CustomerRepository customerRepository;

    @Test
    void existsByEmail_findsSavedCustomer() {
        customerRepository.saveAndFlush(new Customer("alice@example.com", "Alice"));

        assertThat(customerRepository.existsByEmail("alice@example.com")).isTrue();
        assertThat(customerRepository.existsByEmail("bob@example.com")).isFalse();
    }

    @Test
    void save_rejectsDuplicateEmail() {
        customerRepository.saveAndFlush(new Customer("alice@example.com", "Alice"));

        assertThatThrownBy(() -> customerRepository.saveAndFlush(new Customer("alice@example.com", "Other")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findByExternalId_findsTheCustomerOfAKeycloakUser() {
        customerRepository.saveAndFlush(new Customer("sub-alice", "alice@example.com", "Alice"));

        assertThat(customerRepository.findByExternalId("sub-alice"))
                .get()
                .extracting(Customer::getEmail)
                .isEqualTo("alice@example.com");
        assertThat(customerRepository.findByExternalId("sub-bob")).isEmpty();
    }

    @Test
    void save_rejectsTwoCustomersForTheSameKeycloakUser() {
        customerRepository.saveAndFlush(new Customer("sub-alice", "alice@example.com", "Alice"));

        assertThatThrownBy(() -> customerRepository.saveAndFlush(new Customer("sub-alice", "other@example.com", "Other")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findByEmailAndExternalIdIsNull_onlyFindsCustomersNotLinkedYet() {
        customerRepository.saveAndFlush(new Customer("legacy@example.com", "Legacy"));
        customerRepository.saveAndFlush(new Customer("sub-alice", "alice@example.com", "Alice"));

        assertThat(customerRepository.findByEmailAndExternalIdIsNull("legacy@example.com")).isPresent();
        assertThat(customerRepository.findByEmailAndExternalIdIsNull("alice@example.com")).isEmpty();
    }
}
