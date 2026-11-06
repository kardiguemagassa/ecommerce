package com.kmgtechnologies.ecommerce.repository;

import com.kmgtechnologies.ecommerce.entity.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    boolean existsByEmail(String email);

    Optional<Customer> findByExternalId(String externalId);

    Optional<Customer> findByEmailAndExternalIdIsNull(String email);

    /** Customers whose name or e-mail contains the term, ignoring case (an empty term matches everyone). */
    @Query("""
            SELECT c FROM Customer c
            WHERE lower(c.fullName) like lower(concat('%', :term, '%'))
               OR lower(c.email) like lower(concat('%', :term, '%'))
            """)
    Page<Customer> search(@Param("term") String term, Pageable pageable);
}
