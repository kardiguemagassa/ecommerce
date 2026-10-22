package com.kmgtechnologies.ecommerce.repository;

import com.kmgtechnologies.ecommerce.dto.OrderSummaryResponse;
import com.kmgtechnologies.ecommerce.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @EntityGraph(attributePaths = "items")
    Optional<Order> findWithItemsById(Long id);

    @Query("""
            SELECT new com.kmgtechnologies.ecommerce.dto.OrderSummaryResponse(
                o.id, o.status, o.totalAmount, o.createdAt)
            FROM Order o
            WHERE o.customer.id = :customerId
            """)
    Page<OrderSummaryResponse> findSummariesByCustomerId(@Param("customerId") Long customerId, Pageable pageable);
}