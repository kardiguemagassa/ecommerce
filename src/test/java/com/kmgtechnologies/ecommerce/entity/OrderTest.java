package com.kmgtechnologies.ecommerce.entity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {

    private static final Instant NOW = Instant.parse("2026-10-22T10:00:00Z");

    private Order order;
    private Product laptop;
    private Product mouse;

    @BeforeEach
    void setUp() {
        Category electronics = new Category("Electronics");
        laptop = new Product("Laptop", null, new BigDecimal("999.99"), 10, electronics);
        mouse = new Product("Mouse", null, new BigDecimal("19.90"), 10, electronics);
        order = new Order(new Customer("alice@example.com", "Alice"), NOW);
    }

    @Test
    void newOrder_isPendingWithZeroTotal() {
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getTotalAmount()).isEqualByComparingTo("0");
        assertThat(order.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void addItem_updatesTotal() {
        order.addItem(laptop, 2);
        order.addItem(mouse, 3);

        assertThat(order.getItems()).hasSize(2);
        assertThat(order.getTotalAmount()).isEqualByComparingTo("2059.68");
    }

    @Test
    void addItem_snapshotsProductNameAndPrice() {
        order.addItem(laptop, 1);

        laptop.setName("Laptop Pro");
        laptop.setPrice(new BigDecimal("1499.00"));

        OrderItem item = order.getItems().getFirst();
        assertThat(item.getProductName()).isEqualTo("Laptop");
        assertThat(item.getUnitPrice()).isEqualByComparingTo("999.99");
        assertThat(order.getTotalAmount()).isEqualByComparingTo("999.99");
    }

    @Test
    void addItem_rejectsNonPositiveQuantity() {
        assertThatThrownBy(() -> order.addItem(laptop, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void confirm_movesPendingOrderToConfirmed() {
        order.confirm();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void confirm_rejectsAlreadyConfirmedOrder() {
        order.confirm();

        assertThatThrownBy(order::confirm)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CONFIRMED");
    }
}