package com.kmgtechnologies.ecommerce.repository;

import com.kmgtechnologies.ecommerce.dto.OrderSummaryResponse;
import com.kmgtechnologies.ecommerce.dto.TopSellingProductResponse;
import com.kmgtechnologies.ecommerce.entity.Category;
import com.kmgtechnologies.ecommerce.entity.Customer;
import com.kmgtechnologies.ecommerce.entity.Order;
import com.kmgtechnologies.ecommerce.entity.OrderStatus;
import com.kmgtechnologies.ecommerce.entity.Product;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.tuple;

@DataJpaTest
@ActiveProfiles("test")
class OrderRepositoryTest {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Customer alice;
    private Product laptop;

    @BeforeEach
    void setUp() {
        Category electronics = entityManager.persist(new Category("Electronics"));
        laptop = entityManager.persist(new Product("Laptop", null, new BigDecimal("999.99"), 10, electronics));
        alice = entityManager.persist(new Customer("alice@example.com", "Alice"));
        entityManager.flush();
    }

    private Order saveOrder(Instant createdAt, int quantity) {
        Order order = new Order(alice, createdAt);
        order.addItem(laptop, quantity);
        return orderRepository.saveAndFlush(order);
    }

    @Test
    void save_persistsOrderWithItemsAndStatus() {
        Order saved = saveOrder(Instant.parse("2026-10-22T10:00:00Z"), 2);
        entityManager.clear();

        Order reloaded = orderRepository.findWithItemsById(saved.getId()).orElseThrow();

        assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(reloaded.getTotalAmount()).isEqualByComparingTo("1999.98");
        assertThat(reloaded.getItems()).singleElement()
                .satisfies(item -> {
                    assertThat(item.getProductName()).isEqualTo("Laptop");
                    assertThat(item.getQuantity()).isEqualTo(2);
                });
    }

    @Test
    void findWithItemsById_fetchesItemsInOneQuery() {
        Order saved = saveOrder(Instant.parse("2026-10-22T10:00:00Z"), 1);
        entityManager.clear();

        Order reloaded = orderRepository.findWithItemsById(saved.getId()).orElseThrow();

        assertThat(Hibernate.isInitialized(reloaded.getItems())).isTrue();
    }

    @Test
    void findSummariesByCustomerId_returnsPagedSummariesNewestFirst() {
        saveOrder(Instant.parse("2026-10-20T10:00:00Z"), 1);
        saveOrder(Instant.parse("2026-10-21T10:00:00Z"), 2);
        saveOrder(Instant.parse("2026-10-22T10:00:00Z"), 3);

        Page<OrderSummaryResponse> page = orderRepository.findSummariesByCustomerId(
                alice.getId(), PageRequest.of(0, 2, Sort.by(Sort.Direction.DESC, "createdAt")));

        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent())
                .extracting(OrderSummaryResponse::totalAmount)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("2999.97"), new BigDecimal("1999.98"));
    }

    @Test
    void confirm_isPersisted() {
        Order saved = saveOrder(Instant.parse("2026-10-22T10:00:00Z"), 1);
        saved.confirm();
        entityManager.flush();
        entityManager.clear();

        assertThat(orderRepository.findById(saved.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void findTopSelling_ranksProductsByTotalQuantity() {
        Product mouse = entityManager.persist(new Product("Mouse", null, new BigDecimal("19.90"), 50, laptop.getCategory()));
        Order first = new Order(alice, Instant.parse("2026-10-22T10:00:00Z"));
        first.addItem(laptop, 1);
        first.addItem(mouse, 3);
        Order second = new Order(alice, Instant.parse("2026-10-22T11:00:00Z"));
        second.addItem(mouse, 2);
        orderRepository.saveAndFlush(first);
        orderRepository.saveAndFlush(second);

        assertThat(orderRepository.findTopSelling(Limit.of(10)))
                .extracting(TopSellingProductResponse::productName, TopSellingProductResponse::totalQuantity)
                .containsExactly(tuple("Mouse", 5L), tuple("Laptop", 1L));

        assertThat(orderRepository.findTopSelling(Limit.of(1))).hasSize(1);
    }
}