package com.kmgtechnologies.ecommerce.repository;

import com.kmgtechnologies.ecommerce.entity.Customer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
    void search_matchesTheNameOrTheEmail_ignoringCase_sortedAndPaged() {
        customerRepository.saveAndFlush(new Customer("sub-alice", "alice@example.com", "Alice Martin"));
        customerRepository.saveAndFlush(new Customer("sub-bob", "bob@example.com", "Bob Durand"));
        customerRepository.saveAndFlush(new Customer("sub-carla", "carla@martin.fr", "Carla Petit"));
        PageRequest byName = PageRequest.of(0, 10, Sort.by("fullName"));

        assertThat(customerRepository.search("MARTIN", byName).map(Customer::getFullName))
                .containsExactly("Alice Martin", "Carla Petit"); // name of one, e-mail of the other
        assertThat(customerRepository.search("bob@", byName).map(Customer::getFullName)).containsExactly("Bob Durand");
        assertThat(customerRepository.search("", PageRequest.of(1, 2, Sort.by("fullName"))))
                .extracting(Customer::getFullName)
                .containsExactly("Carla Petit");
        assertThat(customerRepository.search("", byName).getTotalElements()).isEqualTo(3);
    }

    @Test
    void findByEmailAndExternalIdIsNull_onlyFindsCustomersNotLinkedYet() {
        customerRepository.saveAndFlush(new Customer("legacy@example.com", "Legacy"));
        customerRepository.saveAndFlush(new Customer("sub-alice", "alice@example.com", "Alice"));

        assertThat(customerRepository.findByEmailAndExternalIdIsNull("legacy@example.com")).isPresent();
        assertThat(customerRepository.findByEmailAndExternalIdIsNull("alice@example.com")).isEmpty();
    }
}
