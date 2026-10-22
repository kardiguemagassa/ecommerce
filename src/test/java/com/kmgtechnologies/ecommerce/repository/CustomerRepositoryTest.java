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
}