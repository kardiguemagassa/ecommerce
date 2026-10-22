package com.kmgtechnologies.ecommerce.entity;

import com.kmgtechnologies.ecommerce.exception.InsufficientStockException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductTest {

    private final Product laptop = new Product("Laptop", null, new BigDecimal("999.99"), 5, new Category("Electronics"));

    @Test
    void decreaseStock_subtractsQuantity() {
        laptop.decreaseStock(5);

        assertThat(laptop.getStock()).isZero();
    }

    @Test
    void decreaseStock_rejectsQuantityAboveStock() {
        assertThatThrownBy(() -> laptop.decreaseStock(6))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessage("Insufficient stock for 'Laptop': requested 6, available 5");
        assertThat(laptop.getStock()).isEqualTo(5);
    }

    @Test
    void decreaseStock_rejectsNonPositiveQuantity() {
        assertThatThrownBy(() -> laptop.decreaseStock(0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}